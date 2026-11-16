package br.com.coti.servicedesk_ai.web;

import br.com.coti.servicedesk_ai.infraestrutura.config.ServicedeskProperties;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Ordem dos filtros: id da requisição, CORS (para o preflight passar sem chave), chave da API, limite de taxa.
 * Sem origem configurada nenhum cabeçalho CORS é devolvido, e o navegador bloqueia chamadas de outra origem.
 */
@Configuration
class WebConfig {

    @Bean
    FilterRegistrationBean<RequisicaoIdFilter> requisicaoIdFilter() {
        return registrar(new RequisicaoIdFilter(), 1);
    }

    @Bean
    FilterRegistrationBean<CorsFilter> corsFilter(ServicedeskProperties propriedades) {
        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        if (StringUtils.hasText(propriedades.corsOrigem())) {
            CorsConfiguration cors = new CorsConfiguration();
            cors.setAllowedOrigins(List.of(propriedades.corsOrigem()));
            cors.setAllowedMethods(List.of("POST", "OPTIONS"));
            cors.setAllowedHeaders(List.of("Content-Type", ApiKeyFilter.CABECALHO));
            cors.setMaxAge(600L);
            fonte.registerCorsConfiguration("/api/**", cors);
        }
        return registrar(new CorsFilter(fonte), 2);
    }

    @Bean
    FilterRegistrationBean<ApiKeyFilter> apiKeyFilter(ServicedeskProperties propriedades) {
        return registrar(new ApiKeyFilter(propriedades.apiKey()), 3);
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(ServicedeskProperties propriedades, Clock relogio) {
        return registrar(
                new RateLimitFilter(new LimitadorTaxa(propriedades.limiteRequisicoesPorMinuto(), relogio)), 4);
    }

    private static <T extends jakarta.servlet.Filter> FilterRegistrationBean<T> registrar(T filtro, int ordem) {
        FilterRegistrationBean<T> registro = new FilterRegistrationBean<>(filtro);
        registro.setOrder(ordem);
        return registro;
    }
}
