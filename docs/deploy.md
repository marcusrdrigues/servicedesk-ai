# Publicação na nuvem (planos gratuitos)

Arquitetura publicada:

```
Navegador ──► Portal + proxy (Vercel) ──► API Spring Boot (Render) ──► OpenAI / Gemini
                                              │  │
                                              │  └──► PostgreSQL (Neon): auditoria
                                              ▼
                                  RabbitMQ (CloudAMQP) ──► consumidor (mesma aplicação) ──► Resend (HTTPS)
```

API e consumidor ficam **na mesma aplicação**, num único Web Service. Não há Spring Boot, Docker Compose nem RabbitMQ
dentro das funções da Vercel. O Compose é só para uso local.

> Estado da publicação: veja [`deploy-verification.md`](deploy-verification.md). Este documento é o procedimento.

## Planos gratuitos usados e seus limites

Conferidos nas páginas oficiais em **2026-11-16**. Planos mudam: reconfira antes de provisionar.

| Serviço | Plano | Limites que importam aqui |
|---|---|---|
| Render | Web Service **Free** | Suspende após 15 min sem tráfego de entrada (~1 min para retomar); 750 h/mês por workspace; portas de saída 25, 465 e 587 bloqueadas; disco efêmero |
| Neon | **Free** (permanente, sem cartão) | 1 GB por projeto; 100 CU-h/mês; autosuspend após 5 min, retoma na próxima consulta |
| CloudAMQP | **Little Lemur** (RabbitMQ compartilhado) | 20 conexões; 100 filas; 1 milhão de mensagens/mês; 10 mil enfileiradas; instância sem uso por 2 meses é apagada após aviso. Selecione **RabbitMQ**, não LavinMQ |
| Resend | **Free** | 100 e-mails/dia e 3.000/mês |
| Vercel | **Hobby** | Função com duração máxima de 300 s; 1 milhão de invocações/mês |
| GitHub | gratuito | Repositório e Actions |

Pontos **não confirmados** nas páginas consultadas, a conferir no painel de cada serviço:

- RAM e CPU da instância gratuita do Render (a imagem foi testada localmente com limite de 512 MB, o que **não** é um dado oficial do Render).
- Se o CloudAMQP expõe `amqps://` e em que porta no plano gratuito.
- A regra de destinatário do `onboarding@resend.dev` (a orientação do laboratório é: remetente `onboarding@resend.dev` e `EMAIL_TO` igual ao e-mail da conta do Resend; para outros destinatários é preciso domínio verificado).
- **Elegibilidade do Vercel Hobby**: o plano é para uso pessoal e não comercial. Confirme que este laboratório se enquadra. Não declaro isso por você.

Não contrate plano pago, não cadastre cartão, não compre domínio e não crie recursos além dos listados.

## Limitações do plano gratuito (leia antes de demonstrar)

- O serviço do Render **pode suspender** após inatividade HTTP.
- A conexão com o RabbitMQ **não** mantém o serviço ativo. Enquanto suspenso, **o consumidor não processa mensagens**: elas esperam na fila (persistente) até a próxima requisição HTTP acordar o serviço.
- A primeira chamada depois da suspensão pode sofrer *cold start* de cerca de 1 minuto e estourar o timeout do proxy (504). Antes da demonstração, abra `https://<backend>/actuator/health` e espere responder.
- O disco local não persiste; tudo que importa está no Neon e no RabbitMQ.
- **Não há keep-alive**, de propósito: não se contornam os limites do plano com pings. Este projeto **não** promete funcionamento contínuo 24 horas.
- Sem Outbox, banco e RabbitMQ não são atômicos. Janelas de falha: (a) a mensagem é publicada e o processo cai antes de gravar `PUBLICACAO_CONFIRMADA`, então a auditoria fica `PUBLICACAO_PENDENTE`; (b) o timeout de confirmação deixa `PUBLICACAO_INCERTA`, e a mensagem pode ou não ter sido aceita.
- A auditoria **pode ter lacunas**: falha ao gravar não derruba o atendimento (fica no log).
- A OpenAI **não** é gratuita. Defina um limite de gasto na conta do provedor. O Gemini só é gratuito quando o modelo e a conta tiverem essa modalidade.

## Proteção de laboratório

O código de acesso da demonstração (`DEMO_ACCESS_CODE`) é uma **proteção de laboratório**: um segredo compartilhado.
Ele **não substitui** autenticação por usuário. Quem o souber pode usar o portal e gerar custo de modelo. Troque-o depois da demonstração.

