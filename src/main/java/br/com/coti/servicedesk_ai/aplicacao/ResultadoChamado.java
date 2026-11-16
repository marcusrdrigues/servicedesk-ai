package br.com.coti.servicedesk_ai.aplicacao;

import br.com.coti.servicedesk_ai.dominio.AnaliseChamado;
import br.com.coti.servicedesk_ai.dominio.StatusNotificacao;

public record ResultadoChamado(AnaliseChamado analise, StatusNotificacao notificacao) {}
