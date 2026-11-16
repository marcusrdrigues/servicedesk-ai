package br.com.coti.servicedesk_ai.aplicacao;

import br.com.coti.servicedesk_ai.dominio.EmailSolicitado;

public interface PublicadorNotificacaoPort {

    /**
     * Retorna somente após a confirmação positiva do broker; do contrário lança FalhaPublicacaoException.
     * Confirmação do broker não é confirmação de envio nem de entrega do e-mail.
     */
    void publicar(EmailSolicitado evento);
}
