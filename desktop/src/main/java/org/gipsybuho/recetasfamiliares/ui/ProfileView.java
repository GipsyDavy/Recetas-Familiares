package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.gipsybuho.recetasfamiliares.api.dto.FamilyDtos;
import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;
import org.gipsybuho.recetasfamiliares.core.FamilyRole;

import java.io.File;
import java.util.List;

/**
 * Vista de perfil (UX-5), accesible a todos los roles: avatar, nombre editable,
 * email, familia con rol y stats familiares con fallback local.
 */
public class ProfileView extends ScrollPane {

    private static final int AVATAR_SIZE = 88;

    private final AppContext context;
    private final Stage stage;
    private final Runnable onUserInfoChanged;
    private final java.util.function.Consumer<String> onStatus;
    private final Runnable onAccountDeleted;

    private final StackPane avatarSlot = new StackPane();
    private final Label nameLabel = new Label();
    private final Label emailLabel = new Label();
    private final Label familyLabel = new Label();
    private final Label roleBadge = new Label();
    private final HBox statsSection = new HBox(16);
    private final Label verificationLabel = new Label();
    private final HBox verificationActions = new HBox(10);
    // Solo se toca en FX thread: invalida cargas de avatar antiguas que lleguen tarde
    private int avatarLoadGeneration = 0;

    public ProfileView(AppContext context, Stage stage, Runnable onUserInfoChanged,
                       java.util.function.Consumer<String> onStatus, Runnable onAccountDeleted) {
        this.context = context;
        this.stage = stage;
        this.onUserInfoChanged = onUserInfoChanged;
        this.onStatus = onStatus;
        this.onAccountDeleted = onAccountDeleted;
        build();
    }

    private void build() {
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        getStyleClass().add("settings-scroll");

        Label title = new Label("Mi perfil");
        title.getStyleClass().add("settings-title");
        Label subtitle = new Label("Tu cuenta, tu familia y la actividad compartida.");
        subtitle.getStyleClass().add("settings-muted");
        VBox header = new VBox(3, title, subtitle);

        VBox content = new VBox(18, header, buildIdentityCard(), buildFamilyCard(),
                buildAccountCard(), buildHelpCard());
        content.getStyleClass().add("settings-tab-content");
        content.setPadding(new Insets(24, 28, 28, 28));
        setContent(content);
    }

