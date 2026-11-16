package br.com.coti.servicedesk_ai.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.coti.servicedesk_ai.aplicacao.AuditoriaPort;
import br.com.coti.servicedesk_ai.aplicacao.ErroSanitizado;
import br.com.coti.servicedesk_ai.dominio.AnaliseChamado;
import br.com.coti.servicedesk_ai.dominio.Categoria;
import br.com.coti.servicedesk_ai.dominio.EstadoExecucao;
import br.com.coti.servicedesk_ai.dominio.EstadoNotificacao;
import br.com.coti.servicedesk_ai.dominio.Prioridade;
import br.com.coti.servicedesk_ai.infraestrutura.auditoria.JdbcAuditoriaAdapter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** A auditoria contra PostgreSQL real: migração, atualização condicional e o que NÃO é gravado. */
@SpringBootTest
@ActiveProfiles({"test", "auditoria-it"})
class AuditoriaPostgresIntegrationTest {

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", ContainersCompartilhados.POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", ContainersCompartilhados.POSTGRES::getUsername);
        registro.add("spring.datasource.password", ContainersCompartilhados.POSTGRES::getPassword);
    }

    @Autowired JdbcClient jdbc;
    @Autowired Flyway flyway;

    private final Clock relogio = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);

    private AuditoriaPort auditoria() {
        return new JdbcAuditoriaAdapter(jdbc, relogio);
    }

    private UUID novaExecucao(AuditoriaPort auditoria, String provedor) {
        UUID execucao = UUID.randomUUID();
        auditoria.registrarExecucao(
                execucao, new AnaliseChamado(Categoria.REDE, Prioridade.ALTA, "resumo", "orientacao", true, provedor));
        return execucao;
    }

    private String statusDaNotificacao(UUID eventoId) {
        return jdbc.sql("SELECT status FROM notificacao WHERE evento_id = :id")
                .param("id", eventoId).query(String.class).single();
    }

    private String statusDaExecucao(UUID execucaoId) {
        return jdbc.sql("SELECT status FROM execucao WHERE id = :id")
                .param("id", execucaoId).query(String.class).single();
    }

    // --- migração ---

    @Test
    void migracaoCriaAsTabelasEUmaSegundaRodadaNaoReaplicaNada() {
        List<String> tabelas = jdbc.sql("""
                        SELECT table_name FROM information_schema.tables
                         WHERE table_schema = 'public' AND table_name IN ('execucao', 'notificacao')""")
                .query(String.class).list();
        assertThat(tabelas).containsExactlyInAnyOrder("execucao", "notificacao");

        assertThat(flyway.migrate().migrationsExecuted).isZero();
        long aplicadas = jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE success")
                .query(Long.class).single();
        assertThat(aplicadas).isEqualTo(1);
    }

    @Test
    void instantesSaoTimestamptz() {
        List<String> tipos = jdbc.sql("""
                        SELECT data_type FROM information_schema.columns
                         WHERE table_name IN ('execucao', 'notificacao') AND column_name IN ('criado_em', 'atualizado_em')""")
                .query(String.class).list();

        assertThat(tipos).isNotEmpty().allMatch("timestamp with time zone"::equals);
    }

    @Test
    void nenhumaColunaGuardaNomeDescricaoResumoOuCorpo() {
        List<String> colunas = jdbc.sql("""
                        SELECT column_name FROM information_schema.columns
                         WHERE table_schema = 'public' AND table_name IN ('execucao', 'notificacao')""")
                .query(String.class).list();

        assertThat(colunas).doesNotContain("nome", "descricao", "resumo", "orientacao", "corpo", "assunto", "destinatario");
    }

    // --- registro e transições ---

    @Test
    void execucaoGuardaOProvedorQueRespondeuECategoriaEPrioridade() {
        UUID execucao = novaExecucao(auditoria(), "gemini");

        var linha = jdbc.sql("SELECT provedor, categoria, prioridade, status FROM execucao WHERE id = :id")
                .param("id", execucao)
                .query((rs, n) -> List.of(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)))
                .single();

        assertThat(linha).containsExactly("gemini", "REDE", "ALTA", "ANALISADA");
    }

    @Test
    void fluxoNormalAvancaAteOAceiteDoProvedor() {
        var auditoria = auditoria();
        UUID execucao = novaExecucao(auditoria, "openai");
        UUID evento = UUID.randomUUID();

        auditoria.registrarNotificacaoPendente(execucao, evento);
        assertThat(statusDaNotificacao(evento)).isEqualTo("PUBLICACAO_PENDENTE");
        assertThat(statusDaExecucao(execucao)).isEqualTo("PUBLICACAO_PENDENTE");

        auditoria.atualizarPublicacao(execucao, evento, EstadoExecucao.PUBLICACAO_CONFIRMADA,
                EstadoNotificacao.PUBLICACAO_CONFIRMADA);
        assertThat(statusDaNotificacao(evento)).isEqualTo("PUBLICACAO_CONFIRMADA");
        assertThat(statusDaExecucao(execucao)).isEqualTo("PUBLICACAO_CONFIRMADA");

        auditoria.registrarEnvio(evento, EstadoNotificacao.ACEITO_PELO_PROVEDOR, null);
        assertThat(statusDaNotificacao(evento)).isEqualTo("ACEITO_PELO_PROVEDOR");
    }

    @Test
    void consumidorQueTerminaAntesDoPublicadorNaoERegredido() {
        var auditoria = auditoria();
        UUID execucao = novaExecucao(auditoria, "openai");
        UUID evento = UUID.randomUUID();
        auditoria.registrarNotificacaoPendente(execucao, evento);

        auditoria.registrarEnvio(evento, EstadoNotificacao.ACEITO_PELO_PROVEDOR, null); // consumidor adiantou-se
        auditoria.atualizarPublicacao(execucao, evento, EstadoExecucao.PUBLICACAO_CONFIRMADA,
                EstadoNotificacao.PUBLICACAO_CONFIRMADA); // publicador chega depois

        assertThat(statusDaNotificacao(evento)).isEqualTo("ACEITO_PELO_PROVEDOR");
        assertThat(statusDaExecucao(execucao)).isEqualTo("PUBLICACAO_CONFIRMADA");
    }

    @Test
    void falhaTardiaDepoisDoAceiteNaoRegride() {
        var auditoria = auditoria();
        UUID execucao = novaExecucao(auditoria, "openai");
        UUID evento = UUID.randomUUID();
        auditoria.registrarNotificacaoPendente(execucao, evento);
        auditoria.registrarEnvio(evento, EstadoNotificacao.ACEITO_PELO_PROVEDOR, null);

        auditoria.registrarEnvio(evento, EstadoNotificacao.FALHA_ENVIO, "FalhaEnvioEmailException");

        assertThat(statusDaNotificacao(evento)).isEqualTo("ACEITO_PELO_PROVEDOR");
    }

    @Test
    void sucessoDepoisDeFalhaAvancaEMantemOUltimoErro() {
        var auditoria = auditoria();
        UUID execucao = novaExecucao(auditoria, "openai");
        UUID evento = UUID.randomUUID();
        auditoria.registrarNotificacaoPendente(execucao, evento);
        auditoria.registrarEnvio(evento, EstadoNotificacao.FALHA_ENVIO, "FalhaEnvioEmailException");

        auditoria.registrarEnvio(evento, EstadoNotificacao.ACEITO_PELO_PROVEDOR, null);

        assertThat(statusDaNotificacao(evento)).isEqualTo("ACEITO_PELO_PROVEDOR");
        String erro = jdbc.sql("SELECT ultimo_erro FROM notificacao WHERE evento_id = :id")
                .param("id", evento).query(String.class).single();
        assertThat(erro).isEqualTo("FalhaEnvioEmailException");
    }

    @Test
    void novaFalhaNoMesmoEstadoAtualizaOUltimoErro() {
        var auditoria = auditoria();
        UUID execucao = novaExecucao(auditoria, "openai");
        UUID evento = UUID.randomUUID();
        auditoria.registrarNotificacaoPendente(execucao, evento);

        auditoria.registrarEnvio(evento, EstadoNotificacao.FALHA_ENVIO, "FalhaA");
        auditoria.registrarEnvio(evento, EstadoNotificacao.FALHA_ENVIO, "FalhaB");

        String erro = jdbc.sql("SELECT ultimo_erro FROM notificacao WHERE evento_id = :id")
                .param("id", evento).query(String.class).single();
        assertThat(erro).isEqualTo("FalhaB");
    }

    @Test
    void publicacaoIncertaECouvertaPeloAceiteSeOConsumidorProcessar() {
        var auditoria = auditoria();
        UUID execucao = novaExecucao(auditoria, "openai");
        UUID evento = UUID.randomUUID();
        auditoria.registrarNotificacaoPendente(execucao, evento);

        auditoria.atualizarPublicacao(execucao, evento, EstadoExecucao.PUBLICACAO_INCERTA,
                EstadoNotificacao.PUBLICACAO_INCERTA);
        assertThat(statusDaNotificacao(evento)).isEqualTo("PUBLICACAO_INCERTA");

        auditoria.registrarEnvio(evento, EstadoNotificacao.ACEITO_PELO_PROVEDOR, null);
        assertThat(statusDaNotificacao(evento)).isEqualTo("ACEITO_PELO_PROVEDOR");
    }

    @Test
    void erroLongoNaoEstouraAColunaDe200Caracteres() {
        var auditoria = auditoria();
        UUID execucao = novaExecucao(auditoria, "openai");
        UUID evento = UUID.randomUUID();
        auditoria.registrarNotificacaoPendente(execucao, evento);
        Throwable cadeia = new IllegalStateException();
        for (int i = 0; i < 6; i++) {
            cadeia = new RuntimeException("x".repeat(500), cadeia);
        }

        auditoria.registrarEnvio(evento, EstadoNotificacao.FALHA_ENVIO, ErroSanitizado.de(cadeia));

        String erro = jdbc.sql("SELECT ultimo_erro FROM notificacao WHERE evento_id = :id")
                .param("id", evento).query(String.class).single();
        assertThat(erro.length()).isLessThanOrEqualTo(200);
        assertThat(erro).doesNotContain("xxxx");
    }

    @Test
    void notificacaoSemExecucaoExistenteEMantidaPelaChaveEstrangeira() {
        var auditoria = auditoria();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> auditoria.registrarNotificacaoPendente(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
