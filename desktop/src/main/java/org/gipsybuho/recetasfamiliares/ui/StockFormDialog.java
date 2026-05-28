package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.gipsybuho.recetasfamiliares.api.dto.StockDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;

import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Consumer;

public class StockFormDialog {

    private final String editItemId;
    private final Stage dialog;
    private final AppContext context;
    private final Consumer<StockDtos.StockItemDto> onSaved;

    private final TextField nameField = new TextField();
    private final TextField quantityField = new TextField();
    private final TextField unitField = new TextField();
    private final DatePicker expiresAtPicker = new DatePicker();
    private final TextField lowStockThresholdField = new TextField();
    private final TextArea noteArea = new TextArea();
    private final Label statusLabel = new Label();

    // ── Factory methods ───────────────────────────────────────────────────────

    public static StockFormDialog forCreate(Window owner, AppContext context,
                                            Consumer<StockDtos.StockItemDto> onCreated) {
        return new StockFormDialog(owner, context, null, onCreated);
    }

    public static StockFormDialog forEdit(Window owner, AppContext context,
                                          StockDtos.StockItemDto item,
                                          Consumer<StockDtos.StockItemDto> onUpdated) {
        StockFormDialog dlg = new StockFormDialog(owner, context, item.id(), onUpdated);
        dlg.prefill(item);
        return dlg;
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    private StockFormDialog(Window owner, AppContext context,
                            String editItemId, Consumer<StockDtos.StockItemDto> onSaved) {
        this.context = context;
        this.editItemId = editItemId;
        this.onSaved = onSaved;
        this.dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(editItemId == null ? "Nuevo item de stock" : "Editar item de stock");
        dialog.setMinWidth(500);
        dialog.setMinHeight(380);
        build();
    }

    public void show() {
        dialog.show();
    }

    // ── Pre-fill (edit mode) ──────────────────────────────────────────────────

    private void prefill(StockDtos.StockItemDto item) {
        nameField.setText(item.name() != null ? item.name() : "");
        if (item.quantity() != null) quantityField.setText(item.quantity().toString());
        if (item.unit() != null) unitField.setText(item.unit());
        if (item.expiresAt() != null) {
            try {
                expiresAtPicker.setValue(LocalDate.parse(item.expiresAt().substring(0, 10)));
            } catch (Exception ignored) {}
        }
        if (item.lowStockThreshold() != null) lowStockThresholdField.setText(item.lowStockThreshold().toString());
        if (item.note() != null) noteArea.setText(item.note());
    }

    // ── Build UI ──────────────────────────────────────────────────────────────

    private void build() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(24));
        content.getStyleClass().add("form-content");
        content.getChildren().addAll(
                buildPrimarySection(),
                buildAdvancedSection(),
                buildFooter()
        );

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Scene scene = new Scene(scroll, 500, 420);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm());
        dialog.setScene(scene);
    }

    private VBox buildPrimarySection() {
        nameField.setPromptText("Nombre del ingrediente *");
        nameField.getStyleClass().add("form-field");

        quantityField.setPromptText("Cantidad");
        quantityField.getStyleClass().add("form-field-small");
        unitField.setPromptText("Unidad (kg, L, ud…)");
        unitField.getStyleClass().add("form-field-small");
        HBox qtyRow = new HBox(10, quantityField, unitField);
        HBox.setHgrow(quantityField, Priority.ALWAYS);
        HBox.setHgrow(unitField, Priority.ALWAYS);

        expiresAtPicker.setPromptText("Fecha de caducidad");
        expiresAtPicker.getStyleClass().add("form-field");
        expiresAtPicker.setMaxWidth(Double.MAX_VALUE);

        return buildFormSection("Información básica", nameField, qtyRow, expiresAtPicker);
    }

    private TitledPane buildAdvancedSection() {
        lowStockThresholdField.setPromptText("Umbral de stock bajo (ej. 2.0)");
        lowStockThresholdField.getStyleClass().add("form-field");

        noteArea.setPromptText("Notas adicionales");
        noteArea.setPrefRowCount(2);
        noteArea.setWrapText(true);
        noteArea.getStyleClass().add("form-field");

        VBox inner = new VBox(10, lowStockThresholdField, noteArea);
        inner.setPadding(new Insets(12, 16, 12, 16));

        TitledPane pane = new TitledPane("Avanzado (opcional)", inner);
        pane.setExpanded(false);
        return pane;
    }

    private HBox buildFooter() {
        statusLabel.getStyleClass().add("status-label");

        Button cancelBtn = new Button("Cancelar");
        cancelBtn.getStyleClass().add("action-button-secondary");
        cancelBtn.setOnAction(e -> dialog.close());

        String saveLabel = editItemId == null ? "Guardar item" : "Guardar cambios";
        Button saveBtn = new Button(saveLabel);
        saveBtn.getStyleClass().add("action-button-primary");
        saveBtn.setOnAction(e -> doSave(saveBtn));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox footer = new HBox(10, statusLabel, spacer, cancelBtn, saveBtn);
        footer.setPadding(new Insets(8, 0, 0, 0));
        return footer;
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void doSave(Button saveBtn) {
        String name = nameField.getText().trim();
        if (name.isBlank()) { statusLabel.setText("El nombre es obligatorio."); return; }

        saveBtn.setDisable(true);
        statusLabel.setText("Guardando...");

        Double quantity = parseDoubleOrNull(quantityField.getText());
        String unit = unitField.getText().trim().isEmpty() ? null : unitField.getText().trim();
        String expiresAt = expiresAtPicker.getValue() != null ? expiresAtPicker.getValue().toString() : null;
        Double lowStock = parseDoubleOrNull(lowStockThresholdField.getText());
        String note = noteArea.getText().trim().isEmpty() ? null : noteArea.getText().trim();

        Thread.ofVirtual().start(() -> {
            try {
                StockDtos.StockItemDto saved;
                if (editItemId == null) {
                    saved = context.getStockRepository().create(
                            new StockDtos.CreateStockItemRequest(name, quantity, unit, lowStock, expiresAt, note));
                } else {
                    saved = context.getStockRepository().update(editItemId,
                            new StockDtos.UpdateStockItemRequest(name, quantity, unit, lowStock, expiresAt, note));
                }
                Platform.runLater(() -> { onSaved.accept(saved); dialog.close(); });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    saveBtn.setDisable(false);
                    statusLabel.setText("Error: " + ex.getMessage());
                });
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private VBox buildFormSection(String title, javafx.scene.Node... nodes) {
        Label header = new Label(title);
        header.getStyleClass().add("form-section-header");
        VBox section = new VBox(10);
        section.getStyleClass().add("form-section");
        section.setPadding(new Insets(16));
        section.getChildren().add(header);
        section.getChildren().addAll(nodes);
        return section;
    }

    private Double parseDoubleOrNull(String text) {
        if (text == null || text.isBlank()) return null;
        try { return Double.parseDouble(text.trim()); }
        catch (NumberFormatException e) { return null; }
    }
}
