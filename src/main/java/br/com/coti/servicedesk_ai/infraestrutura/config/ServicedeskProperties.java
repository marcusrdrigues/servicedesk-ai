package br.com.coti.servicedesk_ai.infraestrutura.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("servicedesk")
public record ServicedeskProperties(
        @ValorDefinido String apiKey,
        String corsOrigem,
        @Min(1) int limiteRequisicoesPorMinuto,
        @Valid @NotNull Notificacao notificacao) {

    /** O destinatário (EMAIL_TO) é do servidor: nunca do modelo nem da requisição. */
    public record Notificacao(
            @NotBlank @Email String destinatario,
            @NotBlank @Email String remetente,
            @NotBlank String prefixoAssunto,
            @NotBlank String exchange,
            @NotBlank String routingKey,
            @NotBlank String fila,
            @NotBlank String exchangeDlq,
            @NotBlank String routingKeyDlq,
            @NotBlank String filaDlq,
            @NotNull Duration timeoutConfirmacao) {}
}
