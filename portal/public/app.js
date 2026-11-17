// O navegador só chama a rota relativa /api/triagem. Nenhum segredo existe aqui: o código de acesso é digitado
// pelo usuário e a chave do backend fica apenas na função do servidor.
const formulario = document.getElementById('formulario');
const botao = document.getElementById('enviar');
const resultado = document.getElementById('resultado');

const NOTIFICACAO = {
  ENFILEIRADO:
    'A notificação foi aceita pela fila. Isso não significa que o e-mail já foi enviado nem entregue.',
  NAO_SOLICITADO: 'Nenhuma notificação foi solicitada para este chamado.',
};

function limpar() {
  resultado.replaceChildren();
  resultado.classList.remove('erro');
}

function mostrarErro(mensagem) {
  limpar();
  const p = document.createElement('p');
  p.textContent = mensagem;
  resultado.append(p);
  resultado.classList.add('erro');
  resultado.hidden = false;
}

function linha(lista, titulo, valor) {
  const dt = document.createElement('dt');
  dt.textContent = titulo;
  const dd = document.createElement('dd');
  dd.textContent = valor; // textContent: nunca interpreta HTML vindo do servidor
  lista.append(dt, dd);
}

function mostrarAnalise(d) {
  limpar();
  const titulo = document.createElement('h2');
  titulo.textContent = 'Resultado da análise';
  const lista = document.createElement('dl');
  linha(lista, 'Categoria', d.categoria);
  linha(lista, 'Prioridade', d.prioridade);
  linha(lista, 'Resumo', d.resumo);
  linha(lista, 'Orientação', d.orientacao);
  linha(lista, 'Notificação', NOTIFICACAO[d.notificacao] ?? d.notificacao);
  resultado.append(titulo, lista);
  resultado.hidden = false;
}

formulario.addEventListener('submit', async (evento) => {
  evento.preventDefault();
  const corpo = {
    nome: document.getElementById('nome').value,
    descricao: document.getElementById('descricao').value,
    autorizaEmail: document.getElementById('autorizaEmail').checked,
    codigoAcesso: document.getElementById('codigoAcesso').value,
  };

  botao.disabled = true;
  botao.textContent = 'Analisando…';
  try {
    // Sem repetição automática: repetir geraria outra execução e outro e-mail.
    const resposta = await fetch('/api/triagem', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(corpo),
    });
    const dados = await resposta.json().catch(() => null);
    if (resposta.ok && dados) {
      mostrarAnalise(dados);
    } else {
      mostrarErro(dados?.mensagem ?? 'Não foi possível concluir a análise.');
    }
  } catch {
    mostrarErro('Falha de conexão. A operação pode ter chegado ao servidor; confira antes de enviar de novo.');
  } finally {
    botao.disabled = false;
    botao.textContent = 'Analisar chamado';
  }
});
