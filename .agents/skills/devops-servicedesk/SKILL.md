---
name: devops-servicedesk
description: Prepara, publica e valida o ServiceDesk AI usando Vercel, Render, Neon, CloudAMQP e Resend, exclusivamente em planos gratuitos.
---

# Objetivo

Publicar o projeto ServiceDesk AI com:

- Portal e proxy na Vercel.
- Aplicação Spring Boot em Docker no Render.
- PostgreSQL no Neon.
- RabbitMQ no CloudAMQP.
- E-mails pela API HTTPS do Resend.
- Código e automação no GitHub.

Executar o trabalho disponível até a validação da publicação.
Não encerrar apenas apresentando um plano.

# Arquitetura obrigatória

Fluxo:

Navegador
→ portal e proxy na Vercel
→ API Spring Boot no Render
→ OpenAI ou Gemini
→ RabbitMQ no CloudAMQP
→ consumidor Spring Boot
→ Resend por HTTPS.

A aplicação Java acessa o PostgreSQL no Neon.

API e consumidor permanecem na mesma aplicação neste laboratório,
reduzindo a quantidade de serviços necessários.

Não tentar executar Spring Boot, Docker Compose ou RabbitMQ
dentro das funções da Vercel.

Não substituir RabbitMQ por outro broker sem registrar
a alteração na especificação.

# Limites de custo

1. Utilizar exclusivamente planos gratuitos.
2. Conferir os planos e limites atuais antes de provisionar.
3. Não contratar planos pagos, upgrades ou testes que convertam
   automaticamente para cobrança.
4. Não cadastrar cartão nem habilitar cobrança automática.
5. Não criar recursos adicionais sem necessidade.
6. Reutilizar projetos existentes quando forem os indicados
   para este laboratório.
7. Não afirmar que as chamadas à OpenAI são gratuitas.
8. Utilizar Gemini gratuito somente quando o modelo e a conta
   possuírem essa modalidade disponível.
9. Não comprar domínio: utilizar os subdomínios dos provedores.
10. Não contornar limites com pings ou tráfego artificial.

Se algum componente não estiver disponível gratuitamente,
informe a limitação e mantenha o restante do trabalho preparado.

Não migrar silenciosamente para um plano pago.

# Leitura inicial

Antes de modificar:

1. Ler AGENTS.md, se existir.
2. Ler pom.xml e identificar as versões reais.
3. Examinar os pacotes e classes existentes.
4. Ler application.yaml e seus profiles.
5. Ler Dockerfile e compose.yaml.
6. Ler os arquivos do portal.
7. Ler as configurações de CI/CD existentes.
8. Ler openspec/config.yaml.
9. Ler as especificações e tarefas da mudança ativa.
10. Examinar a configuração de mensageria e de e-mail.

Preservar as versões compatíveis já adotadas.
Não atualizar o Spring Boot ou Spring AI incidentalmente.

# Integração com OpenSpec

Antes de implementar alterações de comportamento:

1. Verificar se a mudança ativa já contempla publicação e PostgreSQL.
2. Se contemplar, continuar nas tarefas existentes.
3. Caso contrário, criar a mudança publicar-servicedesk-cloud
   com proposta, desenho, requisitos e tarefas.
4. Registrar a arquitetura e as limitações dos planos gratuitos.
5. Manter as especificações coerentes com o que foi implementado.
6. Marcar tarefas somente depois de verificar sua conclusão.

Não arquivar uma mudança com verificações pendentes
sem registrar explicitamente essas pendências.

# Preparação local

Executar as verificações disponíveis:

- Java.
- Maven Wrapper.
- Docker.
- Git.
- Node.js.
- CLI da Vercel, quando necessária.

No Windows, utilizar mvnw.cmd.
No Linux, utilizar ./mvnw.

Verificar:

- Testes automatizados.
- Compilação.
- Validação do Compose.
- Construção da imagem Docker.
- Inicialização local, quando Docker estiver disponível.

Não exigir chaves reais para testes unitários.
Não executar envio real de e-mail em testes automatizados.

# Proteção dos segredos

Manter fora do Git:

- .env.
- .env.local.
- Chaves OpenAI e Gemini.
- Credenciais PostgreSQL.
- URL RabbitMQ com senha.
- Chave Resend.
- Tokens de deploy.
- Segredo do backend.

Atualizar .gitignore e .dockerignore.

Gerar .env.example somente com valores fictícios.

Não imprimir segredos em:

- Logs.
- Comandos de diagnóstico.
- Relatórios.
- Capturas.
- Commits.
- Arquivos de evidências.

Evitar comandos que exibam a configuração resolvida
com segredos, como docker compose config sem --quiet.

