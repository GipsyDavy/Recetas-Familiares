package org.gipsybuho.recetasfamiliares.presence;

import java.util.Set;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Limpia el registro de presencia cuando se cierra una sesion WebSocket
 * (cierre de app, perdida de red, logout). Spring emite este evento para
 * toda desconexion STOMP; hasta ahora el proyecto no tenia listener para el.
 */
@Component
public class PresenceDisconnectListener {

    private final PresenceRegistry registry;
    private final PresencePublisher publisher;

    public PresenceDisconnectListener(PresenceRegistry registry, PresencePublisher publisher) {
        this.registry = registry;
        this.publisher = publisher;
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        Set<String> changedFamilies = registry.unsubscribeSession(event.getSessionId());
        changedFamilies.forEach(publisher::publish);
    }
}
