# Spec Delta

## Purpose

Define quando e como a notificação por e-mail de um chamado analisado é decidida, publicada de forma confiável numa fila, consumida com retentativas e entregue por um provedor de e-mail, sem prometer entrega que o sistema não pode garantir.

## ADDED Requirements

### Requirement: Condição para publicar a notificação
O sistema SHALL publicar uma mensagem de notificação somente quando `autorizaEmail` for verdadeiro na requisição e `enviarEmail` for verdadeiro na decisão validada do agente.

#### Scenario: Autorizado e decidido
- **WHEN** `autorizaEmail` é verdadeiro e a análise validada traz `enviarEmail` verdadeiro
- **THEN** uma mensagem é publicada e a resposta traz o status `ENFILEIRADO`

#### Scenario: Sem autorização
- **WHEN** `autorizaEmail` é falso, qualquer que seja a decisão do agente
- **THEN** nenhuma mensagem é publicada

#### Scenario: Agente decide não enviar
- **WHEN** `autorizaEmail` é verdadeiro e a análise validada traz `enviarEmail` falso
- **THEN** nenhuma mensagem é publicada

#### Scenario: Análise falhou
- **WHEN** a análise termina em HTTP 503
- **THEN** nenhuma mensagem é publicada

### Requirement: Destinatário definido pelo servidor
O sistema SHALL obter o destinatário da configuração do servidor e SHALL NOT aceitar destinatário vindo do modelo nem do corpo da requisição.

#### Scenario: Destinatário na mensagem
- **WHEN** uma mensagem é publicada
- **THEN** o destinatário nela é o configurado no servidor

#### Scenario: Destinatário na requisição
- **WHEN** o corpo da requisição traz um campo de destinatário
- **THEN** o sistema o ignora e não o usa em nenhuma etapa

#### Scenario: Destinatário não configurado
- **WHEN** o destinatário do servidor está ausente ou inválido na inicialização
- **THEN** a aplicação falha ao subir com mensagem que cita o nome da propriedade, sem expor valores secretos

### Requirement: Publicação persistente com confirmação do broker
O sistema SHALL publicar a mensagem como persistente numa fila durável e SHALL considerar a publicação concluída somente após a confirmação positiva do broker.

#### Scenario: Broker confirma
- **WHEN** o broker confirma a mensagem
- **THEN** a resposta é HTTP 202 com status `ENFILEIRADO`

#### Scenario: Broker rejeita ou não confirma
- **WHEN** o broker devolve confirmação negativa ou a confirmação não chega no prazo configurado
- **THEN** o sistema responde HTTP 503 e não informa `ENFILEIRADO`

#### Scenario: Mensagem não roteada
- **WHEN** o broker não consegue rotear a mensagem para uma fila
- **THEN** o sistema trata como falha de publicação e responde HTTP 503

### Requirement: Status honesto da notificação
O sistema SHALL informar `ENFILEIRADO` após publicar e SHALL NOT informar `ENVIADO` em nenhuma resposta; o status indica aceite pela fila, não entrega ao destinatário.

#### Scenario: Resposta após publicar
- **WHEN** a mensagem foi publicada e confirmada
- **THEN** o campo de status vale `ENFILEIRADO` e não existe o valor `ENVIADO` no contrato da API

#### Scenario: Notificação não solicitada
- **WHEN** nenhuma mensagem foi publicada por regra de negócio
- **THEN** o status vale `NAO_SOLICITADO`

### Requirement: Consumo com retentativas e DLQ
O sistema SHALL consumir a mensagem com no máximo três tentativas de envio e SHALL encaminhar a mensagem à fila de mensagens mortas (DLQ) quando as três falharem.

#### Scenario: Sucesso na segunda tentativa
- **WHEN** o envio falha na primeira tentativa e funciona na segunda
- **THEN** a mensagem é confirmada ao broker e não vai para a DLQ

#### Scenario: Três falhas
- **WHEN** as três tentativas de envio falham
- **THEN** a mensagem é encaminhada à DLQ com o motivo da última falha, sem conteúdo do chamado em claro

#### Scenario: Mensagem malformada
- **WHEN** a mensagem consumida não converte para o formato esperado
- **THEN** ela vai à DLQ sem novas tentativas

### Requirement: Garantia de entrega at-least-once
O sistema SHALL tratar a entrega como pelo menos uma vez e SHALL evitar envio duplicado de e-mail por identificador único da notificação.

#### Scenario: Mensagem redelivered
- **WHEN** o broker entrega novamente uma notificação já processada com sucesso
- **THEN** o sistema não envia um segundo e-mail para o mesmo identificador

#### Scenario: Sem promessa de exatamente uma vez
- **WHEN** a documentação da API descreve a entrega
- **THEN** ela afirma at-least-once com deduplicação e não afirma exatamente uma vez

### Requirement: Provedor de e-mail por ambiente
O sistema SHALL enviar o e-mail via Mailpit (SMTP) no perfil local e via Resend por HTTPS no perfil de nuvem, selecionados por configuração.

#### Scenario: Perfil local
- **WHEN** o perfil local está ativo
- **THEN** o e-mail é entregue ao Mailpit por SMTP e nenhuma chamada ao Resend é feita

#### Scenario: Perfil de nuvem
- **WHEN** o perfil de nuvem está ativo
- **THEN** o e-mail é enviado por requisição HTTPS ao Resend com chave vinda de variável de ambiente

#### Scenario: Chave do Resend ausente no perfil de nuvem
- **WHEN** a chave do Resend não está definida
- **THEN** a aplicação falha ao subir citando o nome da variável, nunca o valor

### Requirement: Conteúdo do e-mail controlado pelo servidor
O sistema SHALL montar o e-mail a partir de um modelo fixo, com assunto e remetente definidos no servidor, e SHALL tratar os campos vindos do chamado ou do modelo como texto escapado.

#### Scenario: Campo com HTML ou quebra de linha
- **WHEN** `resumo` ou `nome` contêm marcação HTML ou caracteres de quebra de linha
- **THEN** o e-mail os apresenta como texto e não permite injetar cabeçalhos nem marcação ativa
