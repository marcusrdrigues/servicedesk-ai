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

    @Test
    void chaveDoResendComPlaceholderSemValorNaoSobe() {
        assertThatThrownBy(() -> subir(
                        "servicedesk.email.modo=resend", "servicedesk.email.resend.api-key=${RESEND_NAO_DEFINIDA}"))
                .satisfies(e -> assertThat(mensagensDe(e)).contains("RESEND_API_KEY"));
    }
}
