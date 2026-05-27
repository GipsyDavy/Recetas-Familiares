package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import org.gipsybuho.recetasfamiliares.core.AppContext;

public class LoginView extends VBox {

    private final AppContext context;
    private final Runnable onLoginSuccess;

    private final TextField emailField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Button loginButton = new Button("Iniciar sesión");
    private final Label errorLabel = new Label();

    public LoginView(AppContext context, Runnable onLoginSuccess) {
        this.context = context;
        this.onLoginSuccess = onLoginSuccess;
        build();
    }

    private void build() {
        getStyleClass().add("login-view");
        setAlignment(Pos.CENTER);
        setSpacing(16);
        setPadding(new Insets(40));

        Text title = new Text("Recetas Familiares");
        title.getStyleClass().add("login-title");

        Text subtitle = new Text("Tu cocina familiar, siempre contigo");
        subtitle.getStyleClass().add("login-subtitle");

        emailField.setPromptText("Correo electrónico");
        emailField.getStyleClass().add("login-field");
        emailField.setMaxWidth(320);

        passwordField.setPromptText("Contraseña");
        passwordField.getStyleClass().add("login-field");
        passwordField.setMaxWidth(320);
        passwordField.setOnAction(e -> doLogin());

        loginButton.getStyleClass().add("login-button");
        loginButton.setMaxWidth(320);
        loginButton.setOnAction(e -> doLogin());

        errorLabel.getStyleClass().add("login-error");
        errorLabel.setVisible(false);
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(320);

        getChildren().addAll(title, subtitle, emailField, passwordField, loginButton, errorLabel);
    }

    private void doLogin() {
        String email = emailField.getText().trim();
        String password = passwordField.getText();

        if (email.isBlank() || password.isBlank()) {
            showError("Por favor, introduce tu correo y contraseña.");
            return;
        }

        loginButton.setDisable(true);
        errorLabel.setVisible(false);

        Thread.ofVirtual().start(() -> {
            try {
                context.getAuthRepository().login(email, password);
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
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
