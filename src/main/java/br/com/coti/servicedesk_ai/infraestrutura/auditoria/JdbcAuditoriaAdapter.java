package br.com.coti.servicedesk_ai.infraestrutura.auditoria;

import br.com.coti.servicedesk_ai.aplicacao.AuditoriaPort;
import br.com.coti.servicedesk_ai.dominio.AnaliseChamado;
import br.com.coti.servicedesk_ai.dominio.EstadoExecucao;
import br.com.coti.servicedesk_ai.dominio.EstadoNotificacao;
import java.sql.Types;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Auditoria em PostgreSQL por JDBC puro. Cada comando é curto e roda em autocommit: nenhuma transação fica aberta
 * durante a chamada ao modelo nem durante o envio de e-mail. Toda atualização é condicional (a ordem só avança).
 * SQL só com parâmetro vinculado. Banco e broker não são atômicos (sem Outbox): ver design.md, Decisão 4.
 */
public final class JdbcAuditoriaAdapter implements AuditoriaPort {

    private final JdbcClient jdbc;
    private final Clock relogio;

    public JdbcAuditoriaAdapter(JdbcClient jdbc, Clock relogio) {
        this.jdbc = jdbc;
        this.relogio = relogio;
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC);
    }

    @Override
    public void registrarExecucao(UUID execucaoId, AnaliseChamado analise) {
        jdbc.sql("""
                        INSERT INTO execucao
                            (id, criado_em, provedor, categoria, prioridade, status, status_ordem, atualizado_em)
                        VALUES (:id, :agora, :provedor, :categoria, :prioridade, :status, :ordem, :agora)""")
                .param("id", execucaoId)
                .param("agora", agora())
                .param("provedor", analise.provedor() == null ? "desconhecido" : analise.provedor())
                .param("categoria", analise.categoria().name())
                .param("prioridade", analise.prioridade().name())
                .param("status", EstadoExecucao.ANALISADA.name())
                .param("ordem", EstadoExecucao.ANALISADA.ordem())
                .update();
    }

    @Override
    public void registrarNotificacaoPendente(UUID execucaoId, UUID eventoId) {
        jdbc.sql("""
                        INSERT INTO notificacao (evento_id, execucao_id, status, status_ordem, atualizado_em)
                        VALUES (:evento, :execucao, :status, :ordem, :agora)""")
                .param("evento", eventoId)
                .param("execucao", execucaoId)
                .param("status", EstadoNotificacao.PUBLICACAO_PENDENTE.name())
                .param("ordem", EstadoNotificacao.PUBLICACAO_PENDENTE.ordem())
                .param("agora", agora())
                .update();
        avancarExecucao(execucaoId, EstadoExecucao.PUBLICACAO_PENDENTE);
    }

    @Override
    public void atualizarPublicacao(
            UUID execucaoId, UUID eventoId, EstadoExecucao execucao, EstadoNotificacao notificacao) {
        avancarExecucao(execucaoId, execucao);
        avancarNotificacao(eventoId, notificacao, null);
    }

    @Override
    public void registrarEnvio(UUID eventoId, EstadoNotificacao estado, String erroSanitizado) {
        avancarNotificacao(eventoId, estado, erroSanitizado);
    }

    private void avancarExecucao(UUID execucaoId, EstadoExecucao estado) {
        jdbc.sql("""
                        UPDATE execucao
                           SET status = :status, status_ordem = :ordem, atualizado_em = :agora
                         WHERE id = :id AND status_ordem < :ordem""")
                .param("status", estado.name())
                .param("ordem", estado.ordem())
                .param("agora", agora())
                .param("id", execucaoId)
                .update();
    }

    /**
     * Só avança, com uma exceção: repetir o mesmo estado (nova tentativa que falha de novo) atualiza o instante e o
     * último erro. O último erro nunca é apagado por um estado sem erro.
     */
    private void avancarNotificacao(UUID eventoId, EstadoNotificacao estado, String erro) {
        jdbc.sql("""
                        UPDATE notificacao
                           SET status = :status, status_ordem = :ordem, atualizado_em = :agora,
                               ultimo_erro = COALESCE(:erro, ultimo_erro)
                         WHERE evento_id = :id AND (status_ordem < :ordem OR status = :status)""")
                .param("status", estado.name())
                .param("ordem", estado.ordem())
                .param("agora", agora())
                .param("erro", erro, Types.VARCHAR)
                .param("id", eventoId)
                .update();
    }
}
