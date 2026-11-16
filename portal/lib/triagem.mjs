// Proxy da triagem. Sem dependências: fetch, crypto e URL nativos.
//
// Regras (spec portal-triagem):
//  - só POST; código de acesso comparado em tempo constante;
//  - valida nome, descrição e autorização; encaminha SOMENTE esses três campos;
//  - o destino é a BACKEND_URL da configuração do servidor, nunca algo vindo do navegador;
//  - timeout limitado e NENHUMA repetição: repetir geraria outra execução e outro e-mail;
//  - nunca devolve corpo de erro do backend, stack trace nem segredo.
import { createHash, timingSafeEqual } from 'node:crypto';

export const LIMITES = Object.freeze({
  nomeMax: 100,
  descricaoMin: 20,
  descricaoMax: 4000,
  corpoMaxBytes: 16 * 1024,
});

const CABECALHOS = Object.freeze({
  'Content-Type': 'application/json; charset=utf-8',
  'Cache-Control': 'no-store',
  'X-Content-Type-Options': 'nosniff',
});

const CAMPOS_RESPOSTA = ['categoria', 'prioridade', 'resumo', 'orientacao', 'enviarEmail', 'notificacao'];
const NOTIFICACOES = new Set(['ENFILEIRADO', 'NAO_SOLICITADO']);

function resposta(status, corpo, extras = {}) {
  return new Response(JSON.stringify(corpo), { status, headers: { ...CABECALHOS, ...extras } });
}

function erro(status, codigo, mensagem, extras) {
  return resposta(status, { erro: codigo, mensagem }, extras);
}

function hash(valor) {
  return createHash('sha256').update(String(valor), 'utf8').digest();
}

/** Comparação em tempo constante sobre o hash dos dois valores: nem o tamanho do código vaza. */
export function codigoConfere(informado, esperado) {
  if (typeof informado !== 'string' || typeof esperado !== 'string' || esperado.length === 0) {
    return false;
  }
  return timingSafeEqual(hash(informado), hash(esperado));
}

/** Valida e devolve só os três campos permitidos; qualquer outro campo é descartado. */
export function validarEntrada(corpo) {
  if (corpo === null || typeof corpo !== 'object' || Array.isArray(corpo)) {
    return { ok: false };
  }
  const { nome, descricao, autorizaEmail } = corpo;
  if (typeof nome !== 'string' || nome.trim().length === 0 || nome.length > LIMITES.nomeMax) {
    return { ok: false };
  }
  if (
    typeof descricao !== 'string' ||
    descricao.length < LIMITES.descricaoMin ||
    descricao.length > LIMITES.descricaoMax ||
    descricao.trim().length === 0
  ) {
    return { ok: false };
  }
  if (typeof autorizaEmail !== 'boolean') {
    return { ok: false };
  }
  return { ok: true, valor: { nome, descricao, autorizaEmail } };
}

/** A URL do backend vem só da configuração. HTTPS obrigatório, salvo localhost (desenvolvimento). */
export function urlDoBackend(base) {
  let url;
  try {
    url = new URL(base);
  } catch {
    return null;
  }
  const local = url.hostname === 'localhost' || url.hostname === '127.0.0.1';
  if (url.protocol !== 'https:' && !(local && url.protocol === 'http:')) {
    return null;
  }
  return new URL('/api/chamados', url.origin).toString(); // caminho fixo, sem aproveitar path da variável
}

function respostaPermitida(corpo) {
  if (corpo === null || typeof corpo !== 'object') return null;
  const saida = {};
  for (const campo of CAMPOS_RESPOSTA) {
    if (!(campo in corpo)) return null;
    saida[campo] = corpo[campo];
  }
  const tiposOk =
    typeof saida.categoria === 'string' &&
    typeof saida.prioridade === 'string' &&
    typeof saida.resumo === 'string' &&
    typeof saida.orientacao === 'string' &&
    typeof saida.enviarEmail === 'boolean' &&
    NOTIFICACOES.has(saida.notificacao);
  return tiposOk ? saida : null;
}

