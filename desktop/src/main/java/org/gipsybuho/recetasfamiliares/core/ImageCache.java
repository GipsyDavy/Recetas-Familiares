package org.gipsybuho.recetasfamiliares.core;

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

    private final ApiClient apiClient;
    private final long maxTotalBytes;

    /**
     * Acceso siempre bajo synchronized(cache): la expulsion por presupuesto necesita
     * recorrer el mapa, y eso no lo cubre un synchronizedMap por si solo.
     */
    private final LinkedHashMap<String, byte[]> cache;
    private long totalBytes;

    /**
     * @param maxEntries    techo de entradas (incluye los fallos, que ocupan 0 bytes)
     * @param maxTotalBytes presupuesto de memoria. Una portada sin thumbnail cae a la
     *                      imagen original, que puede pesar varios MB: sin este limite
     *                      un catalogo de fotos grandes agota la memoria del cliente.
     */
    public ImageCache(ApiClient apiClient, int maxEntries, long maxTotalBytes) {
        this.apiClient = apiClient;
        this.maxTotalBytes = maxTotalBytes;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                if (size() > maxEntries) {
                    totalBytes -= eldest.getValue().length;
                    return true;
                }
                return false;
            }
        };
    }

    /** Bytes de la imagen, o null si no se pudo descargar. Los fallos tambien se cachean. */
    public byte[] fetch(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        synchronized (cache) {
            byte[] cached = cache.get(url);
            if (cached != null) {
                return cached == FAILED ? null : cached;
            }
        }
        byte[] bytes;
        try {
            bytes = apiClient.fetchImage(url);
        } catch (Exception e) {
            // Sin log: la URL puede identificar una foto familiar concreta.
            bytes = null;
        }
        store(url, bytes);
        return bytes;
    }

    private void store(String url, byte[] bytes) {
        synchronized (cache) {
            if (bytes == null) {
                cache.put(url, FAILED);
                return;
            }
            if (bytes.length > maxTotalBytes) {
                // No cabe ni sola: se devuelve al llamante pero no se retiene.
                return;
            }
            byte[] previous = cache.put(url, bytes);
            totalBytes += bytes.length - (previous == null ? 0 : previous.length);
            evictUntilWithinBudget(url);
        }
    }

    /** Expulsa las entradas menos usadas hasta caber en el presupuesto, salvo la recien puesta. */
    private void evictUntilWithinBudget(String keepUrl) {
        var iterator = cache.entrySet().iterator();
        while (totalBytes > maxTotalBytes && iterator.hasNext()) {
            Map.Entry<String, byte[]> eldest = iterator.next();
            if (eldest.getKey().equals(keepUrl)) {
                continue;
            }
            totalBytes -= eldest.getValue().length;
            iterator.remove();
        }
    }

    /** Vacia la cache. Obligatorio al cambiar de familia: no deben quedar fotos ajenas. */
    public void clearCache() {
        synchronized (cache) {
            cache.clear();
            totalBytes = 0;
        }
    }
}
