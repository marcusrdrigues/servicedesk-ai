package br.com.coti.servicedesk_ai.aplicacao;

import br.com.coti.servicedesk_ai.dominio.AnaliseChamado;
import br.com.coti.servicedesk_ai.dominio.AnaliseIndisponivelException;
import br.com.coti.servicedesk_ai.dominio.Chamado;
import br.com.coti.servicedesk_ai.dominio.FalhaProvedorModeloException;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Cadeia de dois elos: o reserva só é chamado quando o principal falha (chamada ou validação). */
public final class AnalisadorComFallback implements AnalisadorChamadoPort {

    private static final Logger LOG = LoggerFactory.getLogger(AnalisadorComFallback.class);

    private final AnalisadorChamadoPort principal;
    private final AnalisadorChamadoPort reserva;
    private final Clock relogio;

    public AnalisadorComFallback(AnalisadorChamadoPort principal, AnalisadorChamadoPort reserva, Clock relogio) {
        this.principal = principal;
        this.reserva = reserva;
        this.relogio = relogio;
    }

    @Override
    public AnaliseChamado analisar(Chamado chamado, Instant limite) {
        try {
            return principal.analisar(chamado, limite);
        } catch (FalhaProvedorModeloException falhaPrincipal) {
            LOG.warn("provedor={} resultado=falha motivo={} proximo={}",
                    principal.provedor(), falhaPrincipal.getMessage(), reserva.provedor());
            if (!relogio.instant().isBefore(limite)) {
                throw new AnaliseIndisponivelException("tempo total da análise esgotado");
            }
        }
        try {
            return reserva.analisar(chamado, limite);
        } catch (FalhaProvedorModeloException falhaReserva) {
            LOG.error("provedor={} resultado=falha motivo={}", reserva.provedor(), falhaReserva.getMessage());
            throw new AnaliseIndisponivelException("nenhum provedor de modelo disponível");
        }
    }

    @Override
    public String provedor() {
        return principal.provedor() + "+" + reserva.provedor();
    }
}
