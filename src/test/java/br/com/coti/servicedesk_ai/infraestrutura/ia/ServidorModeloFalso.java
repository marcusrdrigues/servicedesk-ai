package br.com.coti.servicedesk_ai.infraestrutura.ia;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servidor HTTP falso no formato de chat completions. Nenhum teste chama o provedor real.
 * As respostas saem na ordem em que foram enfileiradas; a última se repete.
 */
public final class ServidorModeloFalso implements AutoCloseable {

    public record Resposta(int status, String corpo, long atrasoMs) {}

    private final HttpServer servidor;
    private final List<Resposta> respostas = new CopyOnWriteArrayList<>();
    private final List<String> requisicoes = new CopyOnWriteArrayList<>();
    private final AtomicInteger chamadas = new AtomicInteger();

    public ServidorModeloFalso() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        servidor.createContext("/", troca -> {
            requisicoes.add(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int indice = Math.min(chamadas.getAndIncrement(), respostas.size() - 1);
            Resposta resposta = respostas.get(indice);
            try {
                Thread.sleep(resposta.atrasoMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = resposta.corpo().getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            try {
                troca.sendResponseHeaders(resposta.status(), bytes.length);
                troca.getResponseBody().write(bytes);
            } catch (IOException ignorado) {
                // o cliente desistiu (timeout): é o que o teste provoca
            } finally {
                troca.close();
            }
        });
        servidor.start();
    }

    public ServidorModeloFalso responder(int status, String corpo) {
        respostas.add(new Resposta(status, corpo, 0));
        return this;
    }

    public ServidorModeloFalso responderComAtraso(int status, String corpo, long atrasoMs) {
        respostas.add(new Resposta(status, corpo, atrasoMs));
        return this;
    }

    /** Resposta 200 de chat completions cujo conteúdo da mensagem é o texto informado. */
    public ServidorModeloFalso responderConteudo(String conteudo) {
        return responder(200, completar(conteudo));
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1";
    }

    public int chamadas() {
        return chamadas.get();
    }

    public List<String> requisicoes() {
        return requisicoes;
    }

    static String completar(String conteudo) {
        String escapado = conteudo.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "{\"id\":\"cmpl-1\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"modelo-falso\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"" + escapado + "\"},"
                + "\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}";
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
