package org.gipsybuho.recetasfamiliares.chat;

import java.security.Principal;

/**
 * Principal minimo para sesiones STOMP autenticadas: transporta el userId
 * resuelto del JWT en el CONNECT.
 */
public record StompPrincipal(String userId) implements Principal {

    @Override
    public String getName() {
        return userId;
    }
}
