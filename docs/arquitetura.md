# Arquitetura

## Camadas

A dependência aponta para dentro, e `ArquiteturaTest` garante que `dominio` e `aplicacao` não importam Spring AI,
AMQP, web nem e-mail.

| Pacote | Conteúdo |
|---|---|
| `dominio` | `Chamado`, `AnaliseChamado`, `EmailSolicitado`, enums e exceções |
| `aplicacao` | Casos de uso (`AnalisarChamadoService`, `EmailService`), portas, `ValidadorAnalise`, `MontadorEmail`, `AnalisadorComRetry`, `AnalisadorComFallback` |
| `infraestrutura` | Adaptadores: Spring AI (`ChatClientAnalisadorAdapter`), RabbitMQ (`NotificacaoPublisher`, `EmailConsumer`), e-mail (`SmtpEmailSender`, `ResendEmailSender`), configuração |
| `web` | Controller, filtros (id, CORS, `X-API-Key`, limite) e `TratadorErros` |

## Degrau da escada de IA

Uma chamada ao modelo com prompt versionado e saída estruturada. Foram descartados RAG (não há base de
conhecimento), workflow multi-passo e agente com tools (nada a decidir que o código não decida): cada degrau a
mais custa latência e superfície de ataque. O "agente" é um classificador com decisão validada.

## Decisões

1. **Cadeia de dois elos para o fallback** (`AnalisadorComFallback`), em vez de `if` espalhado. Cada adaptador
   faz a chamada **e** a conversão/validação, então saída inválida também aciona o reserva. Descartei o retry
   genérico do `ChatModel`: ele não cobre saída inválida e esconderia qual provedor respondeu.
2. **Dois `ChatModel` montados à mão**, módulos `spring-ai-openai` e `spring-ai-client-chat` em vez do starter. O
   starter autoconfigura um único modelo e exige chave; aqui há dois endpoints compatíveis com OpenAI, cada um
   com chave, modelo e base-url próprios.
3. **Retry em um único ponto.** `max-retries: 0` no SDK; `AnalisadorComRetry` repete só erro transitório (429,
   408, 5xx, rede) com backoff exponencial e jitter, dentro de um orçamento de tempo total. Substitui o
   reenvio-com-erro previsto pela regra geral: somar reenvio e fallback estouraria a latência.
4. **Decisão de envio em código:** `autorizaEmail && enviarEmail`. O modelo opina; a trava é a autorização do
   solicitante. O destinatário (`EMAIL_TO`) é montado pelo servidor no `MontadorEmail`; o modelo nunca o vê.
5. **E-mail montado antes de publicar.** O evento `EmailSolicitado` carrega `eventoId`, `destinatario`, `assunto`
   e `corpo`. O `eventoId` não muda entre as tentativas e vira `messageId` da mensagem e `Idempotency-Key` no Resend.
6. **Publicação confiável:** fila durável, mensagem persistente, `publisher-confirm-type: correlated`,
   `mandatory` e returns. Só há `ENFILEIRADO` após ACK sem mensagem devolvida. Descartei *outbox*: não há banco
   no laboratório; o risco residual (confirmado, mas o processo cai antes de responder) vira, no máximo, um chamado
   reenviado pelo cliente.
7. **DLQ pela configuração do broker:** a fila principal aponta para a dead-letter exchange; o consumidor deixa
   a exceção propagar, o retry do Spring esgota e a mensagem é rejeitada sem requeue.
8. **Segredos validados na subida.** `@ValorDefinido` rejeita valor nulo, em branco ou `${VARIAVEL}` sem
   resolver. O Spring Boot não falha em placeholder sem valor ao fazer o binding de propriedades: sem a trava, o
   segredo da API seria o texto literal `${SERVICEDESK_API_KEY}`.
9. **Filtro simples em vez do Spring Security.** Um segredo estático não justifica uma dependência nova; se
   surgirem vários clientes, migrar. A chave é comparada em tempo constante, sobre o hash dos dois valores.

## Topologia RabbitMQ

| Item | Nome |
|---|---|
| Exchange direta e durável | `servicedesk.events`, routing key `email.solicitado` |
| Fila durável | `servicedesk.email` (`x-dead-letter-exchange=servicedesk.dlx`, `x-dead-letter-routing-key=email.falhou`) |
| Dead-letter exchange | `servicedesk.dlx`, routing key `email.falhou` |
| Fila de falhas | `servicedesk.email.dlq` |

Consumidor: 1 execução, prefetch 1, 3 tentativas totais (`max-retries: 2`), intervalo inicial de 2 s, multiplicador 2.

## Pontos de atenção descobertos na implementação

- **Boot 4: `max-attempts` não existe mais.** A propriedade foi removida e virou `max-retries`, que conta as
  retentativas **além da primeira**. Com `max-attempts: 3` ignorada em silêncio, o consumidor fazia 4 tentativas.
- **JRE enxuto:** a imagem final usa `eclipse-temurin:21-jre-alpine`, sem o módulo `jdk.random`.
  `RandomGenerator.getDefault()` falha nessa imagem; usa-se `java.util.Random`.
- **Confirmação que não chega:** timeout de confirmação é tratado como resultado incerto, não como falha certa.
