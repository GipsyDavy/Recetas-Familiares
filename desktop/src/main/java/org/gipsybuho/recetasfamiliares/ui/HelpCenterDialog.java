package org.gipsybuho.recetasfamiliares.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import org.gipsybuho.recetasfamiliares.core.HelpContent;

/**
 * Centro de ayuda: el indice completo, para cuando la ayuda de la pantalla no
 * basta. Indice a la izquierda, contenido a la derecha.
 *
 * El texto vive en {@link HelpContent}, no aqui: asi se puede probar y no se
 * mezcla con la interfaz.
 */
final class HelpCenterDialog {

    private HelpCenterDialog() {}

    static void show(Window owner) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Centro de ayuda");
        ButtonType closeType = new ButtonType("Cerrar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(closeType);

        ListView<HelpContent.Section> index = new ListView<>();
        index.getItems().addAll(HelpContent.sections());
        // Ancho suficiente para el titulo mas largo: si no, aparece una barra
        // horizontal y se lee "Apariencia, sonido y accesibilida".
        index.setPrefWidth(285);
        index.setMinWidth(285);
        index.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(HelpContent.Section section, boolean empty) {
                super.updateItem(section, empty);
                setText(empty || section == null ? null : section.emoji() + "  " + section.title());
            }
        });

        VBox detail = new VBox(10);
        detail.setPadding(new Insets(4, 8, 4, 16));
        ScrollPane detailScroll = new ScrollPane(detail);
        DesktopScroll.configurePage(detailScroll, detail);
        detailScroll.setPrefViewportHeight(420);

        index.getSelectionModel().selectedItemProperty().addListener(
                (obs, previous, selected) -> renderSection(detail, selected));
        index.getSelectionModel().selectFirst();

        HBox content = new HBox(index, detailScroll);
        HBox.setHgrow(detailScroll, Priority.ALWAYS);
        content.setPrefWidth(820);
        content.setPadding(new Insets(12, 12, 4, 12));

        dialog.getDialogPane().setContent(content);
        DialogStyler.apply(dialog);
        ((Button) dialog.getDialogPane().lookupButton(closeType)).setDefaultButton(true);
        dialog.showAndWait();
    }

    private static void renderSection(VBox detail, HelpContent.Section section) {
        detail.getChildren().clear();
        if (section == null) {
            return;
        }

        Label title = new Label(section.emoji() + "  " + section.title());
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: bold;");
        detail.getChildren().add(title);

        for (String block : section.blocks()) {
            Label paragraph = new Label("•  " + block);
            paragraph.setWrapText(true);
            paragraph.setMaxWidth(480);
            detail.getChildren().add(paragraph);
        }
    }
}
