package org.gipsybuho.recetasfamiliares.ui;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;

import java.util.Objects;

final class DialogStyler {

    private DialogStyler() {}

    static void apply(Dialog<?> dialog) {
        var pane = dialog.getDialogPane();
        addStylesheets(pane.getStylesheets());

        if (!pane.getStyleClass().contains("recetas-dialog-pane")) {
            pane.getStyleClass().add("recetas-dialog-pane");
        }

        for (ButtonType type : pane.getButtonTypes()) {
            Button button = (Button) pane.lookupButton(type);
            if (button == null) continue;
            ButtonBar.ButtonData data = type.getButtonData();
            if (data == ButtonBar.ButtonData.OK_DONE || data == ButtonBar.ButtonData.YES
                    || data == ButtonBar.ButtonData.APPLY) {
                button.getStyleClass().add("action-button-primary");
            } else {
                button.getStyleClass().add("action-button-secondary");
            }
        }
    }

    static void apply(ContextMenu menu) {
        if (!menu.getStyleClass().contains("recetas-context-menu")) {
            menu.getStyleClass().add("recetas-context-menu");
        }
        menu.setOnShown(e -> {
            if (menu.getScene() != null) {
                addStylesheets(menu.getScene().getStylesheets());
            }
        });
    }

    private static void addStylesheets(javafx.collections.ObservableList<String> stylesheets) {
        String base = Objects.requireNonNull(DialogStyler.class.getResource("/style.css")).toExternalForm();
        if (!stylesheets.contains(base)) stylesheets.add(base);

        String theme = ThemeManager.getInstance().stylesheetFor(
                ThemeManager.getInstance().loadTheme(),
                ThemeManager.getInstance().loadMode());
        if (theme != null && !stylesheets.contains(theme)) stylesheets.add(theme);
    }
}
