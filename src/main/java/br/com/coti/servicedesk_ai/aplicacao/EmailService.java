package br.com.coti.servicedesk_ai.aplicacao;

import br.com.coti.servicedesk_ai.dominio.EmailSolicitado;
import br.com.coti.servicedesk_ai.dominio.EstadoNotificacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumo: a entrega é at-least-once, então a redelivery de um evento já enviado não gera segundo e-mail.
 * A deduplicação é em memória (instância única, some no restart); não é garantia de exatamente uma vez.
 * A auditoria registra o aceite do provedor, nunca a entrega ao destinatário.
 */
public final class EmailService {

    private static final Logger LOG = LoggerFactory.getLogger(EmailService.class);

    private final EnviadorEmailPort enviador;
    private final RegistroNotificacoesProcessadas registro;
    private final AuditoriaPort auditoria;

    public EmailService(
            EnviadorEmailPort enviador, RegistroNotificacoesProcessadas registro, AuditoriaPort auditoria) {
        this.enviador = enviador;
        this.registro = registro;
        this.auditoria = auditoria;
    }

    public void enviar(EmailSolicitado evento) {
        if (registro.jaProcessada(evento.eventoId())) {
            LOG.info("eventoId={} resultado=duplicado", evento.eventoId());
            return;
        }
        try {
            enviador.enviar(evento);
        } catch (RuntimeException falha) {
            auditoria.registrarEnvio(evento.eventoId(), EstadoNotificacao.FALHA_ENVIO, ErroSanitizado.de(falha));
            throw falha;
        }
        registro.registrar(evento.eventoId());
        auditoria.registrarEnvio(evento.eventoId(), EstadoNotificacao.ACEITO_PELO_PROVEDOR, null);
    }
}
