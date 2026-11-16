package br.com.coti.servicedesk_ai.aplicacao;

import br.com.coti.servicedesk_ai.dominio.AnaliseChamado;
import br.com.coti.servicedesk_ai.dominio.SaidaModeloInvalidaException;

/** A saída do modelo é entrada não confiável: só passa o que cumpre o contrato. */
public final class ValidadorAnalise {

    private final int maxResumo;
    private final int maxOrientacao;

    public ValidadorAnalise(int maxResumo, int maxOrientacao) {
        this.maxResumo = maxResumo;
        this.maxOrientacao = maxOrientacao;
    }

    public AnaliseChamado validar(AnaliseChamado analise) {
        if (analise == null) {
            throw new SaidaModeloInvalidaException("análise ausente");
        }
        if (analise.categoria() == null) {
            throw new SaidaModeloInvalidaException("categoria ausente");
        }
        if (analise.prioridade() == null) {
            throw new SaidaModeloInvalidaException("prioridade ausente");
        }
        exigirTexto("resumo", analise.resumo(), maxResumo);
        exigirTexto("orientacao", analise.orientacao(), maxOrientacao);
        return analise;
    }

    private static void exigirTexto(String campo, String valor, int maximo) {
        if (valor == null || valor.isBlank()) {
            throw new SaidaModeloInvalidaException(campo + " ausente ou vazio");
        }
        if (valor.length() > maximo) {
            throw new SaidaModeloInvalidaException(campo + " acima do tamanho máximo");
        }
    }
}