Segredos do backend nunca podem aparecer no HTML
ou em variáveis públicas do frontend.

# PostgreSQL no Neon

Utilizar um projeto no plano Free.

Escolher região próxima ao backend, conforme disponibilidade.

Criar ou reutilizar:

- Projeto: servicedesk-ai.
- Banco: servicedesk.
- Usuário de aplicação dedicado, quando possível.

Obter os parâmetros de conexão pelo painel ou integração autenticada.

Utilizar JDBC PostgreSQL com TLS.

Variáveis:

- DB_URL.
- DB_USERNAME.
- DB_PASSWORD.

Formato ilustrativo:

DB_URL=jdbc:postgresql://HOST/BANCO?sslmode=require

Não utilizar diretamente uma URL postgresql:// como JDBC.
Não embutir usuário e senha na URL versionada.
Não desabilitar validação TLS para resolver problemas de conexão.

Usar pool pequeno:

- maximum-pool-size: 3.
- minimum-idle: 0.
- connection-timeout: 30000.
- idle-timeout: 60000.

Verificar se a conexão é direta ou pooled.
Não remover o sufixo -pooler de um hostname sem avaliar
o tipo de conexão desejado.

Não depender de disco local do Render para persistência.

# Uso real do banco

Primeiro verificar se o projeto já utiliza PostgreSQL.

Se utilizar:
- Adaptar a configuração para o Neon.
- Preservar o modelo e as migrações existentes.
- Verificar leitura e escrita reais.

Se ainda não utilizar:
- Registrar no OpenSpec a inclusão de auditoria de execuções.
- Implementar persistência mínima de execuções e notificações.
- Utilizar Spring JDBC e Flyway para manter a solução simples.
- Adicionar o driver PostgreSQL e o módulo Flyway PostgreSQL.
- Gerenciar versões pelas dependências do Spring Boot.

Persistência mínima sugerida:

Execução:
- id.
- criado_em.
- provedor.
- categoria.
- prioridade.
- status.

Notificação:
- evento_id.
- execucao_id.
- status.
- atualizado_em.
- ultimo_erro sanitizado.

Evitar armazenar nome, descrição completa e conteúdo do e-mail
quando não forem necessários para o laboratório.

Usar TIMESTAMPTZ para instantes.
Não usar ddl-auto=create ou create-drop na nuvem.

Não transformar uma simples integração de banco em uma
reformulação completa da aplicação.

# Consistência da auditoria

Registrar as transições implementadas com nomes precisos.

Exemplos:

- ANALISADA.
- PUBLICACAO_PENDENTE.
- PUBLICACAO_CONFIRMADA.
- PUBLICACAO_INCERTA.
- ACEITO_PELO_PROVEDOR.
- FALHA_ENVIO.

Não registrar ENTREGUE apenas porque a API de e-mail retornou sucesso.

Considerar que o consumidor pode processar a mensagem antes
de o publisher terminar de atualizar a auditoria.

Usar atualizações condicionais para impedir regressão de status.

Não manter transação PostgreSQL aberta durante chamada ao LLM
ou envio de e-mail.

Não afirmar atomicidade entre banco e RabbitMQ.
Se não houver Outbox, documentar as janelas de falha existentes.

Não implementar Outbox automaticamente nesta etapa,
a menos que já exista esse requisito aprovado.

# RabbitMQ no CloudAMQP

Selecionar explicitamente:

- Produto: RabbitMQ.
- Plano: Little Lemur Free.

Não selecionar LavinMQ como se fosse RabbitMQ.

Usar conexão TLS:

RABBITMQ_URL=amqps://USUARIO:SENHA@HOST/VHOST

Preservar as configurações de segurança fornecidas pelo provedor.
Não desabilitar verificação de certificado ou hostname.

Preservar a topologia existente:

- Exchange: servicedesk.events.
- Routing key: email.solicitado.
- Fila: servicedesk.email.
- Dead-letter exchange: servicedesk.dlx.
- Routing key de falha: email.falhou.
- DLQ: servicedesk.email.dlq.

Utilizar:

- Filas e exchanges duráveis.
- Mensagens persistentes.
- Publisher confirms.
- Mandatory e publisher returns.
- Até três tentativas totais no consumidor.
- Requeue infinito desabilitado.
- Prefetch 1.
- Concorrência 1 no laboratório.

Manter o mesmo eventoId em todas as tentativas.

Não apagar filas para resolver incompatibilidade de argumentos.
Identificar o conflito e preparar uma migração sem perda de mensagens.

# E-mail com Resend

Utilizar Resend por HTTPS na nuvem.

