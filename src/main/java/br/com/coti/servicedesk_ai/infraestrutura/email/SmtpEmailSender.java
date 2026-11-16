package br.com.coti.servicedesk_ai.infraestrutura.email;

import br.com.coti.servicedesk_ai.aplicacao.EnviadorEmailPort;
import br.com.coti.servicedesk_ai.dominio.EmailSolicitado;
import br.com.coti.servicedesk_ai.dominio.FalhaEnvioEmailException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Modo smtp: entrega ao Mailpit local. Os timeouts de conexão e envio estão em spring.mail.properties. */
public final class SmtpEmailSender implements EnviadorEmailPort {

    private final JavaMailSender mailSender;
    private final String remetente;

    public SmtpEmailSender(JavaMailSender mailSender, String remetente) {
        this.mailSender = mailSender;
        this.remetente = remetente;
    }

    @Override
    public void enviar(EmailSolicitado evento) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(remetente);
        mensagem.setTo(evento.destinatario());
        mensagem.setSubject(evento.assunto());
        mensagem.setText(evento.corpo());
        try {
            mailSender.send(mensagem);
        } catch (MailException e) {
            throw new FalhaEnvioEmailException("falha ao entregar ao servidor SMTP", e);
        }
    }
}
