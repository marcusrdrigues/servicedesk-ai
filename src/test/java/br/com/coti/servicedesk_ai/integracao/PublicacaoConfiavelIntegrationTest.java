package br.com.coti.servicedesk_ai.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.coti.servicedesk_ai.dominio.EmailSolicitado;
import br.com.coti.servicedesk_ai.dominio.FalhaPublicacaoException;
import br.com.coti.servicedesk_ai.infraestrutura.config.ServicedeskProperties;
import br.com.coti.servicedesk_ai.infraestrutura.mensageria.NotificacaoPublisher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Publicação com broker real e SEM consumidor (auto-startup desligado), para inspecionar a mensagem na fila.
 * Usa nomes de fila próprios para não disputar mensagem com o contexto do outro teste de integração.
 */
@SpringBootTest(properties = {
    "servicedesk.notificacao.exchange=servicedesk.events.pub",
    "servicedesk.notificacao.fila=servicedesk.email.pub",
    "servicedesk.notificacao.exchange-dlq=servicedesk.dlx.pub",
    "servicedesk.notificacao.fila-dlq=servicedesk.email.dlq.pub"
})
@ActiveProfiles("test")
class PublicacaoConfiavelIntegrationTest {

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("spring.rabbitmq.addresses", ContainersCompartilhados::urlRabbit);
    }

    @Autowired NotificacaoPublisher publisher;
    @Autowired RabbitTemplate template;
    @Autowired RabbitAdmin admin;
    @Autowired ServicedeskProperties propriedades;

    @BeforeEach
    void limpar() {
        template.execute(canal -> null); // garante conexão e, com ela, a declaração da topologia
        admin.purgeQueue(propriedades.notificacao().fila(), false);
    }

    private static EmailSolicitado evento(UUID id) {
        return new EmailSolicitado(id, "suporte@exemplo.local", "[ServiceDesk] ALTA - REDE", "Corpo do chamado");
    }

    private ServicedeskProperties comNotificacao(
            String exchange, String routingKey, Duration timeoutConfirmacao) {
        var n = propriedades.notificacao();
        return new ServicedeskProperties(
                propriedades.apiKey(),
                propriedades.corsOrigem(),
                propriedades.limiteRequisicoesPorMinuto(),
                new ServicedeskProperties.Notificacao(
                        n.destinatario(), n.remetente(), n.prefixoAssunto(), exchange, routingKey, n.fila(),
                        n.exchangeDlq(), n.routingKeyDlq(), n.filaDlq(), timeoutConfirmacao));
    }

    @Test
    void mensagemPublicadaEPersistenteComMessageIdIgualAoEventoId() {
        UUID id = UUID.randomUUID();

        publisher.publicar(evento(id));

        Message mensagem = template.receive(propriedades.notificacao().fila(), 5000);
        assertThat(mensagem).isNotNull();
        // em mensagem recebida, o modo de entrega que veio do broker está em getReceivedDeliveryMode()
        assertThat(mensagem.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(mensagem.getMessageProperties().getMessageId()).isEqualTo(id.toString());
        assertThat(mensagem.getMessageProperties().getContentType()).isEqualTo("application/json");
        String corpo = new String(mensagem.getBody(), StandardCharsets.UTF_8);
        assertThat(corpo).contains(id.toString()).contains("suporte@exemplo.local");
    }

    @Test
    void filasSaoDuraveisEAFilaPrincipalApontaParaADeadLetterExchange() throws IOException, InterruptedException {
        var resultado = ContainersCompartilhados.RABBIT.execInContainer(
                "rabbitmqctl", "list_queues", "name", "durable", "arguments");
        String saida = resultado.getStdout();

        assertThat(linhaDa(saida, "servicedesk.email.pub")).contains("true").contains("x-dead-letter-exchange")
                .contains("servicedesk.dlx.pub").contains("x-dead-letter-routing-key").contains("email.falhou");
        assertThat(linhaDa(saida, "servicedesk.email.dlq.pub")).contains("true");
    }

    private static String linhaDa(String saida, String fila) {
        return saida.lines().filter(l -> l.startsWith(fila + "\t")).findFirst().orElse("");
    }

    @Test
    void mensagemSemRotaVoltaPeloMandatoryEViraFalha() {
        var semRota = new NotificacaoPublisher(
                template, comNotificacao(propriedades.notificacao().exchange(), "rota.que.nao.existe", Duration.ofSeconds(5)));

        assertThatThrownBy(() -> semRota.publicar(evento(UUID.randomUUID())))
                .isInstanceOf(FalhaPublicacaoException.class)
                .hasMessageContaining("não roteou");
    }

    @Test
    void exchangeInexistenteViraFalha() {
        var semExchange = new NotificacaoPublisher(
                template, comNotificacao("exchange.que.nao.existe", "email.solicitado", Duration.ofSeconds(5)));

        assertThatThrownBy(() -> semExchange.publicar(evento(UUID.randomUUID())))
                .isInstanceOf(FalhaPublicacaoException.class);
    }

    @Test
    void confirmacaoQueNaoChegaNoPrazoViraFalhaComResultadoIncerto() {
        var semPrazo = new NotificacaoPublisher(
                template, comNotificacao(propriedades.notificacao().exchange(), propriedades.notificacao().routingKey(), Duration.ZERO));

        assertThatThrownBy(() -> semPrazo.publicar(evento(UUID.randomUUID())))
                .isInstanceOf(FalhaPublicacaoException.class)
                .hasMessageContaining("incerto");
    }

    @Test
    void brokerInalcancavelViraFalhaSemVazarCredencial() {
        var conexao = new CachingConnectionFactory("127.0.0.1", 1);
        conexao.setConnectionTimeout(500);
        conexao.setPublisherConfirmType(ConfirmType.CORRELATED);
        conexao.setPublisherReturns(true);
        var templateSemBroker = new RabbitTemplate(conexao);
        templateSemBroker.setMandatory(true);
        templateSemBroker.setMessageConverter(new org.springframework.amqp.support.converter.JacksonJsonMessageConverter());
        var publisherSemBroker = new NotificacaoPublisher(templateSemBroker, propriedades);

        assertThatThrownBy(() -> publisherSemBroker.publicar(evento(UUID.randomUUID())))
                .isInstanceOf(FalhaPublicacaoException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(ContainersCompartilhados.SENHA));
        conexao.destroy();
    }
}
