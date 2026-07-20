package org.gipsybuho.recetasfamiliares.dm;

import java.time.Instant;

/**
 * Ping ligero del topic de bandeja ({@code /topic/users/{userId}/inbox}).
 * Nunca lleva el cuerpo del mensaje (decision de seguridad del spec): solo
 * suficiente informacion para que el cliente refresque su bandeja/badge y
 * decida si pide el contenido real por REST o por el topic de la conversacion.
 */
public record PrivateInboxPing(
        String conversationId,
        String senderUserId,
        Instant sentAt
) {
}