Não depender de SMTP no Render gratuito.

Variáveis:

- EMAIL_MODE=resend.
- EMAIL_FROM.
- EMAIL_TO.
- RESEND_API_KEY.

Para testar sem domínio próprio:

- EMAIL_FROM=onboarding@resend.dev.
- EMAIL_TO deve ser o e-mail da conta Resend.

Para outros destinatários, utilizar domínio já verificado.

Não comprar domínio nem modificar DNS sem necessidade.

Enviar texto simples.
Utilizar eventoId como Idempotency-Key.
Manter o payload estável entre tentativas.

Configurar timeout de conexão e resposta.
Propagar erros para permitir retry.

Não afirmar deduplicação ilimitada:
a chave de idempotência do provedor possui janela de validade.

# Backend Spring Boot no Render

Utilizar um único Web Service no plano Free.

Não criar Background Worker pago.

O consumidor RabbitMQ será iniciado na mesma aplicação da API.

Criar ou ajustar:

- Dockerfile com build em múltiplos estágios.
- Usuário sem privilégios no container.
- .dockerignore.
- application-cloud.yaml, se necessário.
- render.yaml.

Configurar:

- PORT fornecida pelo ambiente.
- Escuta em interface acessível ao provedor.
- Memória da JVM compatível com a instância.
- Logs em stdout.
- Healthcheck em /actuator/health.
- FORCE_PRIMARY_FAILURE=false.

Não expor detalhes sensíveis no Actuator.

Não executar containers de banco, Mailpit ou RabbitMQ
dentro do container da aplicação.

Docker Compose será utilizado somente localmente.

Aguardar o primeiro deploy e verificar os logs de inicialização.

Verificar:

- Aplicação iniciada.
- Conexão PostgreSQL.
- Migrações concluídas.
- Conexão RabbitMQ.
- Listener ativo.
- Endpoint de saúde respondendo.

# Suspensão do Render gratuito

Registrar explicitamente:

- O serviço pode suspender após inatividade HTTP.
- A conexão RabbitMQ não garante que o serviço permaneça ativo.
- Enquanto suspenso, o consumidor não processa mensagens.
- Uma requisição HTTP inicia a retomada.
- A primeira chamada pode sofrer cold start.
- O disco local não é persistente.

Antes do teste ponta a ponta, acessar o healthcheck
e aguardar a aplicação estar pronta.

Não implementar keep-alive artificial para contornar a suspensão.

Não afirmar autonomia contínua 24 horas neste plano.

# Portal na Vercel

Publicar somente o portal e o proxy HTTP.

Utilizar o plano gratuito somente quando o projeto
for compatível com as condições atuais desse plano.

Não declarar automaticamente que um projeto institucional
ou comercial é elegível ao Hobby.

Se houver incompatibilidade, informar antes da publicação
e preparar uma alternativa gratuita elegível.

Preservar o portal existente.

O navegador deve chamar uma rota relativa:

POST /api/triagem

A função da Vercel chama a API Spring Boot.

Variáveis exclusivas do servidor:

- BACKEND_URL.
- BACKEND_API_KEY.
- DEMO_ACCESS_CODE.

BACKEND_API_KEY deve corresponder ao APP_API_KEY do backend.

O proxy deve:

- Aceitar somente POST.
- Validar entradas e tamanhos.
- Validar o código de acesso da demonstração.
- Encaminhar apenas campos permitidos.
- Utilizar URL de backend fixa pela configuração.
- Aplicar timeout limitado.
- Definir Cache-Control: no-store.
- Não registrar segredos.
- Não retornar stack traces.
- Não repetir automaticamente POST após timeout.

Não implementar proxy aberto nem aceitar URL arbitrária do navegador.

Se usar código compartilhado, registrar que essa proteção
é destinada ao laboratório e não substitui autenticação por usuário.

# Timeouts

Alinhar o orçamento de tempo entre:

- Chamada OpenAI.
- Fallback Gemini.
- Publicação RabbitMQ.
- Proxy Vercel.

Manter o processamento dentro do limite efetivo da função.

Não presumir que um timeout no navegador cancelou
a operação no backend.

Não repetir a triagem automaticamente:
uma repetição pode gerar outra execução e outro e-mail.

# Variáveis do backend

Preparar a configuração para:

SPRING_PROFILES_ACTIVE=cloud

OPENAI_API_KEY
OPENAI_MODEL

GEMINI_API_KEY
GEMINI_MODEL

APP_API_KEY

DB_URL
DB_USERNAME
DB_PASSWORD

RABBITMQ_URL

EMAIL_MODE=resend
EMAIL_FROM
EMAIL_TO
RESEND_API_KEY

