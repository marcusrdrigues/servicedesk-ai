package br.com.coti.servicedesk_ai.web;

import br.com.coti.servicedesk_ai.dominio.Chamado;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Não existe campo de destinatário de propósito: se vier no JSON, é ignorado. */
public record ChamadoRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Size(min = 20, max = 4000) String descricao,
        @NotNull Boolean autorizaEmail) {

    Chamado paraDominio() {
        return new Chamado(nome, descricao, autorizaEmail);
    }
}
