package br.com.coti.servicedesk_ai.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.coti.servicedesk_ai.aplicacao.AnalisadorChamadoPort;
import br.com.coti.servicedesk_ai.aplicacao.PublicadorNotificacaoPort;
import br.com.coti.servicedesk_ai.dominio.AnaliseChamado;
import br.com.coti.servicedesk_ai.dominio.AnaliseIndisponivelException;
import br.com.coti.servicedesk_ai.dominio.Categoria;
import br.com.coti.servicedesk_ai.dominio.EmailSolicitado;
import br.com.coti.servicedesk_ai.dominio.FalhaPublicacaoException;
import br.com.coti.servicedesk_ai.dominio.Prioridade;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class ChamadoApiTest {

    private static final String CHAVE = "chave-de-teste-0123456789";
    private static final String CHAMADO_VALIDO = """
            {"nome":"Maria","descricao":"A VPN caiu para todo o escritório desde as nove horas","autorizaEmail":true}""";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AnalisadorChamadoPort analisador;

    @MockitoBean
    PublicadorNotificacaoPort publicador;

    private void modeloResponde(boolean enviarEmail) {
        when(analisador.analisar(any(), any()))
                .thenReturn(new AnaliseChamado(Categoria.REDE, Prioridade.ALTA, "VPN caiu", "Reiniciar", enviarEmail));
    }

    private ResultActions postar(String corpo) throws Exception {
        return mvc.perform(post("/api/chamados")
                .header("X-API-Key", CHAVE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    // --- autenticação ---

    @Test
    void semChaveResponde401ESemChamarModelo() throws Exception {
        mvc.perform(post("/api/chamados").contentType(MediaType.APPLICATION_JSON).content(CHAMADO_VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"));

        verifyNoInteractions(analisador, publicador);
    }

    @Test
    void chaveErradaResponde401() throws Exception {
        mvc.perform(post("/api/chamados")
                        .header("X-API-Key", "chave-errada")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHAMADO_VALIDO))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(analisador, publicador);
    }

    @Test
    void autenticacaoVemAntesDaValidacaoDoCorpo() throws Exception {
        mvc.perform(post("/api/chamados").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotaInexistenteSemChaveTambemResponde401() throws Exception {
        mvc.perform(get("/qualquer-coisa")).andExpect(status().isUnauthorized());
    }

    @Test
    void saudeEPublicaESemDetalhes() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    // --- validação da entrada ---

    @Test
    void entradasInvalidasResponde400ESemChamarModelo() throws Exception {
        String[] invalidas = {
            "{\"descricao\":\"descrição com mais de vinte caracteres\",\"autorizaEmail\":true}",
            "{\"nome\":\"   \",\"descricao\":\"descrição com mais de vinte caracteres\",\"autorizaEmail\":true}",
            "{\"nome\":\"" + "n".repeat(101) + "\",\"descricao\":\"descrição com mais de vinte caracteres\",\"autorizaEmail\":true}",
            "{\"nome\":\"Ana\",\"descricao\":\"curta demais\",\"autorizaEmail\":true}",
            "{\"nome\":\"Ana\",\"descricao\":\"" + "d".repeat(4001) + "\",\"autorizaEmail\":true}",
            "{\"nome\":\"Ana\",\"descricao\":\"descrição com mais de vinte caracteres\"}",
            "{\"nome\":\"Ana\",\"descricao\":\"descrição com mais de vinte caracteres\",\"autorizaEmail\":null}",
            "{\"nome\":\"Ana\",\"descricao\":\"descrição com mais de vinte caracteres\",\"autorizaEmail\":\"talvez\"}",
            "isto não é json",
            ""
        };
        for (String corpo : invalidas) {
            postar(corpo)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.requestId").exists());
        }

        verifyNoInteractions(analisador, publicador);
    }

    @Test
    void limitesExatosSaoAceitos() throws Exception {
        modeloResponde(false);
        String corpo = "{\"nome\":\"" + "n".repeat(100) + "\",\"descricao\":\"" + "d".repeat(20)
                + "\",\"autorizaEmail\":false}";

        postar(corpo).andExpect(status().isOk());
        postar("{\"nome\":\"Ana\",\"descricao\":\"" + "d".repeat(4000) + "\",\"autorizaEmail\":false}")
                .andExpect(status().isOk());
    }

    @Test
    void mensagemDeValidacaoNaoEcoaOConteudoEnviado() throws Exception {
        String resposta = postar("{\"nome\":\"SEGREDO-NO-NOME\",\"descricao\":\"curta\",\"autorizaEmail\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0]").value("descricao"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(resposta).doesNotContain("SEGREDO-NO-NOME").doesNotContain("curta");
    }

    // --- resultado e notificação ---

    @Test
    void autorizadoERecomendadoResponde202Enfileirado() throws Exception {
        modeloResponde(true);

        String resposta = postar(CHAMADO_VALIDO)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.notificacao").value("ENFILEIRADO"))
                .andExpect(jsonPath("$.categoria").value("REDE"))
                .andExpect(jsonPath("$.prioridade").value("ALTA"))
                .andExpect(jsonPath("$.resumo").value("VPN caiu"))
                .andExpect(jsonPath("$.orientacao").value("Reiniciar"))
                .andExpect(jsonPath("$.enviarEmail").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(resposta).doesNotContain("ENVIADO");
        verify(publicador).publicar(any());
    }

    @Test
    void semAutorizacaoResponde200NaoSolicitadoSemPublicar() throws Exception {
        modeloResponde(true);

        postar(CHAMADO_VALIDO.replace("\"autorizaEmail\":true", "\"autorizaEmail\":false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificacao").value("NAO_SOLICITADO"));

        verifyNoInteractions(publicador);
    }

    @Test
    void destinatarioNoCorpoDaRequisicaoEIgnorado() throws Exception {
        modeloResponde(true);

        postar("""
                {"nome":"Maria","descricao":"A VPN caiu para todo o escritório desde as nove horas",
                 "autorizaEmail":true,"destinatario":"atacante@exemplo.com","to":"atacante@exemplo.com"}""")
                .andExpect(status().isAccepted());

        ArgumentCaptor<EmailSolicitado> evento = ArgumentCaptor.forClass(EmailSolicitado.class);
        verify(publicador).publicar(evento.capture());
        assertThat(evento.getValue().destinatario()).isEqualTo("suporte@exemplo.local");
    }

    // --- falhas ---

    @Test
    void ambosOsProvedoresFalhandoResponde503SemPublicarESemVazar() throws Exception {
        when(analisador.analisar(any(), any())).thenThrow(new AnaliseIndisponivelException("nenhum provedor"));

        String resposta = postar(CHAMADO_VALIDO)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.requestId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        verifyNoInteractions(publicador);
        assertThat(resposta)
                .doesNotContain("Exception")
                .doesNotContain("br.com.coti")
                .doesNotContain("nenhum provedor")
                .doesNotContain("openai")
                .doesNotContain("at ");
    }

    @Test
    void falhaDePublicacaoResponde503ENaoDizEnfileirado() throws Exception {
        modeloResponde(true);
        doThrow(new FalhaPublicacaoException("o broker recusou a mensagem")).when(publicador).publicar(any());

        String resposta = postar(CHAMADO_VALIDO)
                .andExpect(status().isServiceUnavailable())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(resposta).doesNotContain("ENFILEIRADO").doesNotContain("broker");
    }

    @Test
    void erroInesperadoResponde500GenericoComIdDaRequisicao() throws Exception {
        when(analisador.analisar(any(), any())).thenThrow(new IllegalStateException("senha=abc123 jdbc://interno"));

        String resposta = postar(CHAMADO_VALIDO)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.requestId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(resposta)
                .doesNotContain("abc123")
                .doesNotContain("jdbc")
                .doesNotContain("IllegalState")
                .doesNotContain("stackTrace");
    }

    // --- log ---

    @Test
    void logTemOIdDaRequisicaoENaoTemNomeNemDescricaoDoChamado(
            org.springframework.boot.test.system.CapturedOutput saida) throws Exception {
        String nome = "NOME-QUE-NAO-PODE-APARECER";
        String descricao = "DESCRICAO-QUE-NAO-PODE-APARECER-NO-LOG-DE-JEITO-NENHUM";
        String corpo = "{\"nome\":\"" + nome + "\",\"descricao\":\"" + descricao + "\",\"autorizaEmail\":true}";

        when(analisador.analisar(any(), any())).thenThrow(new AnaliseIndisponivelException("nenhum provedor"));
        postar(corpo).andExpect(status().isServiceUnavailable());
        // re-stub com doReturn: when(mock.metodo()) chamaria o mock que acabou de ser programado para lançar
        org.mockito.Mockito.doReturn(
                        new AnaliseChamado(Categoria.REDE, Prioridade.ALTA, "VPN caiu", "Reiniciar", true))
                .when(analisador)
                .analisar(any(), any());
        postar(corpo).andExpect(status().isAccepted());
        postar(corpo.replace(descricao, "curta")).andExpect(status().isBadRequest());

        assertThat(saida.getAll()).doesNotContain(nome).doesNotContain(descricao);
        assertThat(saida.getAll()).containsPattern("\\[[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\]");
    }

    // --- CORS ---

    @Test
    void preflightDaOrigemDoPortalPassaSemChave() throws Exception {
        mvc.perform(options("/api/chamados")
                        .header("Origin", "https://portal.exemplo.local")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "x-api-key,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://portal.exemplo.local"));
    }

    @Test
    void origemNaoAutorizadaNaoRecebeCabecalhosCors() throws Exception {
        mvc.perform(options("/api/chamados")
                        .header("Origin", "https://site-malicioso.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
