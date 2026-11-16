package br.com.coti.servicedesk_ai.infraestrutura.ia;

import br.com.coti.servicedesk_ai.aplicacao.AnalisadorChamadoPort;
import br.com.coti.servicedesk_ai.aplicacao.ValidadorAnalise;
import br.com.coti.servicedesk_ai.dominio.AnaliseChamado;
import br.com.coti.servicedesk_ai.dominio.Categoria;
import br.com.coti.servicedesk_ai.dominio.Chamado;
import br.com.coti.servicedesk_ai.dominio.FalhaProvedorModeloException;
import br.com.coti.servicedesk_ai.dominio.Prioridade;
import br.com.coti.servicedesk_ai.dominio.SaidaModeloInvalidaException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.io.Resource;

/**
 * Adaptador do ChatClient do Spring AI para a porta de análise. Uma classe, duas instâncias (OpenAI e Gemini
 * pelo endpoint compatível): o comportamento é o mesmo, só mudam cliente e modelo.
 * Nunca registra em log o texto do chamado nem a resposta do modelo, só metadados.
 */
public final class ChatClientAnalisadorAdapter implements AnalisadorChamadoPort {

    private static final Logger LOG = LoggerFactory.getLogger(ChatClientAnalisadorAdapter.class);
    static final String VERSAO_PROMPT = "analisar-chamado.v1";

    /** Formato que o modelo deve devolver. Os enums fecham os valores permitidos. */
    record SaidaModelo(
            @JsonProperty(required = true) Categoria categoria,
            @JsonProperty(required = true) Prioridade prioridade,
            @JsonProperty(required = true) String resumo,
            @JsonProperty(required = true) String orientacao,
            @JsonProperty(required = true) Boolean enviarEmail) {}

    private final String provedor;
    private final ChatClient client;
    private final Resource promptSistema;
    private final Resource promptUsuario;
    private final boolean schemaNativo;
    private final ValidadorAnalise validador;

    public ChatClientAnalisadorAdapter(
            String provedor,
            ChatClient client,
            Resource promptSistema,
            Resource promptUsuario,
            boolean schemaNativo,
            ValidadorAnalise validador) {
        this.provedor = provedor;
        this.client = client;
        this.promptSistema = promptSistema;
        this.promptUsuario = promptUsuario;
        this.schemaNativo = schemaNativo;
        this.validador = validador;
    }

    @Override
    public AnaliseChamado analisar(Chamado chamado, Instant limite) {
        long inicio = System.nanoTime();
        try {
            var requisicao = client.prompt()
                    .system(promptSistema)
                    .user(u -> u.text(promptUsuario)
                            .param("nome", SanitizadorEntrada.sanitizar(chamado.nome()))
                            .param("descricao", SanitizadorEntrada.sanitizar(chamado.descricao())));
            if (schemaNativo) {
                requisicao = requisicao.advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT);
            }
            ResponseEntity<ChatResponse, SaidaModelo> resposta = requisicao.call().responseEntity(SaidaModelo.class);
            SaidaModelo saida = resposta.entity();
            if (saida == null) {
                throw new SaidaModeloInvalidaException("resposta vazia");
            }
            AnaliseChamado analise = validador.validar(new AnaliseChamado(
                    saida.categoria(),
                    saida.prioridade(),
                    saida.resumo(),
                    saida.orientacao(),
                    Boolean.TRUE.equals(saida.enviarEmail())));
            registrar("ok", inicio, resposta.response());
            return analise;
        } catch (RuntimeException erro) {
            FalhaProvedorModeloException falha = traduzir(erro);
            LOG.warn("provedor={} prompt={} resultado=falha transitoria={} latenciaMs={} motivo={}",
                    provedor, VERSAO_PROMPT, falha.transitoria(), milissegundos(inicio), falha.getMessage());
            throw falha;
        }
    }

    private void registrar(String resultado, long inicio, ChatResponse resposta) {
        Integer entrada = null;
        Integer saida = null;
        if (resposta != null && resposta.getMetadata() != null && resposta.getMetadata().getUsage() != null) {
            entrada = resposta.getMetadata().getUsage().getPromptTokens();
            saida = resposta.getMetadata().getUsage().getCompletionTokens();
        }
        LOG.info("provedor={} prompt={} resultado={} latenciaMs={} tokensEntrada={} tokensSaida={}",
                provedor, VERSAO_PROMPT, resultado, milissegundos(inicio), entrada, saida);
    }

    private static long milissegundos(long inicioNano) {
        return (System.nanoTime() - inicioNano) / 1_000_000;
    }

    /**
     * Classifica a falha sem repassar mensagem do provedor (pode conter trecho da resposta).
     * 429, 408 e 5xx e erro de rede são transitórios; 400, 401, 403 e saída inválida não são.
     */
    FalhaProvedorModeloException traduzir(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof FalhaProvedorModeloException falha) {
                return falha;
            }
            if (causa instanceof OpenAIServiceException servico) {
                int status = servico.statusCode();
                boolean transitoria = status == 429 || status == 408 || status >= 500;
                return new FalhaProvedorModeloException(provedor + ": http " + status, transitoria, erro);
            }
            if (causa instanceof OpenAIIoException) {
                return new FalhaProvedorModeloException(provedor + ": falha de rede ou timeout", true, erro);
            }
            if (causa instanceof SaidaModeloInvalidaException invalida) {
                return new FalhaProvedorModeloException(
                        provedor + ": saída inválida (" + invalida.getMessage() + ")", false, erro);
            }
        }
        return new FalhaProvedorModeloException(
                provedor + ": resposta não convertida (" + erro.getClass().getSimpleName() + ")", false, erro);
    }

    @Override
    public String provedor() {
        return provedor;
    }
}
