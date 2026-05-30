package org.gipsybuho.recetasfamiliares.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.*;
import org.gipsybuho.recetasfamiliares.api.dto.AuthDtos;
import org.gipsybuho.recetasfamiliares.core.AppSession;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String BASE_URL = System.getProperty("api.base.url", "http://localhost:8080/");

    private final AppSession session;
    private final Gson gson;
    private final OkHttpClient client;
    // Separate client for token refresh — avoids authenticator recursion
    private final OkHttpClient refreshClient;

    public ApiClient(AppSession session) {
        this.session = session;
        this.gson = new GsonBuilder().create();
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
                .url(BASE_URL + path)
                .header("Authorization", "Bearer " + session.getAccessToken())
                .get()
                .build();
        return execute(request, responseType);
    }

    public <T> T post(String path, Object body, Class<T> responseType) throws ApiException {
        RequestBody rb = RequestBody.create(gson.toJson(body), JSON);
        Request request = new Request.Builder()
                .url(BASE_URL + path)
                .header("Authorization", "Bearer " + session.getAccessToken())
                .post(rb)
                .build();
        return execute(request, responseType);
    }

    public <T> T postAuth(String path, Object body, Class<T> responseType) throws ApiException {
        RequestBody rb = RequestBody.create(gson.toJson(body), JSON);
        Request request = new Request.Builder()
                .url(BASE_URL + path)
                .post(rb)
                .build();
        return execute(request, responseType);
    }

    public <T> T put(String path, Object body, Class<T> responseType) throws ApiException {
        RequestBody rb = RequestBody.create(gson.toJson(body), JSON);
        Request request = new Request.Builder()
                .url(BASE_URL + path)
                .header("Authorization", "Bearer " + session.getAccessToken())
                .put(rb)
                .build();
        return execute(request, responseType);
    }

    public void put(String path, Object body) throws ApiException {
        RequestBody rb = RequestBody.create(gson.toJson(body), JSON);
        Request request = new Request.Builder()
                .url(BASE_URL + path)
                .header("Authorization", "Bearer " + session.getAccessToken())
                .put(rb)
                .build();
        executeVoid(request);
    }

    public void delete(String path) throws ApiException {
        Request request = new Request.Builder()
                .url(BASE_URL + path)
                .header("Authorization", "Bearer " + session.getAccessToken())
                .delete()
                .build();
        executeVoid(request);
    }

    public <T> T postMultipart(String path, java.io.File file, String partName, Class<T> responseType) throws ApiException {
        RequestBody fileBody = RequestBody.create(file, mediaTypeForImage(file.getName()));
        MultipartBody multipart = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(partName, file.getName(), fileBody)
                .build();
        Request request = new Request.Builder()
                .url(BASE_URL + path)
                .header("Authorization", "Bearer " + session.getAccessToken())
                .post(multipart)
                .build();
        return execute(request, responseType);
    }

    private static MediaType mediaTypeForImage(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png"))  return MediaType.get("image/png");
        if (lower.endsWith(".webp")) return MediaType.get("image/webp");
        return MediaType.get("image/jpeg");
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
                .url(BASE_URL + "api/v1/auth/refresh")
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
}
