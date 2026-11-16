package br.com.coti.servicedesk_ai.web;

import br.com.coti.servicedesk_ai.dominio.AnaliseIndisponivelException;
import br.com.coti.servicedesk_ai.dominio.FalhaPublicacaoException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Todo erro sai como ProblemDetail com texto fixo e o id da requisição. Nada de stack trace, nome de classe,
 * mensagem de biblioteca, chave ou texto do modelo. O detalhe técnico vai só para o log, ligado pelo id.
 */
@RestControllerAdvice
final class TratadorErros extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TratadorErros.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<String> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField())
                .distinct()
                .toList();
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Requisição inválida",
                "Um ou mais campos não atendem às regras de validação.");
        problema.setProperty("campos", campos);
        return ResponseEntity.badRequest().body(problema);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, "Requisição inválida",
                "O corpo da requisição está ausente ou malformado."));
    }

    @ExceptionHandler(AnaliseIndisponivelException.class)
    ResponseEntity<ProblemDetail> analiseIndisponivel(AnaliseIndisponivelException ex) {
        LOG.error("resultado=analise-indisponivel motivo={}", ex.getMessage());
        return resposta(problema(HttpStatus.SERVICE_UNAVAILABLE, "Análise indisponível",
                "Não foi possível analisar o chamado agora. Tente novamente em instantes."));
    }

    @ExceptionHandler(FalhaPublicacaoException.class)
    ResponseEntity<ProblemDetail> publicacaoFalhou(FalhaPublicacaoException ex) {
        LOG.error("resultado=publicacao-falhou motivo={}", ex.getMessage());
        return resposta(problema(HttpStatus.SERVICE_UNAVAILABLE, "Notificação indisponível",
                "Não foi possível confirmar o enfileiramento da notificação. Tente novamente em instantes."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> inesperado(Exception ex) {
        LOG.error("resultado=erro-inesperado", ex);
        return resposta(problema(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                "Ocorreu um erro inesperado. Informe o identificador da requisição ao suporte."));
    }

    private static ResponseEntity<ProblemDetail> resposta(ProblemDetail problema) {
        return ResponseEntity.status(problema.getStatus()).body(problema);
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        String id = MDC.get(RespostaErro.MDC_REQUISICAO);
        problema.setProperty("requestId", id == null ? "" : id);
        return problema;
    }
}
