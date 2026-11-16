package br.com.coti.servicedesk_ai.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.coti.servicedesk_ai.dominio.AnaliseChamado;
import br.com.coti.servicedesk_ai.dominio.Categoria;
import br.com.coti.servicedesk_ai.dominio.Prioridade;
import br.com.coti.servicedesk_ai.dominio.SaidaModeloInvalidaException;
import org.junit.jupiter.api.Test;

class ValidadorAnaliseTest {

    private final ValidadorAnalise validador = new ValidadorAnalise(50, 100);

    private static AnaliseChamado analise(Categoria c, Prioridade p, String resumo, String orientacao) {
        return new AnaliseChamado(c, p, resumo, orientacao, true);
    }

    @Test
    void aceitaAnaliseCompleta() {
        var analise = analise(Categoria.REDE, Prioridade.ALTA, "VPN fora", "Reiniciar o concentrador");

        assertThat(validador.validar(analise)).isSameAs(analise);
    }

    @Test
    void rejeitaAnaliseNula() {
        assertThatThrownBy(() -> validador.validar(null)).isInstanceOf(SaidaModeloInvalidaException.class);
    }

    @Test
    void rejeitaCategoriaAusente() {
        assertThatThrownBy(() -> validador.validar(analise(null, Prioridade.BAIXA, "r", "o")))
                .isInstanceOf(SaidaModeloInvalidaException.class)
                .hasMessageContaining("categoria");
    }

    @Test
    void rejeitaPrioridadeAusente() {
        assertThatThrownBy(() -> validador.validar(analise(Categoria.OUTROS, null, "r", "o")))
                .isInstanceOf(SaidaModeloInvalidaException.class)
                .hasMessageContaining("prioridade");
    }

    @Test
    void rejeitaOrientacaoAusente() {
        assertThatThrownBy(() -> validador.validar(analise(Categoria.OUTROS, Prioridade.BAIXA, "r", null)))
                .isInstanceOf(SaidaModeloInvalidaException.class)
                .hasMessageContaining("orientacao");
    }

    @Test
    void rejeitaResumoEmBranco() {
        assertThatThrownBy(() -> validador.validar(analise(Categoria.OUTROS, Prioridade.BAIXA, "   ", "o")))
                .isInstanceOf(SaidaModeloInvalidaException.class)
                .hasMessageContaining("resumo");
    }

    @Test
    void rejeitaTextoAcimaDoTeto() {
        assertThatThrownBy(() -> validador.validar(analise(Categoria.OUTROS, Prioridade.BAIXA, "x".repeat(51), "o")))
                .isInstanceOf(SaidaModeloInvalidaException.class)
                .hasMessageContaining("resumo");
        assertThatThrownBy(() -> validador.validar(analise(Categoria.OUTROS, Prioridade.BAIXA, "r", "x".repeat(101))))
                .isInstanceOf(SaidaModeloInvalidaException.class)
                .hasMessageContaining("orientacao");
    }

    @Test
    void aceitaTextoExatamenteNoTeto() {
        var analise = analise(Categoria.OUTROS, Prioridade.BAIXA, "x".repeat(50), "y".repeat(100));

        assertThat(validador.validar(analise)).isSameAs(analise);
    }
}
