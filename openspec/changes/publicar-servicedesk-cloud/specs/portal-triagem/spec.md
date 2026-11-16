# Spec Delta

## Purpose

Expor a triagem de chamados por um portal web e um proxy HTTP na Vercel, com acesso controlado por código de demonstração e sem nunca entregar ao navegador o segredo do backend nem permitir que ele escolha o destino da chamada.

## ADDED Requirements

### Requirement: Formulário de triagem
O portal SHALL oferecer um formulário com nome, descrição, autorização de e-mail e código de acesso, e SHALL chamar apenas a rota relativa `POST /api/triagem`.

#### Scenario: Envio pelo portal
- **WHEN** o usuário preenche o formulário e envia
- **THEN** o navegador faz `POST /api/triagem` na própria origem e mostra categoria, prioridade, resumo, orientação e o estado da notificação

#### Scenario: Estado da notificação sem promessa de entrega
- **WHEN** a resposta traz `ENFILEIRADO`
- **THEN** o portal informa que a notificação foi aceita pela fila e não afirma que o e-mail foi enviado ou entregue

### Requirement: Método e código de acesso
O proxy SHALL aceitar somente `POST` e SHALL exigir o código de acesso da demonstração, comparado em tempo constante com o valor do servidor.

#### Scenario: Método diferente de POST
- **WHEN** a requisição usa `GET`
- **THEN** a resposta é 405 com o cabeçalho `Allow: POST`

#### Scenario: Código ausente ou inválido
- **WHEN** o código de acesso está ausente ou errado
- **THEN** a resposta é 401 e o backend não é chamado

### Requirement: Validação e campos permitidos
O proxy SHALL validar nome (obrigatório, até 100 caracteres), descrição (20 a 4000 caracteres) e autorização (booleano) e SHALL encaminhar ao backend somente esses três campos.

#### Scenario: Entrada inválida
- **WHEN** qualquer campo viola as regras
- **THEN** a resposta é 400 e o backend não é chamado

#### Scenario: Campos extras
- **WHEN** o corpo traz campos além dos permitidos, como um destinatário
- **THEN** o proxy os descarta e o backend recebe apenas os três campos

#### Scenario: Corpo grande demais
- **WHEN** o corpo excede o tamanho máximo aceito
- **THEN** a resposta é 413 ou 400 e o backend não é chamado

### Requirement: Destino fixo e segredo no servidor
O proxy SHALL chamar o backend somente na URL definida na configuração do servidor, enviando o segredo do backend no cabeçalho `X-API-Key`, e SHALL NOT aceitar URL, host ou cabeçalho do navegador para definir o destino.

#### Scenario: Navegador tenta mudar o destino
- **WHEN** a requisição traz um campo ou cabeçalho com outra URL
- **THEN** o proxy ignora esse valor e chama a URL configurada

#### Scenario: Segredo não exposto
- **WHEN** o HTML, o JavaScript do portal ou qualquer resposta é inspecionado
- **THEN** nenhum deles contém o segredo do backend nem o código de acesso

### Requirement: Timeout sem repetição
O proxy SHALL aplicar timeout limitado à chamada ao backend e SHALL NOT repetir a chamada automaticamente, porque uma repetição pode gerar outra execução e outro e-mail.

#### Scenario: Backend demora além do limite
- **WHEN** o backend não responde no prazo
- **THEN** a resposta é 504 com mensagem fixa, e o `POST` não é reenviado

#### Scenario: Timeout não cancela o backend
- **WHEN** o proxy responde 504
- **THEN** a mensagem avisa que a operação pode ter sido concluída no servidor

### Requirement: Respostas sem detalhe interno
O proxy SHALL responder com `Cache-Control: no-store`, repassar ao navegador apenas o formato de resposta esperado e SHALL NOT devolver stack trace, corpo de erro bruto do backend nem registrar segredos.

#### Scenario: Backend responde erro
- **WHEN** o backend responde 401, 5xx ou corpo inesperado
- **THEN** o proxy devolve um erro genérico com status 502 ou 503, sem o corpo do backend

#### Scenario: Cabeçalho de cache
- **WHEN** qualquer resposta do proxy é emitida
- **THEN** ela traz `Cache-Control: no-store`

### Requirement: Proteção de laboratório declarada
O portal SHALL declarar, na documentação, que o código compartilhado é uma proteção de laboratório e não substitui autenticação por usuário.

#### Scenario: Documentação
- **WHEN** a documentação de deploy é lida
- **THEN** ela traz essa limitação de forma explícita
