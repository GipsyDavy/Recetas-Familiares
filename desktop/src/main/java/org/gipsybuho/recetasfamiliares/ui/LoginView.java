package org.gipsybuho.recetasfamiliares.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.util.Duration;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.core.AppContext;
import org.gipsybuho.recetasfamiliares.core.ServerConfig;

public class LoginView extends ScrollPane {

    private final AppContext context;
    private final Runnable onLoginSuccess;
    private final VBox content = new VBox();

    private final TextField displayNameField = new TextField();
    private final TextField emailField    = new TextField();
    private final TextField familyNameField = new TextField();
    private final TextField serverUrlField = new TextField();
    private final PasswordField passwordField   = new PasswordField();
    private final TextField passwordVisible     = new TextField();
    private final Button loginButton = new Button("Iniciar sesión");
    private final Button modeSwitchButton = new Button("Crear cuenta");
    private final Hyperlink forgotPasswordLink = new Hyperlink("¿Has olvidado tu contraseña?");
    private final Label errorLabel   = new Label();
    private boolean showingPassword  = false;
    private boolean registerMode = false;

    public LoginView(AppContext context, Runnable onLoginSuccess) {
        this.context = context;
        this.onLoginSuccess = onLoginSuccess;
        build();
    }

    private void build() {
        DesktopScroll.configurePage(this, content);
        content.getStyleClass().add("login-view");
        content.setAlignment(Pos.CENTER);
        content.setFillWidth(true);
        setContent(content);

        // ── Logo circle ───────────────────────────────────────────────────────
        Label logo = new Label("RF");
        logo.getStyleClass().add("login-logo");

        // ── Title + subtitle ─────────────────────────────────────────────────
        Text title = new Text("Recetas Familiares");
        title.getStyleClass().add("login-title");

        Text subtitle = new Text("Tu cocina familiar, siempre contigo");
        subtitle.getStyleClass().add("login-subtitle");

        // ── Email field ───────────────────────────────────────────────────────
        displayNameField.setPromptText("Tu nombre");
        displayNameField.getStyleClass().add("login-field");
        displayNameField.setMaxWidth(Double.MAX_VALUE);
        displayNameField.setVisible(false);
        displayNameField.setManaged(false);
        displayNameField.setOnAction(e -> doSubmit());

        emailField.setPromptText("tucorreo@ejemplo.com");
        emailField.getStyleClass().add("login-field");
        emailField.setMaxWidth(Double.MAX_VALUE);
        emailField.setOnAction(e -> doSubmit());

        familyNameField.setPromptText("Nombre de la familia");
        familyNameField.getStyleClass().add("login-field");
        familyNameField.setMaxWidth(Double.MAX_VALUE);
        familyNameField.setVisible(false);
        familyNameField.setManaged(false);
        familyNameField.setOnAction(e -> doSubmit());

        serverUrlField.setText(ServerConfig.getBaseUrl());
        serverUrlField.setPromptText(ServerConfig.DEFAULT_API_BASE_URL);
        serverUrlField.getStyleClass().add("login-field");
        serverUrlField.setMaxWidth(Double.MAX_VALUE);
        serverUrlField.setDisable(ServerConfig.hasSystemOverride());
        serverUrlField.setOnAction(e -> doSubmit());
        Tooltip.install(serverUrlField, new Tooltip(ServerConfig.hasSystemOverride()
                ? "Servidor fijado por parametro de arranque"
                : "URL del servidor"));

        // ── Password field with show/hide toggle ──────────────────────────────
        passwordField.setPromptText("Contraseña");
        passwordField.getStyleClass().add("login-field");
        passwordField.setMaxWidth(Double.MAX_VALUE);
        passwordField.setOnAction(e -> doSubmit());

        passwordVisible.setPromptText("Contraseña");
        passwordVisible.getStyleClass().add("login-field");
        passwordVisible.setMaxWidth(Double.MAX_VALUE);
        passwordVisible.setVisible(false);
        passwordVisible.setManaged(false);
        passwordVisible.setOnAction(e -> doSubmit());

        // Keep both fields in sync
        passwordField.textProperty().addListener((obs, old, val) -> {
            if (!showingPassword) passwordVisible.setText(val);
        });
        passwordVisible.textProperty().addListener((obs, old, val) -> {
            if (showingPassword) passwordField.setText(val);
        });

        Button toggleBtn = new Button("👁");
        toggleBtn.getStyleClass().add("login-toggle-btn");
        toggleBtn.setFocusTraversable(false);
        Tooltip.install(toggleBtn, new Tooltip("Mostrar / ocultar contraseña"));
        toggleBtn.setOnAction(e -> togglePassword(toggleBtn));

        // Stack password fields + toggle button in a single row
        StackPane pwStack = new StackPane(passwordField, passwordVisible, toggleBtn);
        StackPane.setAlignment(toggleBtn, Pos.CENTER_RIGHT);
        StackPane.setMargin(toggleBtn, new Insets(0, 6, 0, 0));
        pwStack.getStyleClass().add("login-password-stack");
        pwStack.setMaxWidth(Double.MAX_VALUE);

        // ── Login button ──────────────────────────────────────────────────────
        loginButton.getStyleClass().add("login-button");
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.setDefaultButton(true);
        loginButton.setOnAction(e -> doSubmit());

        modeSwitchButton.getStyleClass().add("login-mode-switch");
        modeSwitchButton.setMaxWidth(Double.MAX_VALUE);
        modeSwitchButton.setOnAction(e -> setRegisterMode(!registerMode));

        forgotPasswordLink.setFocusTraversable(false);
        forgotPasswordLink.setOnAction(e -> {
            forgotPasswordLink.setVisited(false);
            PasswordResetDialog.show(getScene() != null ? getScene().getWindow() : null,
                    context.getAuthRepository(), emailField.getText());
        });

        // ── Error label ───────────────────────────────────────────────────────
        errorLabel.getStyleClass().add("login-error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(Double.MAX_VALUE);

        // ── Card ──────────────────────────────────────────────────────────────
        VBox card = new VBox(14, logo, title, subtitle,
                displayNameField, emailField, familyNameField, serverUrlField, pwStack,
                loginButton, modeSwitchButton, forgotPasswordLink, errorLabel);
        card.getStyleClass().add("login-card");
        card.setAlignment(Pos.TOP_CENTER);
        card.setMaxWidth(420);
        card.setFillWidth(true);

        content.getChildren().add(card);

        // ── Entry animation (FadeIn + slight scale-up) ────────────────────────
        if (MotionPreferences.isReducedMotion()) {
            card.setOpacity(1.0);
            card.setScaleX(1.0);
            card.setScaleY(1.0);
        } else {
            card.setOpacity(0);
            card.setScaleX(0.97);
            card.setScaleY(0.97);

            FadeTransition fade = new FadeTransition(Duration.millis(400), card);
            fade.setFromValue(0.0);
            fade.setToValue(1.0);

            ScaleTransition scale = new ScaleTransition(Duration.millis(300), card);
            scale.setFromX(0.97);
            scale.setFromY(0.97);
            scale.setToX(1.0);
            scale.setToY(1.0);
            scale.setInterpolator(Interpolator.EASE_OUT);

            new ParallelTransition(fade, scale).play();
        }
    }

    private void setRegisterMode(boolean enabled) {
        registerMode = enabled;
        displayNameField.setVisible(enabled);
        displayNameField.setManaged(enabled);
        familyNameField.setVisible(enabled);
        familyNameField.setManaged(enabled);
        loginButton.setText(enabled ? "Crear cuenta" : "Iniciar sesión");
        modeSwitchButton.setText(enabled ? "Ya tengo cuenta" : "Crear cuenta");
        forgotPasswordLink.setVisible(!enabled);
        forgotPasswordLink.setManaged(!enabled);
        hideError();
        if (enabled) {
            displayNameField.requestFocus();
        } else {
            emailField.requestFocus();
        }
    }

    private void togglePassword(Button btn) {
        showingPassword = !showingPassword;
        if (showingPassword) {
            passwordVisible.setText(passwordField.getText());
            passwordField.setVisible(false);
            passwordField.setManaged(false);
            passwordVisible.setVisible(true);
            passwordVisible.setManaged(true);
            btn.setText("🔒");
            passwordVisible.requestFocus();
            passwordVisible.positionCaret(passwordVisible.getText().length());
        } else {
            passwordField.setText(passwordVisible.getText());
            passwordVisible.setVisible(false);
            passwordVisible.setManaged(false);
            passwordField.setVisible(true);
            passwordField.setManaged(true);
            btn.setText("👁");
            passwordField.requestFocus();
            passwordField.positionCaret(passwordField.getText().length());
        }
    }

    private void doSubmit() {
        String displayName = displayNameField.getText().trim();
        String email    = emailField.getText().trim();
        String familyName = familyNameField.getText().trim();
        String password = showingPassword ? passwordVisible.getText() : passwordField.getText();

        if (email.isBlank() || password.isBlank()) {
            showError("Por favor, introduce tu correo y contraseña.");
            return;
        }
        if (registerMode && (displayName.isBlank() || familyName.isBlank())) {
            showError("Completa tu nombre y el nombre de la familia.");
            return;
        }
        if (registerMode && password.length() < 12) {
            showError("La contraseña debe tener al menos 12 caracteres.");
            return;
        }
        if (!ServerConfig.hasSystemOverride()) {
            try {
                ServerConfig.saveUserBaseUrl(serverUrlField.getText());
                serverUrlField.setText(ServerConfig.getBaseUrl());
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
                return;
            }
        }

        loginButton.setDisable(true);
        modeSwitchButton.setDisable(true);
        hideError();
        boolean createAccount = registerMode;

        Thread.ofVirtual().start(() -> {
            try {
                if (createAccount) {
                    context.getAuthRepository().register(email, displayName, password, familyName);
                } else {
                    context.getAuthRepository().login(email, password);
                }
                context.getFamilyRepository().detectAndSaveRole();
                // El login no devuelve el avatar (AuthUserResponse no lo incluye), asi
                // que la barra lateral se quedaria con las iniciales hasta abrir el
                // perfil. fetchMe lo guarda en la sesion; si falla, se sigue entrando.
                try {
                    context.getUserRepository().fetchMe();
                } catch (Exception ignored) {
                    // Sin conexion al perfil: iniciales. No es motivo para fallar el login.
                }
                Platform.runLater(onLoginSuccess);
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    loginButton.setDisable(false);
                    modeSwitchButton.setDisable(false);
                    showError(errorMessage(ex, createAccount));
                });
            }
        });
    }

    private String errorMessage(Exception ex, boolean createAccount) {
        if (ex instanceof ApiException apiEx) {
            if (apiEx.getHttpStatus() == 0) {
                return "No se pudo conectar con el backend.";
            }
            if (apiEx.getHttpStatus() == 409) {
                return "Ese correo ya está registrado.";
            }
            if (apiEx.getHttpStatus() == 400 && createAccount) {
                return "Revisa los datos de la cuenta.";
            }
        }
        return createAccount
                ? "No se pudo crear la cuenta."
                : "No se pudo iniciar sesión. Verifica tus datos.";
    }

    private void showError(String message) {
        errorLabel.setText("⚠  " + message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
