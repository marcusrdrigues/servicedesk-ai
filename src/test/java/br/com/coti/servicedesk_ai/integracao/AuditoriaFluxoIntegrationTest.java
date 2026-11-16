package br.com.coti.servicedesk_ai.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.coti.servicedesk_ai.aplicacao.AnalisadorChamadoPort;
import br.com.coti.servicedesk_ai.dominio.AnaliseChamado;
import br.com.coti.servicedesk_ai.dominio.Categoria;
import br.com.coti.servicedesk_ai.dominio.Prioridade;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Fluxo completo com PostgreSQL, RabbitMQ e Mailpit reais: API, publicação, consumo, envio e a trilha gravada.
 * O modelo é dublê. Filas próprias, para não disputar mensagem com o contexto do outro teste de integração.
 */
@SpringBootTest(properties = {
    "servicedesk.notificacao.exchange=servicedesk.events.fluxo",
    "servicedesk.notificacao.fila=servicedesk.email.fluxo",
    "servicedesk.notificacao.exchange-dlq=servicedesk.dlx.fluxo",
    "servicedesk.notificacao.fila-dlq=servicedesk.email.dlq.fluxo"
})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "auditoria-it"})
class AuditoriaFluxoIntegrationTest {

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", ContainersCompartilhados.POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", ContainersCompartilhados.POSTGRES::getUsername);
        registro.add("spring.datasource.password", ContainersCompartilhados.POSTGRES::getPassword);
        registro.add("spring.rabbitmq.addresses", ContainersCompartilhados::urlRabbit);
        registro.add("spring.rabbitmq.listener.simple.auto-startup", () -> "true");
        registro.add("spring.rabbitmq.listener.simple.retry.initial-interval", () -> "200ms");
        registro.add("spring.mail.host", ContainersCompartilhados.MAILPIT::getHost);
        registro.add("spring.mail.port", () -> ContainersCompartilhados.MAILPIT.getMappedPort(1025));
    }

    private static final String NOME = "NomeDoSolicitanteQueNaoPodeIrParaOBanco";
    private static final String DESCRICAO = "DescricaoDoChamadoQueNaoPodeIrParaOBancoDeJeitoNenhum";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired RabbitAdmin admin;

    @MockitoBean AnalisadorChamadoPort analisador;

    private final MailpitCliente mailpit = new MailpitCliente(ContainersCompartilhados.urlMailpitApi());
    private String provedor;

    @BeforeEach
    void preparar() {
        admin.purgeQueue("servicedesk.email.fluxo", false);
        mailpit.apagarTudo();
        // identifica as linhas deste teste na tabela compartilhada; a coluna provedor tem 30 caracteres
        provedor = "t" + UUID.randomUUID().toString().substring(0, 8);
        when(analisador.analisar(any(), any()))
                .thenReturn(new AnaliseChamado(Categoria.REDE, Prioridade.ALTA, "VPN caiu", "Reiniciar o concentrador", true, provedor));
    }

    private void postar(boolean autoriza) throws Exception {
        String corpo = "{\"nome\":\"%s\",\"descricao\":\"%s\",\"autorizaEmail\":%s}".formatted(NOME, DESCRICAO, autoriza);
        mvc.perform(post("/api/chamados")
                        .header("X-API-Key", "chave-de-teste-0123456789")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().is2xxSuccessful());
    }

    private String execucao(String coluna) {
        return jdbc.sql("SELECT " + coluna + " FROM execucao WHERE provedor = :p")
                .param("p", provedor).query(String.class).single();
    }

    @Test
    void chamadoAutorizadoTerminaConfirmadoNaExecucaoEAceitoNaNotificacao() throws Exception {
        postar(true);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            String statusNotificacao = jdbc.sql("""
                            SELECT n.status FROM notificacao n JOIN execucao e ON e.id = n.execucao_id
                             WHERE e.provedor = :p""").param("p", provedor).query(String.class).single();
            assertThat(statusNotificacao).isEqualTo("ACEITO_PELO_PROVEDOR");
        });
        assertThat(execucao("status")).isEqualTo("PUBLICACAO_CONFIRMADA");
        assertThat(execucao("categoria")).isEqualTo("REDE");
        assertThat(mailpit.mensagens()).hasSize(1);
    }

    @Test
    void semAutorizacaoGravaSoAExecucaoENenhumaNotificacao() throws Exception {
        postar(false);

        assertThat(execucao("status")).isEqualTo("ANALISADA");
        long notificacoes = jdbc.sql("""
                        SELECT count(*) FROM notificacao n JOIN execucao e ON e.id = n.execucao_id
                         WHERE e.provedor = :p""").param("p", provedor).query(Long.class).single();
        assertThat(notificacoes).isZero();
        Thread.sleep(1500);
        assertThat(mailpit.mensagens()).isEmpty();
    }

    @Test
    void nenhumaTabelaGuardaONomeADescricaoOResumoOuOCorpo() throws Exception {
        postar(true);
        await().atMost(Duration.ofSeconds(20)).until(() -> !mailpit.mensagens().isEmpty());

        List<String> textos = jdbc.sql("""
                        SELECT concat_ws('|', e.provedor, e.categoria, e.prioridade, e.status,
                                         n.status, n.ultimo_erro)
                          FROM execucao e LEFT JOIN notificacao n ON n.execucao_id = e.id
                         WHERE e.provedor = :p""").param("p", provedor).query(String.class).list();

        // sem esta checagem o teste passaria vazio quando nada fosse gravado
        assertThat(textos).hasSize(1);
        assertThat(textos.get(0)).contains("ACEITO_PELO_PROVEDOR").contains(provedor);
        assertThat(String.join(" ", textos))
                .doesNotContain(NOME)
                .doesNotContain(DESCRICAO)
                .doesNotContain("VPN caiu")
                .doesNotContain("Reiniciar o concentrador")
                .doesNotContain("suporte@exemplo.local");
    }
}
