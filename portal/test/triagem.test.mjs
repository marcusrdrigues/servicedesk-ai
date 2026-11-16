import assert from 'node:assert/strict';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

import { LIMITES, codigoConfere, criarHandler, urlDoBackend, validarEntrada } from '../lib/triagem.mjs';

const ENV = Object.freeze({
  BACKEND_URL: 'https://backend.exemplo.test',
  BACKEND_API_KEY: 'chave-do-backend-ficticia',
  DEMO_ACCESS_CODE: 'codigo-da-demo',
});

const ENTRADA = Object.freeze({
  nome: 'Maria',
  descricao: 'A VPN caiu para todo o escritório desde as nove horas',
  autorizaEmail: true,
  codigoAcesso: 'codigo-da-demo',
});

const ANALISE = Object.freeze({
  categoria: 'REDE',
  prioridade: 'ALTA',
  resumo: 'VPN caiu',
  orientacao: 'Reiniciar o concentrador',
  enviarEmail: true,
  notificacao: 'ENFILEIRADO',
});

/** fetch falso que registra as chamadas e responde como o teste mandar. */
function backendFalso(resposta) {
  const chamadas = [];
  const impl = async (url, opcoes) => {
    chamadas.push({ url, opcoes });
    if (typeof resposta === 'function') return resposta(url, opcoes);
    // o corpo de um Response só pode ser lido uma vez: cada chamada recebe uma cópia nova
    return resposta.clone();
  };
  return { impl, chamadas };
}

const json = (corpo, status = 200) =>
  new Response(JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } });

function post(corpo, extras = {}) {
  return new Request('https://portal.exemplo.test/api/triagem', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...extras },
    body: typeof corpo === 'string' ? corpo : JSON.stringify(corpo),
  });
}

function handlerCom(resposta, opcoes = {}) {
  const backend = backendFalso(resposta);
  const handler = criarHandler({ env: ENV, fetchImpl: backend.impl, ...opcoes });
  return { handler, chamadas: backend.chamadas };
}

async function corpoDe(res) {
  return res.json();
}

// --- método, código e validação ---

test('GET devolve 405 com Allow: POST e não chama o backend', async () => {
  const { handler, chamadas } = handlerCom(json(ANALISE));
  const res = await handler(new Request('https://portal.exemplo.test/api/triagem', { method: 'GET' }));
  assert.equal(res.status, 405);
  assert.equal(res.headers.get('allow'), 'POST');
  assert.equal(chamadas.length, 0);
});

test('outros métodos também são recusados', async () => {
  const { handler, chamadas } = handlerCom(json(ANALISE));
  for (const metodo of ['PUT', 'DELETE', 'PATCH']) {
    const res = await handler(new Request('https://portal.exemplo.test/api/triagem', { method: metodo }));
    assert.equal(res.status, 405, metodo);
  }
  assert.equal(chamadas.length, 0);
});

test('código de acesso ausente ou errado devolve 401 e não chama o backend', async () => {
  const { handler, chamadas } = handlerCom(json(ANALISE));
  const { codigoAcesso: _, ...semCodigo } = ENTRADA;
  for (const corpo of [semCodigo, { ...ENTRADA, codigoAcesso: 'errado' }, { ...ENTRADA, codigoAcesso: 123 }, { ...ENTRADA, codigoAcesso: '' }]) {
    const res = await handler(post(corpo));
    assert.equal(res.status, 401, JSON.stringify(corpo.codigoAcesso));
  }
  assert.equal(chamadas.length, 0);
});

test('o 401 vem antes da validação: quem não tem o código não descobre as regras', async () => {
  const { handler } = handlerCom(json(ANALISE));
  const res = await handler(post({ nome: '', descricao: 'x', autorizaEmail: 'talvez', codigoAcesso: 'errado' }));
  assert.equal(res.status, 401);
});

