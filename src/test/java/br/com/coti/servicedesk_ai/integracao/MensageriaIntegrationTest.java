package br.com.coti.servicedesk_ai.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.coti.servicedesk_ai.aplicacao.AnalisadorChamadoPort;
import br.com.coti.servicedesk_ai.aplicacao.EnviadorEmailPort;
import br.com.coti.servicedesk_ai.aplicacao.PublicadorNotificacaoPort;
import br.com.coti.servicedesk_ai.dominio.AnaliseChamado;
import br.com.coti.servicedesk_ai.dominio.AnaliseIndisponivelException;
import br.com.coti.servicedesk_ai.dominio.Categoria;
import br.com.coti.servicedesk_ai.dominio.EmailSolicitado;
import br.com.coti.servicedesk_ai.dominio.FalhaEnvioEmailException;
import br.com.coti.servicedesk_ai.dominio.Prioridade;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/** Fluxo completo com RabbitMQ e Mailpit reais. Só o modelo é dublê; o envio de e-mail local vai ao Mailpit. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MensageriaIntegrationTest {

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("spring.rabbitmq.addresses", ContainersCompartilhados::urlRabbit);
        registro.add("spring.rabbitmq.listener.simple.auto-startup", () -> "true");
        registro.add("spring.rabbitmq.listener.simple.retry.initial-interval", () -> "200ms");
        registro.add("spring.mail.host", ContainersCompartilhados.MAILPIT::getHost);
        registro.add("spring.mail.port", () -> ContainersCompartilhados.MAILPIT.getMappedPort(1025));
    }

    private static final String CORPO_CHAMADO = """
            {"nome":"Maria","descricao":"A VPN caiu para todo o escritório desde as nove horas","autorizaEmail":true}""";

    @Autowired MockMvc mvc;
    @Autowired RabbitTemplate template;
    @Autowired RabbitAdmin admin;
    @Autowired PublicadorNotificacaoPort publicador;

    @MockitoBean AnalisadorChamadoPort analisador;
    @MockitoSpyBean EnviadorEmailPort enviador;

    @Value("${servicedesk.notificacao.exchange}") String exchange;
    @Value("${servicedesk.notificacao.routing-key}") String routingKey;
    @Value("${servicedesk.notificacao.fila}") String fila;
    @Value("${servicedesk.notificacao.fila-dlq}") String filaDlq;

    private final MailpitCliente mailpit = new MailpitCliente(ContainersCompartilhados.urlMailpitApi());

    @BeforeEach
    void limpar() {
        admin.purgeQueue(fila, false);
        admin.purgeQueue(filaDlq, false);
        mailpit.apagarTudo();
        org.mockito.Mockito.clearInvocations(enviador);
    }

    private static EmailSolicitado evento(UUID id) {
        return new EmailSolicitado(id, "suporte@exemplo.local", "[ServiceDesk] ALTA - REDE", "Corpo do chamado");
    }

    private long mensagensNaDlq() {
        var info = admin.getQueueInfo(filaDlq);
        return info == null ? 0 : info.getMessageCount();
    }

    private void modeloRecomendaEnvio() {
        when(analisador.analisar(any(), any()))
                .thenReturn(new AnaliseChamado(Categoria.REDE, Prioridade.ALTA, "VPN caiu", "Reiniciar o concentrador", true));
    }

    @Test
    void notificacaoPublicadaChegaAoMailpit() {
        UUID id = UUID.randomUUID();

        publicador.publicar(evento(id));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<MailpitCliente.Mensagem> caixa = mailpit.mensagens();
            assertThat(caixa).hasSize(1);
            assertThat(caixa.get(0).para()).isEqualTo("suporte@exemplo.local");
            assertThat(caixa.get(0).de()).isEqualTo("servicedesk@exemplo.local");
            assertThat(caixa.get(0).assunto()).isEqualTo("[ServiceDesk] ALTA - REDE");
            assertThat(caixa.get(0).texto()).contains("Corpo do chamado");
        });
    }

    @Test
    void chamadoAutorizadoPelaApiResponde202EOEmailChegaAoMailpit() throws Exception {
        modeloRecomendaEnvio();

        mvc.perform(post("/api/chamados")
                        .header("X-API-Key", "chave-de-teste-0123456789")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_CHAMADO))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.notificacao").value("ENFILEIRADO"));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<MailpitCliente.Mensagem> caixa = mailpit.mensagens();
            assertThat(caixa).hasSize(1);
            assertThat(caixa.get(0).para()).isEqualTo("suporte@exemplo.local");
            assertThat(caixa.get(0).texto()).contains("VPN caiu").contains("Solicitante: Maria");
        });
    }

    @Test
    void semAutorizacaoNenhumaMensagemEPublicadaNemEmailEnviado() throws Exception {
        modeloRecomendaEnvio();

        mvc.perform(post("/api/chamados")
                        .header("X-API-Key", "chave-de-teste-0123456789")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_CHAMADO.replace("\"autorizaEmail\":true", "\"autorizaEmail\":false")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificacao").value("NAO_SOLICITADO"));

        Thread.sleep(1500);
        verify(enviador, never()).enviar(any());
        assertThat(mailpit.mensagens()).isEmpty();
    }

    @Test
    void falhaDosDoisModelosResponde503SemPublicarMensagem() throws Exception {
        when(analisador.analisar(any(), any())).thenThrow(new AnaliseIndisponivelException("nenhum provedor"));

        mvc.perform(post("/api/chamados")
                        .header("X-API-Key", "chave-de-teste-0123456789")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_CHAMADO))
                .andExpect(status().isServiceUnavailable());

        Thread.sleep(1500);
        verify(enviador, never()).enviar(any());
        assertThat(mailpit.mensagens()).isEmpty();
        assertThat(mensagensNaDlq()).isZero();
    }

    @Test
    void falhaTransitoriaDeEnvioFazRetryEOEmailSaiNaSegundaTentativa() {
        UUID id = UUID.randomUUID();
        doThrow(new FalhaEnvioEmailException("smtp fora", null)).doCallRealMethod().when(enviador).enviar(any());

        publicador.publicar(evento(id));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(mailpit.mensagens()).hasSize(1));
        verify(enviador, times(2)).enviar(any());
        assertThat(mensagensNaDlq()).isZero();
    }

    @Test
    void tresFalhasDeEnvioEncaminhamAMensagemParaADlqComOMesmoEventoId() {
        UUID id = UUID.randomUUID();
        doThrow(new FalhaEnvioEmailException("smtp fora", null)).when(enviador).enviar(any());

        publicador.publicar(evento(id));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(mensagensNaDlq()).isEqualTo(1));
        ArgumentCaptor<EmailSolicitado> tentativas = ArgumentCaptor.forClass(EmailSolicitado.class);
        verify(enviador, times(3)).enviar(tentativas.capture());
        assertThat(tentativas.getAllValues()).extracting(EmailSolicitado::eventoId).containsOnly(id);
        assertThat(mailpit.mensagens()).isEmpty();

        Message naDlq = template.receive(filaDlq, 5000);
        assertThat(naDlq).isNotNull();
        assertThat(naDlq.getMessageProperties().getMessageId()).isEqualTo(id.toString());
        assertThat(admin.getQueueInfo(fila).getMessageCount()).isZero();
    }

    @Test
    void mensagemMalformadaVaiParaADlqSemEnviarEmail() {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message lixo = MessageBuilder.withBody("isto não é json".getBytes(StandardCharsets.UTF_8))
                .andProperties(propriedades)
                .build();

        template.send(exchange, routingKey, lixo);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(mensagensNaDlq()).isEqualTo(1));
        verify(enviador, never()).enviar(any());
    }

    @Test
    void redeliveryDoMesmoEventoNaoGeraSegundoEmail() {
        UUID id = UUID.randomUUID();

        publicador.publicar(evento(id));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(mailpit.mensagens()).hasSize(1));
        publicador.publicar(evento(id));

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(mailpit.mensagens()).hasSize(1));
        verify(enviador, times(1)).enviar(any());
    }
}
