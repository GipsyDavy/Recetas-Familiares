package org.gipsybuho.recetasfamiliares.ui;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.List;
import java.util.prefs.Preferences;

/**
 * Guia de bienvenida de primer arranque (UX-8/UX-13). Se muestra una sola vez;
 * el usuario puede saltarla en cualquier paso.
 */
final class OnboardingDialog {

    private record Step(String emoji, String title, String body) {}

    private static final List<Step> STEPS = List.of(
        new Step("🍲", "Bienvenido a Recetas Familiares",
            "Tu recetario familiar: recetas, despensa, menus y lista de compra, " +
            "sincronizados con toda tu familia."),
        new Step("📖", "Recetas y modo cocina",
            "Crea recetas con ingredientes, pasos y fotos. Al cocinar, pulsa " +
            "\"Cocinar\" para el modo pantalla completa: navega con ← y →, " +
            "controla el temporizador con Espacio y sal con Esc."),
        new Step("🥕", "Stock y caducidades",
            "Lleva el control de tu despensa: cantidades, caducidades con aviso " +
            "y umbral minimo para no quedarte sin lo esencial."),
        new Step("🗓", "Menu semanal y compra",
            "Planifica la semana asignando recetas a cada comida y genera la " +
            "lista de compra. Busca en todo con Ctrl+F.")
    );

    private static final Preferences PREFS = Preferences.userRoot().node("recetas/ui");
    private static final String SEEN_KEY = "onboardingSeen";

    private OnboardingDialog() {}

    static void showIfFirstRun(Window owner) {
        if (PREFS.getBoolean(SEEN_KEY, false)) return;
        show(owner);
    }

    /** Reabre la guia bajo demanda (Ajustes), aunque ya se haya visto. */
    static void showAgain(Window owner) {
        show(owner);
    }

    private static void show(Window owner) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Bienvenida");

        ButtonType skipType = new ButtonType("Saltar", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType backType = new ButtonType("Anterior", ButtonBar.ButtonData.BACK_PREVIOUS);
        ButtonType nextType = new ButtonType("Siguiente", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(skipType, backType, nextType);

        Label emojiLabel = new Label();
        emojiLabel.setStyle("-fx-font-size: 52px;");

        Label titleLabel = new Label();
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        titleLabel.setWrapText(true);

        Label bodyLabel = new Label();
        bodyLabel.setStyle("-fx-font-size: 14px;");
        bodyLabel.setWrapText(true);
        bodyLabel.setMaxWidth(420);

        HBox dots = new HBox(8);
        dots.setAlignment(Pos.CENTER);
        for (int i = 0; i < STEPS.size(); i++) {
            Label dot = new Label("●");
            dots.getChildren().add(dot);
        }

        VBox content = new VBox(14, emojiLabel, titleLabel, bodyLabel, dots);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(24, 32, 12, 32));
        content.setPrefWidth(480);
        dialog.getDialogPane().setContent(content);
        DialogStyler.apply(dialog);

        Button backBtn = (Button) dialog.getDialogPane().lookupButton(backType);
        Button nextBtn = (Button) dialog.getDialogPane().lookupButton(nextType);

        int[] index = {0};
        Runnable render = () -> {
            Step step = STEPS.get(index[0]);
            emojiLabel.setText(step.emoji());
            titleLabel.setText(step.title());
            bodyLabel.setText(step.body());
            for (int i = 0; i < dots.getChildren().size(); i++) {
                dots.getChildren().get(i).setStyle(
                    "-fx-font-size: 10px; -fx-text-fill: " + (i == index[0] ? "#C17D52" : "#D4BBA8") + ";");
            }
            backBtn.setDisable(index[0] == 0);
            nextBtn.setText(index[0] == STEPS.size() - 1 ? "¡Empezar!" : "Siguiente");
        };
        render.run();

        // Navegacion interna: consumir el evento evita que el dialogo se cierre
        nextBtn.addEventFilter(ActionEvent.ACTION, e -> {
            if (index[0] < STEPS.size() - 1) {
                index[0]++;
                render.run();
                fade(content);
                e.consume();
            }
        });
        backBtn.addEventFilter(ActionEvent.ACTION, e -> {
            if (index[0] > 0) {
                index[0]--;
                render.run();
                fade(content);
            }
            e.consume();
        });

        if (MotionPreferences.isReducedMotion()) {
            dialog.getDialogPane().setOpacity(1.0);
            dialog.getDialogPane().setScaleX(1.0);
            dialog.getDialogPane().setScaleY(1.0);
        } else {
            dialog.getDialogPane().setOpacity(0);
            dialog.setOnShown(e -> {
                var pane = dialog.getDialogPane();
                pane.setOpacity(1);
                ScaleTransition scale = new ScaleTransition(Duration.millis(200), pane);
                scale.setFromX(0.95); scale.setFromY(0.95);
                scale.setToX(1.0);    scale.setToY(1.0);
                scale.play();
            });
        }

        dialog.showAndWait();
        PREFS.putBoolean(SEEN_KEY, true);
    }

    private static void fade(VBox content) {
        if (MotionPreferences.isReducedMotion()) {
            content.setOpacity(1.0);
            return;
        }
        FadeTransition fade = new FadeTransition(Duration.millis(200), content);
        fade.setFromValue(0.4);
        fade.setToValue(1.0);
        fade.play();
    }
}
