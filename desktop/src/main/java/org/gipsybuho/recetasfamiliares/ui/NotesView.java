package org.gipsybuho.recetasfamiliares.ui;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.gipsybuho.recetasfamiliares.api.dto.SyncDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;

import java.util.ArrayList;
import java.util.List;

public class NotesView extends ScrollPane {

    private final AppContext context;
    private final Runnable onSync;
    private final VBox content = new VBox();
    private final Label statusLabel = new Label();
    private final ObservableList<SyncDtos.NoteDtos.FamilyNoteDto> allNotes = FXCollections.observableArrayList();
    private final FilteredList<SyncDtos.NoteDtos.FamilyNoteDto> filteredNotes = new FilteredList<>(allNotes);
    private final ListView<SyncDtos.NoteDtos.FamilyNoteDto> listView = new ListView<>();
    private final TextField filterField = new TextField();
    private final Button loadMoreBtn = new Button("Cargar más notas");
    private int currentPage = 0;
    private boolean hasMore = false;
    private static final int PAGE_SIZE = 30;

    // Right-panel controls
    private final TextField titleField = new TextField();
    private final TextArea bodyArea = new TextArea();
    private final CheckBox pinnedCheck = new CheckBox("Fijar nota");
    private final Button saveBtn = new Button("Guardar");
    private final Button deleteBtn = new Button("Eliminar");
    private final Label detailStatus = new Label();

    private SyncDtos.NoteDtos.FamilyNoteDto editing = null; // null = new note

    public NotesView(AppContext context, Runnable onSync) {
        this.context = context;
        this.onSync = onSync;
        build();
    }

    // ── Build ──────────────────────────────────────────────────────────────────

    private void build() {
        DesktopScroll.configurePage(this, content);
        content.getStyleClass().add("stock-view");
        content.setSpacing(0);
        content.setPadding(new Insets(24));
        setContent(content);

        Label header = new Label("Notas familiares");
        header.getStyleClass().add("view-header");

        Button refreshBtn = new Button("Actualizar");
        refreshBtn.getStyleClass().add("action-button-secondary");
        refreshBtn.setOnAction(e -> onSync.run());

        Button newBtn = new Button("Nueva nota");
        newBtn.getStyleClass().add("action-button-primary");
        newBtn.setOnAction(e -> startNew());

        HBox toolbar = new HBox(12, refreshBtn, newBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8, 0, 16, 0));

        // ── List (left) ───────────────────────────────────────────────────────

        filterField.setPromptText("Buscar notas...");
        filterField.getStyleClass().add("search-field");
        filterField.textProperty().addListener((obs, old, val) -> {
            filteredNotes.setPredicate(val == null || val.isBlank() ? null :
                n -> (n.title() != null && n.title().toLowerCase().contains(val.toLowerCase()))
                  || (n.body()  != null && n.body() .toLowerCase().contains(val.toLowerCase())));
            updateLoadMoreBtn();
        });

        loadMoreBtn.getStyleClass().add("action-button-secondary");
        loadMoreBtn.setMaxWidth(Double.MAX_VALUE);
        loadMoreBtn.setVisible(false);
        loadMoreBtn.setManaged(false);
        loadMoreBtn.setOnAction(e -> loadNextPage());

