package br.com.coti.servicedesk_ai.aplicacao;

import br.com.coti.servicedesk_ai.dominio.AnaliseChamado;
import br.com.coti.servicedesk_ai.dominio.EstadoExecucao;
import br.com.coti.servicedesk_ai.dominio.EstadoNotificacao;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Falha da auditoria não derruba o atendimento: vai para o log só com o nome da operação e o tipo da falha.
 * Consequência aceita: a trilha pode ter lacunas.
 */
public final class AuditoriaTolerante implements AuditoriaPort {

    private static final Logger LOG = LoggerFactory.getLogger(AuditoriaTolerante.class);

    private final AuditoriaPort delegada;

    public AuditoriaTolerante(AuditoriaPort delegada) {
        this.delegada = delegada;
    }

    @Override
    public void registrarExecucao(UUID execucaoId, AnaliseChamado analise) {
        tentar("registrarExecucao", () -> delegada.registrarExecucao(execucaoId, analise));
    }

    @Override
    public void registrarNotificacaoPendente(UUID execucaoId, UUID eventoId) {
        tentar("registrarNotificacaoPendente", () -> delegada.registrarNotificacaoPendente(execucaoId, eventoId));
    }

    @Override
    public void atualizarPublicacao(
            UUID execucaoId, UUID eventoId, EstadoExecucao execucao, EstadoNotificacao notificacao) {
        tentar("atualizarPublicacao", () -> delegada.atualizarPublicacao(execucaoId, eventoId, execucao, notificacao));
    }

    @Override
    public void registrarEnvio(UUID eventoId, EstadoNotificacao estado, String erroSanitizado) {
        tentar("registrarEnvio", () -> delegada.registrarEnvio(eventoId, estado, erroSanitizado));
    }

    private static void tentar(String operacao, Runnable acao) {
        try {
            acao.run();
        } catch (RuntimeException e) {
            LOG.warn("auditoria falhou operacao={} tipo={}", operacao, e.getClass().getSimpleName());
        }
    }
}
