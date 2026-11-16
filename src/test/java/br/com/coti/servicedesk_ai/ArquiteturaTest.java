package br.com.coti.servicedesk_ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Dependência aponta para dentro: domínio e aplicação não conhecem framework de IA, AMQP, web nem e-mail. */
class ArquiteturaTest {

    private static final Path BASE = Path.of("src/main/java/br/com/coti/servicedesk_ai");
    private static final List<String> PROIBIDOS = List.of(
            "org.springframework.ai", "com.openai", "org.springframework.amqp", "org.springframework.web",
            "org.springframework.mail", "org.springframework.http", "jakarta.servlet", "jakarta.mail");

    private static List<String> importsProibidosEm(String pacote) throws IOException {
        try (Stream<Path> arquivos = Files.walk(BASE.resolve(pacote))) {
            return arquivos
                    .filter(p -> p.toString().endsWith(".java"))
                    .flatMap(p -> linhas(p).filter(l -> l.startsWith("import ")).map(l -> p.getFileName() + ": " + l))
                    .filter(l -> PROIBIDOS.stream().anyMatch(l::contains))
                    .toList();
        }
    }

    private static Stream<String> linhas(Path arquivo) {
        try {
            return Files.readAllLines(arquivo).stream();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void dominioNaoDependeDeFramework() throws IOException {
        assertThat(importsProibidosEm("dominio")).isEmpty();
    }

    @Test
    void aplicacaoNaoDependeDeFramework() throws IOException {
        assertThat(importsProibidosEm("aplicacao")).isEmpty();
    }

    @Test
    void aplicacaoNaoDependeDeInfraestruturaNemDeWeb() throws IOException {
        try (Stream<Path> arquivos = Files.walk(BASE.resolve("aplicacao"))) {
            var violacoes = arquivos
                    .filter(p -> p.toString().endsWith(".java"))
                    .flatMap(p -> linhas(p).filter(l -> l.startsWith("import br.com.coti.servicedesk_ai.")
                            && (l.contains(".infraestrutura.") || l.contains(".web."))))
                    .toList();
            assertThat(violacoes).isEmpty();
        }
    }
}