async function lerCorpoLimitado(request) {
  const declarado = Number(request.headers.get('content-length') ?? '0');
  if (Number.isFinite(declarado) && declarado > LIMITES.corpoMaxBytes) return { grande: true };
  const texto = await request.text();
  if (Buffer.byteLength(texto, 'utf8') > LIMITES.corpoMaxBytes) return { grande: true };
  return { texto };
}

export function criarHandler({ env = process.env, fetchImpl = fetch, timeoutMs = 35_000 } = {}) {
  return async function handler(request) {
    if (request.method !== 'POST') {
      return erro(405, 'metodo_nao_permitido', 'Use POST.', { Allow: 'POST' });
    }

    const destino = urlDoBackend(env.BACKEND_URL);
    if (!destino || !env.BACKEND_API_KEY || !env.DEMO_ACCESS_CODE) {
      console.error('triagem: configuracao incompleta');
      return erro(500, 'servico_nao_configurado', 'O serviço não está configurado.');
    }

    const lido = await lerCorpoLimitado(request);
    if (lido.grande) {
      return erro(413, 'corpo_grande_demais', 'O corpo da requisição é grande demais.');
    }
    let corpo;
    try {
      corpo = JSON.parse(lido.texto);
    } catch {
      return erro(400, 'entrada_invalida', 'O corpo da requisição está ausente ou malformado.');
    }

    if (!codigoConfere(corpo?.codigoAcesso, env.DEMO_ACCESS_CODE)) {
      return erro(401, 'nao_autorizado', 'Código de acesso ausente ou inválido.');
    }

    const entrada = validarEntrada(corpo);
    if (!entrada.ok) {
      return erro(
        400,
        'entrada_invalida',
        `Informe o nome (até ${LIMITES.nomeMax} caracteres), a descrição (${LIMITES.descricaoMin} a ${LIMITES.descricaoMax} caracteres) e a autorização de e-mail.`,
      );
    }

    let retorno;
    try {
      retorno = await fetchImpl(destino, {
        method: 'POST',
        redirect: 'manual',
        headers: { 'Content-Type': 'application/json', 'X-API-Key': env.BACKEND_API_KEY },
        body: JSON.stringify(entrada.valor),
        signal: AbortSignal.timeout(timeoutMs),
      });
    } catch (e) {
      if (e?.name === 'TimeoutError' || e?.name === 'AbortError') {
        console.error('triagem: timeout');
        return erro(
          504,
          'tempo_esgotado',
          'O servidor demorou para responder. A operação pode ter sido concluída no servidor; não envie de novo sem conferir.',
        );
      }
      console.error('triagem: falha de rede');
      return erro(502, 'backend_indisponivel', 'Não foi possível falar com o servidor agora.');
    }

    if (retorno.status === 200 || retorno.status === 202) {
      let json;
      try {
        json = await retorno.json();
      } catch {
        json = null;
      }
      const permitido = respostaPermitida(json);
      if (!permitido) {
        console.error('triagem: resposta inesperada do backend');
        return erro(502, 'resposta_invalida', 'O servidor devolveu uma resposta inesperada.');
      }
      return resposta(retorno.status, permitido);
    }

    // O corpo de erro do backend nunca é repassado.
    console.error(`triagem: backend respondeu ${retorno.status}`);
    if (retorno.status === 400) {
      return erro(400, 'entrada_invalida', 'O servidor recusou os dados informados.');
    }
    if (retorno.status === 429) {
      return erro(429, 'muitas_requisicoes', 'Muitas requisições. Tente de novo mais tarde.');
    }
    if (retorno.status === 503) {
      return erro(503, 'servico_indisponivel', 'O serviço está indisponível agora. Tente de novo em instantes.');
    }
    return erro(502, 'backend_indisponivel', 'O servidor não conseguiu atender agora.');
  };
}
