package org.gipsybuho.recetasfamiliares.api;

import com.google.gson.Gson;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.gipsybuho.recetasfamiliares.api.dto.ChatDtos;
import org.gipsybuho.recetasfamiliares.api.dto.FamilyDtos;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Cliente STOMP minimo sobre el WebSocket de OkHttp (sin dependencias nuevas).
 * Puerto del cliente Android {@code ChatSocket.kt}; comparte contrato con el
 * backend (fase 1):
 * <ul>
 *   <li>CONNECT con el JWT en la cabecera Authorization del frame (no en la URL).</li>
 *   <li>SUBSCRIBE al topic de la familia tras recibir CONNECTED.</li>
 *   <li>Recibir frames MESSAGE (JSON de ChatMessage) y entregarlos.</li>
 * </ul>
 *
 * La entrega en tiempo real es unidireccional: el envio va por REST. Si la
 * conexion falla o se cierra, {@code onConnectionChange(false)} permite al
 * llamador reflejar el estado. Los callbacks llegan en hilos de OkHttp: el
 * llamador debe marshalizar al hilo de UI (Platform.runLater).
 */
public class ChatSocket {

    private static final String NUL = String.valueOf((char) 0);
    private static final long RECONNECT_BASE_MS = 2_000L;
    private static final long RECONNECT_MAX_MS = 30_000L;
    private static final int RECONNECT_SHIFT_LIMIT = 4;

