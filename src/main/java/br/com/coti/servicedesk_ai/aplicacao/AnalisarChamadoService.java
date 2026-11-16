package br.com.coti.servicedesk_ai.aplicacao;

import br.com.coti.servicedesk_ai.dominio.AnaliseChamado;
import br.com.coti.servicedesk_ai.dominio.Chamado;
import br.com.coti.servicedesk_ai.dominio.StatusNotificacao;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

public final class AnalisarChamadoService {

    private final AnalisadorChamadoPort analisador;
    private final PublicadorNotificacaoPort publicador;
    private final MontadorEmail montadorEmail;
    private final Duration orcamentoTotal;
    private final Clock relogio;

    public AnalisarChamadoService(
            AnalisadorChamadoPort analisador,
            PublicadorNotificacaoPort publicador,
            MontadorEmail montadorEmail,
            Duration orcamentoTotal,
            Clock relogio) {
        this.analisador = analisador;
        this.publicador = publicador;
        this.montadorEmail = montadorEmail;
        this.orcamentoTotal = orcamentoTotal;
        this.relogio = relogio;
    }

    public ResultadoChamado analisar(Chamado chamado) {
        // A publicação vem depois da análise com fallback e fora dele.
        AnaliseChamado analise = analisador.analisar(chamado, relogio.instant().plus(orcamentoTotal));
        // A autorização do solicitante é a trava; a opinião do modelo sozinha nunca publica.
        boolean publicar = chamado.autorizaEmail() && analise.enviarEmail();
        if (!publicar) {
            return new ResultadoChamado(analise, StatusNotificacao.NAO_SOLICITADO);
        }
        publicador.publicar(montadorEmail.montar(UUID.randomUUID(), chamado.nome(), analise));
        return new ResultadoChamado(analise, StatusNotificacao.ENFILEIRADO);
    }
}
