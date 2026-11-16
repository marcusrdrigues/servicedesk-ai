package br.com.coti.servicedesk_ai.infraestrutura.config;

import br.com.coti.servicedesk_ai.aplicacao.AnalisadorChamadoPort;
import br.com.coti.servicedesk_ai.aplicacao.AnalisarChamadoService;
import br.com.coti.servicedesk_ai.aplicacao.EmailService;
import br.com.coti.servicedesk_ai.aplicacao.EnviadorEmailPort;
import br.com.coti.servicedesk_ai.aplicacao.MontadorEmail;
import br.com.coti.servicedesk_ai.aplicacao.PublicadorNotificacaoPort;
import br.com.coti.servicedesk_ai.aplicacao.RegistroNotificacoesProcessadas;
import br.com.coti.servicedesk_ai.infraestrutura.email.ResendEmailSender;
import br.com.coti.servicedesk_ai.infraestrutura.email.SmtpEmailSender;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;

/** Liga os casos de uso às portas. O destinatário e o remetente vêm só da configuração do servidor. */
@Configuration
@EnableConfigurationProperties(EmailProperties.class)
class AplicacaoConfig {

    private static final int CAPACIDADE_DEDUPLICACAO = 10_000;

    @Bean
    MontadorEmail montadorEmail(ServicedeskProperties propriedades) {
        ServicedeskProperties.Notificacao n = propriedades.notificacao();
        return new MontadorEmail(n.destinatario(), n.prefixoAssunto());
    }

    @Bean
    AnalisarChamadoService analisarChamadoService(
            AnalisadorChamadoPort analisador,
            PublicadorNotificacaoPort publicador,
            MontadorEmail montadorEmail,
            IaProperties ia,
            Clock relogio) {
        return new AnalisarChamadoService(analisador, publicador, montadorEmail, ia.orcamentoTotal(), relogio);
    }

    @Bean
    RegistroNotificacoesProcessadas registroNotificacoesProcessadas() {
        return new RegistroNotificacoesProcessadas(CAPACIDADE_DEDUPLICACAO);
    }

    @Bean
    EmailService emailService(EnviadorEmailPort enviador, RegistroNotificacoesProcessadas registro) {
        return new EmailService(enviador, registro);
    }

    @Bean
    @ConditionalOnProperty(name = "servicedesk.email.modo", havingValue = "smtp", matchIfMissing = true)
    EnviadorEmailPort enviadorSmtp(JavaMailSender mailSender, ServicedeskProperties propriedades) {
        return new SmtpEmailSender(mailSender, propriedades.notificacao().remetente());
    }

    @Bean
    @ConditionalOnProperty(name = "servicedesk.email.modo", havingValue = "resend")
    EnviadorEmailPort enviadorResend(EmailProperties email, ServicedeskProperties propriedades) {
        EmailProperties.Resend resend = email.resend();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(resend.timeout()).build());
        fabrica.setReadTimeout(resend.timeout());
        RestClient cliente = RestClient.builder()
                .baseUrl(resend.baseUrl())
                .requestFactory(fabrica)
                .defaultHeader("Authorization", "Bearer " + resend.apiKey())
                .build();
        return new ResendEmailSender(cliente, propriedades.notificacao().remetente());
    }
}
