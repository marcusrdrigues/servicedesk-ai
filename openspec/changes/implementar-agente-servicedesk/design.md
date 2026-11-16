# Design

## Context

Projeto greenfield: só existe `ServicedeskAiApplication`. Spring Boot 4.1.2, Java 21, `pom.xml` com amqp, mail, validation, webmvc e actuator; **sem Spring AI**. API e consumidor ficam na mesma aplicação (laboratório). Backend em Docker no Render, portal na Vercel. Motivação e escopo em `proposal.md`; requisitos em `specs/`.

**Degrau da escada de IA:** uma chamada ao modelo com prompt e saída estruturada. Descartei RAG (não há base de conhecimento a consultar), workflow multi-passo e agente com tools (nada a decidir que o código não decida; cada degrau a mais custa latência e superfície de ataque). O "agente" do enunciado é, tecnicamente, um **classificador com decisão validada**; o fluxo e o destinatário ficam em código.

**Versões (conferidas na implementação, 2026-11-16):** Spring AI 2.0.2, linha 2.0 da skill. O `OpenAiChatModel` é montado com `OpenAiChatOptions` e o retry fica no SDK. O Gemini usa `https://generativelanguage.googleapis.com/v1beta/openai` (o SDK acrescenta `/chat/completions`) e a documentação do Google descreve structured output. Detalhes na seção "Desvios registrados na implementação".

## Goals / Non-Goals

**Goals:**
- Fronteira clara: o caso de uso conhece portas, não conhece SDK de IA, AMQP nem HTTP do Resend.
- Falha dos dois provedores nunca gera publicação nem resposta inventada.
- Pontos de falha testáveis sem rede (dublês pelas portas).

**Non-Goals:**
- Persistir chamados, autenticação por usuário, painel, RAG, tools, fila para a análise em si (a análise é síncrona).
- Eval com modelo real no CI (ver Decisão 10).

## Decisions

### 1. Arquitetura em camadas, dependência para dentro
`dominio` (enums `Categoria`, `Prioridade`, `StatusNotificacao`, record `AnaliseChamado`, exceções) → `aplicacao` (casos de uso `AnalisarChamadoService`, portas `AnalisadorChamadoPort`, `PublicadorNotificacaoPort`, `EnviadorEmailPort`) → `infraestrutura` (adaptadores Spring AI, RabbitMQ, SMTP, Resend) e `web` (controller, filtro, handler). Teste de arquitetura impede `org.springframework.ai` em `dominio` e `aplicacao`. Nomes de domínio em português, termos técnicos em inglês.

### 2. Fallback como composição, não como `if` espalhado
`AnalisadorComFallback` implementa `AnalisadorChamadoPort` e recebe duas implementações (`OpenAiAnalisadorAdapter`, `GeminiAnalisadorAdapter`). O padrão usado é **Chain of Responsibility de dois elos**, porque resolve um problema real (reserva) e deixa o caso de uso ignorante de quantos provedores existem. Cada adaptador faz chamada **e** conversão/validação; qualquer falha de um deles, inclusive saída inválida, passa ao próximo. Ambos falhando lança `AnaliseIndisponivelException` → 503.
Descartei o `ChatModel` com retry genérico do Spring: ele não cobre "saída inválida" e esconderia qual provedor respondeu.

### 3. Gemini pelo endpoint compatível com OpenAI
Dois `ChatClient` com `base-url`, chave e modelo próprios. O Spring AI anexa um caminho padrão de completions à `base-url`; o Gemini usa prefixo diferente, então é provável precisar sobrescrever o caminho. **Não conferido:** URL base do Gemini, propriedade de caminho e suporte do Gemini a `response_format`/JSON Schema nativo. Se não houver schema nativo, a validação em código (Decisão 4) continua valendo e o prompt pede JSON estrito.
Como o starter autoconfigura um único `OpenAiChatModel`, os dois clientes serão criados manualmente a partir de `@ConfigurationProperties` próprio. Alternativa descartada: um único bean com troca de `base-url` em runtime (condição de corrida e estado compartilhado).

### 4. Saída estruturada e validação em código
`record AnaliseModelo` com enums; conversão por `.entity(...)`. Depois, `ValidadorAnalise` em código puro: campos obrigatórios, enums, `resumo`/`orientacao` não vazios e com teto de tamanho. Não há remendo por regex. Sem reenvio com o erro: o reenvio é substituído pelo fallback para o outro provedor, e isso é o que o enunciado pede; fica registrado como desvio consciente da regra "até duas novas tentativas", porque a latência de somar reenvios e fallback estouraria o tempo total.

### 5. Retry em um único ponto
`max-retries: 0` no cliente OpenAI do Spring. A aplicação faz **uma** tentativa extra por provedor apenas em erro transitório (429, 5xx, timeout), com backoff exponencial e variação aleatória, sob um **orçamento de tempo total** configurável. 400/401/403 não repetem (usar a distinção `TransientAiException`/`NonTransientAiException`; **conferir** na versão do projeto).