    private VBox buildIdentityCard() {
        avatarSlot.setPrefSize(AVATAR_SIZE, AVATAR_SIZE);
        avatarSlot.setMinSize(AVATAR_SIZE, AVATAR_SIZE);
        avatarSlot.setMaxSize(AVATAR_SIZE, AVATAR_SIZE);

        Button changePhotoBtn = new Button("Cambiar foto");
        changePhotoBtn.getStyleClass().add("action-button-secondary");
        changePhotoBtn.setOnAction(e -> chooseAndUploadAvatar());

        nameLabel.getStyleClass().add("profile-name");
        Button editNameBtn = new Button("✏");
        editNameBtn.getStyleClass().add("action-button-secondary");
        editNameBtn.setPadding(new Insets(2, 8, 2, 8));
        Tooltip tooltip = new Tooltip("Editar nombre");
        tooltip.setShowDelay(Duration.millis(400));
        Tooltip.install(editNameBtn, tooltip);
        editNameBtn.setOnAction(e -> editDisplayName());
        HBox nameRow = new HBox(8, nameLabel, editNameBtn);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        emailLabel.getStyleClass().add("settings-muted");

        VBox textCol = new VBox(6, nameRow, emailLabel, changePhotoBtn);
        textCol.setAlignment(Pos.CENTER_LEFT);

        HBox row = new HBox(20, avatarSlot, textCol);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10, row);
        card.getStyleClass().add("settings-section");
        return card;
    }

    private VBox buildFamilyCard() {
        Label sectionTitle = new Label("Mi familia");
        sectionTitle.getStyleClass().add("settings-section-title");

        familyLabel.getStyleClass().add("profile-family-name");
        roleBadge.getStyleClass().add("profile-role-badge");
        HBox familyRow = new HBox(10, familyLabel, roleBadge);
        familyRow.setAlignment(Pos.CENTER_LEFT);

        statsSection.setPadding(new Insets(6, 0, 0, 0));

        VBox card = new VBox(10, sectionTitle, familyRow, statsSection);
        card.getStyleClass().add("settings-section");
        return card;
    }

    private VBox buildAccountCard() {
        Label sectionTitle = new Label("Cuenta");
        sectionTitle.getStyleClass().add("settings-section-title");

        verificationLabel.getStyleClass().add("settings-muted");
        verificationLabel.setWrapText(true);
        verificationLabel.setText("Comprobando estado del correo...");

        Button sendVerificationBtn = new Button("Enviar correo de verificación");
        sendVerificationBtn.getStyleClass().add("action-button-secondary");
        sendVerificationBtn.setOnAction(e -> requestEmailVerification(sendVerificationBtn));

        Button haveCodeBtn = new Button("Ya tengo el código");
        haveCodeBtn.getStyleClass().add("action-button-secondary");
        haveCodeBtn.setOnAction(e -> confirmEmailVerification());

        verificationActions.getChildren().setAll(sendVerificationBtn, haveCodeBtn);
        verificationActions.setVisible(false);
        verificationActions.setManaged(false);

        Label dangerLabel = new Label("Eliminar tu cuenta borra tu acceso y anonimiza tus datos personales. "
                + "Esta acción no se puede deshacer.");
        dangerLabel.getStyleClass().add("settings-muted");
        dangerLabel.setWrapText(true);

        Button deleteAccountBtn = new Button("Eliminar cuenta");
        deleteAccountBtn.getStyleClass().add("logout-button");
        deleteAccountBtn.setOnAction(e -> confirmDeleteAccount());

        VBox card = new VBox(10, sectionTitle, verificationLabel, verificationActions,
                new Separator(), dangerLabel, deleteAccountBtn);
        card.getStyleClass().add("settings-section");
        return card;
    }

    private VBox buildHelpCard() {
        Label sectionTitle = new Label("Ayuda");
        sectionTitle.getStyleClass().add("settings-section-title");
        Label desc = new Label("Vuelve a ver la guia de primeros pasos cuando quieras.");
        desc.getStyleClass().add("settings-muted");
        desc.setWrapText(true);

        Button onboardingBtn = new Button("Ver guía de bienvenida");
        onboardingBtn.getStyleClass().add("action-button-secondary");
        onboardingBtn.setOnAction(e -> OnboardingDialog.showAgain(stage));

        VBox card = new VBox(10, sectionTitle, desc, onboardingBtn);
        card.getStyleClass().add("settings-section");
        return card;
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    public void refresh() {
        String displayName = context.getSession().getDisplayName();
        String cleanName = displayName != null ? displayName.trim() : "";
        nameLabel.setText(cleanName.isBlank() ? "Sin nombre" : cleanName);
        emailLabel.setText(context.getSession().getEmail() != null ? context.getSession().getEmail() : "");
        renderAvatar(cleanName);

        updateRoleBadge(null);
        familyLabel.setText("Cargando...");
        loadFamilyInfo();
        loadVerificationState();
    }

    /** Consulta /users/me para saber si el correo esta verificado (CRIT-2). */
    private void loadVerificationState() {
        verificationLabel.setText("Comprobando estado del correo...");
        verificationActions.setVisible(false);
        verificationActions.setManaged(false);
        Thread.ofVirtual().start(() -> {
            try {
                var me = context.getUserRepository().fetchMe();
                Platform.runLater(() -> {
                    if (me.emailVerified()) {
                        verificationLabel.setText("✓ Correo verificado");
                    } else {
                        verificationLabel.setText("Tu correo aún no está verificado. "
                                + "Verifícalo para poder recuperar tu cuenta si olvidas la contraseña.");
                        verificationActions.setVisible(true);
                        verificationActions.setManaged(true);
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() ->
                        verificationLabel.setText("Estado del correo no disponible sin conexión."));
            }
        });
    }

    private void renderAvatar(String displayName) {
        String avatarUrl = context.getSession().getAvatarUrl();
        int generation = ++avatarLoadGeneration;
        if (avatarUrl == null || avatarUrl.isBlank()) {
            avatarSlot.getChildren().setAll(initialsNode(displayName));
            return;
        }
        // Carga autenticada en segundo plano: /uploads/** requiere JWT (SEC-3)
        avatarSlot.getChildren().setAll(initialsNode(displayName));
        Thread.ofVirtual().start(() -> {
            try {
                byte[] bytes = context.getApiClient().fetchImage(avatarUrl);
                Image image = new Image(new java.io.ByteArrayInputStream(bytes),
                        AVATAR_SIZE, AVATAR_SIZE, true, true);
                Platform.runLater(() -> {
                    if (generation != avatarLoadGeneration) return; // llegó tarde: hay carga más nueva
                    ImageView imageView = new ImageView(image);
                    imageView.setFitWidth(AVATAR_SIZE);
                    imageView.setFitHeight(AVATAR_SIZE);
                    imageView.setPreserveRatio(false);
                    StackPane pane = new StackPane(imageView);
                    pane.setPrefSize(AVATAR_SIZE, AVATAR_SIZE);
                    pane.setClip(new Circle(AVATAR_SIZE / 2.0, AVATAR_SIZE / 2.0, AVATAR_SIZE / 2.0));
                    avatarSlot.getChildren().setAll(pane);
                });
            } catch (Exception ignored) {
                // Sin avatar remoto: quedan las iniciales
            }
        });
    }

    private Node initialsNode(String displayName) {
        String text = displayName != null && !displayName.isBlank() ? initials(displayName) : "👤";
        Label label = new Label(text);
        // Clase propia: .avatar-circle fija 38px por CSS y pisaría el tamaño de código
        label.getStyleClass().add("profile-avatar-circle");
        label.setTextFill(javafx.scene.paint.Color.WHITE);
        label.setFont(Font.font("System", FontWeight.BOLD, 32));
        label.setAlignment(Pos.CENTER);
        label.setMinSize(AVATAR_SIZE, AVATAR_SIZE);
        label.setPrefSize(AVATAR_SIZE, AVATAR_SIZE);
        return label;
    }

    private String initials(String displayName) {
        StringBuilder sb = new StringBuilder(2);
        for (String part : displayName.split("\\s+")) {
            if (!part.isBlank()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (sb.length() == 2) break;
            }
        }
        return sb.toString();
    }

    /** Badge de rol: usa el rol de la API si llega; si no, el de la sesión. */
    private void updateRoleBadge(String apiRole) {
        FamilyRole role = context.getSession().getFamilyRole();
        if (apiRole != null && !apiRole.isBlank()) {
            try {
                role = FamilyRole.valueOf(apiRole.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Rol desconocido de la API: se conserva el de sesión
            }
        }
        roleBadge.setText(role != null ? role.displayName() : "");
        roleBadge.setVisible(role != null);
    }

    /**
     * Carga familia, rol y stats de forma consistente: la familia elegida
     * (la de sesión o, si no coincide, la primera) aporta nombre, rol e id
     * para stats, evitando mezclar fuentes cuando la sesión está incompleta.
     */
    private void loadFamilyInfo() {
        Thread.ofVirtual().start(() -> {
            try {
                FamilyDtos.FamilyResponse[] families = context.getFamilyRepository().loadMyFamilies();
                String sessionFamilyId = context.getSession().getFamilyId();
                FamilyDtos.FamilyResponse match = null;
                for (FamilyDtos.FamilyResponse family : families) {
                    if (family.id() != null && family.id().equals(sessionFamilyId)) {
                        match = family;
                        break;
                    }
                }
                if (match == null && families.length > 0) match = families[0];
                FamilyDtos.FamilyResponse selected = match;
                Platform.runLater(() -> {
                    if (selected == null) {
                        familyLabel.setText("Sin familia");
                        statsSection.getChildren().clear();
                        return;
                    }
                    familyLabel.setText(selected.name() != null ? selected.name() : "Sin nombre");
                    updateRoleBadge(selected.role());
                    loadFamilyStats(selected.id());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    familyLabel.setText("Familia no disponible sin conexión");
                    loadFamilyStats(context.getSession().getFamilyId());
                });
            }
        });
    }

    private void loadFamilyStats(String familyId) {
        statsSection.getChildren().clear();
        if (familyId == null) {
            renderLocalStats();
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                var stats = context.getFamilyRepository().loadStats(familyId);
                Platform.runLater(() -> renderStats(stats));
            } catch (Exception ex) {
                Platform.runLater(this::renderLocalStats);
            }
        });
    }

    private void renderStats(FamilyDtos.FamilyStatsResponse stats) {
        statsSection.getChildren().clear();
        if (stats == null) return;
        statsSection.getChildren().addAll(
                statChip(stats.totalRecipes() + (stats.totalRecipes() == 1 ? " receta" : " recetas")),
                statChip(stats.totalMembers() + (stats.totalMembers() == 1 ? " miembro" : " miembros")),
                statChip(stats.totalStockItems() + " en despensa"));
        if (stats.lastActivityAt() != null && stats.lastActivityAt().length() >= 10) {
            statsSection.getChildren().add(statChip("Última actividad: " + stats.lastActivityAt().substring(0, 10)));
        }
    }

    /** Fallback local mínimo cuando /stats falla (mismo criterio que DashboardView). */
    private void renderLocalStats() {
        statsSection.getChildren().clear();
        List<RecipeDtos.RecipeDto> cachedRecipes = context.getRecipeRepository().getCache().getItems()
                .stream()
                .filter(recipe -> !recipe.deleted())
                .toList();
        if (cachedRecipes.isEmpty()) return;
        statsSection.getChildren().add(
                statChip(cachedRecipes.size() + (cachedRecipes.size() == 1 ? " receta" : " recetas")));
        cachedRecipes.stream()
                .map(RecipeDtos.RecipeDto::updatedAt)
                .filter(value -> value != null && value.length() >= 10)
                .max(String::compareTo)
                .ifPresent(value -> statsSection.getChildren().add(
                        statChip("Última actividad: " + value.substring(0, 10))));
    }

    private Label statChip(String text) {
        Label chip = new Label(text);
        chip.getStyleClass().add("recipe-cell-meta");
        return chip;
    }

    // ── Acciones ──────────────────────────────────────────────────────────────

    private void chooseAndUploadAvatar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar foto de perfil");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imágenes (JPG, PNG, WebP)", "*.jpg", "*.jpeg", "*.png", "*.webp"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        // Allowlist en cliente: el filtro del chooser es orientativo; la validación real vive en backend
        String lowerName = file.getName().toLowerCase();
        if (!(lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".png") || lowerName.endsWith(".webp"))) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Formato no permitido. Usa JPG, PNG o WebP.");
            alert.setTitle("Formato no válido");
            alert.setHeaderText(null);
            DialogStyler.apply(alert);
            alert.showAndWait();
            return;
        }
        if (file.length() > 8L * 1024 * 1024) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "El tamaño máximo permitido es 8 MB.");
            alert.setTitle("Archivo demasiado grande");
            alert.setHeaderText(null);
            DialogStyler.apply(alert);
            alert.showAndWait();
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                context.getUserRepository().uploadAvatar(file);
                Platform.runLater(() -> {
                    refresh();
                    onUserInfoChanged.run();
                    onStatus.accept("Foto de perfil actualizada");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> onStatus.accept("Error al subir la foto: " + ex.getMessage()));
            }
        });
    }

    private void requestEmailVerification(Button trigger) {
        String email = context.getSession().getEmail();
        if (email == null || email.isBlank()) {
            onStatus.accept("No hay correo en la sesión.");
            return;
        }
        trigger.setDisable(true);
        Thread.ofVirtual().start(() -> {
            try {
                context.getAuthRepository().requestEmailVerification(email);
                Platform.runLater(() -> {
                    trigger.setDisable(false);
                    onStatus.accept("Correo de verificación enviado. Revisa tu bandeja de entrada.");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    trigger.setDisable(false);
                    onStatus.accept("No se pudo enviar el correo de verificación.");
                });
            }
        });
    }

    private void confirmEmailVerification() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Verificar correo");
        dialog.setHeaderText(null);
        dialog.setContentText("Código del correo:");
        DialogStyler.apply(dialog);
        dialog.showAndWait().ifPresent(token -> {
            if (token.isBlank()) return;
            Thread.ofVirtual().start(() -> {
                try {
                    context.getAuthRepository().confirmEmailVerification(token);
                    Platform.runLater(() -> {
                        onStatus.accept("Correo verificado correctamente.");
                        loadVerificationState();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() ->
                            onStatus.accept("El código no es válido o ha caducado."));
                }
            });
        });
    }

    private void confirmDeleteAccount() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Eliminar cuenta");
        dialog.initOwner(stage);

        Label warning = new Label("Vas a eliminar tu cuenta de forma permanente. "
                + "Perderás el acceso y tus datos personales se anonimizarán. "
                + "Escribe tu contraseña para confirmar.");
        warning.setWrapText(true);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Contraseña actual");

        VBox content = new VBox(12, warning, passwordField);
        content.setPrefWidth(380);
        dialog.getDialogPane().setContent(content);

        ButtonType deleteType = new ButtonType("Eliminar cuenta", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(deleteType, ButtonType.CANCEL);
        DialogStyler.apply(dialog);
        Button deleteBtn = (Button) dialog.getDialogPane().lookupButton(deleteType);
        deleteBtn.getStyleClass().add("logout-button");
        deleteBtn.disableProperty().bind(passwordField.textProperty().isEmpty());

        dialog.showAndWait().ifPresent(result -> {
            if (result != deleteType) return;
            String password = passwordField.getText();
            Thread.ofVirtual().start(() -> {
                try {
                    context.getAuthRepository().deleteAccount(password);
                    Platform.runLater(onAccountDeleted);
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        String message = (ex instanceof org.gipsybuho.recetasfamiliares.api.ApiException apiEx
                                && apiEx.getHttpStatus() == 403)
                                ? "Contraseña incorrecta."
                                : "No se pudo eliminar la cuenta.";
                        onStatus.accept(message);
                    });
                }
            });
        });
    }

    private void editDisplayName() {
        String current = context.getSession().getDisplayName();
        TextInputDialog dialog = new TextInputDialog(current != null ? current : "");
        dialog.setTitle("Editar nombre");
        dialog.setHeaderText(null);
        dialog.setContentText("Nombre:");
        DialogStyler.apply(dialog);
        dialog.showAndWait().ifPresent(newName -> {
            if (newName.isBlank()) return;
            Thread.ofVirtual().start(() -> {
                try {
                    context.getUserRepository().updateDisplayName(newName.trim());
                    Platform.runLater(() -> {
                        refresh();
                        onUserInfoChanged.run();
                        onStatus.accept("Nombre actualizado");
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> onStatus.accept("Error al actualizar nombre: " + ex.getMessage()));
                }
            });
        });
    }
}
