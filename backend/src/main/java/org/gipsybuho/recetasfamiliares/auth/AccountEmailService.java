package org.gipsybuho.recetasfamiliares.auth;

import jakarta.annotation.PostConstruct;

import org.gipsybuho.recetasfamiliares.users.UserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class AccountEmailService {

    private static final Logger log = LoggerFactory.getLogger(AccountEmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final boolean enabled;
    private final String smtpHost;
    private final String from;

    public AccountEmailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${spring.mail.host:}") String smtpHost,
            @Value("${app.mail.from:no-reply@recetas.local}") String from
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.enabled = enabled;
        this.smtpHost = smtpHost;
        this.from = from;
    }

    @PostConstruct
    void validateConfiguration() {
        if (enabled && (smtpHost == null || smtpHost.isBlank())) {
            throw new IllegalStateException("SMTP_HOST is required when MAIL_ENABLED=true");
        }
        if (enabled && mailSenderProvider.getIfAvailable() == null) {
            throw new IllegalStateException("JavaMailSender is required when MAIL_ENABLED=true");
        }
    }

    public boolean isDeliveryEnabled() {
        return enabled;
    }

    public void sendPasswordReset(UserEntity user, String token) {
        send(
                user,
                "Restablece tu contraseña de Recetas Familiares",
                """
                Hemos recibido una solicitud para restablecer tu contraseña.

                Tu código de recuperación es:

                %s

                Cópialo entero y pégalo en Recetas Familiares: pulsa "¿Has olvidado tu
                contraseña?" y luego "Ya tengo el código". Caduca en unos minutos.

                Si no lo has pedido tú, puedes ignorar este mensaje.
                """.formatted(token),
                AccountActionTokenType.PASSWORD_RESET
        );
    }

    public void sendEmailVerification(UserEntity user, String token) {
        send(
                user,
                "Verifica tu email de Recetas Familiares",
                """
                Confirma que este email pertenece a tu cuenta de Recetas Familiares.

                Tu código de verificación es:

                %s

                Cópialo entero y pégalo en la aplicación, en Perfil y cuenta.

                Si no has creado esta cuenta, puedes ignorar este mensaje.
                """.formatted(token),
                AccountActionTokenType.EMAIL_VERIFICATION
        );
    }

    private void send(UserEntity user, String subject, String body, AccountActionTokenType type) {
        if (!enabled) {
            return;
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("Account email delivery skipped because JavaMailSender is not configured for userId={} type={}",
                    user.getId(),
                    type);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            log.warn("Account email delivery failed for userId={} type={}", user.getId(), type, exception);
        }
    }
}