### 6. Decisão de envio em código
`AnalisarChamadoService` calcula `publicar = autorizaEmail && analise.enviarEmail()`. O modelo opina em `enviarEmail`, mas a autorização do usuário é a trava; o valor do modelo sozinho nunca publica. O destinatário vem de `@ConfigurationProperties` `servicedesk.notificacao.destinatario` com `@Validated` (e-mail válido, obrigatório). A mensagem publicada **não tem campo de destinatário**; o consumidor lê da configuração. Assim nenhuma injeção de prompt altera para quem o e-mail vai.

### 7. Publicação confiável
Fila durável, `deliveryMode` persistente, `publisher-confirm-type: correlated` e `publisher-returns` com `mandatory`. O serviço usa `CorrelationData` e espera a confirmação com timeout; negativa, timeout ou *return* viram `FalhaPublicacaoException` → 503. Só após `ack` o status é `ENFILEIRADO` (HTTP 202). Descartei *transactional outbox*: sem banco no laboratório, e o custo não se justifica; o risco residual (confirmado, mas o processo cai antes de responder) causa no máximo um chamado reenviado pelo cliente, não perda silenciosa.
Topologia: exchange direta, fila principal, exchange e fila DLQ (declaradas por `Declarables`), com `x-dead-letter-exchange` na principal.

### 8. Consumo, retentativas e DLQ
Listener com retry stateless do Spring AMQP: 3 tentativas no total, backoff exponencial, e `RejectAndDontRequeueRecoverer` para a mensagem cair na DLQ via DLX. Erro de conversão é fatal (sem retry). **Conferir** as propriedades `spring.rabbitmq.listener.simple.retry.*` em Boot 4.1.2.
Entrega é **at-least-once**. Deduplicação por `notificacaoId` (UUID gerado na publicação) com *cache* em memória de IDs já processados. Limite honesto: a deduplicação em memória some no restart e não funciona com mais de uma instância; para o laboratório (instância única) é aceitável, e a spec não promete exatamente uma vez. Alternativa para produção: tabela com chave única.

### 9. Dependência nova: Spring AI (precisa de aprovação)
`spring-ai-bom` em `dependencyManagement` e `spring-ai-starter-model-openai`, com versão fixada e fonte oficial (Maven Central, Apache 2.0, mantida pelo projeto Spring/VMware). Embedding desligado (`spring.ai.model.embedding: none`). **Esta é uma pergunta de aprovação antes da tarefa 1.2**, conforme a regra de dependência nova. Sem SDK do Resend: `RestClient` do Spring chama `POST /emails` com `Authorization: Bearer`. Descartei SMTP do Resend na nuvem porque hospedagens costumam bloquear portas SMTP de saída (a conferir no Render), e HTTPS é o que o enunciado pede.

### 10. Testes e eval
- Unitários: validador, decisão de envio, montagem do e-mail, fallback com portas falsas.
- Adaptadores de modelo contra servidor HTTP falso (via `base-url`): 429, 500, timeout, JSON inválido, enum inválido.
- RabbitMQ real com Testcontainers: confirmação, *return*, 3 tentativas → DLQ, persistência.
- Mailpit: teste do adaptador SMTP contra container; Resend contra servidor HTTP falso.
- Web: 400, 401, 429, 503, 202, ausência de vazamento no corpo.
- **Eval** versionado em `src/test/resources/eval/` (cerca de 20 casos: categoria e prioridade esperadas, incluindo casos adversariais de injeção na descrição). Roda manualmente com modelo real, fora do CI, a cada mudança de prompt, modelo ou parâmetro. Nota mínima inicial a definir com os primeiros casos; **não se afirma qualidade sem rodar**.

### 11. Segurança da API
Filtro de servlet (ou Spring Security, se aprovado como dependência; hoje não está no `pom.xml`) que compara `X-API-Key` com `MessageDigest.isEqual`. Preferi o filtro simples: um segredo estático não justifica trazer o Spring Security agora; se o laboratório crescer para múltiplos clientes, migrar. Limite de taxa em memória por chave/IP (instância única). ProblemDetail com `@RestControllerAdvice`; `server.error.include-*: never`.

### 12. Prompt e parâmetros
Prompt em `src/main/resources/prompts/analisar-chamado.system.v1.st` e `.user.v1.st`, com `nome` e `descricao` entre delimitadores `<chamado>` e instrução de tratá-los como dado, neutralizando `<` e `>` na entrada. Modelo identificado com versão em configuração (sem alias), temperatura baixa, `max-tokens` com teto. Valores concretos definidos na implementação e justificados em comentário. O prompt **não** é controle de segurança: as travas estão nas Decisões 6, 7 e 11.

### 13. Configuração por perfil
`local`: Mailpit SMTP e RabbitMQ do `compose.yaml`. `nuvem`: Resend por HTTPS e RabbitMQ gerenciado. `.env.example` só com nomes. `.env` no `.gitignore` (hoje o `.gitignore` não o cobre; ajustar).

## Risks / Trade-offs

