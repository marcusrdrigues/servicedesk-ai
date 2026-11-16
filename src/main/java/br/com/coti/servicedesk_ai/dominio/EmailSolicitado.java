package br.com.coti.servicedesk_ai.dominio;

import java.util.UUID;

/**
 * Evento publicado na fila. O destinatário é sempre o EMAIL_TO do servidor; o modelo nunca o escolhe.
 * O eventoId é o mesmo em todas as tentativas de processamento da mensagem.
 */
public record EmailSolicitado(UUID eventoId, String destinatario, String assunto, String corpo) {}
