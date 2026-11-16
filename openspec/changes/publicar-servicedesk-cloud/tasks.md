# Tasks

## 1. Auditoria em PostgreSQL

- [x] 1.1 Adicionar `spring-boot-starter-jdbc`, `postgresql`, `flyway-core`, `flyway-database-postgresql` e, em teste, `testcontainers-postgresql`; criar a migração `V1__auditoria.sql` (`execucao` e `notificacao`, `TIMESTAMPTZ`); verificar com teste Testcontainers que a migração cria as tabelas num banco vazio e não reaplica ao subir de novo.
- [x] 1.2 Criar `AuditoriaPort`, os enums de status com ordem, o campo `provedor` em `AnaliseChamado` (construtor de 5 argumentos mantido) e `JdbcAuditoriaAdapter` com `UPDATE` condicional por ordem; verificar com teste de integração em PostgreSQL real: avanço normal, consumidor antes do publicador, falha tardia depois de aceito, sucesso depois de falha, e `ultimo_erro` sanitizado e truncado em 200 caracteres.
- [x] 1.3 Chamar a auditoria em `AnalisarChamadoService`, `EmailService` e na publicação, tolerando falha da auditoria; verificar com dublê da porta: transições `ANALISADA`, `PUBLICACAO_PENDENTE`, `PUBLICACAO_CONFIRMADA`, `PUBLICACAO_INCERTA`, `PUBLICACAO_FALHOU`, `ACEITO_PELO_PROVEDOR`, `FALHA_ENVIO`, e que a falha da auditoria não derruba o atendimento nem vaza conteúdo no log.
- [x] 1.4 Teste ponta a ponta com PostgreSQL, RabbitMQ e Mailpit reais: chamado autorizado termina com execução `PUBLICACAO_CONFIRMADA` e notificação `ACEITO_PELO_PROVEDOR`; sem autorização não há notificação; nenhuma coluna contém nome, descrição, resumo ou corpo.

## 2. Perfil cloud e imagem

- [x] 2.1 Criar `application-cloud.yaml` (PORT, pool 3/0, EMAIL_MODE resend, logs em stdout, saúde sem detalhes) e a validação que recusa JDBC sem `sslmode=require` e broker sem `amqps://`; verificar com testes de inicialização: `DB_URL` ausente, JDBC sem TLS, `amqp://`, e subida correta com valores válidos.
- [x] 2.2 Ajustar `Dockerfile` (memória da JVM para instância pequena, `PORT`) e `compose.yaml` (PostgreSQL local, `DB_*`); verificar `docker compose config --quiet`, build da imagem e subida local com banco, broker e Mailpit, com a aplicação saudável e as migrações aplicadas.

## 3. Portal e proxy na Vercel

- [x] 3.1 Criar `portal/` (página, `api/triagem.js`, `vercel.json`) conforme a spec `portal-triagem`; verificar com testes do runner nativo do Node: GET 405, código inválido 401, entrada inválida 400, campos extras descartados, destino fixo, timeout 504 sem repetição, erro do backend vira 502/503 genérico, `Cache-Control: no-store`, nenhum segredo no HTML.

## 4. Publicação e automação

- [x] 4.1 Criar `render.yaml` (Docker, plano free, `healthCheckPath`, `autoDeployTrigger: checksPass`, secrets com `sync: false`) e `.github/workflows/ci.yml` (testes, build, validação do Docker, testes do portal); verificar o YAML localmente e que o workflow só referencia ações com versão conferida.
- [x] 4.2 Atualizar `.env.example`, `.gitignore` e `.dockerignore` para as novas variáveis; escrever `docs/deploy.md` (provisionamento, variáveis sem valor, limitações dos planos, reimplantação, desativação) e `docs/deploy-verification.md` (somente resultados realmente obtidos); verificar com o teste de segredos e leitura contra a spec `operacao-cloud`.

## 5. Provisionamento e validação publicada

- [ ] 5.1 Provisionar Neon (projeto `servicedesk-ai`, banco `servicedesk`, plano Free) e obter o JDBC com TLS; verificar a conexão e as migrações aplicadas.
- [ ] 5.2 Provisionar CloudAMQP (RabbitMQ, Little Lemur Free) e confirmar `amqps`; verificar que a topologia é declarada e a publicação é confirmada.
- [ ] 5.3 Configurar o Resend e a chave; verificar que a API aceita uma solicitação de teste para o destinatário autorizado.
- [ ] 5.4 Publicar o backend no Render Free a partir do GitHub; verificar healthcheck, logs de inicialização (banco, migrações, broker, listener) e 401 sem chave.
- [ ] 5.5 Publicar o portal na Vercel com `BACKEND_URL`, `BACKEND_API_KEY` e `DEMO_ACCESS_CODE`; verificar HTTPS, 401 com código inválido e 400 com entrada inválida.
- [ ] 5.6 Validação ponta a ponta com dados fictícios: análise, linha em `execucao`, publicação confirmada, consumo, aceite do Resend e estado final na auditoria; sem autorização, nenhum e-mail publicado.

## Workflow follow-up

- Não arquivar a mudança enquanto houver tarefa do grupo 5 sem verificação; registrar as pendências em `docs/deploy-verification.md`.