- **[LGPD] Dado pessoal vai a dois provedores no exterior** → contrato de operador, sem treino e retenção, mais base do art. 33, **não confirmados**. Mitigação: usar só dados fictícios no laboratório até confirmar; aviso no README; mascaramento antes do envio fica como evolução e depende dessa confirmação.
- **[Injeção de prompt]** → modelo só classifica; destinatário, trava de autorização e modelo de e-mail são código. Eval adversarial.
- **[Custo sem teto]** → `max-tokens`, timeout, orçamento de tempo, limite de taxa, segredo da API.
- **[Latência]** análise síncrona com possível fallback pode somar dezenas de segundos → timeout por chamada e total; mensagem clara no 503.
- **[Duplicidade]** at-least-once → dedup em memória (limite descrito na Decisão 8).
- **[Spring AI/Gemini não conferidos]** → tarefa 1.3 confere antes de codar; se o endpoint não suportar schema nativo, vale a validação em código.
- **[Mensagem confirmada, resposta perdida]** → cliente pode reenviar e gerar segundo e-mail; aceito e documentado.
- **[Gemini como fallback usa o mesmo conteúdo]** → mesma exposição de dados nos dois provedores, já listada no risco de LGPD.

## Migration Plan

Greenfield: sem migração. Deploy: imagem Docker no Render com variáveis de ambiente; **rollback** é redeploy da imagem anterior (prompt e modelo vão na imagem e na configuração, então voltam juntos). Fila e DLQ são declaradas pela aplicação no início.

## Open Questions

- Existe contrato com OpenAI e Google que cubra operador, não uso para treino, retenção e transferência internacional? Enquanto não confirmado, só dados fictícios. (Não altera specs nem tarefas.)
- O Render permite SMTP de saída? Irrelevante para o desenho (usa HTTPS), registrado só como justificativa.

## Desvios registrados na implementação

O que mudou em relação ao texto acima, e por quê. Onde houver conflito, vale esta seção.

- **Dependência (Decisão 9):** módulos `spring-ai-openai` e `spring-ai-client-chat` (BOM 2.0.2) em vez do starter.
  O starter autoconfigura um só modelo e exige chave; aqui são dois endpoints com chave, modelo e base-url próprios.
  O Gemini não precisou de `completions-path` próprio.
- **Mensagem com destinatário (Decisões 6 e 7):** o evento `EmailSolicitado` (`eventoId`, `destinatario`, `assunto`,
  `corpo`) é montado **antes** de publicar, e o `destinatario` é o `EMAIL_TO` do servidor. Isso corrige o texto
  original, que dizia que a mensagem não teria destinatário, e acompanha o cenário da spec ("o destinatário nela é
  o configurado no servidor"). O modelo continua sem ver nem escolher o destinatário.
- **Nomes e variáveis:** topologia `servicedesk.events` / `email.solicitado` / `servicedesk.email` / `servicedesk.dlx`
  / `email.falhou` / `servicedesk.email.dlq`; variáveis `EMAIL_TO`, `EMAIL_FROM`, `EMAIL_MODE`, `SMTP_HOST`,
  `SMTP_PORT`, `RESEND_API_KEY`, `RABBITMQ_URL`. O modo de e-mail é escolhido por `EMAIL_MODE` (`smtp` ou `resend`),
  não por perfil do Spring.
- **Classes:** um só `ChatClientAnalisadorAdapter` com duas instâncias (OpenAI e Gemini), `NotificacaoPublisher`,
  `EmailConsumer`, `EmailService` (consumo com deduplicação) e `SmtpEmailSender`/`ResendEmailSender`.
- **Retry do consumidor (Decisão 8):** no Boot 4 `max-attempts` foi removido; vale `max-retries`, que conta as
  retentativas além da primeira. Configurado `max-retries: 2` para 3 tentativas totais, confirmado por teste.
- **DLQ sem motivo na mensagem (conflito com a spec):** a falha definitiva é rejeitada sem requeue e o broker a
  encaminha à DLQ pela dead-letter exchange. A mensagem na DLQ **não** carrega o motivo da última falha (só o
  cabeçalho `x-death` do RabbitMQ); o motivo fica no log com o `eventoId`. A spec `notificacao-email`, requisito
  "Consumo com retentativas e DLQ", pede o motivo na mensagem. **Decisão pendente do Marcus:** manter assim e ajustar
  a spec, ou trocar por `RepublishMessageRecoverer`, que grava o motivo em cabeçalho.
- **Segredos (Decisão 11 e spec `seguranca-api`):** acrescentada a validação `@ValorDefinido`, porque o Spring Boot
  deixa `${VARIAVEL}` sem valor como texto literal ao fazer o binding de propriedades.
- **Ponto sem sorteio de `jdk.random`:** `AnalisadorComRetry` usa `java.util.Random`, pois o JRE enxuto da imagem
  Docker não traz o módulo `jdk.random`.
- **Docker:** `compose.yaml` ganhou o serviço da aplicação, volume do RabbitMQ, healthcheck, `depends_on` com
  condição de saúde e portas em `127.0.0.1`, e há `Dockerfile` multi-estágio com usuário sem privilégio.
