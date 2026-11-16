package br.com.coti.servicedesk_ai.dominio;

/** Falha ao entregar o e-mail ao provedor. A mensagem não pode conter o conteúdo do chamado. */
public final class FalhaEnvioEmailException extends RuntimeException {
    public FalhaEnvioEmailException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
