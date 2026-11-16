package br.com.coti.servicedesk_ai.aplicacao;

import br.com.coti.servicedesk_ai.dominio.AnaliseChamado;
import br.com.coti.servicedesk_ai.dominio.EmailSolicitado;
import java.util.UUID;

/**
 * Monta o evento com texto puro, destinatário do servidor e assunto fixo. Campo livre (nome, resumo) só vai
 * no corpo, nunca no assunto, então CR/LF não injeta cabeçalho e HTML aparece como texto.
 */
public final class MontadorEmail {

    private final String destinatario;
    private final String prefixoAssunto;

    public MontadorEmail(String destinatario, String prefixoAssunto) {
        this.destinatario = destinatario;
        this.prefixoAssunto = prefixoAssunto;
    }

    public EmailSolicitado montar(UUID eventoId, String nomeSolicitante, AnaliseChamado a) {
        String assunto = prefixoAssunto + " " + a.prioridade() + " - " + a.categoria();
        String corpo = """
                Novo chamado analisado.

                Solicitante: %s
                Categoria: %s
                Prioridade: %s

                Resumo:
                %s

                Orientação:
                %s

                Evento %s. Gerado por análise automática; deve ser revisado por uma pessoa.
                """
                .formatted(
                        umaLinha(nomeSolicitante),
                        a.categoria(),
                        a.prioridade(),
                        semControle(a.resumo()),
                        semControle(a.orientacao()),
                        eventoId);
        return new EmailSolicitado(eventoId, destinatario, assunto, corpo);
    }

    /** Troca caracteres de controle, exceto quebra de linha, por espaço. */
    private static String semControle(String valor) {
        return valor == null ? "" : valor.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ");
    }

    /** Campo que deve caber em uma linha: remove também a quebra de linha. */
    private static String umaLinha(String valor) {
        return valor == null ? "" : valor.replaceAll("\\p{Cntrl}", " ");
    }
}