test('entrada inválida devolve 400 e não chama o backend', async () => {
  const { handler, chamadas } = handlerCom(json(ANALISE));
  const invalidas = [
    { ...ENTRADA, nome: undefined },
    { ...ENTRADA, nome: '   ' },
    { ...ENTRADA, nome: 'n'.repeat(LIMITES.nomeMax + 1) },
    { ...ENTRADA, descricao: 'curta demais' },
    { ...ENTRADA, descricao: 'd'.repeat(LIMITES.descricaoMax + 1) },
    { ...ENTRADA, descricao: ' '.repeat(30) },
    { ...ENTRADA, autorizaEmail: undefined },
    { ...ENTRADA, autorizaEmail: 'true' },
    { ...ENTRADA, autorizaEmail: 1 },
    { ...ENTRADA, nome: { a: 1 } },
  ];
  for (const corpo of invalidas) {
    const res = await handler(post(corpo));
    assert.equal(res.status, 400, JSON.stringify(corpo).slice(0, 80));
  }
  assert.equal((await handler(post('isto não é json'))).status, 400);
  assert.equal((await handler(post('[]'))).status, 401); // array: sem código
  assert.equal(chamadas.length, 0);
});

test('limites exatos são aceitos', async () => {
  const { handler, chamadas } = handlerCom(json(ANALISE));
  const res1 = await handler(post({ ...ENTRADA, nome: 'n'.repeat(LIMITES.nomeMax), descricao: 'd'.repeat(LIMITES.descricaoMin) }));
  const res2 = await handler(post({ ...ENTRADA, descricao: 'd'.repeat(LIMITES.descricaoMax) }));
  assert.equal(res1.status, 200);
  assert.equal(res2.status, 200);
  assert.equal(chamadas.length, 2);
});

test('corpo grande demais devolve 413 sem chamar o backend', async () => {
  const { handler, chamadas } = handlerCom(json(ANALISE));
  const res = await handler(post({ ...ENTRADA, lixo: 'x'.repeat(LIMITES.corpoMaxBytes + 1) }));
  assert.equal(res.status, 413);
  assert.equal(chamadas.length, 0);
});

// --- encaminhamento ---

test('encaminha só os três campos permitidos, com a chave só no cabeçalho', async () => {
  const { handler, chamadas } = handlerCom(json(ANALISE, 202));
  const res = await handler(
    post({
      ...ENTRADA,
      destinatario: 'atacante@exemplo.com',
      to: 'atacante@exemplo.com',
      url: 'https://evil.example',
      enviarEmail: true,
    }),
  );
  assert.equal(res.status, 202);
  assert.equal(chamadas.length, 1);
  const enviado = JSON.parse(chamadas[0].opcoes.body);
  assert.deepEqual(Object.keys(enviado).sort(), ['autorizaEmail', 'descricao', 'nome']);
  assert.ok(!chamadas[0].opcoes.body.includes(ENV.DEMO_ACCESS_CODE), 'o código de acesso não vai ao backend');
  assert.equal(chamadas[0].opcoes.headers['X-API-Key'], ENV.BACKEND_API_KEY);
  assert.equal(chamadas[0].opcoes.method, 'POST');
});

test('o destino é fixo na configuração: URL, host e cabeçalhos do navegador são ignorados', async () => {
  const { handler, chamadas } = handlerCom(json(ANALISE));
  await handler(
    post(
      { ...ENTRADA, backendUrl: 'https://evil.example', url: 'https://evil.example/x' },
      { Host: 'evil.example', 'X-Forwarded-Host': 'evil.example', 'X-Backend-Url': 'https://evil.example' },
    ),
  );
  assert.equal(chamadas[0].url, 'https://backend.exemplo.test/api/chamados');
});

test('não repete o POST nem segue redirecionamento', async () => {
  const { handler, chamadas } = handlerCom(json({}, 503));
  await handler(post(ENTRADA));
  assert.equal(chamadas.length, 1);
  assert.equal(chamadas[0].opcoes.redirect, 'manual');
});

test('aplica timeout à chamada ao backend', async () => {
  const { handler, chamadas } = handlerCom(json(ANALISE));
  await handler(post(ENTRADA));
  assert.ok(chamadas[0].opcoes.signal instanceof AbortSignal);
});

// --- falhas ---

