package br.com.coti.servicedesk_ai.integracao;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * Um RabbitMQ e um Mailpit por execução de testes, iniciados uma vez e compartilhados entre as classes.
 * As imagens são as mesmas do compose.yaml. O Testcontainers remove os containers ao final.
 */
final class ContainersCompartilhados {

    static final String USUARIO = "servicedesk";
    static final String SENHA = "senhaDeTeste123";

    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.1-management-alpine")
            .withAdminUser(USUARIO)
            .withAdminPassword(SENHA);

    static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.33.1")
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/livez").forPort(8025));

    static final org.testcontainers.postgresql.PostgreSQLContainer POSTGRES =
            new org.testcontainers.postgresql.PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("servicedesk")
                    .withUsername("servicedesk")
                    .withPassword("senhaDeTeste123");

    static {
        RABBIT.start();
        MAILPIT.start();
        POSTGRES.start();
    }

    private ContainersCompartilhados() {}

    static String urlRabbit() {
        return "amqp://%s:%s@%s:%d".formatted(USUARIO, SENHA, RABBIT.getHost(), RABBIT.getAmqpPort());
    }

    static String urlMailpitApi() {
        return "http://%s:%d".formatted(MAILPIT.getHost(), MAILPIT.getMappedPort(8025));
    }
}
