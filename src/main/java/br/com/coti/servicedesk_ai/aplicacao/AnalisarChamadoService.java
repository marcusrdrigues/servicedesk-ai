package br.com.coti.servicedesk_ai.aplicacao;

import br.com.coti.servicedesk_ai.dominio.AnaliseChamado;
import br.com.coti.servicedesk_ai.dominio.Chamado;
import br.com.coti.servicedesk_ai.dominio.EmailSolicitado;
import br.com.coti.servicedesk_ai.dominio.EstadoExecucao;
import br.com.coti.servicedesk_ai.dominio.EstadoNotificacao;
import br.com.coti.servicedesk_ai.dominio.FalhaPublicacaoException;
import br.com.coti.servicedesk_ai.dominio.StatusNotificacao;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

public final class AnalisarChamadoService {

    private final AnalisadorChamadoPort analisador;
    private final PublicadorNotificacaoPort publicador;
    private final MontadorEmail montadorEmail;
    private final AuditoriaPort auditoria;
    private final Duration orcamentoTotal;
    private final Clock relogio;

    public AnalisarChamadoService(
            AnalisadorChamadoPort analisador,
            PublicadorNotificacaoPort publicador,
            MontadorEmail montadorEmail,
            AuditoriaPort auditoria,
            Duration orcamentoTotal,
            Clock relogio) {
        this.analisador = analisador;
        this.publicador = publicador;
        this.montadorEmail = montadorEmail;
        this.auditoria = auditoria;
        this.orcamentoTotal = orcamentoTotal;
        this.relogio = relogio;
    }

    public ResultadoChamado analisar(Chamado chamado) {
        // A publicação vem depois da análise com fallback e fora dele.
        AnaliseChamado analise = analisador.analisar(chamado, relogio.instant().plus(orcamentoTotal));
        UUID execucaoId = UUID.randomUUID();
        auditoria.registrarExecucao(execucaoId, analise);

        // A autorização do solicitante é a trava; a opinião do modelo sozinha nunca publica.
        boolean publicar = chamado.autorizaEmail() && analise.enviarEmail();
        if (!publicar) {
            return new ResultadoChamado(analise, StatusNotificacao.NAO_SOLICITADO);
        }

        UUID eventoId = UUID.randomUUID();
        EmailSolicitado evento = montadorEmail.montar(eventoId, chamado.nome(), analise);
        auditoria.registrarNotificacaoPendente(execucaoId, eventoId);
        try {
            publicador.publicar(evento);
        } catch (FalhaPublicacaoException falha) {
            EstadoNotificacao estado =
                    falha.incerta() ? EstadoNotificacao.PUBLICACAO_INCERTA : EstadoNotificacao.PUBLICACAO_FALHOU;
            EstadoExecucao estadoExecucao =
                    falha.incerta() ? EstadoExecucao.PUBLICACAO_INCERTA : EstadoExecucao.PUBLICACAO_FALHOU;
            auditoria.atualizarPublicacao(execucaoId, eventoId, estadoExecucao, estado);
            throw falha;
        }
        auditoria.atualizarPublicacao(
                execucaoId, eventoId, EstadoExecucao.PUBLICACAO_CONFIRMADA, EstadoNotificacao.PUBLICACAO_CONFIRMADA);
        return new ResultadoChamado(analise, StatusNotificacao.ENFILEIRADO);
    }
}
