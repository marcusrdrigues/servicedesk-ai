package br.com.coti.servicedesk_ai.infraestrutura.config;

import java.util.regex.Pattern;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Validação que roda antes de qualquer bean (inclusive antes de abrir conexão com o banco ou o broker), para que
 * uma configuração insegura nunca chegue a conectar. As mensagens citam o nome da variável, nunca o valor.
 * O Spring Boot não falha em ${VARIAVEL} sem valor: deixa o texto literal, e por isso o teste de "${".
 */
@Configuration
class ValidacaoAmbiente {

    private static final Pattern TLS_NO_JDBC = Pattern.compile("[?&]sslmode=(require|verify-ca|verify-full)(&|$)");

    @Bean
    static BeanFactoryPostProcessor validarAmbiente(Environment env) {
        return beanFactory -> {
            exigirDefinida(env.getProperty("spring.datasource.password"), "DB_PASSWORD");
            if (env.acceptsProfiles(Profiles.of("cloud"))) {
                validarNuvem(env);
            }
        };
    }

    private static void validarNuvem(Environment env) {
        String jdbc = env.getProperty("spring.datasource.url");
        exigirDefinida(jdbc, "DB_URL");
        if (!jdbc.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("DB_URL deve ser uma URL JDBC (jdbc:postgresql://...), não postgresql://");
        }
        if (!TLS_NO_JDBC.matcher(jdbc).find()) {
            throw new IllegalStateException("DB_URL deve exigir TLS: acrescente sslmode=require");
        }
        String broker = env.getProperty("spring.rabbitmq.addresses");
        exigirDefinida(broker, "RABBITMQ_URL");
        if (!broker.startsWith("amqps://")) {
            throw new IllegalStateException("RABBITMQ_URL deve usar amqps:// (TLS) no perfil cloud");
        }
    }

    private static void exigirDefinida(String valor, String variavel) {
        if (valor == null || valor.isBlank() || valor.contains("${")) {
            throw new IllegalStateException(variavel + " deve estar definida (ausente, em branco ou sem resolver)");
        }
    }
}
