package org.gipsybuho.recetasfamiliares.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.gipsybuho.recetasfamiliares.users.UserEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class AccountEmailServiceTest {

    private static final String TOKEN = "A1B2C3D4E5F6G7H8";

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;
    @Mock
    private JavaMailSender mailSender;

    /**
     * El correo tiene que traer el codigo suelto porque es lo que piden Desktop
     * y Android: un campo donde pegar "el codigo del correo". Un enlace no sirve,
     * no hay pagina web que lo atienda.
     */
    @Test
    void elCorreoDeRecuperacionTraeElCodigoEnUnaLineaPropia() {
        String body = capturarCuerpo(service -> service.sendPasswordReset(usuario(), TOKEN));

        assertThat(body.lines().map(String::trim)).contains(TOKEN);
    }

    @Test
    void elCorreoDeVerificacionTraeElCodigoEnUnaLineaPropia() {
        String body = capturarCuerpo(service -> service.sendEmailVerification(usuario(), TOKEN));

        assertThat(body.lines().map(String::trim)).contains(TOKEN);
    }

    /**
     * Los enlaces /reset-password y /verify-email devuelven 401: no existe pagina
     * que los sirva. Mandarlos solo confunde a quien recibe el correo.
     */
    @Test
    void elCorreoDeRecuperacionNoMandaAUnEnlaceQueNoExiste() {
        String body = capturarCuerpo(service -> service.sendPasswordReset(usuario(), TOKEN));

        assertThat(body).doesNotContain("reset-password");
    }

    @Test
    void elCorreoDeVerificacionNoMandaAUnEnlaceQueNoExiste() {
        String body = capturarCuerpo(service -> service.sendEmailVerification(usuario(), TOKEN));

        assertThat(body).doesNotContain("verify-email");
    }

    @Test
    void conElCorreoDesactivadoNoSeEnviaNada() {
        AccountEmailService service = new AccountEmailService(
                mailSenderProvider, false, "", "no-reply@recetas.local");

        service.sendPasswordReset(usuario(), TOKEN);

        verifyNoInteractions(mailSenderProvider);
    }

    private String capturarCuerpo(java.util.function.Consumer<AccountEmailService> accion) {
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        AccountEmailService service = new AccountEmailService(
                mailSenderProvider, true, "smtp.example.com", "no-reply@recetas.local");

        accion.accept(service);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        org.mockito.Mockito.verify(mailSender).send(captor.capture());
        return captor.getValue().getText();
    }

    private UserEntity usuario() {
        return new UserEntity("abuela@example.com", "Abuela", "hash");
    }
}