## Dados pessoais (LGPD)

O portal aceita texto livre pela internet e o backend o envia à OpenAI e ao Gemini, fora do Brasil. Contrato de operador,
proibição de treino, retenção e base para a transferência internacional (art. 33) **não foram confirmados**.
**Use somente dados fictícios.** A auditoria no banco não guarda nome, descrição, resumo nem corpo de e-mail.

## Variáveis de ambiente

Os valores nunca vão para o Git. Cadastre-os **você mesmo** nos painéis; não cole segredos em chat, issue ou commit.

### Backend (Render)

| Variável | Origem | Observação |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | fixa no `render.yaml` | `cloud` |
| `OPENAI_API_KEY`, `OPENAI_CHAT_MODEL` | OpenAI | Modelo com versão, nunca alias |
| `GEMINI_API_KEY`, `GEMINI_CHAT_MODEL` | Google AI | Endpoint compatível com OpenAI |
| `SERVICEDESK_API_KEY` | você gera | O **mesmo valor** de `BACKEND_API_KEY` na Vercel |
| `DB_URL` | Neon | `jdbc:postgresql://HOST/servicedesk?sslmode=require`. Nunca `postgresql://` puro; sem usuário e senha na URL |
| `DB_USERNAME`, `DB_PASSWORD` | Neon | Usuário de aplicação |
| `RABBITMQ_URL` | CloudAMQP | `amqps://USUARIO:SENHA@HOST/VHOST` |
| `RESEND_API_KEY` | Resend | |
| `EMAIL_FROM`, `EMAIL_TO` | você | O destinatário vem do servidor; o modelo nunca o escolhe |

No perfil `cloud` o modo de e-mail é sempre Resend (não há `EMAIL_MODE`). `FORCE_PRIMARY_FAILURE` **não existe**: uma
chave de falha simulada em código de produção seria superfície de ataque; o fallback é provado por testes automatizados.

### Portal (Vercel) — somente no servidor

| Variável | Observação |
|---|---|
| `BACKEND_URL` | `https://<serviço>.onrender.com`, HTTPS obrigatório. Só a origem é usada; o caminho é fixo |
| `BACKEND_API_KEY` | O mesmo valor de `SERVICEDESK_API_KEY` |
| `DEMO_ACCESS_CODE` | Código da demonstração |

Nenhuma chave de OpenAI, Gemini, PostgreSQL, RabbitMQ ou Resend vai para a Vercel.

Para gerar um segredo (rode no seu terminal e copie direto para o painel):

```bash
node -e "console.log(require('crypto').randomBytes(36).toString('base64url'))"
```

## Ordem de provisionamento

1. **GitHub:** crie o repositório e envie o código. Antes de `git add`, confira que `.env` não aparece em `git status`.
2. **Neon:** projeto `servicedesk-ai`, banco `servicedesk`, plano Free, região próxima do Render. Use um usuário de aplicação, copie host, usuário e senha dos parâmetros de conexão e monte a `DB_URL` JDBC com `?sslmode=require`. Se o host tiver `-pooler`, é conexão com *pooling*; não remova o sufixo sem avaliar. Não desabilite a validação de TLS.
3. **CloudAMQP:** instância **RabbitMQ**, plano **Little Lemur Free**, mesma região. Confirme o `amqps://` no painel e monte a `RABBITMQ_URL`. A aplicação declara a topologia sozinha (`servicedesk.events`, `servicedesk.email`, `servicedesk.dlx`, `servicedesk.email.dlq`). Não apague filas para resolver conflito de argumentos: identifique o conflito e migre sem perder mensagens.
4. **Resend:** crie a chave de API. Sem domínio: `EMAIL_FROM=onboarding@resend.dev` e `EMAIL_TO` o e-mail da conta.
5. **Render:** *New → Blueprint*, aponte para o repositório (usa `render.yaml`) e informe os segredos pedidos. O deploy automático só ocorre depois que a CI do branch passa (`autoDeployTrigger: checksPass`).
6. Aguarde o primeiro deploy e confira nos logs: aplicação iniciada, conexão com o PostgreSQL, migração aplicada, conexão com o RabbitMQ, listener ativo e `/actuator/health` respondendo.
7. **Vercel:** projeto com *Root Directory* `portal` (sem framework). Cadastre `BACKEND_URL`, `BACKEND_API_KEY` e `DEMO_ACCESS_CODE` e faça o deploy.
8. Execute a validação ponta a ponta (abaixo) e registre os resultados em `deploy-verification.md`.

