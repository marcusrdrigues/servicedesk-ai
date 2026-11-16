package br.com.coti.servicedesk_ai.infraestrutura.mensageria;

import br.com.coti.servicedesk_ai.aplicacao.PublicadorNotificacaoPort;
import br.com.coti.servicedesk_ai.dominio.EmailSolicitado;
import br.com.coti.servicedesk_ai.dominio.FalhaPublicacaoException;
import br.com.coti.servicedesk_ai.infraestrutura.config.ServicedeskProperties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica com mensagem persistente, publisher confirm correlacionado e mandatory. Só retorna depois do ACK
 * do broker e sem mensagem devolvida. O ACK diz que o broker aceitou a mensagem, não que o e-mail saiu.
 */
@Component
public final class NotificacaoPublisher implements PublicadorNotificacaoPort {

    private static final Logger LOG = LoggerFactory.getLogger(NotificacaoPublisher.class);

    private final RabbitTemplate template;
    private final ServicedeskProperties.Notificacao configuracao;

    public NotificacaoPublisher(RabbitTemplate template, ServicedeskProperties propriedades) {
        this.template = template;
        this.configuracao = propriedades.notificacao();
    }

    @Override
    public void publicar(EmailSolicitado evento) {
        String id = evento.eventoId().toString();
        CorrelationData correlacao = new CorrelationData(id);
        try {
            template.convertAndSend(
                    configuracao.exchange(),
                    configuracao.routingKey(),
                    evento,
                    mensagem -> {
                        mensagem.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        mensagem.getMessageProperties().setMessageId(id);
                        return mensagem;
                    },
                    correlacao);
            CorrelationData.Confirm confirmacao = correlacao
                    .getFuture()
                    .get(configuracao.timeoutConfirmacao().toMillis(), TimeUnit.MILLISECONDS);
            if (!confirmacao.ack()) {
                throw new FalhaPublicacaoException("o broker recusou a mensagem");
            }
            if (correlacao.getReturned() != null) {
                throw new FalhaPublicacaoException("o broker não roteou a mensagem para nenhuma fila");
            }
            LOG.info("eventoId={} resultado=publicado-e-confirmado", id);
        } catch (TimeoutException e) {
            // Resultado incerto: o broker pode ter aceitado a mensagem e a confirmação não chegou a tempo.
            throw new FalhaPublicacaoException(
                    "confirmação do broker não chegou no prazo; o resultado é incerto", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalhaPublicacaoException("publicação interrompida", e);
        } catch (ExecutionException | AmqpException e) {
            throw new FalhaPublicacaoException("falha ao publicar no broker", e);
        }
    }
}
