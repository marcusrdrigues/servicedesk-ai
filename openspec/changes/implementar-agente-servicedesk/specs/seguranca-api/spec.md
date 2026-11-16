# Spec Delta

## Purpose

Define a proteção da API do Service Desk por segredo mantido no servidor, o formato de erro sem vazamento de detalhe interno e a higiene de segredos, para que custo de modelo e envio de e-mail não fiquem abertos a qualquer chamador.

## ADDED Requirements

### Requirement: Autenticação por segredo do servidor
O sistema SHALL exigir, em toda rota de negócio, um segredo enviado no cabeçalho `X-API-Key`, comparado em tempo constante com o valor configurado no servidor.

#### Scenario: Segredo correto
- **WHEN** a requisição traz `X-API-Key` igual ao segredo do servidor
- **THEN** a requisição segue para validação e análise

#### Scenario: Segredo ausente
- **WHEN** a requisição não traz `X-API-Key`
- **THEN** o sistema responde HTTP 401 antes de validar o corpo e sem chamar modelo

#### Scenario: Segredo incorreto
- **WHEN** a requisição traz `X-API-Key` diferente do segredo do servidor
- **THEN** o sistema responde HTTP 401 e não revela se o erro foi tamanho ou conteúdo

### Requirement: Segredo obrigatório na inicialização
O sistema SHALL recusar a inicialização quando o segredo da API não estiver definido ou for vazio.

#### Scenario: Segredo não definido
- **WHEN** a variável de ambiente do segredo está ausente
- **THEN** a aplicação não sobe e a mensagem cita o nome da variável, nunca um valor

### Requirement: Rotas públicas mínimas
O sistema SHALL manter sem autenticação apenas a verificação de saúde, e SHALL NOT expor nela detalhes de configuração, segredos nem estado interno de dependências.

#### Scenario: Saúde
- **WHEN** um cliente consulta a verificação de saúde sem segredo
- **THEN** recebe apenas o estado geral, sem detalhes das dependências

### Requirement: Limite de requisições
O sistema SHALL limitar a taxa de requisições por chamador, configurável, porque cada requisição aceita pode gerar custo de modelo.

#### Scenario: Limite excedido
- **WHEN** um chamador ultrapassa o limite configurado
- **THEN** o sistema responde HTTP 429 sem chamar modelo

### Requirement: Erros sem detalhe interno
O sistema SHALL responder erros no formato ProblemDetail, sem stack trace, nome de classe, mensagem de exceção de biblioteca, chave ou texto bruto do modelo.

#### Scenario: Erro inesperado
- **WHEN** ocorre uma exceção não prevista
- **THEN** a resposta é HTTP 500 com mensagem genérica e identificador da requisição para correlação

### Requirement: Segredos fora do repositório
O sistema SHALL ler todas as chaves (OpenAI, Gemini, Resend, broker, segredo da API) de variáveis de ambiente, e SHALL NOT conter nenhum valor secreto em arquivo versionado.

#### Scenario: Arquivos versionados
- **WHEN** o repositório é inspecionado
- **THEN** nenhum arquivo versionado contém chave real, e o arquivo de exemplo traz apenas nomes de variáveis com valores de marcação

#### Scenario: Arquivo local de segredos
- **WHEN** o desenvolvedor usa um arquivo `.env` local
- **THEN** o arquivo está no `.gitignore`

### Requirement: CORS restrito
O sistema SHALL aceitar chamadas de navegador somente da origem do portal configurada no servidor.

#### Scenario: Origem não autorizada
- **WHEN** uma requisição de navegador vem de origem diferente da configurada
- **THEN** o sistema não devolve cabeçalhos CORS permissivos
