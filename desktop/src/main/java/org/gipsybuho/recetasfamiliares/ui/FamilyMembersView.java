package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.api.dto.FamilyDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;
import org.gipsybuho.recetasfamiliares.core.FamilyRole;

import java.util.List;
import java.util.Optional;

public class FamilyMembersView extends ScrollPane {

    private final AppContext context;
    private final VBox content = new VBox();
    private final Label statusLabel  = new Label();
    private final Label familyLabel  = new Label("—");
    private final Label roleLabel    = new Label("—");
    private final TableView<MemberRow> table = buildTable();
    private final Button addBtn        = new Button("Añadir miembro");
    private final Button editBtn       = new Button("Editar");
    private final Button changeRoleBtn = new Button("Cambiar rol");
    private final Button removeBtn     = new Button("Expulsar");
    private final Button createFamilyBtn = new Button("Crear familia");
    private final Runnable onFamiliesChanged;

    public FamilyMembersView(AppContext context, Runnable onFamiliesChanged) {
        this.context = context;
        this.onFamiliesChanged = onFamiliesChanged;
        build();
        context.getChatRepository().setPresenceListener(online ->
                Platform.runLater(() -> applyPresence(online)));
        refresh();
    }

    private void build() {
        DesktopScroll.configurePage(this, content);
        content.getStyleClass().add("content-area");
        content.setSpacing(20);
        content.setPadding(new Insets(28, 32, 28, 32));
        setContent(content);

        // ── Header ────────────────────────────────────────────────────────────
        Text title = new Text("👨‍👩‍👧  Miembros de la familia");
        title.getStyleClass().add("view-title");

        Button refreshBtn = new Button("Actualizar");
        refreshBtn.getStyleClass().add("action-button-secondary");
        refreshBtn.setOnAction(e -> refresh());
        Tooltip.install(refreshBtn, new Tooltip("Recargar lista de miembros"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(12, title, spacer, refreshBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        // ── Info card ─────────────────────────────────────────────────────────
        familyLabel.getStyleClass().add("recipe-title");
        roleLabel.getStyleClass().add("recipe-meta");

        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(12);
        infoGrid.setVgap(8);
        infoGrid.add(metaKey("Familia:"), 0, 0);
        infoGrid.add(familyLabel,         1, 0);
        infoGrid.add(metaKey("Tu rol:"),  0, 1);
        infoGrid.add(roleLabel,           1, 1);

        VBox infoCard = new VBox(8, infoGrid);
        infoCard.getStyleClass().add("detail-card");
        infoCard.setPadding(new Insets(16));

        // ── Action toolbar (only visible to ADMIN/OWNER) ──────────────────────
        addBtn.getStyleClass().add("action-button-primary");
        addBtn.setOnAction(e -> onAddMember());
        Tooltip.install(addBtn, new Tooltip("Crear o añadir miembro a la familia"));

        editBtn.getStyleClass().add("action-button-secondary");
        editBtn.setDisable(true);
        editBtn.setOnAction(e -> onEditMember());
        Tooltip.install(editBtn, new Tooltip("Editar datos o contraseña del miembro seleccionado"));

        changeRoleBtn.getStyleClass().add("action-button-secondary");
        changeRoleBtn.setDisable(true);
        changeRoleBtn.setOnAction(e -> onChangeRole());
        Tooltip.install(changeRoleBtn, new Tooltip("Cambiar rol del miembro seleccionado"));

        removeBtn.getStyleClass().add("action-button-danger");
        removeBtn.setDisable(true);
        removeBtn.setOnAction(e -> onRemoveMember());
        Tooltip.install(removeBtn, new Tooltip("Expulsar miembro seleccionado de la familia"));

        createFamilyBtn.getStyleClass().add("action-button-secondary");
        createFamilyBtn.setOnAction(e -> onCreateFamily());
        Tooltip.install(createFamilyBtn, new Tooltip("Crear una nueva familia; serás su propietario"));

        FlowPane toolbar = new FlowPane(8, 8, addBtn, editBtn, changeRoleBtn, removeBtn, createFamilyBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        boolean isAdmin = context.getSession().isAdmin();
        toolbar.setVisible(isAdmin);
        toolbar.setManaged(isAdmin);

        // Update button states when selection changes
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) ->
                updateButtonStates(selected));
        table.setRowFactory(tv -> {
            TableRow<MemberRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty() && canManage(row.getItem())) {
                    table.getSelectionModel().select(row.getItem());
                    onEditMember();
                }
            });
            return row;
        });

