package br.com.coti.servicedesk_ai.infraestrutura.config;

import br.com.coti.servicedesk_ai.aplicacao.AnalisadorChamadoPort;
import br.com.coti.servicedesk_ai.aplicacao.AnalisadorComFallback;
import br.com.coti.servicedesk_ai.aplicacao.AnalisadorComRetry;
import br.com.coti.servicedesk_ai.aplicacao.ValidadorAnalise;
import br.com.coti.servicedesk_ai.infraestrutura.ia.ChatClientAnalisadorAdapter;
import java.time.Clock;
import java.time.Duration;
import java.util.Random;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * Monta os dois provedores à mão: o starter do Spring AI autoconfigura um único modelo e exige chave, e aqui
 * há dois endpoints compatíveis com OpenAI, cada um com chave, modelo e base-url próprios.
 */
@Configuration
@EnableConfigurationProperties({IaProperties.class, ServicedeskProperties.class})
class IaConfig {

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }

    @Bean
    ValidadorAnalise validadorAnalise(IaProperties ia) {
        return new ValidadorAnalise(ia.tamanhoMaximoResumo(), ia.tamanhoMaximoOrientacao());
    }

    @Bean
    AnalisadorChamadoPort analisadorChamado(
            IaProperties ia,
            ValidadorAnalise validador,
            Clock relogio,
            @Value("classpath:prompts/analisar-chamado.system.v1.st") Resource promptSistema,
            @Value("classpath:prompts/analisar-chamado.user.v1.st") Resource promptUsuario) {
        // OpenAI recomenda max_completion_tokens; o endpoint do Gemini documenta max_tokens.
        OpenAiChatOptions.Builder opcoesOpenAi = base(ia, ia.openai()).maxCompletionTokens(ia.maxTokens());
        OpenAiChatOptions.Builder opcoesGemini = base(ia, ia.gemini()).maxTokens(ia.maxTokens());

        AnalisadorChamadoPort principal = comRetry(
                new ChatClientAnalisadorAdapter(
                        "openai", cliente(opcoesOpenAi), promptSistema, promptUsuario,
                        ia.openai().schemaNativo(), validador),
                ia, relogio);
        AnalisadorChamadoPort reserva = comRetry(
                new ChatClientAnalisadorAdapter(
                        "gemini", cliente(opcoesGemini), promptSistema, promptUsuario,
                        ia.gemini().schemaNativo(), validador),
                ia, relogio);
        return new AnalisadorComFallback(principal, reserva, relogio);
    }

    private static OpenAiChatOptions.Builder base(IaProperties ia, IaProperties.Provedor provedor) {
        OpenAiChatOptions.Builder opcoes = OpenAiChatOptions.builder()
                .baseUrl(provedor.baseUrl())
                .apiKey(provedor.apiKey())
                .model(provedor.modelo())
                .timeout(ia.timeout())
                .maxRetries(0); // retry em um único ponto: AnalisadorComRetry
        if (ia.temperatura() != null) {
            opcoes.temperature(ia.temperatura());
        }
        return opcoes;
    }

    private static ChatClient cliente(OpenAiChatOptions.Builder opcoes) {
        return ChatClient.builder(OpenAiChatModel.builder().options(opcoes.build()).build())
                .build();
    }

    private static AnalisadorChamadoPort comRetry(AnalisadorChamadoPort adaptador, IaProperties ia, Clock relogio) {
        return new AnalisadorComRetry(
                adaptador,
                ia.tentativasExtrasTransitorias(),
                ia.backoffInicial(),
                relogio,
                // Random está em java.base. RandomGenerator.getDefault() escolhe um algoritmo de jdk.random,
                // módulo que o JRE enxuto da imagem Docker não traz.
                new Random(),
                IaConfig::dormir);
    }

    private static void dormir(Duration pausa) {
        try {
            Thread.sleep(pausa);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
