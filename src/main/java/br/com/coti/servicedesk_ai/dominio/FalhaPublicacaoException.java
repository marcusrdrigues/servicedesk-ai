package br.com.coti.servicedesk_ai.dominio;

public class FalhaPublicacaoException extends RuntimeException {

    private final boolean incerta;

    public FalhaPublicacaoException(String mensagem) {
        super(mensagem);
        this.incerta = false;
    }

    public FalhaPublicacaoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.incerta = false;
    }

    private FalhaPublicacaoException(String mensagem, Throwable causa, boolean incerta) {
        super(mensagem, causa);
        this.incerta = incerta;
    }

    /** O broker pode ter aceitado a mensagem: a confirmação não chegou no prazo. */
    public static FalhaPublicacaoException incerta(String mensagem, Throwable causa) {
        return new FalhaPublicacaoException(mensagem, causa, true);
    }

    public boolean incerta() {
        return incerta;
    }
}
