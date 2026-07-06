package org.gipsybuho.recetasfamiliares.ui;

import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.List;
import java.util.Map;

/**
 * Ayuda contextual MVP: diálogo modal con consejos según la vista activa.
 * Se abre con F1 o con el botón Ayuda del sidebar.
 */
final class HelpDialog {

    private record Topic(String emoji, String title, List<String> tips) {}

    private static final Map<String, Topic> TOPICS = Map.ofEntries(
        Map.entry("dashboard", new Topic("🏠", "Inicio", List.of(
            "Resumen de tu familia: recetas recientes, stock próximo a caducar y menú de hoy.",
            "Pulsa \"Sincronizar ahora\" para traer los últimos cambios de tu familia.",
            "Usa la búsqueda global (Ctrl+F) para encontrar lo que necesites en cualquier momento."))),
        Map.entry("recipes", new Topic("📖", "Recetas", List.of(
            "Crea recetas con ingredientes, pasos, fotos y tiempo de preparación.",
            "Pulsa \"Cocinar\" en una receta para el modo cocina a pantalla completa.",
            "En el modo cocina, usa las flechas ← y → para avanzar o retroceder, la barra espaciadora para el temporizador y Esc para salir.",
            "Marca favoritas para encontrarlas antes."))),
        Map.entry("stock", new Topic("🧂", "Stock", List.of(
            "Controla tu despensa: cantidades, unidades y fechas de caducidad.",
            "Los artículos próximos a caducar se muestran en la pantalla de Inicio.",
            "Define umbral mínimo para no quedarte sin lo esencial."))),
        Map.entry("menu", new Topic("📅", "Menú semanal", List.of(
            "Asigna recetas a cada comida del día para planificar la semana.",
            "Desde el menú puedes generar la lista de la compra.",
            "Los cambios se sincronizan con toda la familia."))),
        Map.entry("shopping", new Topic("🛒", "Lista de la compra", List.of(
            "Añade artículos a mano o genera la lista desde el menú semanal.",
            "Marca lo comprado; la lista se comparte con tu familia.",
            "Los artículos comprados pueden añadirse directamente a tu stock."))),
        Map.entry("notes", new Topic("📝", "Notas familiares", List.of(
            "Apunta trucos, recuerdos y secretos de cocina de tu familia.",
            "Las notas se sincronizan entre todos los dispositivos.",
            "Usa la búsqueda global (Ctrl+F) para encontrarlas."))),
        Map.entry("members", new Topic("👨‍👩‍👧", "Miembros", List.of(
            "Gestiona quién pertenece a tu familia y su rol.",
            "El propietario y los administradores pueden cambiar roles o expulsar miembros.",
            "Los datos familiares solo son visibles para los miembros."))),
        Map.entry("settings", new Topic("⚙", "Ajustes", List.of(
            "Cambia tema de color, modo oscuro, tipografía y sonidos.",
            "Accede a Ajustes rápidamente con Ctrl + Coma (Ctrl+,).",
            "En Diagnóstico puedes probar la conexión con el backend."))),
        Map.entry("profile", new Topic("👤", "Mi perfil", List.of(
            "Cambia tu foto y tu nombre, que serán visibles para toda la familia.",
            "Consulta tu familia, tu rol y la actividad compartida.",
            "Desde aquí puedes volver a ver la guía de bienvenida.")))
    );

    private static final Topic FALLBACK = new Topic("❓", "Ayuda", List.of(
        "Navega con el menú lateral entre recetas, stock, menú y lista de compra.",
        "Busca en todo con Ctrl+F.",
        "Pulsa F1 en cualquier pantalla para ver consejos de esa vista."));

    private HelpDialog() {}

    static void show(Window owner, String viewKey) {
        Topic topic = TOPICS.getOrDefault(viewKey, FALLBACK);

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Ayuda");
        ButtonType closeType = new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType guideType = new ButtonType("Ver guía de bienvenida", ButtonBar.ButtonData.HELP);
        dialog.getDialogPane().getButtonTypes().addAll(guideType, closeType);

        Label emojiLabel = new Label(topic.emoji());
        emojiLabel.setStyle("-fx-font-size: 40px;");
        Label titleLabel = new Label(topic.title());
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        HBox headerRow = new HBox(12, emojiLabel, titleLabel);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        VBox tipsBox = new VBox(8);
        for (String tip : topic.tips()) {
            Label tipLabel = new Label("•  " + tip);
            tipLabel.setWrapText(true);
            tipLabel.setMaxWidth(420);
            tipsBox.getChildren().add(tipLabel);
        }

        VBox content = new VBox(14, headerRow, tipsBox);
        content.setPadding(new Insets(20, 28, 12, 28));
        content.setPrefWidth(480);
        dialog.getDialogPane().setContent(content);
        DialogStyler.apply(dialog);

        Button guideBtn = (Button) dialog.getDialogPane().lookupButton(guideType);
        guideBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            OnboardingDialog.showAgain(owner);
            e.consume();
        });

        dialog.getDialogPane().setOpacity(0);
        dialog.setOnShown(e -> {
            var pane = dialog.getDialogPane();
            pane.setOpacity(1);
            ScaleTransition scale = new ScaleTransition(Duration.millis(200), pane);
            scale.setFromX(0.95); scale.setFromY(0.95);
            scale.setToX(1.0);    scale.setToY(1.0);
            scale.play();
        });

        dialog.showAndWait();
    }
}
