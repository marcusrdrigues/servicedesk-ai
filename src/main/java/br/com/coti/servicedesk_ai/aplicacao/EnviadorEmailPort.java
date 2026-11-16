package br.com.coti.servicedesk_ai.aplicacao;

import br.com.coti.servicedesk_ai.dominio.EmailSolicitado;

public interface EnviadorEmailPort {

    /** Lança FalhaEnvioEmailException quando o provedor recusa ou não responde, para permitir retry. */
    void enviar(EmailSolicitado evento);
}
