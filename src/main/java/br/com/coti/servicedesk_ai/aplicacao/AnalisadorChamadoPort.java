package br.com.coti.servicedesk_ai.aplicacao;

import br.com.coti.servicedesk_ai.dominio.AnaliseChamado;
import br.com.coti.servicedesk_ai.dominio.Chamado;
import java.time.Instant;

/** Fronteira com o modelo. Lança FalhaProvedorModeloException quando não entrega análise válida. */
public interface AnalisadorChamadoPort {

    AnaliseChamado analisar(Chamado chamado, Instant limite);

    String provedor();
}
