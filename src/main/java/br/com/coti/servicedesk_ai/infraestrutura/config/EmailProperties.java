package br.com.coti.servicedesk_ai.infraestrutura.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** EMAIL_MODE escolhe o provedor: smtp (Mailpit local) ou resend (HTTPS na nuvem). */
@Validated
@ConfigurationProperties("servicedesk.email")
public record EmailProperties(@NotNull Modo modo, Resend resend) {

    public enum Modo { SMTP, RESEND }

    public record Resend(@NotBlank String baseUrl, String apiKey, @NotNull Duration timeout) {}

    @AssertTrue(message = "RESEND_API_KEY é obrigatória quando EMAIL_MODE=resend")
    public boolean isResendConfigurado() {
        return modo != Modo.RESEND || (resend != null && ValorDefinido.Validador.definido(resend.apiKey()));
    }
}
