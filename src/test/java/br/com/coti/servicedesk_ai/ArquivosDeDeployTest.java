package br.com.coti.servicedesk_ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Parse real dos arquivos de publicação: sintaxe, plano gratuito, deploy depois da CI e nenhum segredo com valor. */
class ArquivosDeDeployTest {

    private static final List<String> SEGREDOS_E_AJUSTES_DO_PAINEL = List.of(
            "OPENAI_API_KEY", "OPENAI_CHAT_MODEL", "GEMINI_API_KEY", "GEMINI_CHAT_MODEL", "SERVICEDESK_API_KEY",
            "DB_URL", "DB_USERNAME", "DB_PASSWORD", "RABBITMQ_URL", "RESEND_API_KEY", "EMAIL_FROM", "EMAIL_TO");

    private static Map<String, Object> ler(String arquivo) throws IOException {
        try (Reader leitor = Files.newBufferedReader(Path.of(arquivo))) {
            return new Yaml().load(leitor);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> servico() throws IOException {
        return ((List<Map<String, Object>>) ler("render.yaml").get("services")).get(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void renderUsaDockerNoPlanoGratuitoComSaudeEDeployAposACi() throws IOException {
        Map<String, Object> servico = servico();

        assertThat(servico.get("type")).isEqualTo("web");
        assertThat(servico.get("runtime")).isEqualTo("docker");
        assertThat(servico.get("plan")).isEqualTo("free");
        assertThat(servico.get("healthCheckPath")).isEqualTo("/actuator/health");
        assertThat(servico.get("autoDeployTrigger")).isEqualTo("checksPass");
        assertThat(((List<Map<String, Object>>) ler("render.yaml").get("services"))).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void nenhumSegredoTemValorNoArquivoEPerfilCloudEOUnicoValorFixo() throws IOException {
        List<Map<String, Object>> variaveis = (List<Map<String, Object>>) servico().get("envVars");

        Map<String, Object> comValor = variaveis.stream()
                .filter(v -> v.containsKey("value"))
                .collect(java.util.stream.Collectors.toMap(v -> (String) v.get("key"), v -> v.get("value")));
        assertThat(comValor).containsExactly(Map.entry("SPRING_PROFILES_ACTIVE", "cloud"));

        List<String> pedidasNoPainel = variaveis.stream()
                .filter(v -> Boolean.FALSE.equals(v.get("sync")))
                .map(v -> (String) v.get("key"))
                .toList();
        assertThat(pedidasNoPainel).containsExactlyInAnyOrderElementsOf(SEGREDOS_E_AJUSTES_DO_PAINEL);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ciTemOsDoisJobsEFixaTodasAsAcoesPeloShaDoCommit() throws IOException {
        Map<String, Object> ci = ler(".github/workflows/ci.yml");
        Map<String, Object> jobs = (Map<String, Object>) ci.get("jobs");

        assertThat(jobs).containsKeys("backend", "portal");
        assertThat((Map<String, Object>) ci.get("permissions")).containsEntry("contents", "read");

        String bruto = Files.readString(Path.of(".github/workflows/ci.yml"));
        java.util.regex.Matcher usos = java.util.regex.Pattern.compile("uses:\\s*(\\S+)").matcher(bruto);
        int total = 0;
        while (usos.find()) {
            total++;
            assertThat(usos.group(1)).matches(".+@[0-9a-f]{40}");
        }
        assertThat(total).isGreaterThanOrEqualTo(3);
        assertThat(bruto).contains("./mvnw -B verify").contains("docker build");
    }

    @Test
    void ciNaoTemTokenNemSegredoEmTextoClaro() throws IOException {
        String ci = Files.readString(Path.of(".github/workflows/ci.yml"));

        assertThat(ci).doesNotContainPattern("(?i)(token|password|api[_-]?key)\\s*:\\s*[A-Za-z0-9]{12,}");
    }

    @Test
    void dockerfileUsaUsuarioSemPrivilegioEImagemEmDoisEstagios() throws IOException {
        String dockerfile = Files.readString(Path.of("Dockerfile"));

        assertThat(dockerfile).contains("AS build").contains("USER app").contains("-XX:MaxRAMPercentage");
        assertThat(dockerfile.lines().filter(l -> l.startsWith("FROM ")).count()).isEqualTo(2);
    }
}
