package org.gipsybuho.recetasfamiliares.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;

import java.util.List;
import java.util.Objects;

/** Full-screen step-by-step cooking mode window. */
public class CookingView {

    private final Stage stage;
    private final RecipeDtos.RecipeDto recipe;
    private final List<RecipeDtos.RecipeStepDto> steps;

    private int currentIndex = 0;
    private Timeline countdownTimer;
    private int timerSecondsLeft = 0;
    private boolean timerRunning = false;

    private final Label progressLabel  = new Label();
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label instructionLabel = new Label();
    private final VBox timerBox        = new VBox(10);
    private final Label timerValueLabel = new Label();
    private final Button toggleTimerBtn = new Button("▶  Iniciar");
    private final Button prevBtn        = new Button("← Anterior");
    private final Button nextBtn        = new Button("Siguiente →");

    // ── Factory ───────────────────────────────────────────────────────────────

    public static CookingView open(Window owner, RecipeDtos.RecipeDto recipe,
                                   List<RecipeDtos.RecipeStepDto> steps) {
        CookingView view = new CookingView(owner, recipe, steps);
        view.show();
        return view;
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    private CookingView(Window owner, RecipeDtos.RecipeDto recipe,
                        List<RecipeDtos.RecipeStepDto> steps) {
        this.recipe = recipe;
        this.steps  = steps != null ? steps : List.of();
        this.stage  = new Stage();
        stage.initOwner(owner);
        stage.setTitle("Modo Cocina — " + recipe.title());
        stage.setOnCloseRequest(e -> stopTimer());
        build();
    }

    // ── Show ──────────────────────────────────────────────────────────────────

    private void show() {
        stage.show();
        stage.getScene().setOnKeyPressed((KeyEvent e) -> {
            switch (e.getCode()) {
                case LEFT -> prevStep();
                case RIGHT -> nextStep();
                default -> {}
            }
        });
        stage.setMaximized(true);
        renderStep(0);
    }

    // ── Build UI ──────────────────────────────────────────────────────────────

    private void build() {
        // Top bar
        Button exitBtn = new Button("✕  Salir del modo cocina");
        exitBtn.getStyleClass().add("action-button-secondary");
        exitBtn.setOnAction(e -> { stopTimer(); stage.close(); });

        Label titleLabel = new Label(recipe.title());
        titleLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #8B6F5E;");

        BorderPane topBar = new BorderPane();
        topBar.setLeft(exitBtn);
        topBar.setCenter(titleLabel);
        topBar.setPadding(new Insets(14, 24, 14, 24));
        topBar.setStyle("-fx-background-color: #F5EDE0; -fx-border-color: #D4BBA8; -fx-border-width: 0 0 1 0;");
        BorderPane.setAlignment(exitBtn, Pos.CENTER_LEFT);
        BorderPane.setAlignment(titleLabel, Pos.CENTER);

        // Progress
        progressLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #C17D52;");
        progressBar.setMaxWidth(560);
        progressBar.setPrefHeight(8);
        progressBar.setStyle("-fx-accent: #C17D52;");

        VBox progressBox = new VBox(8, progressLabel, progressBar);
        progressBox.setAlignment(Pos.CENTER);
        progressBox.setPadding(new Insets(8, 48, 0, 48));

        // Instruction (hero element)
        instructionLabel.setWrapText(true);
        instructionLabel.setTextAlignment(TextAlignment.CENTER);
        instructionLabel.setStyle(
            "-fx-font-size: 34px; -fx-font-weight: bold; -fx-text-fill: #3D2B1F; -fx-line-spacing: 8;");
        instructionLabel.setMaxWidth(760);

        StackPane instructionPane = new StackPane(instructionLabel);
        instructionPane.setAlignment(Pos.CENTER);
        instructionPane.setPadding(new Insets(16, 48, 16, 48));
        VBox.setVgrow(instructionPane, Priority.ALWAYS);

        // Timer
        timerValueLabel.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: #3D2B1F;");
        timerValueLabel.setAlignment(Pos.CENTER);

        toggleTimerBtn.setStyle(
            "-fx-background-color: #C17D52; -fx-text-fill: white; -fx-font-size: 14px; " +
            "-fx-background-radius: 8; -fx-padding: 8 24; -fx-cursor: hand;");
        toggleTimerBtn.setOnAction(e -> onToggleTimer());

        timerBox.getChildren().addAll(timerValueLabel, toggleTimerBtn);
        timerBox.setAlignment(Pos.CENTER);
        timerBox.setPadding(new Insets(18, 40, 18, 40));
        timerBox.setStyle(
            "-fx-background-color: #FFF3E8; -fx-background-radius: 16; " +
            "-fx-border-color: #E8C9A8; -fx-border-radius: 16; -fx-border-width: 1;");
        timerBox.setMaxWidth(300);

        StackPane timerWrapper = new StackPane(timerBox);
        timerWrapper.setAlignment(Pos.CENTER);
        timerWrapper.setPadding(new Insets(0, 0, 8, 0));

        // Navigation
        prevBtn.getStyleClass().add("action-button-secondary");
        prevBtn.setStyle(prevBtn.getStyle() +
            "; -fx-font-size: 15px; -fx-min-width: 180px;");
        prevBtn.setOnAction(e -> prevStep());

        nextBtn.setStyle(
            "-fx-background-color: #C17D52; -fx-text-fill: white; -fx-font-size: 15px; " +
            "-fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 10 20; " +
            "-fx-cursor: hand; -fx-min-width: 180px;");
        nextBtn.setOnAction(e -> nextStep());

        HBox navBar = new HBox(20, prevBtn, nextBtn);
        navBar.setAlignment(Pos.CENTER);
        navBar.setPadding(new Insets(8, 24, 32, 24));

        // Root
        VBox center = new VBox(20, progressBox, instructionPane, timerWrapper, navBar);
        center.setAlignment(Pos.CENTER);
        VBox.setVgrow(instructionPane, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(center);
        root.setStyle("-fx-background-color: #FAF7F2;");

        Scene scene = new Scene(root, 1100, 720);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm());
        stage.setScene(scene);
    }

    // ── Step rendering ────────────────────────────────────────────────────────

    private void renderStep(int index) {
        stopTimer();
        timerValueLabel.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: #3D2B1F;");
        timerBox.setStyle(
            "-fx-background-color: #FFF3E8; -fx-background-radius: 16; " +
            "-fx-border-color: #E8C9A8; -fx-border-radius: 16; -fx-border-width: 1;");

        if (steps.isEmpty()) {
            progressLabel.setText("Sin pasos");
            progressBar.setProgress(1.0);
            instructionLabel.setText("Esta receta no tiene pasos registrados.");
            timerBox.setVisible(false); timerBox.setManaged(false);
            prevBtn.setDisable(true);
            nextBtn.setText("Cerrar");
            nextBtn.setOnAction(e -> { stopTimer(); stage.close(); });
            return;
        }

        if (index >= steps.size()) {
            progressLabel.setText("¡Receta completada!");
            progressBar.setProgress(1.0);
            instructionLabel.setStyle(
                "-fx-font-size: 40px; -fx-font-weight: bold; -fx-text-fill: #C17D52; -fx-line-spacing: 8;");
            instructionLabel.setText("¡Buen provecho!");
            timerBox.setVisible(false); timerBox.setManaged(false);
            prevBtn.setDisable(false);
            nextBtn.setText("Cerrar");
            nextBtn.setOnAction(e -> { stopTimer(); stage.close(); });
            return;
        }

        currentIndex = index;
        var step = steps.get(index);

        progressLabel.setText("Paso " + (index + 1) + " de " + steps.size());
        progressBar.setProgress((double) (index + 1) / steps.size());
        instructionLabel.setStyle(
            "-fx-font-size: 34px; -fx-font-weight: bold; -fx-text-fill: #3D2B1F; -fx-line-spacing: 8;");
        instructionLabel.setText(step.instruction() != null ? step.instruction() : "");

        prevBtn.setDisable(index == 0);
        boolean isLast = index == steps.size() - 1;
        nextBtn.setText(isLast ? "¡Finalizar!" : "Siguiente →");
        nextBtn.setOnAction(e -> nextStep());
        nextBtn.setStyle(
            "-fx-background-color: " + (isLast ? "#8DBF8A" : "#C17D52") +
            "; -fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold; " +
            "-fx-background-radius: 8; -fx-padding: 10 20; -fx-cursor: hand; -fx-min-width: 180px;");

        if (step.timerMinutes() != null && step.timerMinutes() > 0) {
            timerSecondsLeft = step.timerMinutes() * 60;
            timerRunning = false;
            updateTimerDisplay();
            updateTimerButton();
            timerBox.setVisible(true); timerBox.setManaged(true);
        } else {
            timerBox.setVisible(false); timerBox.setManaged(false);
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void prevStep() {
        if (currentIndex > 0) {
            stopTimer();
            renderStep(currentIndex - 1);
        }
    }

    private void nextStep() {
        stopTimer();
        renderStep(currentIndex < steps.size() - 1 ? currentIndex + 1 : steps.size());
    }

    private void onToggleTimer() {
        if (timerSecondsLeft <= 0) {
            // Reset
            var step = steps.get(currentIndex);
            if (step.timerMinutes() != null) timerSecondsLeft = step.timerMinutes() * 60;
            timerRunning = false;
            timerValueLabel.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: #3D2B1F;");
            timerBox.setStyle(
                "-fx-background-color: #FFF3E8; -fx-background-radius: 16; " +
                "-fx-border-color: #E8C9A8; -fx-border-radius: 16; -fx-border-width: 1;");
            updateTimerDisplay();
            updateTimerButton();
            return;
        }
        if (timerRunning) stopTimer(); else startTimer();
    }

    private void startTimer() {
        stopTimer();
        timerRunning = true;
        updateTimerButton();
        countdownTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            timerSecondsLeft--;
            updateTimerDisplay();
            if (timerSecondsLeft <= 0) {
                timerRunning = false;
                countdownTimer = null;
                timerValueLabel.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: #C0392B;");
                timerBox.setStyle(
                    "-fx-background-color: #FDECEA; -fx-background-radius: 16; " +
                    "-fx-border-color: #D4837A; -fx-border-radius: 16; -fx-border-width: 1;");
                toggleTimerBtn.setText("↺  Reiniciar");
            }
        }));
        countdownTimer.setCycleCount(timerSecondsLeft);
        countdownTimer.play();
    }

    private void stopTimer() {
        timerRunning = false;
        if (countdownTimer != null) { countdownTimer.stop(); countdownTimer = null; }
        if (timerSecondsLeft > 0) updateTimerButton();
    }

    private void updateTimerDisplay() {
        int mm = timerSecondsLeft / 60;
        int ss = timerSecondsLeft % 60;
        timerValueLabel.setText(
            timerSecondsLeft > 0 ? String.format("⏱  %02d:%02d", mm, ss) : "⏱  ¡Listo!");
    }

    private void updateTimerButton() {
        if (timerSecondsLeft <= 0) {
            toggleTimerBtn.setText("↺  Reiniciar");
        } else {
            toggleTimerBtn.setText(timerRunning ? "⏸  Pausar" : "▶  Iniciar");
        }
    }
}
