package br.com.coti.servicedesk_ai.web;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Janela fixa de um minuto por chamador, em memória. Vale para instância única; com várias instâncias o limite
 * real é multiplicado. Cada requisição aceita pode custar uma chamada ao modelo, por isso o teto.
 */
final class LimitadorTaxa {

    private record Janela(long minuto, int contagem) {}

    private static final int LIMITE_CHAVES = 10_000;

    private final int limitePorMinuto;
    private final Clock relogio;
    private final ConcurrentHashMap<String, Janela> janelas = new ConcurrentHashMap<>();

    LimitadorTaxa(int limitePorMinuto, Clock relogio) {
        this.limitePorMinuto = limitePorMinuto;
        this.relogio = relogio;
    }

    boolean permitir(String chamador) {
        long minuto = relogio.millis() / 60_000;
        if (janelas.size() > LIMITE_CHAVES) {
            janelas.entrySet().removeIf(e -> e.getValue().minuto() < minuto);
        }
        Janela atual = janelas.merge(
                chamador,
                new Janela(minuto, 1),
                (antiga, nova) -> antiga.minuto() == minuto ? new Janela(minuto, antiga.contagem() + 1) : nova);
        return atual.contagem() <= limitePorMinuto;
    }
}
