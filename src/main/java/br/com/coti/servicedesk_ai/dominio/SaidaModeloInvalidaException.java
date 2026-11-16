package br.com.coti.servicedesk_ai.dominio;

public final class SaidaModeloInvalidaException extends RuntimeException {
    public SaidaModeloInvalidaException(String mensagem) {
        super(mensagem);
    }
}
