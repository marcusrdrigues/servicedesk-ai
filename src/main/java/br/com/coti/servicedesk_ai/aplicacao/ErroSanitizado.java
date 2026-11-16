package br.com.coti.servicedesk_ai.aplicacao;

/** Reduz uma falha ao que é seguro gravar: nomes de tipo, nunca a mensagem, que pode conter conteúdo do chamado. */
public final class ErroSanitizado {

    static final int MAXIMO = 200;

    private ErroSanitizado() {}

    public static String de(Throwable erro) {
        StringBuilder texto = new StringBuilder(erro.getClass().getSimpleName());
        Throwable causa = erro.getCause();
        for (int nivel = 0; causa != null && nivel < 3; nivel++, causa = causa.getCause()) {
            texto.append('<').append(causa.getClass().getSimpleName());
        }
        return texto.length() <= MAXIMO ? texto.toString() : texto.substring(0, MAXIMO);
    }
}
