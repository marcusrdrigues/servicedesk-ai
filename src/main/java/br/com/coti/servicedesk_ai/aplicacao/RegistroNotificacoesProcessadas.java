package br.com.coti.servicedesk_ai.aplicacao;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Deduplicação em memória. Vale para uma única instância e some no restart; para produção com várias
 * instâncias use tabela com chave única. Limitado em tamanho para não crescer sem teto.
 */
public final class RegistroNotificacoesProcessadas {

    private final Set<UUID> vistos;

    public RegistroNotificacoesProcessadas(int capacidade) {
        this.vistos = Collections.newSetFromMap(Collections.synchronizedMap(new LinkedHashMap<UUID, Boolean>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
                return size() > capacidade;
            }
        }));
    }

    public boolean jaProcessada(UUID id) {
        return vistos.contains(id);
    }

    public void registrar(UUID id) {
        vistos.add(id);
    }
}
