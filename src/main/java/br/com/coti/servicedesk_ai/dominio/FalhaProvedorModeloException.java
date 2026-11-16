package br.com.coti.servicedesk_ai.dominio;

/** Falha de um provedor: chamada, conversão ou validação. Nunca carrega o texto do chamado nem da resposta. */
public final class FalhaProvedorModeloException extends RuntimeException {

    private final boolean transitoria;

    public FalhaProvedorModeloException(String mensagem, boolean transitoria, Throwable causa) {
        super(mensagem, causa);
        this.transitoria = transitoria;
    }

    public boolean transitoria() {
        return transitoria;
    }
}
