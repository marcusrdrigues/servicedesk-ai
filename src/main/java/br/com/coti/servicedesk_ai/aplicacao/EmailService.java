package br.com.coti.servicedesk_ai.aplicacao;

import br.com.coti.servicedesk_ai.dominio.EmailSolicitado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumo: a entrega é at-least-once, então a redelivery de um evento já enviado não gera segundo e-mail.
 * A deduplicação é em memória (instância única, some no restart); não é garantia de exatamente uma vez.
 */
public final class EmailService {

    private static final Logger LOG = LoggerFactory.getLogger(EmailService.class);

    private final EnviadorEmailPort enviador;
    private final RegistroNotificacoesProcessadas registro;

    public EmailService(EnviadorEmailPort enviador, RegistroNotificacoesProcessadas registro) {
        this.enviador = enviador;
        this.registro = registro;
    }

    public void enviar(EmailSolicitado evento) {
        if (registro.jaProcessada(evento.eventoId())) {
            LOG.info("eventoId={} resultado=duplicado", evento.eventoId());
            return;
        }
        enviador.enviar(evento);
        registro.registrar(evento.eventoId());
    }
}
