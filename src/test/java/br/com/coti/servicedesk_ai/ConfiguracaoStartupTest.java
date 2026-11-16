package br.com.coti.servicedesk_ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** A aplicação recusa subir com configuração obrigatória ausente, e a mensagem cita o nome, nunca o valor. */
class ConfiguracaoStartupTest {

    private static final String SEGREDO_DE_TESTE = "valor-secreto-que-nao-pode-aparecer";

    private static ConfigurableApplicationContext subir(String... propriedades) {
        return new SpringApplicationBuilder(ServicedeskAiApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("test")
                .run(argumentos(propriedades));
    }

    /** Argumento de linha de comando tem a maior precedência; properties() do builder é só valor padrão. */
    private static String[] argumentos(String... propriedades) {
        return java.util.Arrays.stream(propriedades).map(p -> "--" + p).toArray(String[]::new);
    }

    private static String mensagensDe(Throwable erro) {
        var texto = new StringBuilder();
        for (Throwable t = erro; t != null; t = t.getCause()) {
            texto.append(t.getMessage()).append('\n');
        }
        return texto.toString();
    }

    @Test
    void semDestinatarioNaoSobe() {
        assertThatThrownBy(() -> subir("servicedesk.notificacao.destinatario="))
                .satisfies(e -> assertThat(mensagensDe(e)).contains("servicedesk.notificacao.destinatario"));
    }

    @Test
    void destinatarioInvalidoNaoSobe() {
        assertThatThrownBy(() -> subir("servicedesk.notificacao.destinatario=isto-nao-e-email"))
                .satisfies(e -> assertThat(mensagensDe(e)).contains("servicedesk.notificacao.destinatario"));
    }

    @Test
    void semSegredoDaApiNaoSobe() {
        assertThatThrownBy(() -> subir("servicedesk.api-key="))
                .satisfies(e -> assertThat(mensagensDe(e)).contains("servicedesk.api-key"));
    }

    @Test
    void modoResendSemChaveNaoSobeECitaAVariavel() {
        assertThatThrownBy(() -> subir("servicedesk.email.modo=resend", "servicedesk.email.resend.api-key="))
                .satisfies(e -> assertThat(mensagensDe(e)).contains("RESEND_API_KEY"));
    }

    @Test
    void modoResendComChaveSobeSemUsarSmtp() {
        try (var contexto = subir(
                "servicedesk.email.modo=resend",
                "servicedesk.email.resend.api-key=" + SEGREDO_DE_TESTE)) {
            assertThat(contexto.getBeansOfType(br.com.coti.servicedesk_ai.infraestrutura.email.ResendEmailSender.class))
                    .hasSize(1);
            assertThat(contexto.getBeansOfType(br.com.coti.servicedesk_ai.infraestrutura.email.SmtpEmailSender.class))
                    .isEmpty();
        }
    }

    @Test
    void modoSmtpEOPadraoESobeComMailpit() {
        try (var contexto = subir()) {
            assertThat(contexto.getBeansOfType(br.com.coti.servicedesk_ai.infraestrutura.email.SmtpEmailSender.class))
                    .hasSize(1);
            assertThat(contexto.getBeansOfType(br.com.coti.servicedesk_ai.infraestrutura.email.ResendEmailSender.class))
                    .isEmpty();
        }
    }

    /**
     * O Spring Boot não falha em ${VARIAVEL} sem valor ao fazer o binding: deixa a string literal. Sem a trava,
     * o segredo da API passaria a ser o texto "${SERVICEDESK_API_KEY}", que qualquer um adivinha.
     */
    @Test
    void segredoDaApiComPlaceholderSemValorNaoSobe() {
        assertThatThrownBy(() -> subir("servicedesk.api-key=${SERVICEDESK_API_KEY_NAO_DEFINIDA}"))
                .satisfies(e -> assertThat(mensagensDe(e)).contains("servicedesk.api-key"));
    }

    @Test
    void chaveDeProvedorDeModeloComPlaceholderSemValorNaoSobe() {
        assertThatThrownBy(() -> subir("servicedesk.ia.openai.api-key=${OPENAI_API_KEY_NAO_DEFINIDA}"))
                .satisfies(e -> assertThat(mensagensDe(e)).contains("servicedesk.ia.openai.apiKey"));
        assertThatThrownBy(() -> subir("servicedesk.ia.gemini.modelo=${GEMINI_CHAT_MODEL_NAO_DEFINIDO}"))
                .satisfies(e -> assertThat(mensagensDe(e)).contains("servicedesk.ia.gemini.modelo"));
    }

    // --- perfil cloud e auditoria ---

    private static ConfigurableApplicationContext subirNaNuvem(String... propriedades) {
        return new SpringApplicationBuilder(ServicedeskAiApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("test", "cloud")
                .run(argumentos(propriedades));
    }

    private static final String JDBC_COM_TLS = "spring.datasource.url=jdbc:postgresql://host.exemplo/servicedesk?sslmode=require";
    private static final String BROKER_COM_TLS = "spring.rabbitmq.addresses=amqps://usuario:senha@host.exemplo/vhost";
    private static final String CHAVE_RESEND = "servicedesk.email.resend.api-key=" + SEGREDO_DE_TESTE;

    @Test
    void semSenhaDoBancoNaoSobeECitaDbPassword() {
        assertThatThrownBy(() -> subir("spring.datasource.password=${DB_PASSWORD_NAO_DEFINIDA}"))
                .satisfies(e -> assertThat(mensagensDe(e)).contains("DB_PASSWORD"));
    }

    @Test
    void nuvemSemDbUrlNaoSobeECitaDbUrl() {
        assertThatThrownBy(() -> subirNaNuvem(BROKER_COM_TLS, CHAVE_RESEND))
                .satisfies(e -> assertThat(mensagensDe(e)).contains("DB_URL").doesNotContain(SEGREDO_DE_TESTE));
    }

    @Test
    void nuvemComJdbcSemTlsNaoSobe() {
        assertThatThrownBy(() -> subirNaNuvem(
                        "spring.datasource.url=jdbc:postgresql://host.exemplo/servicedesk", BROKER_COM_TLS, CHAVE_RESEND))
                .satisfies(e -> assertThat(mensagensDe(e)).contains("sslmode=require"));
        assertThatThrownBy(() -> subirNaNuvem(
                        "spring.datasource.url=jdbc:postgresql://host.exemplo/servicedesk?sslmode=disable",
                        BROKER_COM_TLS, CHAVE_RESEND))
                .satisfies(e -> assertThat(mensagensDe(e)).contains("sslmode=require"));
    }

    @Test
    void nuvemComUrlPostgresqlPuraNaoServeComoJdbc() {
        assertThatThrownBy(() -> subirNaNuvem(
                        "spring.datasource.url=postgresql://usuario:senha@host.exemplo/servicedesk?sslmode=require",
                        BROKER_COM_TLS, CHAVE_RESEND))
                .satisfies(e -> assertThat(mensagensDe(e)).contains("jdbc:postgresql://").doesNotContain("senha@"));
    }

    @Test
    void nuvemComBrokerSemTlsNaoSobe() {
        assertThatThrownBy(() -> subirNaNuvem(
                        JDBC_COM_TLS, "spring.rabbitmq.addresses=amqp://usuario:senhaSecreta@host.exemplo/vhost", CHAVE_RESEND))
                .satisfies(e -> assertThat(mensagensDe(e))
                        .contains("RABBITMQ_URL")
                        .contains("amqps://")
                        .doesNotContain("senhaSecreta"));
    }

    @Test
    void nuvemComValoresValidosSobeComResendPoolPequenoEPortaDoAmbiente() {
        try (var contexto = subirNaNuvem(JDBC_COM_TLS, BROKER_COM_TLS, CHAVE_RESEND, "PORT=9123")) {
            var env = contexto.getEnvironment();
            assertThat(contexto.getBeansOfType(br.com.coti.servicedesk_ai.infraestrutura.email.ResendEmailSender.class))
                    .hasSize(1);
            assertThat(contexto.getBeansOfType(br.com.coti.servicedesk_ai.infraestrutura.email.SmtpEmailSender.class))
                    .isEmpty();
            assertThat(env.getProperty("server.port")).isEqualTo("9123");
            assertThat(env.getProperty("spring.datasource.hikari.maximum-pool-size")).isEqualTo("3");
            assertThat(env.getProperty("spring.datasource.hikari.minimum-idle")).isEqualTo("0");
            assertThat(env.getProperty("servicedesk.ia.orcamento-total")).isEqualTo("25s");
        }
    }

    @Test
    void nuvemSemChaveDoResendNaoSobe() {
        assertThatThrownBy(() -> subirNaNuvem(JDBC_COM_TLS, BROKER_COM_TLS))
                .satisfies(e -> assertThat(mensagensDe(e)).contains("RESEND_API_KEY"));
    }

    @Test
    void chaveDoResendComPlaceholderSemValorNaoSobe() {
        assertThatThrownBy(() -> subir(
                        "servicedesk.email.modo=resend", "servicedesk.email.resend.api-key=${RESEND_NAO_DEFINIDA}"))
                .satisfies(e -> assertThat(mensagensDe(e)).contains("RESEND_API_KEY"));
    }
}