test('timeout vira 504 com aviso de que a operação pode ter concluído, sem repetir', async () => {
  const { handler, chamadas } = handlerCom(
    (_url, opcoes) =>
      new Promise((_, rejeitar) => {
        opcoes.signal.addEventListener('abort', () => rejeitar(opcoes.signal.reason));
      }),
    { timeoutMs: 50 },
  );
  const res = await handler(post(ENTRADA));
  assert.equal(res.status, 504);
  const corpo = await corpoDe(res);
  assert.equal(corpo.erro, 'tempo_esgotado');
  assert.match(corpo.mensagem, /pode ter sido concluída/);
  assert.equal(chamadas.length, 1);
});

test('erro de rede vira 502 genérico', async () => {
  const { handler } = handlerCom(() => {
    throw new TypeError('fetch failed: ECONNREFUSED 10.0.0.5:443 segredo-interno');
  });
  const res = await handler(post(ENTRADA));
  assert.equal(res.status, 502);
  const texto = JSON.stringify(await corpoDe(res));
  assert.ok(!texto.includes('ECONNREFUSED') && !texto.includes('segredo-interno') && !texto.includes('10.0.0.5'));
});

test('erros do backend viram mensagens fixas e o corpo do backend nunca passa', async () => {
  const vazamentos = 'Exception at br.com.coti.servicedesk_ai StackTrace chave-do-backend-ficticia';
  const casos = [
    [401, 502],
    [403, 502],
    [500, 502],
    [502, 502],
    [503, 503],
    [429, 429],
    [400, 400],
    [404, 502],
  ];
  for (const [vindo, esperado] of casos) {
    const { handler } = handlerCom(json({ detail: vazamentos, requestId: 'x' }, vindo));
    const res = await handler(post(ENTRADA));
    assert.equal(res.status, esperado, `backend ${vindo}`);
    const texto = JSON.stringify(await corpoDe(res));
    assert.ok(!texto.includes('Exception') && !texto.includes('StackTrace') && !texto.includes('chave-do-backend'), `backend ${vindo}`);
  }
});

test('resposta de sucesso inesperada do backend vira 502', async () => {
  for (const corpo of [{ ok: true }, { ...ANALISE, notificacao: 'ENVIADO' }, { ...ANALISE, enviarEmail: 'sim' }, null]) {
    const { handler } = handlerCom(json(corpo));
    const res = await handler(post(ENTRADA));
    assert.equal(res.status, 502, JSON.stringify(corpo));
  }
});

test('resposta de sucesso devolve só os campos esperados e preserva 200 e 202', async () => {
  for (const status of [200, 202]) {
    const { handler } = handlerCom(json({ ...ANALISE, interno: 'nao-deve-passar', requestId: 'r' }, status));
    const res = await handler(post(ENTRADA));
    assert.equal(res.status, status);
    assert.deepEqual(await corpoDe(res), ANALISE);
  }
});

test('toda resposta traz Cache-Control: no-store', async () => {
  const casos = [
    () => handlerCom(json(ANALISE)).handler(post(ENTRADA)),
    () => handlerCom(json(ANALISE)).handler(post({ ...ENTRADA, codigoAcesso: 'x' })),
    () => handlerCom(json(ANALISE)).handler(post({ ...ENTRADA, nome: '' })),
    () => handlerCom(json(ANALISE)).handler(new Request('https://portal.exemplo.test/api/triagem')),
    () => handlerCom(json({}, 500)).handler(post(ENTRADA)),
  ];
  for (const caso of casos) {
    const res = await caso();
    assert.equal(res.headers.get('cache-control'), 'no-store');
  }
});

test('configuração incompleta devolve 500 genérico sem citar nomes de variáveis', async () => {
  for (const faltando of ['BACKEND_URL', 'BACKEND_API_KEY', 'DEMO_ACCESS_CODE']) {
    const env = { ...ENV, [faltando]: undefined };
    const handler = criarHandler({ env, fetchImpl: backendFalso(json(ANALISE)).impl });
    const res = await handler(post(ENTRADA));
    assert.equal(res.status, 500);
    assert.ok(!JSON.stringify(await corpoDe(res)).includes(faltando));
  }
});

