package br.com.coti.servicedesk_ai.infraestrutura.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.coti.servicedesk_ai.dominio.EmailSolicitado;
import br.com.coti.servicedesk_ai.dominio.FalhaEnvioEmailException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Resend simulado: nenhum teste faz envio real. */
class ResendEmailSenderTest {

    private static final String CHAVE = "re_chave_de_teste";

    private MockRestServiceServer servidor;
    private ResendEmailSender sender;
    private final UUID id = UUID.randomUUID();
    private final EmailSolicitado evento =
            new EmailSolicitado(id, "suporte@exemplo.local", "[ServiceDesk] ALTA - REDE", "Corpo do chamado");

    @BeforeEach
    void preparar() {
        RestClient.Builder construtor = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer " + CHAVE);
        servidor = MockRestServiceServer.bindTo(construtor).build();
        sender = new ResendEmailSender(construtor.build(), "servicedesk@exemplo.local");
    }

    @Test
    void enviaPorHttpsComBearerIdempotencyKeyIgualAoEventoIdEOsCamposDoServidor() {
        servidor.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + CHAVE))
                .andExpect(header("Idempotency-Key", id.toString()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.from").value("servicedesk@exemplo.local"))
                .andExpect(jsonPath("$.to[0]").value("suporte@exemplo.local"))
                .andExpect(jsonPath("$.subject").value("[ServiceDesk] ALTA - REDE"))
                .andExpect(jsonPath("$.text").value("Corpo do chamado"))
                .andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

        sender.enviar(evento);

        servidor.verify();
    }

    @Test
    void resposta5xxViraExcecaoParaPermitirRetry() {
        servidor.expect(requestTo("https://api.resend.com/emails")).andRespond(withServerError());

        assertThatThrownBy(() -> sender.enviar(evento)).isInstanceOf(FalhaEnvioEmailException.class);
    }

    @Test
    void resposta4xxViraExcecaoENaoVazaAChaveNemOCorpo() {
        servidor.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThatThrownBy(() -> sender.enviar(evento))
                .isInstanceOf(FalhaEnvioEmailException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(CHAVE).doesNotContain("Corpo do chamado"));
    }

    @Test
    void resposta429ViraExcecao() {
        servidor.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> sender.enviar(evento)).isInstanceOf(FalhaEnvioEmailException.class);
    }
}
