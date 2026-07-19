package org.gipsybuho.recetasfamiliares.presence;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Registro en memoria de quien esta conectado a que familia via WebSocket.
 * Contador (no booleano) por (familia, usuario) para soportar el mismo
 * usuario conectado desde varios dispositivos sin que uno "apague" al otro.
 * Sin persistencia: si el backend reinicia, los clientes se reconectan solos
 * (ChatSocket ya tiene backoff exponencial) y repueblan el registro.
 */
@Component
public class PresenceRegistry {

    private record PresenceKey(String familyId, String userId) {}

    private final Map<String, Map<String, AtomicInteger>> onlineByFamily = new ConcurrentHashMap<>();
    private final Map<String, List<PresenceKey>> keysBySession = new ConcurrentHashMap<>();

    public synchronized boolean subscribe(String sessionId, String familyId, String userId) {
        Map<String, AtomicInteger> familyCounts =
                onlineByFamily.computeIfAbsent(familyId, id -> new ConcurrentHashMap<>());
        AtomicInteger counter = familyCounts.computeIfAbsent(userId, id -> new AtomicInteger(0));
        boolean becameOnline = counter.getAndIncrement() == 0;
        keysBySession.computeIfAbsent(sessionId, id -> new CopyOnWriteArrayList<>())
                .add(new PresenceKey(familyId, userId));
        return becameOnline;
    }

    public synchronized Set<String> unsubscribeSession(String sessionId) {
        List<PresenceKey> keys = keysBySession.remove(sessionId);
        if (keys == null || keys.isEmpty()) {
            return Set.of();
        }
        Set<String> changedFamilies = new HashSet<>();
        for (PresenceKey key : keys) {
            Map<String, AtomicInteger> familyCounts = onlineByFamily.get(key.familyId());
            if (familyCounts == null) {
                continue;
            }
            AtomicInteger counter = familyCounts.get(key.userId());
            if (counter == null) {
                continue;
            }
            if (counter.decrementAndGet() <= 0) {
                familyCounts.remove(key.userId());
                changedFamilies.add(key.familyId());
            }
            if (familyCounts.isEmpty()) {
                onlineByFamily.remove(key.familyId());
            }
        }
        return changedFamilies;
    }

    public List<String> onlineUserIds(String familyId) {
        Map<String, AtomicInteger> familyCounts = onlineByFamily.get(familyId);
        if (familyCounts == null) {
            return List.of();
        }
        return familyCounts.keySet().stream().sorted().collect(Collectors.toList());
    }
}
