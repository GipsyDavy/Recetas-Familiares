package org.gipsybuho.recetasfamiliares.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.*;
import org.gipsybuho.recetasfamiliares.api.dto.AuthDtos;
import org.gipsybuho.recetasfamiliares.core.AppSession;
import org.gipsybuho.recetasfamiliares.core.ServerConfig;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final AppSession session;
    private final Gson gson;
    private final Supplier<String> baseUrlSupplier;
    private final OkHttpClient client;
    // Separate client for token refresh — avoids authenticator recursion
    private final OkHttpClient refreshClient;

    public ApiClient(AppSession session) {
        this(session, ServerConfig::getBaseUrl);
    }

    public ApiClient(AppSession session, String baseUrl) {
        this(session, fixedBaseUrl(baseUrl));
    }

    private ApiClient(AppSession session, Supplier<String> baseUrlSupplier) {
        this.session = session;
        this.gson = new GsonBuilder().create();
        this.baseUrlSupplier = baseUrlSupplier;
        this.refreshClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .authenticator(this::authenticate)
                .build();
    }

    // ── HTTP primitives ───────────────────────────────────────────────────────

    public <T> T get(String path, Class<T> responseType) throws ApiException {
        Request request = new Request.Builder()
                .url(url(path))
                .header("Authorization", "Bearer " + session.getAccessToken())
                .get()
                .build();
        return execute(request, responseType);
    }

    public <T> T post(String path, Object body, Class<T> responseType) throws ApiException {
        RequestBody rb = RequestBody.create(gson.toJson(body), JSON);
        Request request = new Request.Builder()
                .url(url(path))
                .header("Authorization", "Bearer " + session.getAccessToken())
                .post(rb)
                .build();
        return execute(request, responseType);
    }

    public <T> T postAuth(String path, Object body, Class<T> responseType) throws ApiException {
        RequestBody rb = RequestBody.create(gson.toJson(body), JSON);
        Request request = new Request.Builder()
                .url(url(path))
                .post(rb)
                .build();
        return execute(request, responseType);
    }

    public <T> T put(String path, Object body, Class<T> responseType) throws ApiException {
        RequestBody rb = RequestBody.create(gson.toJson(body), JSON);
        Request request = new Request.Builder()
                .url(url(path))
                .header("Authorization", "Bearer " + session.getAccessToken())
                .put(rb)
                .build();
        return execute(request, responseType);
    }

    public void put(String path, Object body) throws ApiException {
        RequestBody rb = RequestBody.create(gson.toJson(body), JSON);
        Request request = new Request.Builder()
                .url(url(path))
                .header("Authorization", "Bearer " + session.getAccessToken())
                .put(rb)
                .build();
        executeVoid(request);
    }

    public void delete(String path) throws ApiException {
        Request request = new Request.Builder()
                .url(url(path))
                .header("Authorization", "Bearer " + session.getAccessToken())
                .delete()
                .build();
        executeVoid(request);
    }

    public <T> T delete(String path, Class<T> responseType) throws ApiException {
        Request request = new Request.Builder()
                .url(url(path))
                .header("Authorization", "Bearer " + session.getAccessToken())
                .delete()
                .build();
        return execute(request, responseType);
    }

    public <T> T postMultipart(String path, File file, String partName, Class<T> responseType) throws ApiException {
        RequestBody fileBody = RequestBody.create(file, mediaTypeForImage(file.getName()));
        MultipartBody multipart = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(partName, file.getName(), fileBody)
                .build();
        Request request = new Request.Builder()
                .url(url(path))
                .header("Authorization", "Bearer " + session.getAccessToken())
                .post(multipart)
                .build();
        return execute(request, responseType);
    }

    public <T> T postMultipart(
            String path,
            Map<String, String> fields,
            List<File> files,
            String partName,
            Class<T> responseType
    ) throws ApiException {
        MultipartBody.Builder multipart = new MultipartBody.Builder().setType(MultipartBody.FORM);
        if (fields != null) {
            fields.forEach((name, value) -> {
                if (name != null && !name.isBlank() && value != null) {
                    multipart.addFormDataPart(name, value);
                }
            });
        }
        if (files != null) {
            for (File file : files) {
                if (file == null) {
                    continue;
                }
                RequestBody fileBody = RequestBody.create(file, mediaTypeForImage(file.getName()));
                multipart.addFormDataPart(partName, file.getName(), fileBody);
            }
        }
        Request request = new Request.Builder()
                .url(url(path))
                .header("Authorization", "Bearer " + session.getAccessToken())
                .post(multipart.build())
                .build();
        return execute(request, responseType);
    }

    private static MediaType mediaTypeForImage(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png"))  return MediaType.get("image/png");
        if (lower.endsWith(".webp")) return MediaType.get("image/webp");
        return MediaType.get("image/jpeg");
    }

    /**
     * Descarga bytes de una imagen del backend adjuntando Authorization
     * (SEC-3: /uploads/** requiere JWT). El token solo se envia a URLs del
     * propio backend para no filtrarlo a otros hosts.
     */
    public byte[] fetchImage(String absoluteUrl) throws ApiException {
        HttpUrl requested = normalizeBackendImageUrl(absoluteUrl);
        Request.Builder builder = new Request.Builder().url(requested != null ? requested.toString() : absoluteUrl).get();
        if (requested != null && isApiOrigin(requested)) {
            builder.header("Authorization", "Bearer " + session.getAccessToken());
        }
        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new ApiException(response.code(), "HTTP " + response.code());
            }
            return response.body().bytes();
        } catch (IOException e) {
            throw new ApiException(0, "Network error: " + e.getMessage());
        }
    }

    /** Base URL normalizada (termina en "/"). Necesaria para derivar la URL WebSocket. */
    public String getBaseUrl() {
        return ServerConfig.normalizeAndValidate(baseUrlSupplier.get());
    }

    /**
     * Abre un WebSocket sobre el mismo cliente autenticado. La autenticacion del
     * canal STOMP viaja en el frame CONNECT (no en la URL), gestionada por el
     * llamador. Se reutiliza {@code client} para compartir pool y timeouts.
     */
    public WebSocket newWebSocket(Request request, WebSocketListener listener) {
        return client.newWebSocket(request, listener);
    }

    public void shutdown() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
        refreshClient.dispatcher().executorService().shutdown();
        refreshClient.connectionPool().evictAll();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private <T> T execute(Request request, Class<T> responseType) throws ApiException {
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new ApiException(response.code(), "HTTP " + response.code() + ": " + body);
            }
            return gson.fromJson(body, responseType);
        } catch (IOException e) {
            throw new ApiException(0, "Network error: " + e.getMessage());
        }
    }

    private void executeVoid(Request request) throws ApiException {
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                throw new ApiException(response.code(), "HTTP " + response.code() + ": " + body);
            }
        } catch (IOException e) {
            throw new ApiException(0, "Network error: " + e.getMessage());
        }
    }

    /** OkHttp Authenticator — called automatically on 401. */
    private Request authenticate(Route route, Response response) throws IOException {
        // Nunca responder con credenciales a un 401 de un host ajeno al API
        if (!isApiOrigin(response.request().url())) {
            return null;
        }
        // Prevent infinite retry loops
        if (responseCount(response) >= 2) {
            session.clear();
            return null;
        }
        String refreshToken = session.getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            session.clear();
            return null;
        }

        // Perform token refresh with the separate client
        var refreshBody = new AuthDtos.RefreshRequest(refreshToken);
        RequestBody rb = RequestBody.create(gson.toJson(refreshBody), JSON);
        Request refreshRequest = new Request.Builder()
                .url(url("api/v1/auth/refresh"))
                .post(rb)
                .build();

        try (Response refreshResponse = refreshClient.newCall(refreshRequest).execute()) {
            if (!refreshResponse.isSuccessful() || refreshResponse.body() == null) {
                session.clear();
                return null;
            }
            AuthDtos.AuthResponse authResp = gson.fromJson(refreshResponse.body().string(), AuthDtos.AuthResponse.class);
            session.setTokens(authResp.accessToken(), authResp.refreshToken());
            return response.request().newBuilder()
                    .header("Authorization", "Bearer " + authResp.accessToken())
                    .build();
        }
    }

    private static int responseCount(Response response) {
        int count = 1;
        while ((response = response.priorResponse()) != null) count++;
        return count;
    }

    private boolean isApiOrigin(HttpUrl requested) {
        HttpUrl base = HttpUrl.parse(getBaseUrl());
        return base != null
                && requested.scheme().equals(base.scheme())
                && requested.host().equals(base.host())
                && requested.port() == base.port();
    }

    private HttpUrl normalizeBackendImageUrl(String rawUrl) {
        HttpUrl requested = HttpUrl.parse(rawUrl);
        if (requested == null || isApiOrigin(requested) || !isChatUploadPath(requested)) {
            return requested;
        }
        HttpUrl base = HttpUrl.parse(getBaseUrl());
        return base != null
                ? base.newBuilder()
                        .encodedPath(requested.encodedPath())
                        .encodedQuery(null)
                        .fragment(null)
                        .build()
                : requested;
    }

    private static boolean isChatUploadPath(HttpUrl requested) {
        for (String segment : requested.pathSegments()) {
            if ("..".equals(segment)) {
                return false;
            }
        }
        String path = requested.encodedPath();
        return path.startsWith("/uploads/chat/")
                || path.startsWith("/uploads/chat_thumbnails/");
    }

    private String url(String path) {
        return getBaseUrl() + path;
    }

    private static Supplier<String> fixedBaseUrl(String rawBaseUrl) {
        String normalized = ServerConfig.normalizeAndValidate(rawBaseUrl);
        return () -> normalized;
    }
}
