package br.com.coti.servicedesk_ai.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.coti.servicedesk_ai.aplicacao.AnalisadorChamadoPort;
import br.com.coti.servicedesk_ai.aplicacao.PublicadorNotificacaoPort;
import br.com.coti.servicedesk_ai.dominio.AnaliseChamado;
import br.com.coti.servicedesk_ai.dominio.Categoria;
import br.com.coti.servicedesk_ai.dominio.Prioridade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "servicedesk.limite-requisicoes-por-minuto=2")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LimiteTaxaApiTest {

    private static final String CORPO = """
            {"nome":"Maria","descricao":"A VPN caiu para todo o escritório desde as nove horas","autorizaEmail":false}""";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AnalisadorChamadoPort analisador;

    @MockitoBean
    PublicadorNotificacaoPort publicador;

    @Test
    void excedenteResponde429SemChamarOModelo() throws Exception {
        when(analisador.analisar(any(), any()))
                .thenReturn(new AnaliseChamado(Categoria.REDE, Prioridade.BAIXA, "r", "o", false));

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/chamados")
                            .header("X-API-Key", "chave-de-teste-0123456789")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPO))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/chamados")
                        .header("X-API-Key", "chave-de-teste-0123456789")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));

        verify(analisador, times(2)).analisar(any(), any());
    }

    @Test
    void quemNaoTemAChaveNaoConsomeOLimite() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/chamados").contentType(MediaType.APPLICATION_JSON).content(CORPO))
                    .andExpect(status().isUnauthorized());
        }
    }
}
