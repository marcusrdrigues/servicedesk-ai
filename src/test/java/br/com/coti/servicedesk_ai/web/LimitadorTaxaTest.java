package br.com.coti.servicedesk_ai.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LimitadorTaxaTest {

    /** Relógio que o teste avança à mão. */
    private static final class RelogioManual extends Clock {
        private Instant agora = Instant.parse("2026-01-01T00:00:10Z");

        void avancarSegundos(long s) {
            agora = agora.plusSeconds(s);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    @Test
    void bloqueiaDepoisDoLimiteNaMesmaJanela() {
        var limitador = new LimitadorTaxa(2, new RelogioManual());

        assertThat(limitador.permitir("1.1.1.1")).isTrue();
        assertThat(limitador.permitir("1.1.1.1")).isTrue();
        assertThat(limitador.permitir("1.1.1.1")).isFalse();
    }

    @Test
    void chamadoresDiferentesTemContagensIndependentes() {
        var limitador = new LimitadorTaxa(1, new RelogioManual());

        assertThat(limitador.permitir("1.1.1.1")).isTrue();
        assertThat(limitador.permitir("2.2.2.2")).isTrue();
        assertThat(limitador.permitir("1.1.1.1")).isFalse();
    }

    @Test
    void novaJanelaLiberaDeNovo() {
        var relogio = new RelogioManual();
        var limitador = new LimitadorTaxa(1, relogio);

        assertThat(limitador.permitir("1.1.1.1")).isTrue();
        assertThat(limitador.permitir("1.1.1.1")).isFalse();
        relogio.avancarSegundos(60);
        assertThat(limitador.permitir("1.1.1.1")).isTrue();
    }
}
