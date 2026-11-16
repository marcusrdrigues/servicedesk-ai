package br.com.coti.servicedesk_ai.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.MDC;

/** Escreve ProblemDetail nos filtros, onde o handler global não alcança. Só textos fixos, nada do cliente. */
final class RespostaErro {

    static final String MDC_REQUISICAO = "requestId";

    private RespostaErro() {}

    static void escrever(HttpServletResponse resposta, int status, String titulo, String detalhe) throws IOException {
        String id = MDC.get(MDC_REQUISICAO);
        String json = "{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\",\"requestId\":\"%s\"}"
                .formatted(titulo, status, detalhe, id == null ? "" : id);
        resposta.setStatus(status);
        resposta.setContentType("application/problem+json");
        resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resposta.getWriter().write(json);
    }
}
