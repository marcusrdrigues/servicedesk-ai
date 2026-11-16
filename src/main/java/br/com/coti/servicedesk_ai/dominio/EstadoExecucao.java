package br.com.coti.servicedesk_ai.dominio;

/** Estados da execução na auditoria. A ordem só avança: um estado de ordem menor nunca sobrescreve um maior. */
public enum EstadoExecucao {
    ANALISADA(10),
    PUBLICACAO_PENDENTE(20),
    PUBLICACAO_CONFIRMADA(30),
    PUBLICACAO_INCERTA(30),
    PUBLICACAO_FALHOU(30);

    private final int ordem;

    EstadoExecucao(int ordem) {
        this.ordem = ordem;
    }

    public int ordem() {
        return ordem;
    }
}
