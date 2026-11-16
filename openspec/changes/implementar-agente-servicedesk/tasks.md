# Tasks

## 1. Preparação e dependências

- [x] 1.1 Criar a estrutura de pacotes (`dominio`, `aplicacao`, `infraestrutura`, `web`) em `br.com.coti.servicedesk_ai` e um teste de arquitetura (sem `org.springframework.ai` em `dominio` e `aplicacao`); verificar com `./mvnw test` passando.
- [x] 1.2 **Após aprovação do Marcus**, adicionar `spring-ai-bom` e `spring-ai-starter-model-openai` com versão fixada, `spring.ai.model.embedding: none`; verificar `./mvnw -q dependency:tree` sem outro artefato Spring AI avulso.
- [x] 1.3 Conferir na documentação oficial: BOM compatível com Boot 4.1.2, propriedades `spring.ai.openai.*`, base-url e caminho do Gemini compatível com OpenAI, suporte a saída estruturada no Gemini, propriedades de retry do listener AMQP; registrar o resultado como comentário no topo de `application.yaml` e corrigir `design.md` onde divergir.
- [x] 1.4 Criar `compose.yaml` (RabbitMQ com painel e Mailpit), `.env.example` só com nomes de variáveis, e incluir `.env` no `.gitignore`; verificar `docker compose up -d` saudável e `git check-ignore .env` retornando o arquivo.
- [x] 1.5 Criar `ConfiguracaoServicedesk` (`@ConfigurationProperties` + `@Validated`: destinatário, remetente, assunto, limites, timeouts, tentativas) e teste de que a aplicação falha ao subir sem destinatário, citando o nome da propriedade e nunca valor.

## 2. Domínio e análise do chamado

- [x] 2.1 Criar enums `Categoria` e `Prioridade`, record `AnaliseChamado`, `ValidadorAnalise` e exceções de domínio; verificar testes unitários cobrindo enum inválido, campo faltando, texto vazio e acima do teto.
- [x] 2.2 Criar `AnalisarChamadoService` e as portas `AnalisadorChamadoPort`, `PublicadorNotificacaoPort`; verificar teste com portas falsas: decisão `autorizaEmail && enviarEmail` nas quatro combinações, sem publicar quando a análise falha.
- [x] 2.3 Escrever os arquivos de prompt versionados (`prompts/analisar-chamado.system.v1.st` e `.user.v1.st`) com delimitadores e neutralização de `<` `>`; verificar teste do montador com entrada contendo `</chamado>` e quebras de linha.
- [x] 2.4 Criar `OpenAiAnalisadorAdapter` e `GeminiAnalisadorAdapter` (um `ChatClient` por provedor, `max-retries: 0`, timeout, `max-tokens`, retry só em transitório com backoff e jitter, orçamento de tempo total); verificar com servidor HTTP falso: 200 válido, 429, 500, timeout, JSON inválido, enum inválido, 401 sem retry.
- [x] 2.5 Criar `AnalisadorComFallback` e `AnaliseIndisponivelException`; verificar teste: principal ok não chama reserva; falha de chamada e saída inválida chamam reserva; ambos falhando lança a exceção.

## 3. Notificação, fila e e-mail

- [x] 3.1 Declarar topologia RabbitMQ (exchange, fila durável, DLX e DLQ) e configurar `publisher-confirm-type: correlated`, `publisher-returns`, `mandatory`; verificar com Testcontainers que as filas existem e a mensagem é persistente.
- [x] 3.2 Implementar `RabbitPublicadorNotificacaoAdapter` com `CorrelationData`, espera pela confirmação e timeout, `notificacaoId` UUID e mensagem sem campo de destinatário; verificar com Testcontainers: ack → `ENFILEIRADO`; sem rota ou nack/timeout → `FalhaPublicacaoException`.
- [x] 3.3 Implementar `EnviadorEmailPort` com `SmtpEnviadorEmailAdapter` (Mailpit) e `ResendEnviadorEmailAdapter` (`RestClient`, `Authorization: Bearer`, HTTPS, timeout), escolhidos por perfil; verificar com Mailpit em container e servidor HTTP falso do Resend, incluindo chave ausente no perfil `nuvem` derrubando a subida.
- [x] 3.4 Montar o e-mail com modelo fixo, assunto e remetente do servidor e escape de campos; verificar teste com HTML e CR/LF em `nome` e `resumo` sem injeção de cabeçalho.
- [x] 3.5 Implementar o consumidor com 3 tentativas (backoff), `RejectAndDontRequeueRecoverer`, erro de conversão sem retry e deduplicação por `notificacaoId`; verificar com Testcontainers e Mailpit: sucesso na 2ª tentativa, 3 falhas → DLQ, mensagem malformada → DLQ, redelivery sem segundo e-mail.

## 4. API HTTP e segurança

- [x] 4.1 Criar `ChamadoController` (`POST /api/chamados`) com DTOs validados (`nome` ≤ 100, `descricao` 20–4000, `autorizaEmail` obrigatório) e resposta com análise e status `ENFILEIRADO` ou `NAO_SOLICITADO` (nunca `ENVIADO`); verificar com testes MockMvc: 200/202 e quatro casos de 400 sem chamar modelo.
- [x] 4.2 Implementar filtro `X-API-Key` com `MessageDigest.isEqual`, falha de subida sem segredo e `/actuator/health` público sem detalhes; verificar com testes: 401 sem chave e com chave errada (antes de validar corpo), subida falha citando o nome da variável.
- [x] 4.3 Implementar limite de taxa (429) e CORS restrito à origem do portal configurada; verificar com testes: excedente → 429 sem chamar modelo, origem estranha sem cabeçalhos CORS.
- [x] 4.4 Implementar `@RestControllerAdvice` com ProblemDetail (503 análise indisponível, 503 falha de publicação, 500 genérico com id da requisição), `server.error.include-*: never` e id de requisição no log (sem conteúdo do chamado); verificar teste que varre o corpo de 503/500 sem stack, classe, chave nem texto do modelo, e teste de log sem `nome`/`descricao`.

## 5. Eval, documentação e verificação final

- [ ] 5.1 Criar o conjunto de eval em `src/test/resources/eval/` (~20 casos, com adversariais de injeção na descrição e pedido de outro destinatário) e um runner manual fora do CI; verificar rodando uma vez com modelo real e registrando a nota (sem afirmar qualidade antes disso).
- [ ] 5.2 Escrever `README.md` (execução local, variáveis, perfis, aviso de dados fictícios por LGPD, at-least-once, significado de `ENFILEIRADO`) e `docs/arquitetura.md` com as decisões; verificar executando os comandos documentados do zero.
- [x] 5.3 Teste de integração ponta a ponta com Testcontainers e provedores falsos: chamado autorizado → 202 → e-mail no Mailpit; ambos os provedores falham → 503 e fila vazia; e conferir que nenhum arquivo versionado contém chave (busca por padrões `sk-`, `AIza`, `re_`); verificar `./mvnw verify` passando.

## Workflow follow-up

- Revisão OWASP (skill `revisao-producao-ia`) antes de qualquer uso com dado real.
- Arquivar a mudança após a revisão.
