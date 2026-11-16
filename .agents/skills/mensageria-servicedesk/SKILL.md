---
name: mensageria-servicedesk
description: Implementa e valida notificações assíncronas de e-mail com RabbitMQ no projeto ServiceDesk AI.
---

# Objetivo

Implementar a infraestrutura de mensageria para que o agente
ServiceDesk solicite o envio de e-mails de forma assíncrona.

A aplicação publica um evento no RabbitMQ.
Um consumidor recebe o evento e executa o envio.

# Tecnologias

- Java 21.
- Spring Boot
- Spring AMQP.
- RabbitMQ.
- Java Mail Sender.
- Mailpit para execução local.
- Resend por HTTPS para execução na nuvem.
- Docker Compose.

# Leitura obrigatória

Antes de implementar:

1. Leia o pom.xml.
2. Examine a estrutura e os pacotes existentes.
3. Leia os artefatos da mudança:
   openspec/changes/implementar-agente-servicedesk/
4. Leia as especificações relacionadas à notificação.
5. Verifique o application.yaml e o compose.yaml existentes.

Preserve os nomes e contratos já implementados.
Não crie classes duplicadas.

Se houver conflito entre esta skill e uma especificação aprovada,
informe o conflito antes de alterar o comportamento.

# Contrato do evento

Utilize um evento chamado EmailSolicitado com:

- eventoId: UUID.
- destinatario: String.
- assunto: String.
- corpo: String.

Serialize o evento em JSON.

O eventoId deve permanecer igual durante todas as tentativas
de processamento da mesma mensagem.

# Topologia RabbitMQ

Crie os seguintes componentes:

- Exchange direta e durável: servicedesk.events.
- Routing key: email.solicitado.
- Fila durável: servicedesk.email.
- Dead-letter exchange direta e durável: servicedesk.dlx.
- Routing key de falha: email.falhou.
- Fila durável de falhas: servicedesk.email.dlq.

Configure a fila principal com:

- x-dead-letter-exchange: servicedesk.dlx.
- x-dead-letter-routing-key: email.falhou.

Declare os bindings explicitamente.

# Publicação

Implemente um componente NotificacaoPublisher.

Responsabilidades:

1. Receber EmailSolicitado.
2. Serializar o evento em JSON.
3. Publicar na exchange com a routing key correta.
4. Marcar a mensagem como persistente.
5. Definir messageId usando eventoId.
6. Usar publisher confirms correlacionados.
7. Habilitar mandatory e publisher returns.
8. Aguardar confirmação com timeout limitado.
9. Verificar ACK e ausência de mensagem retornada.
10. Propagar falha de publicação para o chamador.

Não considerar confirmação do broker como confirmação
de envio ou entrega do e-mail.

Se ocorrer timeout, tratar o resultado como incerto:
a publicação pode ter sido aceita pelo broker.

# Consumo

Implemente um EmailConsumer com @RabbitListener.

Responsabilidades:

1. Receber e desserializar o evento.
2. Acionar EmailService.
3. Deixar a exceção propagar quando o envio falhar.
4. Registrar eventoId e resultado sem registrar o corpo completo.

Configure:

- Uma execução concorrente para o laboratório.
- Prefetch igual a 1.
- Até três tentativas totais de processamento.
- Intervalo inicial de dois segundos.
- Backoff com multiplicador 2.
- Sem requeue infinito.
- Rejeição após esgotar as tentativas.
- Encaminhamento para a DLQ pela configuração do broker.

Não capturar uma exceção de envio apenas para registrar e retornar:
isso faria o processamento parecer bem-sucedido.

# Envio de e-mail

Implemente EmailService com dois modos de operação.

## Modo smtp

- Utilizar JavaMailSender.
- Conectar ao Mailpit na execução local.
- Ler host e porta da configuração.
- Configurar timeouts de conexão e envio.

## Modo resend

- Utilizar a API HTTPS do Resend.
- Ler a chave da variável RESEND_API_KEY.
- Utilizar eventoId na Idempotency-Key.
- Configurar timeouts.
- Propagar respostas de erro para permitir retry.

O SMTP local não possui deduplicação persistente nesta versão.
A idempotência do Resend possui uma janela limitada.
Não prometer processamento exatamente uma vez.

# Política de autonomia

O modelo pode recomendar a notificação.

O código Java deve verificar:

1. Se o usuário autorizou a notificação.
2. Se a decisão do modelo é válida.
3. Se a decisão recomenda enviar o e-mail.

O destinatário deve vir da configuração EMAIL_TO.
O modelo não pode escolher ou alterar destinatários.

A publicação deve acontecer depois da análise com fallback.
Não incluir publicação ou envio dentro do bloco de fallback dos modelos.

Após confirmação da publicação, utilizar o estado ENFILEIRADO.
Não utilizar ENVIADO como resultado da publicação.

# Configuração

Utilize variáveis de ambiente para:

- RABBITMQ_URL.
- EMAIL_MODE.
- EMAIL_TO.
- EMAIL_FROM.
- SMTP_HOST.
- SMTP_PORT.
- RESEND_API_KEY.

Não incluir segredos no código ou no Git.

Atualize .env.example apenas com valores fictícios.

# Docker

Adicione ou ajuste os serviços:

- RabbitMQ com interface de gerenciamento.
- Mailpit.
- Aplicação Spring Boot.

Requisitos:

- Volume persistente para RabbitMQ.
- Healthcheck do RabbitMQ.
- Aplicação aguardando o broker ficar saudável.
- Credenciais locais diferentes de guest.
- Portas locais vinculadas a 127.0.0.1.

Dentro dos containers, utilize os nomes dos serviços como hostname.
Não utilize localhost para acessar outro container.

# Verificações

Execute as verificações disponíveis no ambiente:

1. Testes unitários.
2. Compilação Maven.
3. docker compose config --quiet.
4. Build da imagem.
5. Inicialização dos containers, quando Docker estiver disponível.

Valide os cenários:

- Notificação autorizada é publicada.
- Sem autorização, nenhuma mensagem é publicada.
- Falha dos dois modelos não publica mensagem.
- Envio local aparece no Mailpit.
- Falha de envio provoca retry.
- Tentativas esgotadas encaminham a mensagem para DLQ.

Não executar envios reais durante testes automatizados.
Utilize mocks ou Mailpit.

Se faltar Docker, credencial ou serviço externo,
informe a verificação que ficou pendente.
Não registre uma verificação não executada como concluída.

# Entrega

Ao terminar:

1. Liste os arquivos criados e alterados.
2. Informe os testes executados e seus resultados.
3. Explique como iniciar os serviços.
4. Informe os endereços do RabbitMQ e Mailpit.
5. Atualize tasks.md apenas com tarefas concluídas.
6. Registre limitações e verificações pendentes.

Não adicionar banco, outbox ou replay automático de DLQ
sem que essas funcionalidades estejam especificadas.