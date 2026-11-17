// Adaptador da Netlify para o proxy da triagem: POST /api/triagem. A lógica é a mesma da Vercel (../../lib/triagem.mjs).
// Formato Web padrão das Netlify Functions: export default (Request, Context) => Response, com o caminho em config.path.
import { criarHandler } from '../../lib/triagem.mjs';

// O limite de execução síncrona das funções da Netlify NÃO foi confirmado na documentação consultada. O padrão aqui
// é o mesmo da Vercel (35 s); reduza com PROXY_TIMEOUT_MS se a plataforma cortar a função antes disso.
const configurado = Number(process.env.PROXY_TIMEOUT_MS);
const timeoutMs = Number.isInteger(configurado) && configurado >= 1_000 && configurado <= 60_000 ? configurado : 35_000;

const handler = criarHandler({ timeoutMs });

export default async (request) => handler(request);

export const config = { path: '/api/triagem' };
