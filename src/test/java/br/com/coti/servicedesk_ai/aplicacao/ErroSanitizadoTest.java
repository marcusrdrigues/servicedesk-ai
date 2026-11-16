package br.com.coti.servicedesk_ai.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErroSanitizadoTest {

    @Test
    void guardaSoOsNomesDosTiposNuncaAMensagem() {
        var erro = new RuntimeException("descricao do chamado com dado pessoal", new IllegalStateException("outra"));

        assertThat(ErroSanitizado.de(erro)).isEqualTo("RuntimeException<IllegalStateException");
    }

    @Test
    void semCausaSoOTipo() {
        assertThat(ErroSanitizado.de(new IllegalArgumentException("x"))).isEqualTo("IllegalArgumentException");
    }

    @Test
    void naoPassaDe200Caracteres() {
        class UmaExcecaoComUmNomeBemLongoParaForcarOTruncamentoDaCadeiaDeCausasNaAuditoriaDoLaboratorio
                extends RuntimeException {
            UmaExcecaoComUmNomeBemLongoParaForcarOTruncamentoDaCadeiaDeCausasNaAuditoriaDoLaboratorio(Throwable c) {
                super(c);
            }
        }
        Throwable cadeia = new IllegalStateException();
        for (int i = 0; i < 5; i++) {
            cadeia = new UmaExcecaoComUmNomeBemLongoParaForcarOTruncamentoDaCadeiaDeCausasNaAuditoriaDoLaboratorio(cadeia);
        }

        assertThat(ErroSanitizado.de(cadeia).length()).isLessThanOrEqualTo(200);
    }
}
