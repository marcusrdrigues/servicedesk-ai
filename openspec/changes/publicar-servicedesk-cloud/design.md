# Design

## Context

O backend (Spring Boot 4.1.2, Java 21, Spring AI 2.0.2) já tem API, fallback de modelo, RabbitMQ e e-mail (Mailpit/Resend),
mas **não usa banco**. A mudança `implementar-agente-servicedesk` termina com 2 tarefas pendentes (eval e doc) e não cobre
publicação nem auditoria. Motivação e escopo em `proposal.md`; requisitos em `specs/`.

Fluxo publicado: navegador → portal e proxy (Vercel) → API Spring Boot (Render) → OpenAI/Gemini → RabbitMQ (CloudAMQP) →
consumidor na **mesma aplicação** → Resend (HTTPS). A aplicação grava a auditoria no PostgreSQL (Neon).

**Limites dos planos gratuitos (conferidos em 2026-11-16 nas páginas oficiais):**

| Serviço | Limite que importa aqui |
|---|---|
| Render Free (web) | Suspende após 15 min sem tráfego de entrada; ~1 min para retomar; 750 h/mês por workspace; **portas 25, 465 e 587 de saída bloqueadas**; disco efêmero. RAM e CPU não constam da página consultada |
| Neon Free | Permanente, sem cartão; 1 GB por projeto; 100 CU-h/mês por projeto; **autosuspend em 5 min** (retoma na próxima consulta) |
| CloudAMQP Little Lemur | RabbitMQ compartilhado; 20 conexões; 100 filas; 1 milhão de mensagens/mês; 10 mil mensagens enfileiradas; instância sem publicar nem consumir por 2 meses é apagada após aviso. **A página não afirma TLS**: conferir no painel |
| Resend Free | 100 e-mails/dia e 3.000/mês; 3 domínios. A regra de destinatário de `onboarding@resend.dev` **não foi confirmada** nas páginas consultadas |
| Vercel Hobby | Função com duração máxima de 300 s; 1 milhão de invocações; **elegibilidade (uso pessoal, não comercial) não confirmada** nas páginas consultadas: o Marcus precisa confirmar que este uso é pessoal/educacional |

## Goals / Non-Goals

**Goals:**
- Publicar o laboratório sem custo e sem expor segredo.
- Trilha de auditoria útil e honesta (nunca afirma entrega).
- Deploy que só acontece depois da CI.

**Non-Goals:**
- Outbox, atomicidade entre banco e broker, autenticação por usuário, domínio próprio, keep-alive, alta disponibilidade.

## Decisions

### 1. Auditoria atrás de uma porta, com JDBC puro
`AuditoriaPort` na camada de aplicação; `JdbcAuditoriaAdapter` na infraestrutura usa `JdbcClient` (Spring JDBC). Descartei JPA:
são duas tabelas e quatro `UPDATE`s condicionais, e JPA esconderia exatamente o SQL que precisa ser controlado.
Descartei Spring Data JDBC pelo mesmo motivo. Flyway versiona o esquema.

### 2. Estados que só avançam
Cada status tem uma **ordem** numérica gravada junto; o `UPDATE` leva `WHERE evento_id = ? AND ordem < ?`. O consumidor pode
terminar antes de o publicador atualizar a auditoria, e a atualização condicional garante que o estado mais avançado vence.

| Notificação | Ordem | | Execução | Ordem |
|---|---|---|---|---|
| `PUBLICACAO_PENDENTE` | 10 | | `ANALISADA` | 10 |
| `PUBLICACAO_CONFIRMADA`, `PUBLICACAO_INCERTA`, `PUBLICACAO_FALHOU` | 20 | | `PUBLICACAO_PENDENTE` | 20 |
| `FALHA_ENVIO` | 30 | | `PUBLICACAO_CONFIRMADA`, `PUBLICACAO_INCERTA`, `PUBLICACAO_FALHOU` | 30 |
| `ACEITO_PELO_PROVEDOR` | 40 | | | |

`FALHA_ENVIO` (30) pode ser superado por `ACEITO_PELO_PROVEDOR` (40) numa tentativa seguinte. Não existe `ENTREGUE`:
`ACEITO_PELO_PROVEDOR` significa que a API de e-mail aceitou a solicitação, só isso.

### 3. Auditoria não derruba o atendimento
Falha ao gravar é capturada, vai para o log (id da requisição, sem conteúdo) e o fluxo segue. Alternativa descartada:
exigir a auditoria e devolver 503. O banco tem autosuspend de 5 min e retomada em instantes; derrubar um atendimento já
analisado (que custou chamada de modelo) por causa da trilha seria pior que uma lacuna registrada em log. Consequência
documentada: a auditoria pode ter lacunas. Nenhuma transação fica aberta durante modelo ou e-mail: cada gravação é um
comando curto em autocommit.

