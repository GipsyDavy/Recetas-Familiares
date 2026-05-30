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
import org.gipsybuho.recetasfamiliares.core.AppContext;

public class LoginView extends VBox {

    private final AppContext context;
    private final Runnable onLoginSuccess;

    private final TextField emailField    = new TextField();
    private final PasswordField passwordField   = new PasswordField();
    private final TextField passwordVisible     = new TextField();
    private final Button loginButton = new Button("Iniciar sesión");
    private final Label errorLabel   = new Label();
    private boolean showingPassword  = false;

    public LoginView(AppContext context, Runnable onLoginSuccess) {
        this.context = context;
        this.onLoginSuccess = onLoginSuccess;
        build();
    }

    private void build() {
        getStyleClass().add("login-view");
        setAlignment(Pos.CENTER);
        setFillWidth(true);

        // ── Logo circle ───────────────────────────────────────────────────────
        Label logo = new Label("RF");
        logo.getStyleClass().add("login-logo");

        // ── Title + subtitle ─────────────────────────────────────────────────
        Text title = new Text("Recetas Familiares");
        title.getStyleClass().add("login-title");

        Text subtitle = new Text("Tu cocina familiar, siempre contigo");
        subtitle.getStyleClass().add("login-subtitle");

        // ── Email field ───────────────────────────────────────────────────────
        emailField.setPromptText("tucorreo@ejemplo.com");
        emailField.getStyleClass().add("login-field");
        emailField.setMaxWidth(Double.MAX_VALUE);
        emailField.setOnAction(e -> doLogin());

        // ── Password field with show/hide toggle ──────────────────────────────
        passwordField.setPromptText("Contraseña");
        passwordField.getStyleClass().add("login-field");
        passwordField.setMaxWidth(Double.MAX_VALUE);
        passwordField.setOnAction(e -> doLogin());

        passwordVisible.setPromptText("Contraseña");
        passwordVisible.getStyleClass().add("login-field");
        passwordVisible.setMaxWidth(Double.MAX_VALUE);
        passwordVisible.setVisible(false);
        passwordVisible.setManaged(false);
        passwordVisible.setOnAction(e -> doLogin());

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

        // ── Login button ──────────────────────────────────────────────────────
        loginButton.getStyleClass().add("login-button");
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.setDefaultButton(true);
        loginButton.setOnAction(e -> doLogin());

        // ── Error label ───────────────────────────────────────────────────────
        errorLabel.getStyleClass().add("login-error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(Double.MAX_VALUE);

        // ── Card ──────────────────────────────────────────────────────────────
        VBox card = new VBox(14, logo, title, subtitle, emailField, pwStack, loginButton, errorLabel);
        card.getStyleClass().add("login-card");
        card.setAlignment(Pos.TOP_CENTER);
        card.setMaxWidth(420);
        card.setFillWidth(true);

        getChildren().add(card);

        // ── Entry animation (FadeIn + slight scale-up) ────────────────────────
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

    private void doLogin() {
        String email    = emailField.getText().trim();
        String password = showingPassword ? passwordVisible.getText() : passwordField.getText();

        if (email.isBlank() || password.isBlank()) {
            showError("Por favor, introduce tu correo y contraseña.");
            return;
        }

        loginButton.setDisable(true);
        hideError();

        Thread.ofVirtual().start(() -> {
            try {
                context.getAuthRepository().login(email, password);
                context.getFamilyRepository().detectAndSaveRole();
                Platform.runLater(onLoginSuccess);
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    loginButton.setDisable(false);
                    showError("No se pudo iniciar sesión. Verifica tus datos.");
                });
            }
        });
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
