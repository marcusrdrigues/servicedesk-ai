package br.com.coti.servicedesk_ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.coti.servicedesk_ai.aplicacao.AnalisadorChamadoPort;
import br.com.coti.servicedesk_ai.dominio.AnaliseChamado;
import br.com.coti.servicedesk_ai.dominio.AnaliseIndisponivelException;
import br.com.coti.servicedesk_ai.dominio.Chamado;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Eval com o MODELO REAL. Fica fora do CI: só roda com EVAL_REAL=true e as chaves reais no ambiente (ou no .env).
 * Rode a cada mudança de prompt, modelo ou parâmetro e compare com a nota anterior.
 *
 * Correção verificada por código (igualdade de categoria, prioridade dentro do conjunto aceito, ausência de
 * texto proibido na resposta), sem juiz por LLM. A nota mínima é inicial e deve ser revista com o uso real.
 * Cada falha de IA corrigida em produção vira um caso novo em casos.json.
 *
 * Como rodar:  EVAL_REAL=true ./mvnw test -Dtest=EvalModeloRealManualTest
 */
@EnabledIfEnvironmentVariable(named = "EVAL_REAL", matches = "true")
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class EvalModeloRealManualTest {

    private static final double NOTA_MINIMA = 0.80;

    @Autowired
    AnalisadorChamadoPort analisador;

    @Test
    void notaDoEvalNaoFicaAbaixoDoMinimo() throws Exception {
        JsonNode casos;
        try (InputStream entrada = getClass().getResourceAsStream("/eval/casos.json")) {
            casos = JsonMapper.builder().build().readTree(entrada);
        }

        List<String> falhas = new ArrayList<>();
        int acertos = 0;
        for (JsonNode caso : casos) {
            String id = caso.path("id").asString();
            try {
                AnaliseChamado analise = analisador.analisar(
                        new Chamado(caso.path("nome").asString(), caso.path("descricao").asString(), false),
                        Instant.now().plusSeconds(60));
                String problema = avaliar(caso, analise);
                if (problema == null) {
                    acertos++;
                } else {
                    falhas.add(id + ": " + problema);
                }
            } catch (AnaliseIndisponivelException e) {
                falhas.add(id + ": análise indisponível");
            }
        }

        double nota = (double) acertos / casos.size();
        System.out.printf("EVAL nota=%.2f (%d/%d) minima=%.2f%n", nota, acertos, casos.size(), NOTA_MINIMA);
        falhas.forEach(f -> System.out.println("EVAL falha " + f));
        assertThat(nota).as("nota do eval; falhas: %s", falhas).isGreaterThanOrEqualTo(NOTA_MINIMA);
    }

    private static String avaliar(JsonNode caso, AnaliseChamado analise) {
        String esperada = caso.path("categoria").asString();
        if (!analise.categoria().name().equals(esperada)) {
            return "categoria " + analise.categoria() + " (esperada " + esperada + ")";
        }
        boolean prioridadeAceita = false;
        for (JsonNode p : caso.path("prioridades")) {
            prioridadeAceita |= p.asString().equals(analise.prioridade().name());
        }
        if (!prioridadeAceita) {
            return "prioridade " + analise.prioridade() + " fora do conjunto aceito";
        }
        String texto = (analise.resumo() + " " + analise.orientacao()).toLowerCase();
        for (JsonNode proibido : caso.path("naoDeveConter")) {
            if (texto.contains(proibido.asString().toLowerCase())) {
                return "resposta contém texto proibido: " + proibido.asString();
            }
        }
        return null;
    }
}
