package br.com.coti.servicedesk_ai.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.com.coti.servicedesk_ai.dominio.EmailSolicitado;
import br.com.coti.servicedesk_ai.dominio.EstadoNotificacao;
import br.com.coti.servicedesk_ai.dominio.FalhaEnvioEmailException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmailServiceTest {

    private EnviadorEmailPort enviador;
    private RegistroNotificacoesProcessadas registro;
    private AuditoriaPort auditoria;
    private EmailService servico;

    @BeforeEach
    void preparar() {
        enviador = mock(EnviadorEmailPort.class);
        registro = new RegistroNotificacoesProcessadas(10);
        auditoria = mock(AuditoriaPort.class);
        servico = new EmailService(enviador, registro, auditoria);
    }

    @Test
    void aceiteDoProvedorGravaAceitoENuncaEntregue() {
        UUID id = UUID.randomUUID();

        servico.enviar(evento(id));

        verify(auditoria).registrarEnvio(id, EstadoNotificacao.ACEITO_PELO_PROVEDOR, null);
    }

    @Test
    void falhaGravaFalhaEnvioComErroSanitizadoSemMensagem() {
        UUID id = UUID.randomUUID();
        doThrow(new FalhaEnvioEmailException("falha com texto-do-chamado dentro", new IllegalStateException("outro-segredo")))
                .when(enviador)
                .enviar(evento(id));

        assertThatThrownBy(() -> servico.enviar(evento(id))).isInstanceOf(FalhaEnvioEmailException.class);

        var erro = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(auditoria).registrarEnvio(org.mockito.ArgumentMatchers.eq(id), org.mockito.ArgumentMatchers.eq(EstadoNotificacao.FALHA_ENVIO), erro.capture());
        assertThat(erro.getValue()).isEqualTo("FalhaEnvioEmailException<IllegalStateException");
        assertThat(erro.getValue()).doesNotContain("texto-do-chamado").doesNotContain("outro-segredo");
    }

    @Test
    void eventoDuplicadoNaoGravaNovoEnvio() {
        UUID id = UUID.randomUUID();
        servico.enviar(evento(id));
        org.mockito.Mockito.clearInvocations(auditoria);

        servico.enviar(evento(id));

        org.mockito.Mockito.verifyNoInteractions(auditoria);
    }

    private static EmailSolicitado evento(UUID id) {
        return new EmailSolicitado(id, "suporte@exemplo.local", "assunto", "corpo");
    }

    @Test
    void enviaERegistraOEvento() {
        UUID id = UUID.randomUUID();

        servico.enviar(evento(id));

        verify(enviador).enviar(evento(id));
        assertThat(registro.jaProcessada(id)).isTrue();
    }

    @Test
    void redeliveryDoMesmoEventoNaoEnviaSegundoEmail() {
        UUID id = UUID.randomUUID();

        servico.enviar(evento(id));
        servico.enviar(evento(id));

        verify(enviador, times(1)).enviar(evento(id));
    }

    @Test
    void falhaDeEnvioPropagaENaoMarcaComoProcessado() {
        UUID id = UUID.randomUUID();
        doThrow(new FalhaEnvioEmailException("fora", null)).when(enviador).enviar(evento(id));

        assertThatThrownBy(() -> servico.enviar(evento(id))).isInstanceOf(FalhaEnvioEmailException.class);

        assertThat(registro.jaProcessada(id)).isFalse();
    }

    @Test
    void registroEsquecedeOMaisAntigoAoPassarDaCapacidade() {
        var pequeno = new RegistroNotificacoesProcessadas(2);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        pequeno.registrar(a);
        pequeno.registrar(b);
        pequeno.registrar(c);

        assertThat(pequeno.jaProcessada(a)).isFalse();
        assertThat(pequeno.jaProcessada(b)).isTrue();
        assertThat(pequeno.jaProcessada(c)).isTrue();
    }
}
