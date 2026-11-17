# Verificação da publicação

Registro **apenas do que foi executado**. Cada linha traz o resultado real; o que não foi feito aparece como pendente.
Data: 2026-11-16. Sem segredos neste arquivo.

## Resumo

| Etapa | Estado |
|---|---|
| Código, testes e imagem Docker, **local** | Verificado |
| Neon (PostgreSQL) | **Pendente**: sem integração nem login por aqui |
| CloudAMQP (RabbitMQ) | **Pendente**: sem integração nem login por aqui |
| Resend | **Pendente**: sem integração nem login por aqui |
| GitHub (repositório e push) | **Pendente**: a pasta ainda não é um repositório |
| Render (backend) | **Pendente**: depende do GitHub e dos segredos acima |
| Vercel (portal e proxy) | **Bloqueado** pela Vercel (ver abaixo) |
| Netlify (portal e proxy, alternativa à Vercel) | **Publicado** em `https://servicedesk-ai-portal.netlify.app`, plano Free, atrás do login da Netlify; **comportamento ao vivo não verificado** (ver abaixo) |
| Validação ponta a ponta na nuvem | **Pendente** |

**Só o portal está publicado, e só na Netlify.** O backend, o banco, o broker e o e-mail não estão no ar.

### Netlify (verificado por API, não por navegação)

| Verificação | Resultado |
|---|---|
| Conta e time | `marcusp181's team`, `account_type: Free` |
| Deploy | `ready`, contexto `production`, 4 arquivos e 1 função |
| Arquivos estáticos publicados | `/index.html`, `/app.js`, `/style.css`, `/netlify.toml` (nenhum código de servidor) |
| Acesso por `curl` | **401** com redirecionamento para o login da Netlify, em `/` e em `/api/triagem` |

Por causa da proteção de acesso do site, **não foi possível verificar ao vivo** que a página abre, os cabeçalhos de segurança, o
405 a `GET /api/triagem` nem o 401 do código de acesso. Esses comportamentos estão cobertos só pelos testes locais do portal
(28 de 28, incluindo o adaptador da Netlify). Nenhuma variável de ambiente foi cadastrada no site.

## Verificado localmente

### Backend: `./mvnw -B verify`

BUILD SUCCESS: **147 testes, 0 falhas, 1 ignorado** (o eval com modelo real, que exige `EVAL_REAL=true` e chaves reais).

| Grupo | Testes | Resultado |
|---|---|---|
| Unitários da aplicação (análise, fallback, retry, validador, e-mail, auditoria, sanitização) | 49 | passam |
| Arquitetura (domínio e aplicação sem framework) | 3 | passam |
| Adaptador de IA contra servidor HTTP falso (429, 5xx, 401, timeout, JSON e enum inválidos) | 14 | passam |
| Adaptador do Resend simulado | 4 | passam |
| Publicador: nack, ACK, sem confirmação | 3 | passam |
| API HTTP (401, 400, 429, 503, 202, CORS, log sem conteúdo) | 22 | passam |
| Inicialização e perfil `cloud` (variável ausente, sem TLS, `postgresql://` puro, `amqp://`) | 16 | passam |
| Arquivos de deploy (parse de `render.yaml`, CI, Dockerfile) e segredos no repositório | 8 | passam |
| **Integração com RabbitMQ e Mailpit reais** (publicação, retry, DLQ, duplicata) | 14 | passam |
| **Integração com PostgreSQL real** (migração, transições, fluxo completo) | 15 | passam |

Contra PostgreSQL 17 real: a migração cria `execucao` e `notificacao` e não reaplica ao subir de novo; os estados só avançam
(consumidor antes do publicador, falha tardia depois do aceite, sucesso depois de falha); nenhuma coluna guarda nome,
descrição, resumo ou corpo. No fluxo completo, um chamado autorizado termina com execução `PUBLICACAO_CONFIRMADA` e
notificação `ACEITO_PELO_PROVEDOR`, e o e-mail chega ao Mailpit.

### Portal: `cd portal && node --test`

**25 testes, 25 passam**: 405 para método diferente de POST, 401 para código ausente ou errado (antes da validação),
400 e 413 para entrada inválida ou grande, só os três campos encaminhados, destino fixo na configuração, timeout 504 sem
repetir o POST, erros do backend sem repassar o corpo, `Cache-Control: no-store`, nenhum segredo no HTML, CSS e JS, e a
página só chama `/api/triagem`.

### Docker

| Verificação | Resultado |
|---|---|
| `docker compose config --quiet` com ambiente completo | OK |
| `docker compose config --quiet` sem as variáveis obrigatórias | falha citando a variável, como esperado |
| Build da imagem (multi-estágio, usuário sem privilégio) | OK |
| Subida com PostgreSQL, RabbitMQ, Mailpit e aplicação | os quatro saudáveis |
| `/actuator/health` | 200, só o estado geral |
| `POST /api/chamados` sem `X-API-Key` | 401 |
| Migração no PostgreSQL do container | `1 auditoria`, sucesso |
| Memória da JVM com limite de **512 MB** | ~180 MiB (35%), sem OOM, depois de 15 requisições. Teste local: **não** é dado oficial do Render |

### Defeito encontrado só pelo Docker

O JRE enxuto da imagem final não traz o módulo `jdk.random`, e `RandomGenerator.getDefault()` falhava na subida. Os testes
passavam porque rodam em JDK completo. Corrigido com `java.util.Random`.

### Arquivos de publicação

`render.yaml` e `.github/workflows/ci.yml` foram **lidos por parser YAML** (teste `ArquivosDeDeployTest`): plano `free`,
`autoDeployTrigger: checksPass`, `healthCheckPath: /actuator/health`, nenhum segredo com valor, todas as ações fixadas pelo
SHA do commit do release. **A CI nunca rodou no GitHub e o `render.yaml` nunca foi validado pelo Render.**

## Pendências reais

1. **Vercel bloqueada.** Ao criar o projeto, a API respondeu `402 resource_creation_blocked`: *"Your Team exceeded our fair use
   limits and has been blocked."* A resposta não diz qual limite. Quem resolve é o dono da conta, no painel do time
   (Settings, Billing e a página de bloqueio da conta). Não foi feita nenhuma tentativa de contornar nem de mudar plano.
   A elegibilidade do Hobby (uso pessoal e não comercial) não foi confirmada nas páginas consultadas.
2. **Neon, CloudAMQP, Resend e Render:** sem integração, CLI nem sessão acessível. É preciso entrar em cada painel.
3. **GitHub:** `git init`, primeiro commit e push (o `gh` está autenticado como `marcusrdrigues`). Antes, confira que `.env`
   não aparece em `git status`.
4. **Segredos do portal, na Vercel ou na Netlify** (`BACKEND_API_KEY`, `DEMO_ACCESS_CODE`, `BACKEND_URL`): devem ser cadastrados por você no painel.
   Passá-los por ferramenta registraria o valor na conversa.
5. **TLS do CloudAMQP, RAM do Render e regra do `onboarding@resend.dev`:** não confirmados nas páginas oficiais consultadas.
6. **Eval com modelo real** (tarefa da mudança anterior): nunca rodou.

## O que ainda não se pode afirmar

- Que o portal abre por HTTPS: não está publicado.
- Que o deploy só ocorre depois da CI: está configurado e testado no arquivo, **não** observado no Render.
- Que o Resend aceita a solicitação, que o consumidor processa na nuvem ou que a auditoria grava no Neon.
- Qualquer entrega de e-mail ao destinatário: sem evidência.
