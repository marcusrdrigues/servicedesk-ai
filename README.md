# ServiceDesk AI

Agente de Service Desk com Java 21, Spring Boot 4 e Spring AI. Recebe um chamado, classifica por categoria e
prioridade com um modelo de linguagem e, se o solicitante autorizar e a análise recomendar, enfileira um e-mail
para a equipe de suporte.

> **Dados fictícios apenas.** `nome` e `descricao` podem ser dado pessoal e seguem para dois provedores de modelo
> (OpenAI e Gemini) fora do Brasil. Contrato de operador, proibição de treino, retenção e base para transferência
> internacional (LGPD, art. 33) **não foram confirmados** neste projeto. Não use dado real antes de confirmar.

## Como funciona

```
POST /api/chamados ──► valida ──► OpenAI ──(falha de chamada ou de validação)──► Gemini ──(falha)──► 503
                                     │ análise válida
                                     ▼
                  autorizaEmail && enviarEmail ? ──não──► 200 NAO_SOLICITADO
                                     │ sim
                                     ▼
              RabbitMQ (persistente + confirmação do broker) ──► 202 ENFILEIRADO
                                     │
                                     ▼  consumidor: até 3 tentativas ──► DLQ
                     Mailpit (smtp, local)  ou  Resend (HTTPS, nuvem)
```

- O modelo só classifica e redige. A autorização, o destinatário e a decisão de publicar são código.
- **`ENFILEIRADO` quer dizer que o broker aceitou a mensagem.** Não quer dizer que o e-mail foi enviado nem
  entregue. Não existe o status `ENVIADO`.
- A entrega é **at-least-once**, com deduplicação em memória por `eventoId`. A deduplicação vale para uma só
  instância e some ao reiniciar. Não há garantia de exatamente uma vez.
- Se a confirmação do broker não chega no prazo, a resposta é 503 e o resultado é **incerto**: a mensagem pode ter
  sido aceita. Um novo envio do chamado pode gerar um segundo e-mail.

## Requisitos

Java 21, Docker (Compose v2) e chaves da OpenAI e do Gemini. Os testes de integração também usam Docker.

## Executar com Docker Compose

```bash
cp .env.example .env        # edite: chaves, modelos, segredo da API, senha do RabbitMQ
docker compose up -d --build --wait
```

| Serviço | Endereço |
|---|---|
| API | http://127.0.0.1:8080 |
| PostgreSQL (auditoria) | 127.0.0.1:5432, banco `servicedesk` |
| Mailpit (caixa de entrada) | http://127.0.0.1:8025 |
| RabbitMQ (painel) | http://127.0.0.1:15672 (usuário e senha do `.env`) |

As portas só escutam em `127.0.0.1`. Dentro da rede do Compose, a aplicação fala com `rabbitmq` e `mailpit`
pelo nome do serviço. Para parar: `docker compose down` (o volume `rabbitmq-data` é mantido).

Exemplo de chamada:

```bash
curl -i -X POST http://127.0.0.1:8080/api/chamados \
  -H "X-API-Key: <SERVICEDESK_API_KEY>" -H "Content-Type: application/json" \
  -d '{"nome":"Maria","descricao":"A VPN caiu para todo o escritório desde as nove horas","autorizaEmail":true}'
```

| Resposta | Quando |
|---|---|
| 200 | Análise feita, nenhuma notificação a enviar (`NAO_SOLICITADO`) |
| 202 | Análise feita e notificação aceita pela fila (`ENFILEIRADO`) |
| 400 | Entrada inválida |
| 401 | `X-API-Key` ausente ou errada |
| 429 | Limite de requisições por minuto excedido |
| 503 | Os dois provedores falharam, ou o broker não confirmou. Nada foi publicado no primeiro caso |

## Executar a aplicação fora do Docker

```bash
docker compose up -d rabbitmq mailpit
# no .env, descomente/ajuste RABBITMQ_URL=amqp://<usuario>:<senha>@localhost:5672
./mvnw spring-boot:run
```

