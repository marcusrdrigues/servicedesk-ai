package br.com.coti.servicedesk_ai.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.coti.servicedesk_ai.dominio.AnaliseChamado;
import br.com.coti.servicedesk_ai.dominio.Categoria;
import br.com.coti.servicedesk_ai.dominio.Prioridade;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MontadorEmailTest {

    private final MontadorEmail montador = new MontadorEmail("suporte@exemplo.local", "[ServiceDesk]");
    private final UUID id = UUID.randomUUID();

    @Test
    void destinatarioEAssuntoSaoDoServidor() {
        var evento = montador.montar(id, "Maria", new AnaliseChamado(Categoria.REDE, Prioridade.CRITICA, "r", "o", true));

        assertThat(evento.destinatario()).isEqualTo("suporte@exemplo.local");
        assertThat(evento.assunto()).isEqualTo("[ServiceDesk] CRITICA - REDE");
        assertThat(evento.eventoId()).isEqualTo(id);
    }

    @Test
    void campoLivreNuncaVaiParaOAssuntoNemInjetaCabecalho() {
        String nomeMalicioso = "Maria\r\nBcc: atacante@exemplo.com";
        var evento = montador.montar(id, nomeMalicioso, new AnaliseChamado(Categoria.OUTROS, Prioridade.BAIXA, "r", "o", true));

        assertThat(evento.assunto()).isEqualTo("[ServiceDesk] BAIXA - OUTROS");
        assertThat(evento.assunto()).doesNotContain("Bcc").doesNotContain("\r").doesNotContain("\n");
        assertThat(evento.corpo()).contains("Solicitante: Maria  Bcc: atacante@exemplo.com");
        assertThat(evento.corpo().lines().filter(l -> l.startsWith("Bcc:"))).isEmpty();
    }

    @Test
    void htmlApareceComoTextoNoCorpo() {
        var analise = new AnaliseChamado(Categoria.SISTEMA, Prioridade.MEDIA, "<script>alert(1)</script>", "<b>x</b>", true);

        var evento = montador.montar(id, "Ana", analise);

        assertThat(evento.corpo()).contains("<script>alert(1)</script>").contains("<b>x</b>");
    }

    @Test
    void resumoMantemQuebraDeLinhaMasPerdeCaractereDeControle() {
        var analise = new AnaliseChamado(Categoria.SISTEMA, Prioridade.MEDIA, "linha1\nlinha2\u0007", "o", true);

        var evento = montador.montar(id, "Ana", analise);

        assertThat(evento.corpo()).contains("linha1\nlinha2 ").doesNotContain("\u0007");
    }
}
