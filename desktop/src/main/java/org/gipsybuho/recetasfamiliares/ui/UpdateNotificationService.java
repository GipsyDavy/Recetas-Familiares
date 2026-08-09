package org.gipsybuho.recetasfamiliares.ui;

import java.awt.Desktop;
import java.net.URI;
import java.util.prefs.Preferences;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.gipsybuho.recetasfamiliares.api.dto.AppVersionDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;
import org.gipsybuho.recetasfamiliares.core.AppVersion;
import org.gipsybuho.recetasfamiliares.core.UpdateCheck;

/**
 * Avisa de que hay una version mas nueva publicada.
 *
 * No descarga ni ejecuta nada: abre el navegador del sistema en la URL, que se
 * muestra con su dominio a la vista para que se vea a donde lleva. La decision
 * de avisar vive en {@link UpdateCheck}, que si tiene tests.
 */
public final class UpdateNotificationService {

    private static final String PREF_NODE = "org/gipsybuho/recetasfamiliares";
    private static final String PREF_KEY = "update.dismissed.version";

    private UpdateNotificationService() {}

    /** Consulta en segundo plano. Cualquier fallo se traga: el aviso es opcional. */
    public static void checkInBackground(AppContext context, Stage owner) {
        Thread.ofVirtual().start(() -> {
            try {
                AppVersionDtos.AppVersionResponse response = context.getApiClient()
                        .getPublic("/app-version", AppVersionDtos.AppVersionResponse.class);
                AppVersionDtos.PlatformRelease desktop =
                        response == null ? null : response.desktop();
                if (!UpdateCheck.shouldNotify(desktop, AppVersion.current(), dismissedVersion())) {
                    return;
                }
                Platform.runLater(() -> show(desktop, owner));
            } catch (Exception sinAviso) {
                // Sin red, sin servidor o con una respuesta rara: no se avisa de nada.
                // Enterarse de una actualizacion es opcional; molestar por un fallo
                // de red, no.
            }
        });
    }

    private static String dismissedVersion() {
        return Preferences.userRoot().node(PREF_NODE).get(PREF_KEY, "");
    }

    private static void show(AppVersionDtos.PlatformRelease release, Stage owner) {
        if (!owner.isShowing() || owner.getScene() == null) {
            return;
        }

        Stage toast = new Stage();
        toast.initOwner(owner);
        toast.initStyle(StageStyle.TRANSPARENT);

        Label title = new Label("Hay una versión nueva: " + release.latestVersion());
        title.getStyleClass().add("expiry-toast-title");

        Label detail = new Label("Tienes la " + AppVersion.current()
                + ". Al instalarla encima se conservan tus datos y tu sesión.");
        detail.setWrapText(true);

        Hyperlink download = new Hyperlink("Descargar desde " + host(release.downloadUrl()));
        download.setOnAction(e -> {
            openInBrowser(release.downloadUrl());
            toast.close();
        });

        Hyperlink dismiss = new Hyperlink("No volver a avisar de esta versión");
        dismiss.setOnAction(e -> {
            Preferences.userRoot().node(PREF_NODE).put(PREF_KEY, release.latestVersion());
            toast.close();
        });

        Hyperlink later = new Hyperlink("Más tarde");
        later.setOnAction(e -> toast.close());

        HBox actions = new HBox(12, download, dismiss, later);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(6, title, detail, actions);
        content.getStyleClass().add("expiry-toast");
        content.setPadding(new Insets(12, 16, 12, 16));
        content.setMaxWidth(400);

        Scene scene = new Scene(content);
        scene.setFill(Color.TRANSPARENT);
        owner.getScene().getStylesheets().forEach(scene.getStylesheets()::add);
        toast.setScene(scene);

        // Abajo a la derecha, igual que el aviso de caducidades. A diferencia de
        // aquel, este NO se cierra solo: tiene enlaces que hay que poder pulsar.
        var bounds = Screen.getPrimary().getVisualBounds();
        toast.setX(bounds.getMaxX() - 420);
        toast.setY(bounds.getMaxY() - 170);
        toast.show();
    }

    private static String host(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "el sitio de descargas" : host;
        } catch (IllegalArgumentException urlMalformada) {
            return "el sitio de descargas";
        }
    }

    private static void openInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception sinNavegador) {
            // Sin navegador disponible: el usuario siempre puede ir a mano.
        }
    }
}