### 4. Janelas de falha sem Outbox
Banco e broker não são atômicos. Janelas: (a) publica e o processo cai antes de gravar `PUBLICACAO_CONFIRMADA`, então a
notificação fica `PUBLICACAO_PENDENTE` embora tenha sido publicada; (b) grava `PUBLICACAO_PENDENTE`, a publicação falha e o
estado vira `PUBLICACAO_FALHOU` só se o processo sobreviver; (c) timeout de confirmação deixa `PUBLICACAO_INCERTA`. Sem Outbox
(não aprovado), isso é documentado, não escondido.

### 5. O provedor que respondeu entra na auditoria
`AnaliseChamado` ganha o componente `provedor`, preenchido pelo adaptador. Mantive um construtor de 5 argumentos (provedor
nulo) para não reescrever todos os testes e dublês. Descartei devolver um par `(análise, provedor)` pela porta: mudaria a
assinatura em todos os chamadores.

### 6. Perfil `cloud` fecha o que o laboratório relaxa
`application-cloud.yaml` e uma validação de inicialização exigem `sslmode=require` no JDBC e `amqps://` no broker, e
fixam o pool em 3/0. Descartei apenas documentar: um erro de configuração que derruba o TLS não pode subir em silêncio.
`FORCE_PRIMARY_FAILURE`, citado na skill, **não foi criado**: uma chave de falha simulada em código de produção é superfície
de ataque, e o fallback já é provado por testes automatizados.

### 7. Orçamento de tempo alinhado
Backend na nuvem: `IA_TIMEOUT=12s` por chamada, `IA_ORCAMENTO_TOTAL=25s`, confirmação do broker 5 s. Proxy: timeout de 35 s e
`maxDuration` de 40 s na função (o limite do plano é 300 s). A ordem é: chamada < orçamento do backend < proxy < função.
O proxy **não repete** o POST: uma repetição geraria outra execução e outro e-mail, e o timeout do proxy não cancela o
backend. Cold start do Render (~1 min) pode estourar o primeiro pedido; a documentação manda consultar o healthcheck antes.

### 8. Proxy fino, sem dependências
Função Node da Vercel (`api/triagem.js`) com `fetch` nativo e `crypto.timingSafeEqual`. Valida, descarta campo extra,
usa `BACKEND_URL` da configuração e envia `X-API-Key`. Descartei framework (Next.js): um formulário e uma função não
justificam dependência nem build. Descartei rate limit no proxy: em função sem estado ele não limita de verdade; o limite
real está no backend (por IP) e no código de acesso.

### 9. Deploy condicionado à CI
`render.yaml` com `autoDeployTrigger: checksPass` (documentação do Render: "deploy only if the linked branch's CI checks
pass"). Os secrets ficam com `sync: false`, informados no painel. Vercel: projeto sem integração Git automática nesta etapa;
o deploy do portal é acionado de forma controlada.

### 10. Dependências (aprovadas pela execução da skill)
`spring-boot-starter-jdbc`, `postgresql` (BSD-2-Clause, mantido pelo PostgreSQL JDBC Project), `flyway-core` e
`flyway-database-postgresql` (Apache 2.0, Redgate), versões do BOM do Spring Boot. Teste: `testcontainers-postgresql` (MIT).
Portal: nenhuma.

## Risks / Trade-offs

- **[LGPD] Portal público recebe texto livre e o envia a OpenAI e Gemini** → só dados fictícios; código de acesso;
  a auditoria não guarda o texto; contrato e art. 33 seguem não confirmados.
- **[Código compartilhado] não é autenticação por usuário** → declarado na documentação como proteção de laboratório.
- **[Suspensão do Render] consumidor parado enquanto suspenso** → mensagens esperam na fila (persistente); sem keep-alive.
- **[Auditoria com lacunas]** → decisão 3; log com id da requisição.
- **[CloudAMQP sem TLS confirmado]** → conferir `amqps` e a porta no painel antes de apontar o broker; a aplicação recusa `amqp://` no perfil cloud.
- **[Custo de modelo]** OpenAI não é gratuita → teto de tokens, orçamento de tempo, limite por IP e código de acesso.
- **[Elegibilidade Hobby]** → confirmar uso pessoal/educacional antes de publicar o portal.

## Migration Plan

Sem dados anteriores: Flyway cria o esquema no primeiro boot. **Rollback:** redeploy do commit anterior no Render e da
implantação anterior na Vercel; as migrações são só aditivas. Encerrar recursos: ver `docs/deploy.md`.

## Open Questions

- Região de Render, Neon e CloudAMQP: escolher a mesma região (ou a mais próxima) conforme disponibilidade no momento da criação. Não altera specs nem tarefas.
