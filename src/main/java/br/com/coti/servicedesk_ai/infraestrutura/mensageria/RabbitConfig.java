package br.com.coti.servicedesk_ai.infraestrutura.mensageria;

import br.com.coti.servicedesk_ai.infraestrutura.config.ServicedeskProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia explícita: exchange e filas duráveis, bindings declarados e a fila principal apontando para a
 * dead-letter exchange. Quem rejeita sem requeue (tentativas esgotadas ou mensagem malformada) cai na DLQ
 * por configuração do broker.
 */
@Configuration
class RabbitConfig {

    @Bean
    Declarables topologiaNotificacao(ServicedeskProperties propriedades) {
        ServicedeskProperties.Notificacao n = propriedades.notificacao();
        DirectExchange exchange = new DirectExchange(n.exchange(), true, false);
        DirectExchange deadLetterExchange = new DirectExchange(n.exchangeDlq(), true, false);
        Queue fila = QueueBuilder.durable(n.fila())
                .deadLetterExchange(n.exchangeDlq())
                .deadLetterRoutingKey(n.routingKeyDlq())
                .build();
        Queue filaDlq = QueueBuilder.durable(n.filaDlq()).build();
        Binding bindingFila = BindingBuilder.bind(fila).to(exchange).with(n.routingKey());
        Binding bindingDlq = BindingBuilder.bind(filaDlq).to(deadLetterExchange).with(n.routingKeyDlq());
        return new Declarables(exchange, deadLetterExchange, fila, filaDlq, bindingFila, bindingDlq);
    }

    /** JSON nas duas pontas. O consumidor infere o tipo pelo parâmetro do método, não por cabeçalho da mensagem. */
    @Bean
    MessageConverter conversorMensagem() {
        return new JacksonJsonMessageConverter();
    }
}
