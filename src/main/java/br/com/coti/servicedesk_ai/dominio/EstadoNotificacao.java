package br.com.coti.servicedesk_ai.dominio;

/**
 * Estados da notificação na auditoria. ACEITO_PELO_PROVEDOR quer dizer que a API de e-mail aceitou a
 * solicitação. Não existe ENTREGUE: aceite não é entrega ao destinatário.
 */
public enum EstadoNotificacao {
    PUBLICACAO_PENDENTE(10),
    PUBLICACAO_CONFIRMADA(20),
    PUBLICACAO_INCERTA(20),
    PUBLICACAO_FALHOU(20),
    FALHA_ENVIO(30),
    ACEITO_PELO_PROVEDOR(40);

    private final int ordem;

    EstadoNotificacao(int ordem) {
        this.ordem = ordem;
    }

    public int ordem() {
        return ordem;
    }
}
