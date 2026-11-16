package br.com.coti.servicedesk_ai.infraestrutura.mensageria;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import br.com.coti.servicedesk_ai.dominio.EmailSolicitado;
import br.com.coti.servicedesk_ai.dominio.FalhaPublicacaoException;
import br.com.coti.servicedesk_ai.infraestrutura.config.ServicedeskProperties;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** Os ramos do broker que o broker real não deixa provocar com facilidade: nack e ACK sem retorno. */
class NotificacaoPublisherTest {

    private final EmailSolicitado evento =
            new EmailSolicitado(UUID.randomUUID(), "suporte@exemplo.local", "assunto", "corpo");

    private final ServicedeskProperties propriedades = new ServicedeskProperties(
            "chave", null, 10,
            new ServicedeskProperties.Notificacao(
                    "suporte@exemplo.local", "servicedesk@exemplo.local", "[ServiceDesk]",
                    "servicedesk.events", "email.solicitado", "servicedesk.email",
                    "servicedesk.dlx", "email.falhou", "servicedesk.email.dlq", Duration.ofMillis(500)));

    /** RabbitTemplate falso que responde a confirmação do jeito que o teste pedir. */
    private NotificacaoPublisher publisherComConfirmacao(CorrelationData.Confirm confirmacao) {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocacao -> {
                    CorrelationData correlacao = invocacao.getArgument(4);
                    correlacao.getFuture().complete(confirmacao);
                    return null;
                })
                .when(template)
                .convertAndSend(eq("servicedesk.events"), eq("email.solicitado"), any(Object.class),
                        any(MessagePostProcessor.class), any(CorrelationData.class));
        return new NotificacaoPublisher(template, propriedades);
    }

    @Test
    void nackDoBrokerViraFalhaDePublicacao() {
        var publisher = publisherComConfirmacao(new CorrelationData.Confirm(false, "sem espaço"));

        assertThatThrownBy(() -> publisher.publicar(evento))
                .isInstanceOf(FalhaPublicacaoException.class)
                .hasMessageContaining("recusou");
    }

    @Test
    void ackSemMensagemDevolvidaConcluiSemExcecao() {
        var publisher = publisherComConfirmacao(new CorrelationData.Confirm(true, null));

        assertThatCode(() -> publisher.publicar(evento)).doesNotThrowAnyException();
    }

    @Test
    void semConfirmacaoNoPrazoOResultadoEIncerto() {
        RabbitTemplate template = mock(RabbitTemplate.class); // nunca completa a confirmação
        var publisher = new NotificacaoPublisher(template, propriedades);

        assertThatThrownBy(() -> publisher.publicar(evento))
                .isInstanceOf(FalhaPublicacaoException.class)
                .hasMessageContaining("incerto");
    }
}
