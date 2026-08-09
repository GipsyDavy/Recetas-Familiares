package org.gipsybuho.recetasfamiliares.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Interpolator;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.gipsybuho.recetasfamiliares.api.dto.StockDtos;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Shows a brief toast notification (bottom-right, auto-dismiss 5s) when stock items
 * are expiring within the next 3 days.  Called from MainWindow after every sync.
 */
public final class ExpiryNotificationService {

    private ExpiryNotificationService() {}

    public static void showIfNeeded(ObservableList<StockDtos.StockItemDto> items, Stage owner) {
        if (!owner.isShowing()) return;

        String today = LocalDate.now().toString();
        String limit = LocalDate.now().plusDays(3).toString();

        List<StockDtos.StockItemDto> expiring = items.stream()
                .filter(i -> i.expiresAt() != null
                        && i.expiresAt().substring(0, 10).compareTo(today) >= 0
                        && i.expiresAt().substring(0, 10).compareTo(limit) <= 0)
                .toList();

        if (expiring.isEmpty()) return;

        org.gipsybuho.recetasfamiliares.ui.SoundPlayer.play(
                org.gipsybuho.recetasfamiliares.core.SoundEffect.ALERT);

        // ── Build toast ────────────────────────────────────────────────────────
        Stage toast = new Stage();
        toast.initOwner(owner);
        toast.initStyle(StageStyle.TRANSPARENT);
        toast.setAlwaysOnTop(true);

        Label header = new Label("⏰  Stock próximo a caducar");
        header.getStyleClass().add("expiry-toast-title");

        VBox content = new VBox(4, header);
        content.getStyleClass().add("expiry-toast");
        content.setPadding(new Insets(12, 16, 12, 16));
        content.setMaxWidth(280);

        int shown = Math.min(expiring.size(), 4);
        for (int i = 0; i < shown; i++) {
            var item = expiring.get(i);
            String days = daysLabel(item.expiresAt().substring(0, 10), today);
            Label row = new Label("• " + item.name() + "  —  " + days);
            row.getStyleClass().add("expiry-toast-row");
            content.getChildren().add(row);
        }
        if (expiring.size() > 4) {
            Label more = new Label("… y " + (expiring.size() - 4) + " más");
            more.getStyleClass().add("expiry-toast-more");
            content.getChildren().add(more);
        }

        content.setOnMouseClicked(e -> toast.close());

        Scene scene = new Scene(content, Color.TRANSPARENT);
        scene.getStylesheets().add(
                Objects.requireNonNull(ExpiryNotificationService.class.getResource("/style.css")).toExternalForm());
        ThemeManager.getInstance().applyCurrentTheme(scene);
        toast.setScene(scene);

        // ── Position: bottom-right of primary screen ───────────────────────────
        var bounds = Screen.getPrimary().getVisualBounds();
        toast.setOnShown(e -> {
            toast.setX(bounds.getMaxX() - toast.getWidth() - 20);
            toast.setY(bounds.getMaxY() - toast.getHeight() - 20);
        });
        toast.show();

        if (MotionPreferences.isReducedMotion()) {
            content.setTranslateY(0.0);
        } else {
            TranslateTransition slide = new TranslateTransition(Duration.millis(220), content);
            slide.setFromY(60);
            slide.setToY(0);
            slide.setInterpolator(Interpolator.EASE_OUT);
            slide.play();
        }

        // ── Auto-dismiss after 5 s ─────────────────────────────────────────────
        Timeline dismiss = new Timeline(new KeyFrame(Duration.seconds(5), e -> toast.close()));
        dismiss.play();
    }

    private static String daysLabel(String expiresAt, String today) {
        if (expiresAt.equals(today)) return "caduca hoy";
        try {
            long days = java.time.temporal.ChronoUnit.DAYS.between(
                    LocalDate.parse(today), LocalDate.parse(expiresAt));
            return days == 1 ? "caduca mañana" : "caduca en " + days + " días";
        } catch (Exception ex) {
            return "caduca: " + expiresAt;
        }
    }
}
