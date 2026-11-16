# Spec Delta

## Purpose

Definir o comportamento operacional do backend publicado na nuvem em planos gratuitos: configuração por ambiente, conexões com TLS, saúde, segredos e as limitações de suspensão que o operador precisa conhecer.

## ADDED Requirements

### Requirement: Perfil de nuvem por variáveis de ambiente
O sistema SHALL, com o perfil `cloud`, obter porta, banco, broker, provedor de e-mail e segredos exclusivamente de variáveis de ambiente, e SHALL recusar subir quando uma delas estiver ausente, em branco ou sem resolver, citando o nome e nunca o valor.

#### Scenario: Variável do banco ausente
- **WHEN** `DB_URL` não está definida no perfil `cloud`
- **THEN** a aplicação não sobe e a mensagem cita `DB_URL`

#### Scenario: Porta do provedor
- **WHEN** o ambiente define `PORT`
- **THEN** a aplicação escuta nessa porta, em interface acessível ao provedor

#### Scenario: Provedor de e-mail da nuvem
- **WHEN** o perfil `cloud` está ativo
- **THEN** o modo de e-mail é Resend por HTTPS e nenhuma conexão SMTP é aberta

### Requirement: Conexões com TLS verificado
O sistema SHALL conectar ao PostgreSQL com `sslmode=require` e ao RabbitMQ por `amqps://`, e SHALL NOT desabilitar a verificação de certificado nem de nome de host.

#### Scenario: Banco sem TLS
- **WHEN** `DB_URL` não exige TLS no perfil `cloud`
- **THEN** a aplicação recusa subir

#### Scenario: Broker sem TLS
- **WHEN** `RABBITMQ_URL` usa `amqp://` no perfil `cloud`
- **THEN** a aplicação recusa subir

### Requirement: Pool de conexões pequeno
O sistema SHALL limitar o pool do PostgreSQL a 3 conexões, sem conexões ociosas mínimas, para respeitar os limites do plano gratuito.

#### Scenario: Configuração do pool
- **WHEN** o perfil `cloud` está ativo
- **THEN** o pool tem tamanho máximo 3 e mínimo ocioso 0

### Requirement: Saúde pública sem detalhes
O sistema SHALL expor `/actuator/health` sem autenticação e sem detalhes de dependências, para o healthcheck do provedor.

#### Scenario: Healthcheck
- **WHEN** o provedor consulta `/actuator/health`
- **THEN** recebe apenas o estado geral

### Requirement: Acesso direto ao backend protegido
O sistema SHALL responder 401 a qualquer chamada direta à API de negócio sem o segredo do servidor, mesmo vinda do portal ou de fora dele.

#### Scenario: Chamada direta sem chave
- **WHEN** a URL pública do backend recebe `POST /api/chamados` sem `X-API-Key`
- **THEN** a resposta é 401

### Requirement: Limitações da suspensão documentadas
A documentação SHALL registrar que o serviço gratuito pode suspender após inatividade HTTP, que enquanto suspenso o consumidor não processa mensagens, que a primeira chamada pode ter cold start e que o disco local não persiste, e SHALL NOT prever mecanismo de keep-alive.

#### Scenario: Leitura da documentação
- **WHEN** a documentação de deploy é lida
- **THEN** ela traz essas quatro limitações e não afirma funcionamento contínuo 24 horas

### Requirement: Deploy somente após a CI
A configuração de publicação SHALL condicionar o deploy automático ao sucesso das verificações da CI quando o provedor suportar esse comportamento.

#### Scenario: CI falha
- **WHEN** a CI do branch publicado falha
- **THEN** o provedor não faz o deploy automático dessa versão
