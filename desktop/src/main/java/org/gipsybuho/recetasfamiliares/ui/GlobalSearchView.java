package org.gipsybuho.recetasfamiliares.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.gipsybuho.recetasfamiliares.api.dto.StockDtos;
import org.gipsybuho.recetasfamiliares.api.dto.SyncDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;

import java.util.List;
import java.util.function.Consumer;

public class GlobalSearchView extends VBox {

    private final AppContext context;
    private final Consumer<String> onNavigate;

    public GlobalSearchView(AppContext context, Consumer<String> onNavigate) {
        this.context = context;
        this.onNavigate = onNavigate;
        setPadding(new Insets(24));
        setSpacing(0);
    }

    public void search(String rawQuery) {
        getChildren().clear();

        String q = rawQuery == null ? "" : rawQuery.trim();
        String lower = q.toLowerCase();

        Label header = new Label("Resultados para «" + q + "»");
        header.getStyleClass().add("view-header");
        getChildren().add(header);

        List<RecipeDtos.RecipeDto> recipes = context.getRecipeRepository().getCache().getItems()
                .stream().filter(r -> matches(r.title(), lower) || matches(r.description(), lower))
                .toList();

        List<StockDtos.StockItemDto> stock = context.getStockRepository().getCache().getItems()
                .stream().filter(s -> matches(s.name(), lower))
                .toList();

        List<SyncDtos.NoteDtos.FamilyNoteDto> notes = context.getNoteRepository().getCache().getItems()
                .stream().filter(n -> matches(n.title(), lower) || matches(n.body(), lower))
                .toList();

        if (recipes.isEmpty() && stock.isEmpty() && notes.isEmpty()) {
            Label empty = new Label("Sin resultados para «" + q + "»");
            empty.setStyle("-fx-font-size: 14px; -fx-text-fill: #8B6F5E;");
            VBox.setMargin(empty, new Insets(48, 0, 0, 0));
            getChildren().add(empty);
            return;
        }

        VBox results = new VBox(4);
        results.setPadding(new Insets(16, 0, 0, 0));

        if (!recipes.isEmpty()) {
            results.getChildren().add(sectionHeader("📖  Recetas", recipes.size()));
            for (var r : recipes) {
                StringBuilder meta = new StringBuilder();
                if (r.prepMinutes() != null) meta.append(r.prepMinutes()).append(" min");
                if (r.difficulty() != null) {
                    if (!meta.isEmpty()) meta.append("  ·  ");
                    meta.append(r.difficulty());
                }
                results.getChildren().add(resultRow(r.title(), meta.toString(), "recipes"));
            }
        }

        if (!stock.isEmpty()) {
            results.getChildren().add(sectionHeader("🧂  Stock", stock.size()));
            for (var s : stock) {
                String qty = s.quantity() != null
                        ? s.quantity() + (s.unit() != null ? " " + s.unit() : "")
                        : "";
                results.getChildren().add(resultRow(s.name(), qty, "stock"));
            }
        }

        if (!notes.isEmpty()) {
            results.getChildren().add(sectionHeader("📝  Notas", notes.size()));
            for (var n : notes) {
                String title = (n.title() != null && !n.title().isBlank()) ? n.title() : "Sin título";
                String preview = n.body() != null
                        ? n.body().replaceAll("\\s+", " ").substring(0, Math.min(80, n.body().length()))
                        : "";
                results.getChildren().add(resultRow(title, preview, "notes"));
            }
        }

        ScrollPane scroll = new ScrollPane(results);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);
    }

    private Button resultRow(String title, String meta, String viewKey) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #3D2B1F;");

        VBox box = new VBox(2, titleLabel);
        if (meta != null && !meta.isBlank()) {
            Label metaLabel = new Label(meta);
            metaLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #8B6F5E;");
            metaLabel.setMaxWidth(Double.MAX_VALUE);
            box.getChildren().add(metaLabel);
        }

        Button btn = new Button();
        btn.setGraphic(box);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        btn.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; " +
                     "-fx-cursor: hand; -fx-padding: 8 12 8 12;");
        btn.setOnMouseEntered(e -> btn.setStyle(
                "-fx-background-color: #EDD9C8; -fx-border-color: transparent; " +
                "-fx-cursor: hand; -fx-padding: 8 12 8 12;"));
        btn.setOnMouseExited(e -> btn.setStyle(
                "-fx-background-color: transparent; -fx-border-color: transparent; " +
                "-fx-cursor: hand; -fx-padding: 8 12 8 12;"));
        btn.setOnAction(e -> onNavigate.accept(viewKey));
        return btn;
    }

    private static Label sectionHeader(String text, int count) {
        Label label = new Label(text + "  (" + count + ")");
        label.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #8B6F5E; " +
                       "-fx-padding: 14 0 4 4;");
        return label;
    }

    private static boolean matches(String field, String lower) {
        return field != null && field.toLowerCase().contains(lower);
    }
}
