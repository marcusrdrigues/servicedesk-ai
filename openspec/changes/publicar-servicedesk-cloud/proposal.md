# Proposal

## Why

O agente de Service Desk só roda local (Docker Compose, Mailpit). O laboratório final precisa dele publicado, com um portal
para demonstração, usando apenas planos gratuitos. A publicação traz duas necessidades que a mudança
`implementar-agente-servicedesk` não cobre: uma **trilha de auditoria** das execuções (o que foi analisado, se a
notificação foi publicada, se o provedor de e-mail a aceitou) e a **exposição segura** da API por um portal, sem
entregar o segredo do backend ao navegador.

## What Changes

- **Auditoria mínima em PostgreSQL (Neon):** tabelas `execucao` e `notificacao`, migrações Flyway, transições de status
  com nomes precisos e atualização condicional que impede regressão. Sem nome, descrição nem corpo do e-mail.
- **Perfil `cloud`:** configuração por variáveis de ambiente para PostgreSQL (TLS), RabbitMQ em `amqps://` (CloudAMQP),
  Resend por HTTPS, porta e memória compatíveis com o Render Free.
- **Portal e proxy na Vercel:** página estática e função `POST /api/triagem` que valida entrada e código de acesso da
  demonstração e chama o backend por URL fixa, com o segredo só no servidor.
- **Publicação e automação:** `render.yaml` (Docker, plano free, deploy após CI), pipeline do GitHub Actions, documentação
  de deploy e de verificação.
- A decisão de **não** criar flag de falha simulada (`FORCE_PRIMARY_FAILURE`) em código de produção: o fallback é
  testado de forma automatizada.

## Capabilities

### New Capabilities
- `auditoria-execucoes`: registro das execuções e do ciclo de vida das notificações, sem dado pessoal em claro.
- `portal-triagem`: portal na Vercel e proxy HTTP que expõe a triagem com acesso controlado.
- `operacao-cloud`: comportamento operacional do backend na nuvem (configuração, saúde, segredos, TLS, suspensão).

### Modified Capabilities
<!-- Nenhuma. O comportamento de analise-chamado, notificacao-email e seguranca-api, ainda em change ativa, não muda. -->

## Impact

- **Código:** novos pacotes de auditoria (porta no domínio/aplicação, adaptador JDBC), `AnaliseChamado` ganha o campo
  `provedor`, chamadas de auditoria em `AnalisarChamadoService`, `NotificacaoPublisher` e `EmailService`.
- **Dependências novas (backend):** `spring-boot-starter-jdbc`, `org.postgresql:postgresql` (BSD-2-Clause),
  `flyway-core` e `flyway-database-postgresql` (Apache 2.0), todos geridos pelo BOM do Spring Boot; teste:
  `testcontainers-postgresql` (MIT). **Portal:** nenhuma dependência de runtime; testes com o runner nativo do Node.
- **Sistemas externos:** Neon, CloudAMQP, Resend, Render, Vercel, GitHub, todos em planos gratuitos.
- **LGPD:** o portal passa a aceitar texto de usuário pela internet e o backend o envia a OpenAI e Gemini. A trilha em
  banco **não** guarda nome, descrição nem corpo. A transferência internacional e o contrato de operador continuam
  **não confirmados**; a demonstração deve usar dados fictícios.
- **Fora de escopo:** Outbox, autenticação por usuário, domínio próprio, keep-alive, reprocessamento automático da DLQ.