test('BACKEND_URL só é aceita em HTTPS (ou localhost)', () => {
  assert.equal(urlDoBackend('https://api.exemplo.test'), 'https://api.exemplo.test/api/chamados');
  assert.equal(urlDoBackend('https://api.exemplo.test/qualquer/coisa?x=1'), 'https://api.exemplo.test/api/chamados');
  assert.equal(urlDoBackend('http://localhost:8080'), 'http://localhost:8080/api/chamados');
  assert.equal(urlDoBackend('http://api.exemplo.test'), null);
  assert.equal(urlDoBackend('ftp://api.exemplo.test'), null);
  assert.equal(urlDoBackend('não é url'), null);
  assert.equal(urlDoBackend(undefined), null);
});

test('comparação do código de acesso', () => {
  assert.equal(codigoConfere('abc', 'abc'), true);
  assert.equal(codigoConfere('abc', 'abd'), false);
  assert.equal(codigoConfere('abc', 'abcd'), false);
  assert.equal(codigoConfere(undefined, 'abc'), false);
  assert.equal(codigoConfere('abc', ''), false);
  assert.equal(codigoConfere('', ''), false);
});

test('validarEntrada descarta campos extras', () => {
  const r = validarEntrada({ ...ENTRADA, extra: 1, destinatario: 'x@y.z' });
  assert.equal(r.ok, true);
  assert.deepEqual(Object.keys(r.valor).sort(), ['autorizaEmail', 'descricao', 'nome']);
});

// --- o portal publicado não contém segredo ---

const RAIZ = fileURLToPath(new URL('..', import.meta.url));
const SEM_SEGREDO = ['BACKEND_API_KEY', 'DEMO_ACCESS_CODE', 'X-API-Key', 'process.env'];

function arquivosPublicos(dir) {
  return readdirSync(dir).flatMap((nome) => {
    const caminho = join(dir, nome);
    if (nome === 'node_modules' || nome === 'test' || nome === 'api' || nome === 'lib' || nome.startsWith('.')) return [];
    return statSync(caminho).isDirectory() ? arquivosPublicos(caminho) : [caminho];
  });
}

test('HTML, CSS e JavaScript do navegador não contêm segredo nem variável de servidor', () => {
  const publicos = arquivosPublicos(RAIZ).filter((f) => /\.(html|css|js)$/.test(f));
  assert.ok(publicos.length >= 3, 'esperava index.html, style.css e app.js');
  for (const arquivo of publicos) {
    const texto = readFileSync(arquivo, 'utf8');
    for (const proibido of SEM_SEGREDO) {
      assert.ok(!texto.includes(proibido), `${arquivo} contém ${proibido}`);
    }
  }
});

test('o navegador chama só a rota relativa /api/triagem', () => {
  const app = readFileSync(join(RAIZ, 'app.js'), 'utf8');
  const urls = [...app.matchAll(/fetch\(\s*['"`]([^'"`]+)['"`]/g)].map((m) => m[1]);
  assert.deepEqual(urls, ['/api/triagem']);
  assert.ok(!/https?:\/\//.test(app), 'sem URL absoluta no JavaScript do navegador');
});

test('a página não afirma que o e-mail foi enviado ou entregue', () => {
  const app = readFileSync(join(RAIZ, 'app.js'), 'utf8');
  assert.match(app, /aceita pela fila/);
  assert.match(app, /não significa que o e-mail já foi enviado nem entregue/);
});

test('vercel.json fixa a duração da função acima do timeout do proxy', () => {
  const cfg = JSON.parse(readFileSync(join(RAIZ, 'vercel.json'), 'utf8'));
  assert.equal(cfg.functions['api/triagem.mjs'].maxDuration, 40);
  const csp = cfg.headers[0].headers.find((h) => h.key === 'Content-Security-Policy').value;
  assert.ok(!csp.includes("'unsafe-inline'") && !csp.includes("'unsafe-eval'"));
});
