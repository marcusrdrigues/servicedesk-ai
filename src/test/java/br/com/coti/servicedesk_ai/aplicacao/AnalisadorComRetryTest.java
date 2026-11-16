package br.com.coti.servicedesk_ai.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.coti.servicedesk_ai.dominio.AnaliseChamado;
import br.com.coti.servicedesk_ai.dominio.Categoria;
import br.com.coti.servicedesk_ai.dominio.Chamado;
import br.com.coti.servicedesk_ai.dominio.FalhaProvedorModeloException;
import br.com.coti.servicedesk_ai.dominio.Prioridade;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnalisadorComRetryTest {

    private static final Instant AGORA = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant LIMITE = AGORA.plusSeconds(30);
    private static final Chamado CHAMADO = new Chamado("Ana", "Descrição com mais de vinte caracteres", true);
    private static final AnaliseChamado ANALISE = new AnaliseChamado(Categoria.REDE, Prioridade.ALTA, "r", "o", true);

    private AnalisadorChamadoPort delegado;
    private final List<Duration> esperas = new ArrayList<>();

    @BeforeEach
    void preparar() {
        delegado = mock(AnalisadorChamadoPort.class);
        esperas.clear();
    }

    private AnalisadorComRetry com(int tentativasExtras, Instant agora) {
        RandomGenerator semJitter = new java.util.Random() {
            @Override
            public double nextDouble() {
                return 0;
            }
        };
        return new AnalisadorComRetry(
                delegado,
                tentativasExtras,
                Duration.ofMillis(500),
                Clock.fixed(agora, ZoneOffset.UTC),
                semJitter,
                esperas::add);
    }

    private static FalhaProvedorModeloException transitoria() {
        return new FalhaProvedorModeloException("429", true, null);
    }

    @Test
    void repeteUmaVezEmErroTransitorioEUsaORetorno() {
        when(delegado.analisar(CHAMADO, LIMITE)).thenThrow(transitoria()).thenReturn(ANALISE);

        assertThat(com(1, AGORA).analisar(CHAMADO, LIMITE)).isSameAs(ANALISE);

        verify(delegado, times(2)).analisar(CHAMADO, LIMITE);
        assertThat(esperas).containsExactly(Duration.ofMillis(500));
    }

    @Test
    void naoRepeteErroNaoTransitorio() {
        when(delegado.analisar(CHAMADO, LIMITE)).thenThrow(new FalhaProvedorModeloException("401", false, null));

        assertThatThrownBy(() -> com(3, AGORA).analisar(CHAMADO, LIMITE))
                .isInstanceOf(FalhaProvedorModeloException.class);

        verify(delegado, times(1)).analisar(any(), any());
        assertThat(esperas).isEmpty();
    }

    @Test
    void respeitaOMaximoDeTentativasExtras() {
        when(delegado.analisar(CHAMADO, LIMITE)).thenThrow(transitoria());

        assertThatThrownBy(() -> com(2, AGORA).analisar(CHAMADO, LIMITE))
                .isInstanceOf(FalhaProvedorModeloException.class);

        verify(delegado, times(3)).analisar(any(), any());
        assertThat(esperas).containsExactly(Duration.ofMillis(500), Duration.ofMillis(1000));
    }

    @Test
    void semTentativasExtrasNaoRepete() {
        when(delegado.analisar(CHAMADO, LIMITE)).thenThrow(transitoria());

        assertThatThrownBy(() -> com(0, AGORA).analisar(CHAMADO, LIMITE))
                .isInstanceOf(FalhaProvedorModeloException.class);

        verify(delegado, times(1)).analisar(any(), any());
    }

    @Test
    void naoRepeteQuandoAPausaNaoCabeNoTempoTotal() {
        when(delegado.analisar(CHAMADO, LIMITE)).thenThrow(transitoria());

        assertThatThrownBy(() -> com(3, LIMITE.minusMillis(100)).analisar(CHAMADO, LIMITE))
                .isInstanceOf(FalhaProvedorModeloException.class);

        verify(delegado, times(1)).analisar(any(), any());
    }
}
