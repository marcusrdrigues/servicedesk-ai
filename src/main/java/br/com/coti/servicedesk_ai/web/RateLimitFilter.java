package br.com.coti.servicedesk_ai.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Roda depois da autenticação: quem não tem a chave não consome o limite de ninguém. */
final class RateLimitFilter extends OncePerRequestFilter {

    private final LimitadorTaxa limitador;

    RateLimitFilter(LimitadorTaxa limitador) {
        this.limitador = limitador;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return req.getRequestURI().startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!limitador.permitir(req.getRemoteAddr())) {
            res.setHeader("Retry-After", "60");
            RespostaErro.escrever(res, 429, "Muitas requisições", "Limite de requisições excedido. Tente mais tarde.");
            return;
        }
        chain.doFilter(req, res);
    }
}
