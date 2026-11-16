package br.com.coti.servicedesk_ai.dominio;

/** O provedor que respondeu (openai ou gemini) é metadado de auditoria; é nulo quando não se aplica. */
public record AnaliseChamado(
        Categoria categoria,
        Prioridade prioridade,
        String resumo,
        String orientacao,
        boolean enviarEmail,
        String provedor) {

    public AnaliseChamado(
            Categoria categoria, Prioridade prioridade, String resumo, String orientacao, boolean enviarEmail) {
        this(categoria, prioridade, resumo, orientacao, enviarEmail, null);
    }
}
