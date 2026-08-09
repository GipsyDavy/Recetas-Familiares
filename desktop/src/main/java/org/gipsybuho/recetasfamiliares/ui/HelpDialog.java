package org.gipsybuho.recetasfamiliares.ui;

import javafx.animation.ScaleTransition;
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

import org.gipsybuho.recetasfamiliares.core.HelpContent;

/**
 * Ayuda de la pantalla en la que estas. Se abre con F1 o con el boton Ayuda del
 * menu lateral.
 *
 * El texto vive en {@link HelpContent}, que no depende de JavaFX y tiene tests.
 * Desde aqui se llega al centro de ayuda y a la guia de bienvenida.
 */
final class HelpDialog {

    private HelpDialog() {}

    static void show(Window owner, String viewKey) {
        HelpContent.Topic topic = HelpContent.topicOrGeneral(viewKey);

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Ayuda");
        // "Cerrar" a la derecha y por defecto. Con la disposicion anterior, la
        // guia de bienvenida caia donde se espera "Aceptar" y se pulsaba por
        // inercia: la ayuda desaparecia y salia la bienvenida.
        ButtonType closeType = new ButtonType("Cerrar", ButtonBar.ButtonData.OK_DONE);
        ButtonType centerType = new ButtonType("Todos los temas", ButtonBar.ButtonData.LEFT);
        ButtonType guideType = new ButtonType("Guía de bienvenida", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(centerType, guideType, closeType);

        Label emojiLabel = new Label(topic.emoji());
        emojiLabel.setStyle("-fx-font-size: 40px;");
        Label titleLabel = new Label(topic.title());
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        HBox headerRow = new HBox(12, emojiLabel, titleLabel);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        VBox tipsBox = new VBox(8);
        for (String tip : topic.tips()) {
            Label tipLabel = new Label("•  " + tip);
            tipLabel.setWrapText(true);
            tipLabel.setMaxWidth(440);
            tipsBox.getChildren().add(tipLabel);
        }

        VBox content = new VBox(14, headerRow, tipsBox);
        content.setPadding(new Insets(20, 28, 12, 28));
        content.setPrefWidth(540);
        dialog.getDialogPane().setContent(content);
        DialogStyler.apply(dialog);

        Button centerBtn = (Button) dialog.getDialogPane().lookupButton(centerType);
        // Sin esto, ButtonBar iguala el ancho de todos los botones al del mas
        // ancho y los textos largos se truncan a "Guia de bienve...".
        ButtonBar.setButtonUniformSize(centerBtn, false);
        centerBtn.getStyleClass().add("action-button-secondary");
        centerBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            HelpCenterDialog.show(owner);
            e.consume();
        });

        Button guideBtn = (Button) dialog.getDialogPane().lookupButton(guideType);
        ButtonBar.setButtonUniformSize(guideBtn, false);
        guideBtn.getStyleClass().add("action-button-secondary");
        guideBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            OnboardingDialog.showAgain(owner);
            e.consume();
        });

        Button closeBtn = (Button) dialog.getDialogPane().lookupButton(closeType);
        closeBtn.setDefaultButton(true);

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
    }
}
