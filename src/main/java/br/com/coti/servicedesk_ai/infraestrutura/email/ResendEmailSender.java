package br.com.coti.servicedesk_ai.infraestrutura.email;

import br.com.coti.servicedesk_ai.aplicacao.EnviadorEmailPort;
import br.com.coti.servicedesk_ai.dominio.EmailSolicitado;
import br.com.coti.servicedesk_ai.dominio.FalhaEnvioEmailException;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Modo resend: API HTTPS do Resend. O eventoId vai na Idempotency-Key para que um retry não duplique o
 * envio dentro da janela do provedor. A janela é limitada: isto não promete exatamente uma vez.
 * Resposta de erro (4xx, 5xx) e timeout viram exceção, para o consumidor tentar de novo.
 */
public final class ResendEmailSender implements EnviadorEmailPort {

    private final RestClient cliente;
    private final String remetente;

    public ResendEmailSender(RestClient cliente, String remetente) {
        this.cliente = cliente;
        this.remetente = remetente;
    }

    @Override
    public void enviar(EmailSolicitado evento) {
        Map<String, Object> corpo = Map.of(
                "from", remetente,
                "to", List.of(evento.destinatario()),
                "subject", evento.assunto(),
                "text", evento.corpo());
        try {
            cliente.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", evento.eventoId().toString())
                    .body(corpo)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new FalhaEnvioEmailException("falha ao enviar pelo Resend", e);
        }
    }
}