O `application.yaml` importa o `.env` da pasta atual, se existir.

## Variáveis de ambiente

Todas vêm do ambiente; nenhuma chave é versionada. A aplicação **recusa subir** se um valor obrigatório estiver
ausente, em branco ou for um `${VARIAVEL}` sem valor, e a mensagem cita o nome, nunca o valor.

| Variável | Uso |
|---|---|
| `OPENAI_API_KEY`, `OPENAI_CHAT_MODEL` | Provedor principal. Modelo com versão, nunca alias |
| `GEMINI_API_KEY`, `GEMINI_CHAT_MODEL` | Reserva, pelo endpoint compatível com OpenAI |
| `SERVICEDESK_API_KEY` | Segredo da API (cabeçalho `X-API-Key`) |
| `EMAIL_TO`, `EMAIL_FROM` | Destinatário (do servidor) e remetente |
| `EMAIL_MODE` | `smtp` (Mailpit, padrão) ou `resend` (HTTPS) |
| `SMTP_HOST`, `SMTP_PORT` | Mailpit |
| `RESEND_API_KEY` | Só com `EMAIL_MODE=resend` |
| `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL local (auditoria). A senha não tem padrão: a aplicação não sobe sem ela |
| `DB_URL` | Padrão `jdbc:postgresql://localhost:5432/servicedesk`. Na nuvem é obrigatória e exige `sslmode=require` |
| `RABBITMQ_URL` | `amqp://usuario:senha@host:5672` (contém segredo) |
| `SERVICEDESK_CORS_ORIGEM` | Origem do portal. Sem valor, nenhum cabeçalho CORS é devolvido |

## Testes

```bash
./mvnw test        # unitários, API, e integração com RabbitMQ e Mailpit reais (exige Docker)
```

Nenhum teste chama provedor de modelo nem envia e-mail real: o modelo é dublê ou servidor HTTP falso, e o
e-mail local vai ao Mailpit.

O comportamento do modelo é coberto por **eval**, fora do CI e com o modelo real:

```bash
EVAL_REAL=true ./mvnw test -Dtest=EvalModeloRealManualTest
```

Os 20 casos estão em `src/test/resources/eval/casos.json` (4 adversariais). Rode a cada mudança de prompt,
modelo ou parâmetro e compare com a nota anterior. A nota mínima inicial (0,80) deve ser revista com o uso real.

## Auditoria e publicação na nuvem

Cada execução e o ciclo de vida da notificação ficam no PostgreSQL (tabelas `execucao` e `notificacao`, migração Flyway),
sem nome, descrição, resumo nem corpo de e-mail. Os estados só avançam e nenhum deles afirma entrega: `ACEITO_PELO_PROVEDOR`
quer dizer que a API de e-mail aceitou a solicitação. A falha ao gravar a auditoria não derruba o atendimento.

A publicação (Vercel, Render, Neon, CloudAMQP e Resend, todos em planos gratuitos), as limitações desses planos e o
procedimento de redeploy e de desativação estão em [`docs/deploy.md`](docs/deploy.md). O que já foi verificado e o que
falta está em [`docs/deploy-verification.md`](docs/deploy-verification.md). O portal e o proxy ficam em `portal/`
(`cd portal && node --test`).

## Limitações conhecidas

- Deduplicação e limite de requisições em memória: só valem com uma instância.
- Sem outbox: banco e broker não são atômicos, e a auditoria pode ter lacunas. Sem reprocessamento automático da DLQ. A DLQ é inspecionada e tratada à mão no painel.
- A DLQ recebe a mensagem rejeitada pelo broker; o motivo da última falha fica no log da aplicação, com o
  `eventoId`, e não dentro da mensagem.
- Resend: a `Idempotency-Key` vale por 24 horas, e o Mailpit local não deduplica.

Detalhes de arquitetura e decisões em [`docs/arquitetura.md`](docs/arquitetura.md). Especificações em
`openspec/changes/implementar-agente-servicedesk/`.
