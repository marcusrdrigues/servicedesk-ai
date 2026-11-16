package br.com.coti.servicedesk_ai.web;

import br.com.coti.servicedesk_ai.aplicacao.ResultadoChamado;
import br.com.coti.servicedesk_ai.dominio.Categoria;
import br.com.coti.servicedesk_ai.dominio.Prioridade;
import br.com.coti.servicedesk_ai.dominio.StatusNotificacao;

/** notificacao: ENFILEIRADO quer dizer aceito pela fila, não entregue. Não existe ENVIADO. */
public record ChamadoResponse(
        Categoria categoria,
        Prioridade prioridade,
        String resumo,
        String orientacao,
        boolean enviarEmail,
        StatusNotificacao notificacao) {

    static ChamadoResponse de(ResultadoChamado resultado) {
        var a = resultado.analise();
        return new ChamadoResponse(
                a.categoria(), a.prioridade(), a.resumo(), a.orientacao(), a.enviarEmail(), resultado.notificacao());
    }
}
