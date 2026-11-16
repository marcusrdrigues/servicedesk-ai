package br.com.coti.servicedesk_ai.web;

import br.com.coti.servicedesk_ai.aplicacao.AnalisarChamadoService;
import br.com.coti.servicedesk_ai.aplicacao.ResultadoChamado;
import br.com.coti.servicedesk_ai.dominio.StatusNotificacao;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chamados")
final class ChamadoController {

    private final AnalisarChamadoService servico;

    ChamadoController(AnalisarChamadoService servico) {
        this.servico = servico;
    }

    /** 202 quando a notificação foi aceita pela fila; 200 quando não houve notificação a enviar. */
    @PostMapping
    ResponseEntity<ChamadoResponse> analisar(@Valid @RequestBody ChamadoRequest requisicao) {
        ResultadoChamado resultado = servico.analisar(requisicao.paraDominio());
        HttpStatus status = resultado.notificacao() == StatusNotificacao.ENFILEIRADO
                ? HttpStatus.ACCEPTED
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(ChamadoResponse.de(resultado));
    }
}
