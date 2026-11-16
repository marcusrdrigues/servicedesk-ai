package br.com.coti.servicedesk_ai.integracao;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Lê a caixa de entrada do Mailpit pela API HTTP, para provar que o e-mail chegou. */
final class MailpitCliente {

    record Mensagem(String id, String de, String para, String assunto, String texto) {}

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newHttpClient();
    private final String base;

    MailpitCliente(String base) {
        this.base = base;
    }

    void apagarTudo() {
        enviar(HttpRequest.newBuilder(URI.create(base + "/api/v1/messages")).DELETE().build());
    }

    List<Mensagem> mensagens() {
        JsonNode lista = JSON.readTree(enviar(HttpRequest.newBuilder(URI.create(base + "/api/v1/messages")).GET().build()));
        List<Mensagem> resultado = new ArrayList<>();
        for (JsonNode resumo : lista.path("messages")) {
            String id = resumo.path("ID").asString();
            JsonNode detalhe = JSON.readTree(
                    enviar(HttpRequest.newBuilder(URI.create(base + "/api/v1/message/" + id)).GET().build()));
            resultado.add(new Mensagem(
                    id,
                    detalhe.path("From").path("Address").asString(),
                    detalhe.path("To").path(0).path("Address").asString(),
                    detalhe.path("Subject").asString(),
                    detalhe.path("Text").asString()));
        }
        return resultado;
    }

    private String enviar(HttpRequest requisicao) {
        try {
            return http.send(requisicao, HttpResponse.BodyHandlers.ofString()).body();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
