package br.com.coti.servicedesk_ai.infraestrutura.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Parâmetros de chamada ao modelo. Nenhum valor fica em código: tudo vem de application.yaml. */
@Validated
@ConfigurationProperties("servicedesk.ia")
public record IaProperties(
        @NotNull Duration timeout,
        @NotNull Duration orcamentoTotal,
        @Min(0) int tentativasExtrasTransitorias,
        @NotNull Duration backoffInicial,
        @Min(1) int maxTokens,
        Double temperatura,
        @Min(1) int tamanhoMaximoResumo,
        @Min(1) int tamanhoMaximoOrientacao,
        @Valid @NotNull Provedor openai,
        @Valid @NotNull Provedor gemini) {

    public record Provedor(
            @NotBlank String baseUrl, @ValorDefinido String apiKey, @ValorDefinido String modelo, boolean schemaNativo) {}
}
