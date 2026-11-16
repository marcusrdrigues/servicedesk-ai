# Proposal

## Why

O laboratório final precisa de um agente de Service Desk que analise um chamado em linguagem natural (categoria, prioridade, resumo e orientação) e, quando o solicitante autorizar, dispare uma notificação por e-mail. O modelo é uma dependência externa que falha, então o desenho precisa mostrar resiliência (provedor principal e reserva), segurança (o modelo não escolhe destinatário) e honestidade sobre o que foi feito (publicar na fila não é e-mail enviado).

## What Changes

- Novo endpoint HTTP que recebe `nome`, `descricao` e `autorizaEmail`, valida no backend e devolve a análise do chamado.
- Análise por **uma chamada ao modelo** com saída estruturada (degrau "uma chamada com bom prompt" da escada de IA; não há RAG, tool nem laço de agente). O modelo apenas classifica e redige; quem decide o fluxo é código.
- OpenAI como provedor principal; Gemini, pelo endpoint compatível com OpenAI, como reserva quando a chamada **ou** a conversão/validação da resposta principal falhar.
- Falha dos dois provedores: HTTP 503, sem publicar mensagem.
- Destinatário vem da configuração do servidor; nunca do modelo nem do corpo da requisição.
- Publicação no RabbitMQ (mensagem persistente, com confirmação do broker) só quando `autorizaEmail` for verdadeiro **e** a decisão validada do agente indicar `enviarEmail`.
- Consumidor na mesma aplicação: até três tentativas, depois DLQ. Entrega **at-least-once**; o consumidor é idempotente por identificador da notificação.
- Envio por adaptador de e-mail: Mailpit (SMTP) no perfil local, Resend por HTTPS na nuvem.
- Resposta da API usa o status `ENFILEIRADO` após publicar; nunca `ENVIADO`.
- API protegida por segredo no servidor (cabeçalho comparado em tempo constante). Nenhuma chave em arquivo versionado.

## Capabilities

### New Capabilities
- `analise-chamado`: recebimento e validação do chamado, análise pelo modelo com saída estruturada, validação em código, fallback entre provedores e resposta 503.
- `notificacao-email`: decisão de envio, publicação confiável no RabbitMQ, consumo com retry e DLQ, adaptadores Mailpit e Resend, status `ENFILEIRADO`.
- `seguranca-api`: autenticação da API por segredo do servidor, tratamento de erro sem detalhe interno, higiene de segredos e de log.

### Modified Capabilities
<!-- Nenhuma: openspec/specs está vazio. -->

## Impact

- **Código**: pacotes novos em `br.com.coti.servicedesk_ai` (domínio, aplicação com portas, infraestrutura com adaptadores, web). Projeto hoje só tem a classe `ServicedeskAiApplication`.
- **Dependências**: `pom.xml` já tem amqp, mail, validation, webmvc e actuator. **Falta** o `spring-ai-bom` e o starter OpenAI; exige aprovação (ver `design.md`, Decisão 9). Cliente HTTP do Resend usa o `RestClient` do Spring, sem SDK novo.
- **Sistemas externos**: OpenAI, Gemini, RabbitMQ, Mailpit (local), Resend (nuvem).
- **LGPD**: `nome` e `descricao` são dado pessoal potencial e vão a dois provedores de modelo, no exterior. Isso depende de contrato (operador, sem treino, retenção) e de base para transferência internacional (art. 33), que **não está confirmado** no projeto; fica como pergunta aberta no `design.md` e bloqueia o uso com dado real.
- **Fora de escopo**: portal (Vercel), autenticação por usuário, persistência do chamado, RAG, eval com modelo real em CI.
