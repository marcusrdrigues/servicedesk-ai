package br.com.coti.servicedesk_ai.aplicacao;

import br.com.coti.servicedesk_ai.dominio.AnaliseChamado;
import br.com.coti.servicedesk_ai.dominio.EstadoExecucao;
import br.com.coti.servicedesk_ai.dominio.EstadoNotificacao;
import java.util.UUID;

/**
 * Trilha de auditoria. Nunca recebe nome, descrição, resumo nem corpo de e-mail. Os estados só avançam:
 * a implementação deve atualizar de forma condicional, porque o consumidor pode terminar antes de o
 * publicador registrar a confirmação.
 */
public interface AuditoriaPort {

    void registrarExecucao(UUID execucaoId, AnaliseChamado analise);

    /** Registra a notificação como PUBLICACAO_PENDENTE e avança a execução para o mesmo estado. */
    void registrarNotificacaoPendente(UUID execucaoId, UUID eventoId);

    void atualizarPublicacao(
            UUID execucaoId, UUID eventoId, EstadoExecucao execucao, EstadoNotificacao notificacao);

    /** erroSanitizado é só tipo e código curto; nunca mensagem de exceção nem conteúdo do chamado. */
    void registrarEnvio(UUID eventoId, EstadoNotificacao estado, String erroSanitizado);

    static AuditoriaPort semAuditoria() {
        return new AuditoriaPort() {
            @Override
            public void registrarExecucao(UUID execucaoId, AnaliseChamado analise) {}

            @Override
            public void registrarNotificacaoPendente(UUID execucaoId, UUID eventoId) {}

            @Override
            public void atualizarPublicacao(
                    UUID execucaoId, UUID eventoId, EstadoExecucao execucao, EstadoNotificacao notificacao) {}

            @Override
            public void registrarEnvio(UUID eventoId, EstadoNotificacao estado, String erroSanitizado) {}
        };
    }
}
