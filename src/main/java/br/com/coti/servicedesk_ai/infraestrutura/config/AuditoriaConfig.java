package br.com.coti.servicedesk_ai.infraestrutura.config;

import br.com.coti.servicedesk_ai.aplicacao.AuditoriaPort;
import br.com.coti.servicedesk_ai.aplicacao.AuditoriaTolerante;
import br.com.coti.servicedesk_ai.infraestrutura.auditoria.JdbcAuditoriaAdapter;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Sem DataSource (só nos testes que excluem a autoconfiguração do banco) a auditoria fica desligada. Na nuvem o
 * banco é obrigatório: a validação do perfil cloud recusa subir sem DB_URL.
 */
@Configuration
class AuditoriaConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AuditoriaConfig.class);

    @Bean
    AuditoriaPort auditoria(ObjectProvider<JdbcClient> jdbc, Clock relogio) {
        JdbcClient cliente = jdbc.getIfAvailable();
        if (cliente == null) {
            LOG.warn("sem DataSource configurado: a auditoria está desligada");
            return AuditoriaPort.semAuditoria();
        }
        return new AuditoriaTolerante(new JdbcAuditoriaAdapter(cliente, relogio));
    }
}
