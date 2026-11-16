package br.com.coti.servicedesk_ai.infraestrutura.mensageria;

import br.com.coti.servicedesk_ai.aplicacao.EmailService;
import br.com.coti.servicedesk_ai.dominio.EmailSolicitado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * A exceção de envio propaga de propósito: capturar e retornar faria a mensagem parecer processada e
 * desligaria o retry e a DLQ. O registro tem só eventoId e resultado, nunca o corpo do e-mail.
 */
@Component
final class EmailConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(EmailConsumer.class);

    private final EmailService emailService;

    EmailConsumer(EmailService emailService) {
        this.emailService = emailService;
    }

    @RabbitListener(queues = "${servicedesk.notificacao.fila}")
    void consumir(EmailSolicitado evento) {
        try {
            emailService.enviar(evento);
            LOG.info("eventoId={} resultado=processado", evento.eventoId());
        } catch (RuntimeException e) {
            LOG.warn("eventoId={} resultado=falha motivo={}", evento.eventoId(), e.getClass().getSimpleName());
            throw e;
        }
    }
}
