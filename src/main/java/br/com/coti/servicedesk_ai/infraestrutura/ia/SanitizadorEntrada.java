package br.com.coti.servicedesk_ai.infraestrutura.ia;

/** Neutraliza o que imita delimitadores do prompt e remove caracteres de controle da entrada do usuário. */
final class SanitizadorEntrada {

    private SanitizadorEntrada() {}

    static String sanitizar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").replace('<', '‹').replace('>', '›');
    }
}
