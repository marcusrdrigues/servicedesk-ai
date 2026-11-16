// Função da Vercel: POST /api/triagem. A lógica fica em ../lib/triagem.mjs (testável sem a Vercel).
import { criarHandler } from '../lib/triagem.mjs';

// Orçamento de tempo: chamada ao modelo (12 s) < orçamento do backend (25 s) < este timeout (35 s) < maxDuration (40 s).
const handler = criarHandler({ timeoutMs: 35_000 });

export default {
  fetch(request) {
    return handler(request);
  },
};