        // ── Status bar ────────────────────────────────────────────────────────
        statusLabel.getStyleClass().add("status-label");

        content.getChildren().addAll(header, infoCard, toolbar, table, statusLabel);
        VBox.setVgrow(table, Priority.ALWAYS);
    }

    private TableView<MemberRow> buildTable() {
        TableView<MemberRow> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tv.getStyleClass().add("data-table");
        tv.setPlaceholder(new Label("Sin miembros"));
        tv.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        TableColumn<MemberRow, MemberRow> onlineCol = new TableColumn<>("");
        onlineCol.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        onlineCol.setCellFactory(col -> new TableCell<>() {
            private final Circle dot = new Circle(5);

            @Override
            protected void updateItem(MemberRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                dot.getStyleClass().setAll(row.isOnline() ? "presence-dot-online" : "presence-dot-offline");
                setGraphic(dot);
            }
        });
        onlineCol.setSortable(false);
        onlineCol.setResizable(false);
        onlineCol.setMinWidth(28);
        onlineCol.setMaxWidth(28);

        TableColumn<MemberRow, String> nameCol = new TableColumn<>("Nombre");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("displayName"));
        nameCol.setMinWidth(160);

        TableColumn<MemberRow, String> emailCol = new TableColumn<>("Correo electrónico");
        emailCol.setCellValueFactory(new PropertyValueFactory<>("email"));
        emailCol.setMinWidth(200);

        TableColumn<MemberRow, String> roleCol = new TableColumn<>("Rol");
        roleCol.setCellValueFactory(new PropertyValueFactory<>("roleLabel"));
        roleCol.setMinWidth(120);

        tv.getColumns().add(onlineCol);
        tv.getColumns().add(nameCol);
        tv.getColumns().add(emailCol);
        tv.getColumns().add(roleCol);
        return tv;
    }

    public void refresh() {
        statusLabel.setText("Cargando miembros...");
        table.getItems().clear();
        String familyId = context.getSession().getFamilyId();
        if (familyId == null || familyId.isBlank()) {
            Platform.runLater(() -> statusLabel.setText("Sin sesión de familia activa."));
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                // Load family name
                FamilyDtos.FamilyResponse[] families = context.getFamilyRepository().loadMyFamilies();
                // Load members
                FamilyDtos.FamilyMemberResponse[] members = context.getFamilyRepository().loadMembers(familyId);
                Platform.runLater(() -> {
                    if (families.length > 0) {
                        familyLabel.setText(families[0].name() != null ? families[0].name() : "—");
                    }
                    FamilyRole myRole = context.getSession().getFamilyRole();
                    roleLabel.setText(myRole != null ? myRole.displayName() : "—");

                    table.getItems().clear();
                    String myEmail = context.getSession().getEmail();
                    for (FamilyDtos.FamilyMemberResponse m : members) {
                        boolean isSelf = myEmail != null && myEmail.equalsIgnoreCase(m.email());
                        table.getItems().add(new MemberRow(
                                m.userId(), m.displayName(), m.email(), m.role(), isSelf));
                    }
                    applyPresence(context.getChatRepository().lastOnlineUserIds());
                    statusLabel.setText(members.length + " miembro(s)");
                });
                FamilyDtos.PresenceResponse presence = context.getFamilyRepository().loadPresence(familyId);
                Platform.runLater(() -> applyPresence(new java.util.HashSet<>(presence.onlineUserIds())));
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("Error al cargar: " + ex.getMessage()));
            }
        });
    }

    /** Aplica un snapshot de presencia a las filas ya cargadas. Llamado en el hilo JavaFX. */
    private void applyPresence(java.util.Set<String> onlineUserIds) {
        for (MemberRow row : table.getItems()) {
            row.setOnline(onlineUserIds.contains(row.getUserId()));
        }
        table.refresh();
    }

    private void onChangeRole() {
        MemberRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        ChoiceDialog<String> dialog = new ChoiceDialog<>("ADMIN", List.of("ADMIN", "MEMBER"));
        dialog.setTitle("Cambiar rol");
        dialog.setHeaderText("Cambiar rol de " + selected.getDisplayName());
        dialog.setContentText("Nuevo rol:");
        DialogStyler.apply(dialog);

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newRole -> {
            String familyId = context.getSession().getFamilyId();
            statusLabel.setText("Cambiando rol...");
            editBtn.setDisable(true);
            changeRoleBtn.setDisable(true);
            removeBtn.setDisable(true);
            Thread.ofVirtual().start(() -> {
                try {
                    context.getFamilyRepository().updateMemberRole(familyId, selected.getUserId(), newRole);
                    Platform.runLater(() -> {
                        statusLabel.setText("Rol actualizado correctamente.");
                        refresh();
                    });
                } catch (ApiException ex) {
                    Platform.runLater(() -> statusLabel.setText("Error: " + ex.getMessage()));
                }
            });
        });
    }

    private void onCreateFamily() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Crear familia");
        dialog.setHeaderText("Crear una nueva familia");
        dialog.setContentText("Nombre de la nueva familia:");
        DialogStyler.apply(dialog);

        Optional<String> result = dialog.showAndWait();
        result.map(String::trim).filter(name -> !name.isEmpty()).ifPresent(name -> {
            statusLabel.setText("Creando familia...");
            createFamilyBtn.setDisable(true);
            Thread.ofVirtual().start(() -> {
                try {
                    FamilyDtos.FamilyResponse created = context.getFamilyRepository().createFamily(name);
                    Platform.runLater(() -> {
                        createFamilyBtn.setDisable(false);
                        statusLabel.setText("Familia creada: " + created.name()
                                + ". Cámbiala desde el selector del menú lateral.");
                        if (onFamiliesChanged != null) onFamiliesChanged.run();
                    });
                } catch (ApiException ex) {
                    Platform.runLater(() -> {
                        createFamilyBtn.setDisable(false);
                        statusLabel.setText(createFamilyErrorMessage(ex));
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        createFamilyBtn.setDisable(false);
                        statusLabel.setText("No se pudo crear la familia.");
                    });
                }
            });
        });
    }

    private String createFamilyErrorMessage(ApiException ex) {
        return switch (ex.getHttpStatus()) {
            case 403 -> "Necesitas ser propietario o administrador para crear familias.";
            case 400 -> "No se pudo crear: revisa el nombre o has alcanzado el límite de familias.";
            default -> "No se pudo crear la familia.";
        };
    }

    private void onEditMember() {
        MemberRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null || !canManage(selected)) return;

        String familyId = context.getSession().getFamilyId();
        if (familyId == null || familyId.isBlank()) {
            statusLabel.setText("Sin sesión de familia activa.");
            return;
        }

        Dialog<EditForm> dialog = new Dialog<>();
        dialog.setTitle("Editar miembro");
        dialog.setHeaderText(selected.getDisplayName());

        ButtonType saveType = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField nameField = new TextField(selected.getDisplayName());
        nameField.setMaxWidth(Double.MAX_VALUE);

        TextField emailField = new TextField(selected.getEmail());
        emailField.setMaxWidth(Double.MAX_VALUE);

        ChoiceBox<PasswordActionOption> passwordActionBox = new ChoiceBox<>();
        passwordActionBox.getItems().addAll(PasswordActionOption.NONE, PasswordActionOption.SEND_RESET, PasswordActionOption.SET_TEMPORARY);
        passwordActionBox.setValue(PasswordActionOption.NONE);
        passwordActionBox.setMaxWidth(Double.MAX_VALUE);

        PasswordField temporaryPasswordField = new PasswordField();
        temporaryPasswordField.setPromptText("Contraseña temporal");
        temporaryPasswordField.setMaxWidth(Double.MAX_VALUE);
        temporaryPasswordField.setDisable(true);
        passwordActionBox.valueProperty().addListener((obs, old, value) -> {
            boolean enabled = value == PasswordActionOption.SET_TEMPORARY;
            temporaryPasswordField.setDisable(!enabled);
            if (!enabled) {
                temporaryPasswordField.clear();
            }
        });

        Label error = new Label();
        error.getStyleClass().add("login-error");
        error.setVisible(false);
        error.setManaged(false);
        error.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.add(metaKey("Nombre:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(metaKey("Email:"), 0, 1);
        grid.add(emailField, 1, 1);
        grid.add(metaKey("Contraseña:"), 0, 2);
        grid.add(passwordActionBox, 1, 2);
        grid.add(metaKey("Temporal:"), 0, 3);
        grid.add(temporaryPasswordField, 1, 3);
        grid.add(error, 1, 4);
        ColumnConstraints keyCol = new ColumnConstraints();
        keyCol.setMinWidth(92);
        ColumnConstraints valueCol = new ColumnConstraints();
        valueCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(keyCol, valueCol);

        dialog.getDialogPane().setContent(grid);
        DialogStyler.apply(dialog);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String message = editValidationMessage(
                    nameField.getText(),
                    emailField.getText(),
                    passwordActionBox.getValue(),
                    temporaryPasswordField.getText());
            if (message != null) {
                error.setText(message);
                error.setVisible(true);
                error.setManaged(true);
                event.consume();
            }
        });

        dialog.setResultConverter(button -> {
            if (button != saveType) return null;
            PasswordActionOption action = passwordActionBox.getValue();
            String temporaryPassword = action == PasswordActionOption.SET_TEMPORARY
                    ? temporaryPasswordField.getText()
                    : null;
            return new EditForm(
                    nameField.getText().trim(),
                    emailField.getText().trim(),
                    action.apiValue(),
                    temporaryPassword
            );
        });

        dialog.showAndWait().ifPresent(form -> {
            statusLabel.setText("Actualizando miembro...");
            editBtn.setDisable(true);
            changeRoleBtn.setDisable(true);
            removeBtn.setDisable(true);
            Thread.ofVirtual().start(() -> {
                try {
                    context.getFamilyRepository().updateMember(
                            familyId,
                            selected.getUserId(),
                            form.displayName(),
                            form.email(),
                            form.passwordAction(),
                            form.temporaryPassword());
                    Platform.runLater(() -> {
                        statusLabel.setText("Miembro actualizado correctamente.");
                        refresh();
                    });
                } catch (ApiException ex) {
                    Platform.runLater(() -> {
                        statusLabel.setText("Error: " + ex.getMessage());
                        updateButtonStates(table.getSelectionModel().getSelectedItem());
                    });
                }
            });
        });
    }

    private void onAddMember() {
        String familyId = context.getSession().getFamilyId();
        if (familyId == null || familyId.isBlank()) {
            statusLabel.setText("Sin sesión de familia activa.");
            return;
        }

        Dialog<InviteForm> dialog = new Dialog<>();
        dialog.setTitle("Añadir miembro");
        dialog.setHeaderText(null);

        ButtonType addType = new ButtonType("Añadir", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addType, ButtonType.CANCEL);

        TextField emailField = new TextField();
        emailField.setPromptText("correo@ejemplo.com");
        emailField.setMaxWidth(Double.MAX_VALUE);

        TextField nameField = new TextField();
        nameField.setPromptText("Nombre visible");
        nameField.setMaxWidth(Double.MAX_VALUE);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Contraseña temporal");
        passwordField.setMaxWidth(Double.MAX_VALUE);

        ChoiceBox<String> roleBox = new ChoiceBox<>();
        roleBox.getItems().addAll("MEMBER", "ADMIN");
        roleBox.setValue("MEMBER");
        roleBox.setMaxWidth(Double.MAX_VALUE);

        Label error = new Label();
        error.getStyleClass().add("login-error");
        error.setVisible(false);
        error.setManaged(false);
        error.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.add(metaKey("Email:"), 0, 0);
        grid.add(emailField, 1, 0);
        grid.add(metaKey("Nombre:"), 0, 1);
        grid.add(nameField, 1, 1);
        grid.add(metaKey("Contraseña:"), 0, 2);
        grid.add(passwordField, 1, 2);
        grid.add(metaKey("Rol:"), 0, 3);
        grid.add(roleBox, 1, 3);
        grid.add(error, 1, 4);
        ColumnConstraints keyCol = new ColumnConstraints();
        keyCol.setMinWidth(92);
        ColumnConstraints valueCol = new ColumnConstraints();
        valueCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(keyCol, valueCol);

        dialog.getDialogPane().setContent(grid);
        DialogStyler.apply(dialog);

        Button addButton = (Button) dialog.getDialogPane().lookupButton(addType);
        addButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String message = inviteValidationMessage(
                    emailField.getText(), nameField.getText(), passwordField.getText(), roleBox.getValue());
            if (message != null) {
                error.setText(message);
                error.setVisible(true);
                error.setManaged(true);
                event.consume();
            }
        });

        dialog.setResultConverter(button -> {
            if (button != addType) return null;
            return new InviteForm(
                    emailField.getText().trim(),
                    nameField.getText().trim(),
                    passwordField.getText(),
                    roleBox.getValue()
            );
        });

        dialog.showAndWait().ifPresent(form -> {
            statusLabel.setText("Añadiendo miembro...");
            addBtn.setDisable(true);
            Thread.ofVirtual().start(() -> {
                try {
                    context.getFamilyRepository().inviteMember(
                            familyId, form.email(), form.displayName(), form.password(), form.role());
                    Platform.runLater(() -> {
                        addBtn.setDisable(false);
                        statusLabel.setText("Miembro añadido correctamente.");
                        refresh();
                    });
                } catch (ApiException ex) {
                    Platform.runLater(() -> {
                        addBtn.setDisable(false);
                        statusLabel.setText("Error: " + ex.getMessage());
                    });
                }
            });
        });
    }

    private String inviteValidationMessage(String email, String displayName, String password, String role) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return "Introduce un email válido.";
        }
        if (displayName == null || displayName.isBlank()) {
            return "Introduce el nombre del nuevo miembro.";
        }
        if (password == null || password.length() < 12) {
            return "La contraseña debe tener al menos 12 caracteres.";
        }
        if (!"MEMBER".equals(role) && !"ADMIN".equals(role)) {
            return "Selecciona un rol válido.";
        }
        return null;
    }

    private String editValidationMessage(String displayName, String email, PasswordActionOption passwordAction, String temporaryPassword) {
        if (displayName == null || displayName.isBlank()) {
            return "Introduce el nombre del miembro.";
        }
        if (email == null || email.isBlank() || !email.contains("@")) {
            return "Introduce un email válido.";
        }
        if (passwordAction == PasswordActionOption.SET_TEMPORARY
                && (temporaryPassword == null || temporaryPassword.length() < 12)) {
            return "La contraseña temporal debe tener al menos 12 caracteres.";
        }
        return null;
    }

    private void onRemoveMember() {
        MemberRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Expulsar miembro");
        confirm.setHeaderText("¿Expulsar a " + selected.getDisplayName() + " de la familia?");
        confirm.setContentText("Esta acción no se puede deshacer.");
        DialogStyler.apply(confirm);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String familyId = context.getSession().getFamilyId();
            statusLabel.setText("Eliminando miembro...");
            editBtn.setDisable(true);
            changeRoleBtn.setDisable(true);
            removeBtn.setDisable(true);
            Thread.ofVirtual().start(() -> {
                try {
                    context.getFamilyRepository().removeMember(familyId, selected.getUserId());
                    Platform.runLater(() -> {
                        statusLabel.setText("Miembro eliminado.");
                        refresh();
                    });
                } catch (ApiException ex) {
                    Platform.runLater(() -> statusLabel.setText("Error: " + ex.getMessage()));
                }
            });
        }
    }

    private void updateButtonStates(MemberRow selected) {
        if (selected == null || !canManage(selected)) {
            editBtn.setDisable(true);
            changeRoleBtn.setDisable(true);
            removeBtn.setDisable(true);
            return;
        }
        editBtn.setDisable(false);
        changeRoleBtn.setDisable(false);
        removeBtn.setDisable(false);
    }

    private boolean canManage(MemberRow selected) {
        if (selected == null || !context.getSession().isAdmin()) {
            return false;
        }
        return !"OWNER".equalsIgnoreCase(selected.getRole()) && !selected.isSelf();
    }

    private Label metaKey(String text) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("form-label");
        return lbl;
    }

    // ── Row model ─────────────────────────────────────────────────────────────

    private record InviteForm(String email, String displayName, String password, String role) {}

    private record EditForm(String displayName, String email, String passwordAction, String temporaryPassword) {}

    private enum PasswordActionOption {
        NONE(null, "No cambiar"),
        SEND_RESET("SEND_RESET", "Enviar email de recuperación"),
        SET_TEMPORARY("SET_TEMPORARY", "Definir temporal");

        private final String apiValue;
        private final String label;

        PasswordActionOption(String apiValue, String label) {
            this.apiValue = apiValue;
            this.label = label;
        }

        String apiValue() {
            return apiValue;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static final class MemberRow {
        private final String userId;
        private final String displayName;
        private final String email;
        private final String role;
        private final boolean self;
        private boolean online;

        public MemberRow(String userId, String displayName, String email, String role, boolean self) {
            this.userId      = userId;
            this.displayName = displayName != null ? displayName : "—";
            this.email       = email != null ? email : "—";
            this.role        = role != null ? role : "MEMBER";
            this.self        = self;
        }

        public String getUserId()      { return userId; }
        public String getDisplayName() { return displayName; }
        public String getEmail()       { return email; }
        public String getRole()        { return role; }
        public boolean isSelf()        { return self; }
        public boolean isOnline()      { return online; }
        public void setOnline(boolean online) { this.online = online; }

        /** Label shown in the table — includes "(Tú)" marker for self. */
        public String getRoleLabel() {
            String label = switch (role.toUpperCase()) {
                case "OWNER"  -> "Propietario";
                case "ADMIN"  -> "Administrador";
                default       -> "Miembro";
            };
            return self ? label + " (Tú)" : label;
        }
    }
}
