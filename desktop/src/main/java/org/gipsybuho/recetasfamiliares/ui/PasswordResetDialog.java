package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.gipsybuho.recetasfamiliares.api.ApiException;
import org.gipsybuho.recetasfamiliares.data.repository.AuthRepository;

/**
 * Recuperacion de contraseña en dos pasos (CRIT-2):
 * 1) pedir el correo de recuperacion; 2) pegar el codigo del correo y elegir
 * la nueva contraseña. El backend responde 202 exista o no la cuenta
 * (anti-enumeracion), asi que el paso 1 nunca revela si el email existe.
 */
final class PasswordResetDialog {

    private static final int MIN_PASSWORD_LENGTH = 12;

    private PasswordResetDialog() {}

    static void show(Window owner, AuthRepository authRepository, String prefillEmail) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Recuperar contraseña");
        dialog.initOwner(owner);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TextField emailField = new TextField(prefillEmail != null ? prefillEmail.trim() : "");
        emailField.setPromptText("tucorreo@ejemplo.com");

        Label infoLabel = new Label("Te enviaremos un correo con un código de recuperación.");
        infoLabel.setWrapText(true);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("login-error");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        Button sendButton = new Button("Enviar correo de recuperación");
        sendButton.getStyleClass().add("action-button-primary");
        sendButton.setMaxWidth(Double.MAX_VALUE);

        Button haveCodeButton = new Button("Ya tengo el código");
        haveCodeButton.getStyleClass().add("action-button-secondary");
        haveCodeButton.setMaxWidth(Double.MAX_VALUE);

        TextField tokenField = new TextField();
        tokenField.setPromptText("Código del correo");

        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("Nueva contraseña (mínimo " + MIN_PASSWORD_LENGTH + " caracteres)");

        PasswordField repeatPasswordField = new PasswordField();
        repeatPasswordField.setPromptText("Repite la nueva contraseña");

        Button confirmButton = new Button("Cambiar contraseña");
        confirmButton.getStyleClass().add("action-button-primary");
        confirmButton.setMaxWidth(Double.MAX_VALUE);

        VBox stepOne = new VBox(10, infoLabel, emailField, sendButton, haveCodeButton);
        VBox stepTwo = new VBox(10,
                new Label("Pega el código que aparece en el correo y elige tu nueva contraseña."),
                tokenField, newPasswordField, repeatPasswordField, confirmButton);
        stepTwo.setVisible(false);
        stepTwo.setManaged(false);
        ((Label) stepTwo.getChildren().get(0)).setWrapText(true);

        VBox content = new VBox(12, stepOne, stepTwo, errorLabel);
        content.setPadding(new Insets(8, 4, 4, 4));
        content.setPrefWidth(400);
        dialog.getDialogPane().setContent(content);
        DialogStyler.apply(dialog);

        Runnable showStepTwo = () -> {
            stepOne.setVisible(false);
            stepOne.setManaged(false);
            stepTwo.setVisible(true);
            stepTwo.setManaged(true);
            tokenField.requestFocus();
        };

        Runnable hideError = () -> {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        };
        java.util.function.Consumer<String> showError = message -> {
            errorLabel.setText("⚠  " + message);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        };

        sendButton.setOnAction(e -> {
            String email = emailField.getText().trim();
            if (email.isBlank() || !email.contains("@")) {
                showError.accept("Introduce un correo válido.");
                return;
            }
            hideError.run();
            sendButton.setDisable(true);
            Thread.ofVirtual().start(() -> {
                try {
                    authRepository.requestPasswordReset(email);
                    Platform.runLater(() -> {
                        sendButton.setDisable(false);
                        infoLabel.setText("Si el correo existe, hemos enviado el código. Revisa tu bandeja de entrada.");
                        showStepTwo.run();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        sendButton.setDisable(false);
                        showError.accept(requestErrorMessage(ex));
                    });
                }
            });
        });

        haveCodeButton.setOnAction(e -> {
            hideError.run();
            showStepTwo.run();
        });

        confirmButton.setOnAction(e -> {
            String token = tokenField.getText().trim();
            String newPassword = newPasswordField.getText();
            if (token.isBlank()) {
                showError.accept("Pega el código que has recibido por correo.");
                return;
            }
            if (newPassword.length() < MIN_PASSWORD_LENGTH) {
                showError.accept("La contraseña debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres.");
                return;
            }
            if (!newPassword.equals(repeatPasswordField.getText())) {
                showError.accept("Las contraseñas no coinciden.");
                return;
            }
            hideError.run();
            confirmButton.setDisable(true);
            Thread.ofVirtual().start(() -> {
                try {
                    authRepository.confirmPasswordReset(token, newPassword);
                    Platform.runLater(() -> {
                        confirmButton.setDisable(false);
                        javafx.scene.control.Alert done = new javafx.scene.control.Alert(
                                javafx.scene.control.Alert.AlertType.INFORMATION,
                                "Contraseña cambiada. Ya puedes iniciar sesión con la nueva contraseña.");
                        done.setTitle("Contraseña actualizada");
                        done.setHeaderText(null);
                        done.initOwner(owner);
                        DialogStyler.apply(done);
                        done.showAndWait();
                        dialog.close();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        confirmButton.setDisable(false);
                        showError.accept(confirmErrorMessage(ex));
                    });
                }
            });
        });

        dialog.showAndWait();
    }

    private static String requestErrorMessage(Exception ex) {
        if (ex instanceof ApiException apiEx) {
            if (apiEx.getHttpStatus() == 0) return "No se pudo conectar con el servidor.";
            if (apiEx.getHttpStatus() == 429) return "Demasiados intentos. Espera un momento y vuelve a probar.";
        }
        return "No se pudo enviar el correo de recuperación.";
    }

    private static String confirmErrorMessage(Exception ex) {
        if (ex instanceof ApiException apiEx) {
            if (apiEx.getHttpStatus() == 0) return "No se pudo conectar con el servidor.";
            if (apiEx.getHttpStatus() == 429) return "Demasiados intentos. Espera un momento y vuelve a probar.";
            if (apiEx.getHttpStatus() == 400) return "El código no es válido o ha caducado. Pide uno nuevo.";
        }
        return "No se pudo cambiar la contraseña.";
    }
}
