package org.gipsybuho.recetasfamiliares.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.prefs.Preferences;

public final class ServerConfig {

    public static final String DEFAULT_API_BASE_URL = "https://recetas.167.233.213.242.sslip.io/";
    public static final String SYSTEM_PROPERTY = "api.base.url";

    private static final String PREF_NODE = "recetas";
    private static final String PREF_KEY = "api.base.url";
    private static final Set<String> HTTP_DEV_HOSTS = Set.of("localhost", "127.0.0.1", "10.0.2.2");
    private static final ServerConfig DEFAULT_INSTANCE =
            new ServerConfig(Preferences.userRoot().node(PREF_NODE));

    private final Preferences preferences;

    ServerConfig(Preferences preferences) {
        this.preferences = preferences;
    }

    public static String getBaseUrl() {
        return DEFAULT_INSTANCE.baseUrl();
    }

    public static String getSavedBaseUrl() {
        return DEFAULT_INSTANCE.savedBaseUrl();
    }

    public static boolean hasSystemOverride() {
        return System.getProperty(SYSTEM_PROPERTY) != null
                && !System.getProperty(SYSTEM_PROPERTY).isBlank();
    }

    public static void saveUserBaseUrl(String rawBaseUrl) {
        DEFAULT_INSTANCE.saveBaseUrl(rawBaseUrl);
    }

    public static void resetUserBaseUrl() {
        DEFAULT_INSTANCE.resetBaseUrl();
    }

    public static String normalizeAndValidate(String rawBaseUrl) {
        if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
            throw new IllegalArgumentException("Introduce la URL del servidor.");
        }
        if (rawBaseUrl.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("La URL del servidor no puede contener espacios.");
        }

        URI uri;
        try {
            uri = new URI(rawBaseUrl);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("La URL del servidor no es valida.");
        }

        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : null;
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            throw new IllegalArgumentException("La URL del servidor debe usar https.");
        }
        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("La URL del servidor no puede incluir usuario o contraseña.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("La URL del servidor debe incluir host.");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if ("http".equals(scheme) && !HTTP_DEV_HOSTS.contains(normalizedHost)) {
            throw new IllegalArgumentException("http solo esta permitido para hosts de desarrollo.");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("La URL del servidor no puede incluir query ni fragmento.");
        }
        String path = uri.getRawPath();
        if (path != null && !path.isBlank() && !"/".equals(path)) {
            throw new IllegalArgumentException("La URL del servidor debe apuntar al origen, sin ruta adicional.");
        }

        try {
            return new URI(scheme, null, normalizedHost, uri.getPort(), "/", null, null).toString();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("La URL del servidor no es valida.");
        }
    }

    String baseUrl() {
        String override = System.getProperty(SYSTEM_PROPERTY);
        if (override != null && !override.isBlank()) {
            return normalizeAndValidate(override);
        }
        String saved = preferences.get(PREF_KEY, null);
        if (saved != null && !saved.isBlank()) {
            try {
                return normalizeAndValidate(saved);
            } catch (IllegalArgumentException ex) {
                preferences.remove(PREF_KEY);
            }
        }
        return DEFAULT_API_BASE_URL;
    }

    String savedBaseUrl() {
        return preferences.get(PREF_KEY, DEFAULT_API_BASE_URL);
    }

    void saveBaseUrl(String rawBaseUrl) {
        preferences.put(PREF_KEY, normalizeAndValidate(rawBaseUrl));
    }

    void resetBaseUrl() {
        preferences.remove(PREF_KEY);
    }
}
