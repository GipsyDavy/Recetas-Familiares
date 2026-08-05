package org.gipsybuho.recetasfamiliares.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.gipsybuho.recetasfamiliares.api.dto.StockDtos;
import org.gipsybuho.recetasfamiliares.api.dto.SyncDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;
import org.gipsybuho.recetasfamiliares.ui.state.GlobalSearchResults;

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

        Label header = new Label("Resultados para «" + q + "»");
        header.getStyleClass().add("view-header");
        getChildren().add(header);

        GlobalSearchResults results = GlobalSearchResults.from(
                context.getRecipeRepository().getCache().getItems(),
                context.getStockRepository().getCache().getItems(),
                context.getNoteRepository().getCache().getItems(),
                rawQuery);
        List<RecipeDtos.RecipeDto> recipes = results.recipes();
        List<StockDtos.StockItemDto> stock = results.stock();
        List<SyncDtos.NoteDtos.FamilyNoteDto> notes = results.notes();

        if (results.isEmpty()) {
            Label empty = new Label("Sin resultados para «" + q + "»");
            empty.getStyleClass().add("search-empty");
            VBox.setMargin(empty, new Insets(48, 0, 0, 0));
            getChildren().add(empty);
            return;
        }

        VBox resultsBox = new VBox(4);
        resultsBox.setPadding(new Insets(16, 0, 0, 0));

        if (!recipes.isEmpty()) {
            resultsBox.getChildren().add(sectionHeader("📖  Recetas", recipes.size()));
            for (var r : recipes) {
                StringBuilder meta = new StringBuilder();
                if (r.prepMinutes() != null) meta.append(r.prepMinutes()).append(" min");
                if (r.difficulty() != null) {
                    if (!meta.isEmpty()) meta.append("  ·  ");
                    meta.append(r.difficulty());
                }
                resultsBox.getChildren().add(recipeResultRow(r.title(), meta.toString(), r.coverThumbnailUrl()));
            }
        }

        if (!stock.isEmpty()) {
            resultsBox.getChildren().add(sectionHeader("🧂  Stock", stock.size()));
            for (var s : stock) {
                String qty = s.quantity() != null
                        ? s.quantity() + (s.unit() != null ? " " + s.unit() : "")
                        : "";
                resultsBox.getChildren().add(resultRow(s.name(), qty, "stock"));
            }
        }

        if (!notes.isEmpty()) {
            resultsBox.getChildren().add(sectionHeader("📝  Notas", notes.size()));
            for (var n : notes) {
                String title = (n.title() != null && !n.title().isBlank()) ? n.title() : "Sin título";
                resultsBox.getChildren().add(
                        resultRow(title, GlobalSearchResults.notePreview(n.body()), "notes"));
            }
        }

        ScrollPane scroll = new ScrollPane(resultsBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);
    }

    private Button resultRow(String title, String meta, String viewKey) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("search-result-title");

        VBox box = new VBox(2, titleLabel);
        if (meta != null && !meta.isBlank()) {
            Label metaLabel = new Label(meta);
            metaLabel.getStyleClass().add("search-result-meta");
            metaLabel.setMaxWidth(Double.MAX_VALUE);
            box.getChildren().add(metaLabel);
        }

        Button btn = new Button();
        btn.setGraphic(box);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        btn.getStyleClass().add("search-result-row");
        btn.setOnAction(e -> onNavigate.accept(viewKey));
        return btn;
    }

    /**
     * Fila de resultado de receta: identica a las demas, con la miniatura de portada
     * delante. Se construye sobre resultRow para no duplicar estilos ni navegacion.
     */
    private Button recipeResultRow(String title, String meta, String coverUrl) {
        Button btn = resultRow(title, meta, "recipes");
        Node texts = btn.getGraphic();
        RecipeThumbnail thumb = new RecipeThumbnail(context, RecipeThumbnail.LIST_SIZE);
        thumb.show(coverUrl);
        HBox row = new HBox(12, thumb, texts);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(texts, Priority.ALWAYS);
        btn.setGraphic(row);
        return btn;
    }

    private static Label sectionHeader(String text, int count) {
        Label label = new Label(text + "  (" + count + ")");
        label.getStyleClass().add("search-result-section");
        return label;
    }
}
