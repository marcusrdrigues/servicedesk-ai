package br.com.coti.servicedesk_ai.dominio;

/** Nenhum provedor de modelo entregou uma análise válida. */
public final class AnaliseIndisponivelException extends RuntimeException {
    public AnaliseIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
