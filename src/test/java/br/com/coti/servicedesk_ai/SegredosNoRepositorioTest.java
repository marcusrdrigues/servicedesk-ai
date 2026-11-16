package br.com.coti.servicedesk_ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Nenhum arquivo versionável contém chave real, e o .env fica fora do controle de versão. */
class SegredosNoRepositorioTest {

    private static final List<String> PASTAS_IGNORADAS = List.of(
            "target", ".git", ".idea", ".vscode", ".claude", ".agents", ".mvn", "node_modules");

    private static final List<Pattern> PADROES_DE_CHAVE = List.of(
            Pattern.compile("sk-[A-Za-z0-9_-]{20,}"), // OpenAI
            Pattern.compile("AIza[0-9A-Za-z_-]{30,}"), // Google
            Pattern.compile("re_[A-Za-z0-9]{20,}"), // Resend
            Pattern.compile("amqps?://[^:\\s/]+:(?!troque|<|\\$\\{|%|senhaDeTeste|senhaSecreta|teste@|SenhaLocal|senha@|SENHA@)[^@\\s]{4,}@"));

    private static boolean versionavel(Path arquivo) {
        for (Path parte : Path.of(".").relativize(arquivo)) {
            String nome = parte.toString();
            if (PASTAS_IGNORADAS.contains(nome)) {
                return false;
            }
        }
        String nome = arquivo.getFileName().toString();
        return !nome.equals(".env") && !(nome.startsWith(".env.") && !nome.equals(".env.example"));
    }

    @Test
    void nenhumArquivoVersionavelTemChaveReal() throws IOException {
        List<String> achados;
        try (Stream<Path> arquivos = Files.walk(Path.of("."))) {
            achados = arquivos
                    .filter(Files::isRegularFile)
                    .filter(SegredosNoRepositorioTest::versionavel)
                    .filter(p -> !p.getFileName().toString().equals("SegredosNoRepositorioTest.java"))
                    .filter(p -> !p.toString().endsWith(".jar") && !p.toString().endsWith(".class"))
                    .flatMap(p -> {
                        try {
                            String conteudo = Files.readString(p);
                            return PADROES_DE_CHAVE.stream()
                                    .filter(padrao -> padrao.matcher(conteudo).find())
                                    .map(padrao -> p + " casa com " + padrao.pattern());
                        } catch (IOException e) {
                            return Stream.empty(); // binário ou ilegível: não é texto versionado
                        }
                    })
                    .toList();
        }
        assertThat(achados).isEmpty();
    }

    @Test
    void envEstaNoGitignoreEOExemploNao() throws IOException {
        List<String> linhas = Files.readAllLines(Path.of(".gitignore"));

        assertThat(linhas).contains(".env", ".env.*", "!.env.example");
    }

    @Test
    void envExampleSoTemValoresDeMarcacao() throws IOException {
        List<String> atribuicoes = Files.readAllLines(Path.of(".env.example")).stream()
                .filter(l -> l.matches("^[A-Z_]+=.*"))
                .toList();

        assertThat(atribuicoes).isNotEmpty();
        // chaves e senhas precisam de valor de marcação, nunca um valor que pareça real
        assertThat(atribuicoes.stream().filter(l -> l.matches("^(OPENAI|GEMINI|RESEND)_API_KEY=.*")))
                .allMatch(l -> l.contains("troque"));
        assertThat(atribuicoes.stream().filter(l -> l.matches("^(SERVICEDESK_API_KEY|RABBITMQ_PASSWORD|DB_PASSWORD)=.*")))
                .allMatch(l -> l.contains("troque"));
    }
}
