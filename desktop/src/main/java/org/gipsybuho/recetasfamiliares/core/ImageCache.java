package org.gipsybuho.recetasfamiliares.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.gipsybuho.recetasfamiliares.api.ApiClient;

/**
 * Cache LRU acotada de imagenes ya descargadas. La descarga la hace
 * {@link ApiClient#fetchImage(String)}, que solo manda el JWT a URLs del propio
 * backend (SEC-3); aqui no se construye ningun cliente HTTP nuevo para no
 * duplicar esa comprobacion.
 *
 * Existe por el reciclado de celdas de las listas de JavaFX: sin cache, cada
 * scroll vuelve a pedir al servidor las mismas miniaturas.
 *
 * NUNCA llamar a fetch desde el JavaFX Application Thread: bloquea en red.
 */
public final class ImageCache {

    /** Marca de fallo: se cachea para no reintentar una URL rota en cada scroll. */
    private static final byte[] FAILED = new byte[0];

    /** Por encima de esto la imagen se devuelve pero no se retiene en memoria. */
    private static final int MAX_CACHED_BYTES = 2 * 1024 * 1024;

    private final ApiClient apiClient;
    private final Map<String, byte[]> cache;

    public ImageCache(ApiClient apiClient, int maxEntries) {
        this.apiClient = apiClient;
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                return size() > maxEntries;
            }
        });
    }

    /** Bytes de la imagen, o null si no se pudo descargar. Los fallos tambien se cachean. */
    public byte[] fetch(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        byte[] cached = cache.get(url);
        if (cached != null) {
            return cached == FAILED ? null : cached;
        }
        try {
            byte[] bytes = apiClient.fetchImage(url);
            if (bytes == null) {
                cache.put(url, FAILED);
                return null;
            }
            if (bytes.length <= MAX_CACHED_BYTES) {
                cache.put(url, bytes);
            }
            return bytes;
        } catch (Exception e) {
            // Sin log: la URL puede identificar una foto familiar concreta.
            cache.put(url, FAILED);
            return null;
        }
    }

    /** Vacia la cache. Obligatorio al cambiar de familia: no deben quedar fotos ajenas. */
    public void clearCache() {
        cache.clear();
    }
}