        listView.setItems(filteredNotes);
        listView.getStyleClass().add("notes-list");
        listView.setPlaceholder(buildEmptyState(
                "📝",
                "Sin notas familiares",
                "Escribe recuerdos, anécdotas y secretos culinarios de vuestra familia"
        ));
        listView.setCellFactory(lv -> new NoteCell());
        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) loadForEdit(sel);
        });

        // ── Detail / edit form (right) ─────────────────────────────────────────

        titleField.setPromptText("Título");
        titleField.getStyleClass().add("recipe-title-small");
        titleField.setMaxWidth(Double.MAX_VALUE);

        bodyArea.setPromptText("Escribe tu nota aquí…");
        bodyArea.setWrapText(true);
        bodyArea.setPrefRowCount(10);
        VBox.setVgrow(bodyArea, Priority.ALWAYS);

        saveBtn.getStyleClass().add("action-button-primary");
        saveBtn.setOnAction(e -> saveNote());

        deleteBtn.getStyleClass().addAll("action-button-secondary", "delete-button");
        deleteBtn.setOnAction(e -> deleteNote());
        deleteBtn.setVisible(false);
        deleteBtn.setManaged(false);

        detailStatus.getStyleClass().add("status-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actionRow = new HBox(10, pinnedCheck, spacer, deleteBtn, saveBtn);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        actionRow.setPadding(new Insets(8, 0, 0, 0));

        VBox detailPane = new VBox(10, titleField, bodyArea, actionRow, detailStatus);
        detailPane.setPadding(new Insets(0, 0, 0, 16));
        detailPane.setPrefWidth(500);
        VBox.setVgrow(detailPane, Priority.ALWAYS);
        setDetailEnabled(false);

        VBox listPane = new VBox(8, filterField, listView, loadMoreBtn);
        VBox.setVgrow(listView, Priority.ALWAYS);
        SplitPane split = new SplitPane(listPane, detailPane);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.38);
        VBox.setVgrow(split, Priority.ALWAYS);

        statusLabel.getStyleClass().add("status-label");
        statusLabel.setPadding(new Insets(8, 0, 0, 0));

        content.getChildren().addAll(header, toolbar, split, statusLabel);
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public void refresh() {
        currentPage = 0;
        hasMore = false;
        updateLoadMoreBtn();
        statusLabel.setText("Cargando...");
        String familyAtStart = context.getSession().getFamilyId();
        Thread.ofVirtual().start(() -> {
            try {
                var page = context.getNoteRepository().loadPage(0, PAGE_SIZE);
                List<SyncDtos.NoteDtos.FamilyNoteDto> notes =
                        page.items() == null ? List.of()
                        : page.items().stream().filter(n -> !n.deleted()).toList();
                Platform.runLater(() -> {
                    if (!java.util.Objects.equals(familyAtStart, context.getSession().getFamilyId())) {
                        return;
                    }
                    allNotes.setAll(notes);
                    context.getNoteRepository().updateCache(notes);
                    hasMore = page.totalPages() > 1;
                    statusLabel.setText(notes.size() + " notas");
                    updateLoadMoreBtn();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("Error al cargar: " + ex.getMessage()));
            }
        });
    }

    private void loadNextPage() {
        loadMoreBtn.setDisable(true);
        int nextPage = currentPage + 1;
        String familyAtStart = context.getSession().getFamilyId();
        Thread.ofVirtual().start(() -> {
            try {
                var page = context.getNoteRepository().loadPage(nextPage, PAGE_SIZE);
                List<SyncDtos.NoteDtos.FamilyNoteDto> newNotes =
                        page.items() == null ? List.of()
                        : page.items().stream().filter(n -> !n.deleted()).toList();
                Platform.runLater(() -> {
                    if (!java.util.Objects.equals(familyAtStart, context.getSession().getFamilyId())) {
                        return;
                    }
                    var appended = new ArrayList<>(allNotes);
                    appended.addAll(newNotes);
                    allNotes.setAll(appended);
                    context.getNoteRepository().updateCache(allNotes);
                    currentPage = nextPage;
                    hasMore = nextPage < page.totalPages() - 1;
                    loadMoreBtn.setDisable(false);
                    statusLabel.setText(allNotes.size() + " notas");
                    updateLoadMoreBtn();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    loadMoreBtn.setDisable(false);
                    statusLabel.setText("Error al cargar más notas.");
                });
            }
        });
    }

    private void updateLoadMoreBtn() {
        boolean show = hasMore && (filterField.getText() == null || filterField.getText().isBlank());
        loadMoreBtn.setVisible(show);
        loadMoreBtn.setManaged(show);
    }

    public void filterBy(String query) {
        filterField.setText(query != null ? query : "");
    }

    // ── Form helpers ───────────────────────────────────────────────────────────

    private void startNew() {
        listView.getSelectionModel().clearSelection();
        editing = null;
        titleField.clear();
        bodyArea.clear();
        pinnedCheck.setSelected(false);
        detailStatus.setText("");
        deleteBtn.setVisible(false);
        deleteBtn.setManaged(false);
        setDetailEnabled(true);
        titleField.requestFocus();
    }

    private void loadForEdit(SyncDtos.NoteDtos.FamilyNoteDto note) {
        editing = note;
        titleField.setText(note.title() != null ? note.title() : "");
        bodyArea.setText(note.body() != null ? note.body() : "");
        pinnedCheck.setSelected(note.pinned());
        detailStatus.setText("");
        deleteBtn.setVisible(true);
        deleteBtn.setManaged(true);
        setDetailEnabled(true);
    }

    private void setDetailEnabled(boolean enabled) {
        titleField.setDisable(!enabled);
        bodyArea.setDisable(!enabled);
        pinnedCheck.setDisable(!enabled);
        saveBtn.setDisable(!enabled);
    }

    private void saveNote() {
        String title = titleField.getText().trim();
        if (title.isBlank()) {
            detailStatus.setText("El título no puede estar vacío.");
            return;
        }
        String body = bodyArea.getText().trim();
        boolean pinned = pinnedCheck.isSelected();

        saveBtn.setDisable(true);
        detailStatus.setText("Guardando...");

        final SyncDtos.NoteDtos.FamilyNoteDto target = editing;
        Thread.ofVirtual().start(() -> {
            try {
                SyncDtos.NoteDtos.FamilyNoteDto saved;
                if (target == null) {
                    saved = context.getNoteRepository().create(title, body, pinned);
                } else {
                    saved = context.getNoteRepository().update(target.id(), title, body, pinned);
                }
                final SyncDtos.NoteDtos.FamilyNoteDto finalSaved = saved;
                Platform.runLater(() -> {
                    // Replace or add in list
                    if (target == null) {
                        allNotes.add(0, finalSaved);
                    } else {
                        int idx = allNotes.indexOf(target);
                        if (idx >= 0) allNotes.set(idx, finalSaved);
                    }
                    editing = finalSaved;
                    deleteBtn.setVisible(true);
                    deleteBtn.setManaged(true);
                    saveBtn.setDisable(false);
                    detailStatus.setText("Guardada");
                    statusLabel.setText(allNotes.size() + " notas");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    saveBtn.setDisable(false);
                    detailStatus.setText("Error: " + ex.getMessage());
                });
            }
        });
    }

    private void deleteNote() {
        if (editing == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Eliminar nota");
        confirm.setHeaderText("¿Eliminar \"" + editing.title() + "\"?");
        confirm.setContentText("Esta acción no se puede deshacer.");
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        DialogStyler.apply(confirm);
        SyncDtos.NoteDtos.FamilyNoteDto note = editing;
        confirm.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) animateDelete(note, () -> doDelete(note));
        });
    }

    private void animateDelete(SyncDtos.NoteDtos.FamilyNoteDto note, Runnable afterAnimation) {
        ListCell<?> cell = findCell(note);
        Node target = cell != null ? cell : listView;

        FadeTransition fade = new FadeTransition(Duration.millis(150), target);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);
        fade.setOnFinished(e -> {
            if (cell == null) {
                listView.setOpacity(1.0);
                afterAnimation.run();
                return;
            }
            double height = cell.getHeight() > 0 ? cell.getHeight() : cell.getPrefHeight();
            cell.setMinHeight(0);
            Timeline collapse = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(cell.prefHeightProperty(), height)),
                    new KeyFrame(Duration.millis(150), new KeyValue(cell.prefHeightProperty(), 0))
            );
            collapse.setOnFinished(ev -> afterAnimation.run());
            collapse.play();
        });
        fade.play();
    }

    private ListCell<?> findCell(SyncDtos.NoteDtos.FamilyNoteDto note) {
        for (Node node : listView.lookupAll(".list-cell")) {
            if (node instanceof ListCell<?> cell && cell.getItem() == note) {
                return cell;
            }
        }
        return null;
    }

    private void doDelete(SyncDtos.NoteDtos.FamilyNoteDto note) {
        deleteBtn.setDisable(true);
        Thread.ofVirtual().start(() -> {
            try {
                context.getNoteRepository().delete(note.id());
                Platform.runLater(() -> {
                    allNotes.remove(note);
                    editing = null;
                    titleField.clear();
                    bodyArea.clear();
                    pinnedCheck.setSelected(false);
                    deleteBtn.setVisible(false);
                    deleteBtn.setManaged(false);
                    deleteBtn.setDisable(false);
                    setDetailEnabled(false);
                    statusLabel.setText(allNotes.size() + " notas");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    deleteBtn.setDisable(false);
                    detailStatus.setText("Error al eliminar: " + ex.getMessage());
                });
            }
        });
    }

    private Node buildEmptyState(String emoji, String title, String subtitle) {
        Label emojiLabel = new Label(emoji);
        emojiLabel.setStyle("-fx-font-size: 48px;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #3D2B1F;");

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #8B6F5E; -fx-text-alignment: center;");
        subtitleLabel.setWrapText(true);
        subtitleLabel.setMaxWidth(320);
        subtitleLabel.setAlignment(Pos.CENTER);

        VBox box = new VBox(12, emojiLabel, titleLabel, subtitleLabel);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        return box;
    }

    // ── Cell ───────────────────────────────────────────────────────────────────

    private static class NoteCell extends ListCell<SyncDtos.NoteDtos.FamilyNoteDto> {
        @Override
        protected void updateItem(SyncDtos.NoteDtos.FamilyNoteDto item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                String titleText = (item.pinned() ? "📌 " : "") + (item.title() != null ? item.title() : "");
                Label title = new Label(titleText);
                title.getStyleClass().add("recipe-title-small");

                String preview = item.body() != null ? item.body().replace('\n', ' ') : "";
                if (preview.length() > 60) preview = preview.substring(0, 60) + "…";
                Label sub = new Label(preview);
                sub.getStyleClass().add("recipe-meta");

                VBox box = new VBox(2, title, sub);
                box.setPadding(new Insets(6, 8, 6, 8));
                setGraphic(box);
                setText(null);
            }
        }
    }
}
