package br.com.coti.servicedesk_ai.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Exige X-API-Key em toda rota, menos a verificação de saúde. A comparação é em tempo constante e sobre o
 * hash dos dois valores, então nem o tamanho do segredo vaza pela resposta.
 */
final class ApiKeyFilter extends OncePerRequestFilter {

    static final String CABECALHO = "X-API-Key";
    private static final String SAUDE = "/actuator/health";

    private final byte[] hashEsperado;

    ApiKeyFilter(String segredo) {
        this.hashEsperado = hash(segredo);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String caminho = req.getRequestURI();
        return caminho.equals(SAUDE) || caminho.startsWith(SAUDE + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String informado = req.getHeader(CABECALHO);
        if (informado == null || !MessageDigest.isEqual(hashEsperado, hash(informado))) {
            RespostaErro.escrever(res, 401, "Não autorizado", "Credencial ausente ou inválida.");
            return;
        }
        chain.doFilter(req, res);
    }

    private static byte[] hash(String valor) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
