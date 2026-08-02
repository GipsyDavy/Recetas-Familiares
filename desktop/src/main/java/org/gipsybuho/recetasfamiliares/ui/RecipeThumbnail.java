package org.gipsybuho.recetasfamiliares.ui;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.gipsybuho.recetasfamiliares.core.AppContext;

import java.io.ByteArrayInputStream;
import java.util.Objects;

/**
 * Miniatura de portada de receta, reutilizable en cualquier vista.
 *
 * Nace de RecipeCell, que era el unico sitio con esta logica: al llevarla a mas
 * listados hacia falta una sola implementacion del guard de url, del presupuesto
 * de imagen y del fade, en vez de una copia por vista.
 *
 * La descarga la hace ImageCache, que a su vez usa ApiClient.fetchImage: NUNCA
 * construir aqui un cliente HTTP propio, porque las urls salen de la base de datos
 * y el token solo debe viajar al origen del backend.
 */
public final class RecipeThumbnail extends StackPane {

    /** Tamaño usado en listados y tarjetas: dashboard, búsqueda global y lista de recetas. */
    public static final double LIST_SIZE = 56;
    /** Tamaño usado en las celdas del menú semanal, más compactas que un listado. */
    public static final double MENU_SIZE = 40;

    private static final double CORNER_RADIUS = 12;
    private static final double FADE_MILLIS = 150;

    private final AppContext context;
    private final double size;
    private final ImageView thumb = new ImageView();
    private final Region placeholder = new Region();

    /**
     * Url de la carga en curso. Si el nodo se reutiliza -- celda reciclada de una
     * ListView, o vista que se vuelve a pintar -- el resultado viejo se descarta en
     * vez de pintar la foto de otra receta.
     */
    private String pendingUrl;

    public RecipeThumbnail(AppContext context, double size) {
        this.context = context;
        this.size = size;

        thumb.setFitWidth(size);
        thumb.setFitHeight(size);
        thumb.setPreserveRatio(true);
        thumb.setSmooth(true);
        Rectangle clip = new Rectangle(size, size);
        clip.setArcWidth(CORNER_RADIUS);
        clip.setArcHeight(CORNER_RADIUS);
        thumb.setClip(clip);

        placeholder.setPrefSize(size, size);
        placeholder.setMinSize(size, size);
        placeholder.setMaxSize(size, size);
        placeholder.getStyleClass().add("recipe-cell-thumb-placeholder");

        setPrefSize(size, size);
        setMinSize(size, size);
        getChildren().addAll(placeholder, thumb);
    }

    /**
     * Pinta la portada de esa url. Con null o vacio deja el placeholder visible, que
     * es lo que corresponde a una receta sin fotos. Llamar en el JavaFX Application
     * Thread: la red se hace dentro, en un hilo virtual.
     */
    public void show(String url) {
        pendingUrl = url;
        thumb.setImage(null);
        thumb.setOpacity(0);
        if (url == null || url.isBlank()) {
            return;
        }
        Thread.ofVirtual().start(() -> {
            byte[] bytes = context.getImageCache().fetch(url);
            if (bytes == null) {
                return;
            }
            Image image = new Image(new ByteArrayInputStream(bytes), size * 2, size * 2, true, true);
            Platform.runLater(() -> {
                if (!Objects.equals(pendingUrl, url) || image.isError()) {
                    return;
                }
                thumb.setImage(image);
                if (MotionPreferences.isReducedMotion()) {
                    thumb.setOpacity(1);
                } else {
                    FadeTransition fade = new FadeTransition(Duration.millis(FADE_MILLIS), thumb);
                    fade.setFromValue(0);
                    fade.setToValue(1);
                    fade.play();
                }
            });
        });
    }

    /** Descarta la carga en vuelo y vuelve al placeholder. */
    public void clear() {
        show(null);
    }
}
