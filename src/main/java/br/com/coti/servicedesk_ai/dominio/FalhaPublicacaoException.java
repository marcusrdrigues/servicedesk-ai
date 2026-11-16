package br.com.coti.servicedesk_ai.dominio;

public final class FalhaPublicacaoException extends RuntimeException {
    public FalhaPublicacaoException(String mensagem) {
        super(mensagem);
    }

    public FalhaPublicacaoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