FORCE_PRIMARY_FAILURE=false

Adaptar os nomes somente quando o projeto já possuir
uma convenção diferente.

Manter documentação e configuração consistentes.

# Variáveis da Vercel

BACKEND_URL
BACKEND_API_KEY
DEMO_ACCESS_CODE

Não duplicar as chaves OpenAI, Gemini, PostgreSQL,
RabbitMQ ou Resend no frontend.

O acesso aos serviços acontece pelo backend Java.

# Provisionamento e publicação

Usar integrações autenticadas disponíveis quando forem suficientes.
Como alternativa, utilizar CLIs oficiais ou o painel do provedor.

Não inventar comandos, IDs de recursos, tokens ou URLs.

Executar em ordem:

1. Verificar planos, elegibilidade e ferramentas disponíveis.
2. Validar a implementação local.
3. Preparar especificações e migrações.
4. Provisionar ou configurar Neon.
5. Provisionar ou configurar CloudAMQP.
6. Configurar Resend.
7. Configurar e publicar o backend no Render.
8. Aguardar healthcheck e verificar logs.
9. Configurar e publicar o portal na Vercel.
10. Executar validação ponta a ponta.
11. Atualizar tarefas e documentação.

Se faltar autenticação:
- Concluir primeiro os arquivos e verificações independentes.
- Informar o provedor e a etapa exata que depende de login.
- Solicitar que o usuário faça login pelo fluxo seguro.
- Não solicitar que cole tokens no chat.
- Retomar a etapa depois da autenticação.

Não afirmar publicação concluída quando somente
os arquivos de deploy tiverem sido gerados.

# GitHub e automação

Preservar o repositório existente.

Criar ou ajustar uma pipeline que:

1. Faça checkout.
2. Configure Java compatível com o projeto.
3. Execute testes.
4. Compile a aplicação.
5. Valide o build Docker quando aplicável.

Configurar o deploy para acontecer somente após sucesso da CI,
se a integração do provedor suportar esse comportamento.

Se não suportar, utilizar deploy manual ou acionamento controlado.
Não afirmar que o deploy depende da CI quando ele ocorre
automaticamente em paralelo ao push.

Não armazenar tokens em YAML.
Utilizar secrets do GitHub somente quando forem necessários.

Não criar automação de keep-alive.

# Validação ponta a ponta

Antes de testes reais, usar dados fictícios e o destinatário
de demonstração autorizado.

Verificar:

1. Portal abre por HTTPS.
2. Código inválido retorna 401.
3. Acesso direto ao backend sem chave retorna 401.
4. Entrada inválida retorna 400.
5. Healthcheck retorna sucesso.
6. Chamado válido produz análise.
7. Execução é persistida no Neon.
8. Publicação RabbitMQ é confirmada.
9. Consumidor processa o evento.
10. Resend aceita a solicitação.
11. Auditoria registra o estado correto.
12. Sem autorização, nenhum e-mail é publicado.

Testar fallback por teste automatizado ou configuração temporária
no ambiente local.

Não manter falha simulada habilitada na nuvem.

Não parar serviços compartilhados para testar DLQ.
Executar testes destrutivos apenas no ambiente local isolado.

Distinguir:

- Publicação confirmada.
- Processamento do consumidor.
- Aceite do provedor de e-mail.
- Entrega ao destinatário.

Somente afirmar entrega quando houver evidência correspondente.

# Arquivos esperados

Criar ou atualizar conforme a necessidade:

- Dockerfile.
- .dockerignore.
- compose.yaml.
- .env.example.
- .gitignore.
- render.yaml.
- portal/vercel.json.
- Proxy do portal.
- application-cloud.yaml.
- Migrações Flyway.
- Configuração de persistência.
- Pipeline GitHub Actions.
- docs/deploy.md.
- docs/deploy-verification.md.
- Artefatos OpenSpec.

Não criar arquivos vazios apenas para completar a lista.

# Relatório final

Entregar:

1. Arquivos criados e alterados.
2. URLs reais dos serviços publicados.
3. Serviços e planos efetivamente utilizados.
4. Variáveis configuradas, sem os valores.
5. Testes executados e resultados.
6. Evidência de persistência no Neon.
7. Evidência de publicação e consumo RabbitMQ.
8. Evidência do aceite ou entrega do e-mail.
9. Limitações dos planos gratuitos.
10. Pendências reais.
11. Procedimento de nova publicação.
12. Procedimento para desativar os recursos.

Não incluir credenciais, URLs com senha ou tokens.

Não marcar como concluída uma etapa que dependa de login,
configuração manual ou verificação ainda não realizada.