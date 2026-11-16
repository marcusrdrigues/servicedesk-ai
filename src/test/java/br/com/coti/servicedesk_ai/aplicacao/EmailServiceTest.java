package br.com.coti.servicedesk_ai.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.com.coti.servicedesk_ai.dominio.EmailSolicitado;
import br.com.coti.servicedesk_ai.dominio.FalhaEnvioEmailException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmailServiceTest {

    private EnviadorEmailPort enviador;
    private RegistroNotificacoesProcessadas registro;
    private EmailService servico;

    @BeforeEach
    void preparar() {
        enviador = mock(EnviadorEmailPort.class);
        registro = new RegistroNotificacoesProcessadas(10);
        servico = new EmailService(enviador, registro);
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
