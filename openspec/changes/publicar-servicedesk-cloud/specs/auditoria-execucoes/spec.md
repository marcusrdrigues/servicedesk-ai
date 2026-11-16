# Spec Delta

## Purpose

Registrar, em banco relacional, cada execução do agente e o ciclo de vida da notificação associada, com nomes de estado precisos e sem dado pessoal em claro, para que se saiba o que foi analisado, publicado e aceito pelo provedor de e-mail.

## ADDED Requirements

### Requirement: Registro da execução
O sistema SHALL registrar cada análise concluída com identificador, instante, provedor que respondeu, categoria, prioridade e status, sem gravar nome, descrição nem texto da resposta do modelo.

#### Scenario: Análise concluída
- **WHEN** uma análise termina com sucesso
- **THEN** existe uma execução com status `ANALISADA`, o provedor que respondeu, a categoria e a prioridade, e nenhum campo com o nome, a descrição ou o resumo

#### Scenario: Análise que falha
- **WHEN** os dois provedores falham e a resposta é 503
- **THEN** nenhuma execução é registrada

### Requirement: Ciclo de vida da notificação
O sistema SHALL registrar a notificação publicada com `eventoId`, execução de origem, status, instante da última atualização e o último erro sanitizado.

#### Scenario: Notificação solicitada
- **WHEN** a decisão é publicar
- **THEN** a notificação é registrada como `PUBLICACAO_PENDENTE` antes da publicação, e a execução passa a `PUBLICACAO_PENDENTE`

#### Scenario: Publicação confirmada
- **WHEN** o broker confirma a mensagem
- **THEN** a notificação e a execução passam a `PUBLICACAO_CONFIRMADA`

#### Scenario: Confirmação que não chegou
- **WHEN** o prazo de confirmação do broker expira
- **THEN** a notificação e a execução passam a `PUBLICACAO_INCERTA`

#### Scenario: Publicação recusada
- **WHEN** o broker recusa ou não roteia a mensagem
- **THEN** a notificação e a execução passam a `PUBLICACAO_FALHOU`

### Requirement: Estados do envio sem afirmar entrega
O sistema SHALL registrar o resultado do envio como `ACEITO_PELO_PROVEDOR` quando o provedor de e-mail aceitar a solicitação e como `FALHA_ENVIO` quando falhar, e SHALL NOT usar um estado que afirme entrega ao destinatário.

#### Scenario: Provedor aceita
- **WHEN** o provedor de e-mail aceita a solicitação
- **THEN** a notificação fica `ACEITO_PELO_PROVEDOR`

#### Scenario: Envio falha
- **WHEN** uma tentativa de envio falha
- **THEN** a notificação fica `FALHA_ENVIO` com o último erro sanitizado

#### Scenario: Ausência de estado de entrega
- **WHEN** os estados possíveis são listados
- **THEN** nenhum deles se chama `ENTREGUE` nem `ENVIADO`

### Requirement: Sem regressão de estado
O sistema SHALL atualizar o status por operação condicional que só avança, de modo que uma atualização tardia nunca sobrescreva um estado mais avançado.

#### Scenario: Consumidor adianta-se ao publicador
- **WHEN** o consumidor registra `ACEITO_PELO_PROVEDOR` antes de o publicador registrar `PUBLICACAO_CONFIRMADA`
- **THEN** o status final continua `ACEITO_PELO_PROVEDOR`

#### Scenario: Reentrega depois do sucesso
- **WHEN** uma falha tardia chega depois de `ACEITO_PELO_PROVEDOR`
- **THEN** o status continua `ACEITO_PELO_PROVEDOR`

#### Scenario: Sucesso depois de falha
- **WHEN** uma tentativa seguinte tem sucesso depois de `FALHA_ENVIO`
- **THEN** o status passa a `ACEITO_PELO_PROVEDOR`

### Requirement: Erro sanitizado
O sistema SHALL gravar em `ultimo_erro` apenas o tipo da falha e um código curto, com no máximo 200 caracteres, e SHALL NOT gravar mensagem de exceção, corpo de requisição nem conteúdo do chamado.

#### Scenario: Falha do provedor de e-mail
- **WHEN** o envio falha com uma exceção cuja mensagem contém texto do chamado
- **THEN** `ultimo_erro` não contém esse texto

### Requirement: Falha da auditoria não derruba o atendimento
O sistema SHALL tratar a falha ao gravar a auditoria como não impeditiva do atendimento, registrando-a em log com o identificador da requisição, e SHALL NOT manter transação aberta durante a chamada ao modelo nem durante o envio de e-mail.

#### Scenario: Banco indisponível
- **WHEN** o banco está indisponível durante uma requisição válida
- **THEN** a análise e a publicação seguem normalmente e a falha da auditoria aparece em log sem conteúdo do chamado

### Requirement: Esquema versionado por migração
O sistema SHALL criar e evoluir o esquema somente por migrações versionadas, com instantes em `TIMESTAMPTZ`, e SHALL NOT usar geração automática de esquema.

#### Scenario: Banco novo
- **WHEN** a aplicação sobe contra um banco vazio
- **THEN** as migrações criam as tabelas e a aplicação fica pronta

#### Scenario: Banco já migrado
- **WHEN** a aplicação sobe de novo contra o mesmo banco
- **THEN** nenhuma migração é reaplicada e os dados são preservados
