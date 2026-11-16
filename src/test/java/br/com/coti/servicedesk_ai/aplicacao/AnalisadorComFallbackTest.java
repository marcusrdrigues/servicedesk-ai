package br.com.coti.servicedesk_ai.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.coti.servicedesk_ai.dominio.AnaliseChamado;
import br.com.coti.servicedesk_ai.dominio.AnaliseIndisponivelException;
import br.com.coti.servicedesk_ai.dominio.Categoria;
import br.com.coti.servicedesk_ai.dominio.Chamado;
import br.com.coti.servicedesk_ai.dominio.FalhaProvedorModeloException;
import br.com.coti.servicedesk_ai.dominio.Prioridade;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnalisadorComFallbackTest {

    private static final Instant AGORA = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant LIMITE = AGORA.plusSeconds(30);
    private static final Chamado CHAMADO = new Chamado("Ana", "Descrição com mais de vinte caracteres", true);
    private static final AnaliseChamado DO_PRINCIPAL =
            new AnaliseChamado(Categoria.REDE, Prioridade.ALTA, "principal", "o", true);
    private static final AnaliseChamado DA_RESERVA =
            new AnaliseChamado(Categoria.SISTEMA, Prioridade.BAIXA, "reserva", "o", false);

    private AnalisadorChamadoPort principal;
    private AnalisadorChamadoPort reserva;

    @BeforeEach
    void preparar() {
        principal = mock(AnalisadorChamadoPort.class);
        reserva = mock(AnalisadorChamadoPort.class);
        when(principal.provedor()).thenReturn("openai");
        when(reserva.provedor()).thenReturn("gemini");
    }

    private AnalisadorComFallback com(Instant agora) {
        return new AnalisadorComFallback(principal, reserva, Clock.fixed(agora, ZoneOffset.UTC));
    }

    private static FalhaProvedorModeloException falha() {
        return new FalhaProvedorModeloException("falhou", false, null);
    }

    @Test
    void principalRespondeEReservaNaoEChamada() {
        when(principal.analisar(CHAMADO, LIMITE)).thenReturn(DO_PRINCIPAL);

        assertThat(com(AGORA).analisar(CHAMADO, LIMITE)).isSameAs(DO_PRINCIPAL);

        verify(reserva, never()).analisar(any(), any());
    }

    @Test
    void falhaDeChamadaOuDeValidacaoDoPrincipalChamaAReserva() {
        when(principal.analisar(CHAMADO, LIMITE)).thenThrow(falha());
        when(reserva.analisar(CHAMADO, LIMITE)).thenReturn(DA_RESERVA);

        assertThat(com(AGORA).analisar(CHAMADO, LIMITE)).isSameAs(DA_RESERVA);
    }

    @Test
    void ambosFalhandoLancaAnaliseIndisponivel() {
        when(principal.analisar(CHAMADO, LIMITE)).thenThrow(falha());
        when(reserva.analisar(CHAMADO, LIMITE)).thenThrow(falha());

        assertThatThrownBy(() -> com(AGORA).analisar(CHAMADO, LIMITE))
                .isInstanceOf(AnaliseIndisponivelException.class)
                .hasMessageNotContaining("Descrição");
    }

    @Test
    void tempoTotalEsgotadoNaoChamaAReserva() {
        when(principal.analisar(CHAMADO, LIMITE)).thenThrow(falha());

        assertThatThrownBy(() -> com(LIMITE.plusSeconds(1)).analisar(CHAMADO, LIMITE))
                .isInstanceOf(AnaliseIndisponivelException.class);

        verify(reserva, never()).analisar(any(), any());
    }
}