## Alternativa: portal na Netlify

Serve para **ver o portal** quando a Vercel não estiver disponível. É o mesmo código: a pasta `portal/public/` é a página e
`portal/netlify/functions/triagem.mjs` expõe `POST /api/triagem` reaproveitando o mesmo proxy testado. Só `public/` é servida como
estática: o código do servidor (`lib/`, `test/`, `netlify/`) não fica exposto.

```bash
cd portal
npx netlify-cli@27.20.0 login     # abre o navegador; o login é feito por você
npx netlify-cli@27.20.0 deploy --prod --no-build --dir public --functions netlify/functions --site-name servicedesk-ai-portal
```

**Estado atual (2026-11-16):** o site `servicedesk-ai-portal` existe e tem um deploy `ready` em
`https://servicedesk-ai-portal.netlify.app`, no plano **Free** do time `marcusp181's team`, sem repositório conectado.
Os arquivos estáticos publicados são `index.html`, `app.js`, `style.css` e `netlify.toml`.

- O site está protegido pelo login da Netlify (quem não é do time recebe 401 e um redirecionamento). Essa proteção não foi alterada.
- As variáveis `BACKEND_URL`, `BACKEND_API_KEY` e `DEMO_ACCESS_CODE` **não foram cadastradas**. Cadastre-as você mesmo no painel
  (Site configuration, Environment variables): o valor não deve passar pela conversa. Sem elas a página abre, mas o formulário
  devolve o erro genérico "O serviço não está configurado". Sem o backend publicado, devolve 502 ou 504.
- O **limite de execução síncrona das funções da Netlify e o que o plano gratuito inclui para funções não foram confirmados** na
  documentação consultada. O proxy espera até 35 s (como na Vercel); se a plataforma cortar a função antes, defina
  `PROXY_TIMEOUT_MS` menor.
- O CLI guarda o token de login em texto puro no arquivo de configuração do usuário. Para remover: `npx netlify-cli@27.20.0 logout`.
- Para apagar o site: painel da Netlify (Site configuration, Danger zone) ou `npx netlify-cli@27.20.0 sites:delete 686fccd9-0bb4-4c43-9a01-f2fa387aacdb`.
- O código de acesso continua sendo só uma proteção de laboratório.

## Validação depois de publicar

Use dados fictícios e o destinatário autorizado. Antes, acorde o backend: abra `/actuator/health`.

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://<portal>/                      # 200
curl -s -o /dev/null -w "%{http_code}\n" https://<backend>/actuator/health      # 200
curl -s -o /dev/null -w "%{http_code}\n" -X POST https://<backend>/api/chamados \
  -H "Content-Type: application/json" -d '{}'                                   # 401 (sem chave)
curl -s -o /dev/null -w "%{http_code}\n" -X POST https://<portal>/api/triagem \
  -H "Content-Type: application/json" \
  -d '{"nome":"A","descricao":"descricao fictícia com mais de vinte caracteres","autorizaEmail":false,"codigoAcesso":"errado"}'  # 401
```

Distinga sempre quatro coisas: **publicação confirmada** (o broker aceitou), **processamento do consumidor**, **aceite do
provedor de e-mail** (`ACEITO_PELO_PROVEDOR`) e **entrega ao destinatário**. Só afirme entrega com evidência (o e-mail na caixa).
Teste de falha de modelo, de DLQ e de retry é feito no ambiente local isolado, **nunca** nos serviços compartilhados.

## Nova publicação (redeploy)

1. Abra um PR ou envie ao branch publicado e espere a CI passar.
2. O Render publica sozinho (`checksPass`). Acompanhe os logs.
3. Portal: novo deploy na Vercel.
4. **Rollback:** no Render, redeploy do commit anterior; na Vercel, promova a implantação anterior. As migrações são só aditivas.

## Desativar os recursos

Nesta ordem, para não deixar mensagens órfãs e parar qualquer consumo:

1. **Vercel:** exclua o projeto do portal (ou o domínio) para encerrar o acesso.
2. **Render:** suspenda e depois exclua o Web Service.
3. **CloudAMQP:** exclua a instância (as mensagens pendentes são perdidas).
4. **Neon:** exclua o projeto `servicedesk-ai` (apaga a auditoria).
5. **Resend:** revogue a chave de API.
6. **OpenAI e Gemini:** revogue as chaves usadas.
7. **GitHub:** revogue tokens de deploy, se criou algum.