    private final ApiClient apiClient;
    private final Supplier<String> tokenSupplier;
    private final String familyId;
    private final String wsUrl;
    private final String topic;
    private final String presenceTopic;
    private final Gson gson;
    private final Consumer<ChatDtos.ChatMessage> onMessage;
    private final Consumer<Boolean> onConnectionChange;
    private final Consumer<Set<String>> onPresenceUpdate;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "chat-ws-reconnect");
                thread.setDaemon(true);
                return thread;
            });

    private volatile WebSocket webSocket;
    private volatile boolean closedByClient = false;
    private int reconnectAttempt = 0;
    private ScheduledFuture<?> reconnectFuture;

    public ChatSocket(
            ApiClient apiClient,
            Supplier<String> tokenSupplier,
            String familyId,
            Gson gson,
            Consumer<ChatDtos.ChatMessage> onMessage,
            Consumer<Boolean> onConnectionChange,
            Consumer<Set<String>> onPresenceUpdate
    ) {
        this.apiClient = apiClient;
        this.tokenSupplier = tokenSupplier;
        this.familyId = familyId;
        this.wsUrl = toWebSocketUrl(apiClient.getBaseUrl());
        this.topic = "/topic/families/" + familyId + "/chat";
        this.presenceTopic = "/topic/families/" + familyId + "/presence";
        this.gson = gson;
        this.onMessage = onMessage;
        this.onConnectionChange = onConnectionChange;
        this.onPresenceUpdate = onPresenceUpdate;
    }

    public synchronized void connect() {
        closedByClient = false;
        cancelReconnect();
        Request request = new Request.Builder().url(wsUrl).build();
        webSocket = apiClient.newWebSocket(request, listener);
    }

    public synchronized void disconnect() {
        closedByClient = true;
        cancelReconnect();
        WebSocket current = webSocket;
        if (current != null) {
            current.close(1000, null);
        }
        webSocket = null;
        scheduler.shutdownNow();
        onConnectionChange.accept(false);
    }

    private final WebSocketListener listener = new WebSocketListener() {
        @Override
        public void onOpen(WebSocket socket, Response response) {
            String token = tokenSupplier.get();
            if (token == null || token.isBlank()) {
                closedByClient = true;
                socket.close(1000, null);
                onConnectionChange.accept(false);
                return;
            }
            String connect = "CONNECT\n"
                    + "accept-version:1.2\n"
                    + "heart-beat:0,0\n"
                    + "host:stomp\n"
                    + "Authorization:Bearer " + token + "\n"
                    + "\n"
                    + NUL;
            socket.send(connect);
        }

        @Override
        public void onMessage(WebSocket socket, String text) {
            // Un mensaje de texto puede contener uno o varios frames STOMP
            // separados por el byte NUL.
            for (String raw : text.split(NUL, -1)) {
                String frame = stripLeadingNewlines(raw);
                if (frame.isBlank()) {
                    continue;
                }
                handleFrame(socket, frame);
            }
        }

        @Override
        public void onClosing(WebSocket socket, int code, String reason) {
            socket.close(1000, null);
        }

        @Override
        public void onClosed(WebSocket socket, int code, String reason) {
            ChatSocket.this.webSocket = null;
            if (!closedByClient) {
                onConnectionChange.accept(false);
                scheduleReconnect();
            }
        }

        @Override
        public void onFailure(WebSocket socket, Throwable throwable, Response response) {
            ChatSocket.this.webSocket = null;
            onConnectionChange.accept(false);
            scheduleReconnect();
        }
    };

    private void handleFrame(WebSocket socket, String frame) {
        int newline = frame.indexOf('\n');
        String command = (newline >= 0 ? frame.substring(0, newline) : frame).trim();
        switch (command) {
            case "CONNECTED" -> {
                String subscribeChat = "SUBSCRIBE\n"
                        + "id:sub-chat\n"
                        + "destination:" + topic + "\n"
                        + "\n"
                        + NUL;
                socket.send(subscribeChat);
                String subscribePresence = "SUBSCRIBE\n"
                        + "id:sub-presence\n"
                        + "destination:" + presenceTopic + "\n"
                        + "\n"
                        + NUL;
                socket.send(subscribePresence);
                reconnectAttempt = 0;
                onConnectionChange.accept(true);
            }
            case "MESSAGE" -> {
                String destination = extractHeader(frame, "destination");
                int split = frame.indexOf("\n\n");
                String body = split >= 0 ? frame.substring(split + 2).trim() : "";
                if (body.isEmpty()) {
                    // no-op
                } else if (presenceTopic.equals(destination)) {
                    handlePresenceMessage(body);
                } else {
                    handleChatMessage(body);
                }
            }
            case "ERROR" -> {
                onConnectionChange.accept(false);
                socket.close(1000, null);
            }
            default -> {
                // RECEIPT u otros comandos no usados en fase 1.
            }
        }
    }

    private void handleChatMessage(String body) {
        try {
            ChatDtos.ChatMessage message = gson.fromJson(body, ChatDtos.ChatMessage.class);
            if (message != null && message.isUsable() && familyId.equals(message.familyId())) {
                onMessage.accept(message);
            }
        } catch (RuntimeException ignored) {
            // Frame no parseable: se descarta sin romper la conexion.
        }
    }

    private void handlePresenceMessage(String body) {
        try {
            FamilyDtos.PresenceResponse presence = gson.fromJson(body, FamilyDtos.PresenceResponse.class);
            if (presence != null && presence.onlineUserIds() != null) {
                onPresenceUpdate.accept(new HashSet<>(presence.onlineUserIds()));
            }
        } catch (RuntimeException ignored) {
            // Frame no parseable: se descarta sin romper la conexion.
        }
    }

    static String extractHeader(String frame, String name) {
        int headersEnd = frame.indexOf("\n\n");
        String headerBlock = headersEnd >= 0 ? frame.substring(0, headersEnd) : frame;
        String prefix = name + ":";
        for (String line : headerBlock.split("\n")) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length());
            }
        }
        return null;
    }

    private synchronized void scheduleReconnect() {
        if (closedByClient || scheduler.isShutdown()) {
            return;
        }
        long delayMillis = Math.min(
                RECONNECT_BASE_MS * (1L << Math.min(reconnectAttempt, RECONNECT_SHIFT_LIMIT)),
                RECONNECT_MAX_MS);
        reconnectAttempt++;
        cancelReconnect();
        try {
            reconnectFuture = scheduler.schedule(() -> {
                if (!closedByClient) {
                    connect();
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
            // Scheduler cerrado por disconnect concurrente: no reintentar.
        }
    }

    private void cancelReconnect() {
        if (reconnectFuture != null) {
            reconnectFuture.cancel(false);
            reconnectFuture = null;
        }
    }

    private static String stripLeadingNewlines(String value) {
        int index = 0;
        while (index < value.length() && value.charAt(index) == '\n') {
            index++;
        }
        return value.substring(index);
    }

    private static String toWebSocketUrl(String baseUrl) {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String wsBase;
        if (trimmed.startsWith("wss://") || trimmed.startsWith("ws://")) {
            wsBase = trimmed;
        } else if (trimmed.startsWith("https://")) {
            wsBase = "wss://" + trimmed.substring("https://".length());
        } else if (trimmed.startsWith("http://")) {
            wsBase = "ws://" + trimmed.substring("http://".length());
        } else {
            wsBase = "ws://" + trimmed;
        }
        return wsBase + "/ws";
    }
}
