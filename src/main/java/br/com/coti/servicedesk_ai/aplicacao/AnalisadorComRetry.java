package br.com.coti.servicedesk_ai.aplicacao;

import br.com.coti.servicedesk_ai.dominio.AnaliseChamado;
import br.com.coti.servicedesk_ai.dominio.Chamado;
import br.com.coti.servicedesk_ai.dominio.FalhaProvedorModeloException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

/**
 * Retry em um único ponto da aplicação (o SDK do provedor fica com max-retries 0).
 * Só repete erro transitório, com backoff exponencial e jitter, e nunca passa do limite de tempo total.
 */
public final class AnalisadorComRetry implements AnalisadorChamadoPort {

    private final AnalisadorChamadoPort delegado;
    private final int tentativasExtras;
    private final Duration backoffInicial;
    private final Clock relogio;
    private final RandomGenerator aleatorio;
    private final Consumer<Duration> espera;

    public AnalisadorComRetry(
            AnalisadorChamadoPort delegado,
            int tentativasExtras,
            Duration backoffInicial,
            Clock relogio,
            RandomGenerator aleatorio,
            Consumer<Duration> espera) {
        this.delegado = delegado;
        this.tentativasExtras = tentativasExtras;
        this.backoffInicial = backoffInicial;
        this.relogio = relogio;
        this.aleatorio = aleatorio;
        this.espera = espera;
    }

    @Override
    public AnaliseChamado analisar(Chamado chamado, Instant limite) {
        int tentativa = 0;
        while (true) {
            try {
                return delegado.analisar(chamado, limite);
            } catch (FalhaProvedorModeloException falha) {
                Duration pausa = pausaDaTentativa(tentativa);
                boolean cabeNoTempo = relogio.instant().plus(pausa).isBefore(limite);
                if (!falha.transitoria() || tentativa >= tentativasExtras || !cabeNoTempo) {
                    throw falha;
                }
                espera.accept(pausa);
                tentativa++;
            }
        }
    }

    private Duration pausaDaTentativa(int tentativa) {
        long base = backoffInicial.toMillis() << tentativa;
        long jitter = base == 0 ? 0 : (long) (aleatorio.nextDouble() * base * 0.5);
        return Duration.ofMillis(base + jitter);
    }

    @Override
    public String provedor() {
        return delegado.provedor();
    }
}
