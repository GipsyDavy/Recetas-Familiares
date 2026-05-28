package org.gipsybuho.recetasfamiliares.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import org.gipsybuho.recetasfamiliares.api.dto.RecipeDtos;
import org.gipsybuho.recetasfamiliares.core.AppContext;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class RecipeDetailView extends VBox {

    private final AppContext context;
    private final Runnable onRecipeChanged;   // called after edit or delete so list refreshes

    private final Text titleText = new Text();
    private final Text metaText = new Text();
    private final Text descText = new Text();
    private final VBox ingredientsList = new VBox(6);
    private final VBox stepsList = new VBox(10);
    private final HBox photosList = new HBox(10);
    private final Label photosStatusLabel = new Label();
    private final Label statusLabel = new Label();
    private final HBox actionBar = new HBox(10);
    private final Button favBtn = new Button("♡  Favorito");
    private final Button addPhotoBtn = new Button("Añadir foto");

    private RecipeDtos.RecipeDto currentRecipe;
    private List<RecipeDtos.RecipeIngredientDto> currentIngredients = new ArrayList<>();
    private List<RecipeDtos.RecipeStepDto> currentSteps = new ArrayList<>();
    private List<RecipeDtos.RecipePhotoResponse> currentPhotos = new ArrayList<>();

    public RecipeDetailView(AppContext context, Runnable onRecipeChanged) {
        this.context = context;
        this.onRecipeChanged = onRecipeChanged;
        build();
    }

    private void build() {
        getStyleClass().add("detail-view");
        setSpacing(0);
        setPadding(new Insets(0));

        titleText.getStyleClass().add("recipe-title");
        metaText.getStyleClass().add("recipe-meta");
        descText.getStyleClass().add("recipe-desc");
        descText.setWrappingWidth(480);

        // Action bar (favorite + edit + delete) — hidden until a recipe is selected
        favBtn.getStyleClass().add("action-button-secondary");
        favBtn.setOnAction(e -> toggleFavorite());

        addPhotoBtn.getStyleClass().add("action-button-secondary");
        addPhotoBtn.setOnAction(e -> chooseAndUploadPhoto());

        Button cookingBtn = new Button("👨‍🍳  Modo Cocina");
        cookingBtn.getStyleClass().add("action-button-secondary");
        cookingBtn.setOnAction(e -> openCookingMode());

        Button copyBtn = new Button("📋  Copiar");
        copyBtn.getStyleClass().add("action-button-secondary");
        copyBtn.setOnAction(e -> copyToClipboard());

        Button exportBtn = new Button("💾  Exportar");
        exportBtn.getStyleClass().add("action-button-secondary");
        exportBtn.setOnAction(e -> exportToFile());

        Button editBtn = new Button("Editar");
        editBtn.getStyleClass().add("action-button-secondary");
        editBtn.setOnAction(e -> openEditForm());

        Button deleteBtn = new Button("Eliminar");
        deleteBtn.getStyleClass().addAll("action-button-secondary", "delete-button");
        deleteBtn.setOnAction(e -> confirmDelete());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        actionBar.getChildren().addAll(spacer, favBtn, addPhotoBtn, cookingBtn, copyBtn, exportBtn, editBtn, deleteBtn);
        actionBar.setPadding(new Insets(12, 16, 8, 16));
        actionBar.setVisible(false);
        actionBar.setManaged(false);

        Label ingLabel = new Label("Ingredientes");
        ingLabel.getStyleClass().add("section-header");

        Label stepsLabel = new Label("Pasos");
        stepsLabel.getStyleClass().add("section-header");

        Label photosLabel = new Label("Fotos");
        photosLabel.getStyleClass().add("section-header");

        photosList.setPadding(new Insets(4, 0, 4, 0));
        ScrollPane photosScroll = new ScrollPane(photosList);
        photosScroll.setFitToHeight(true);
        photosScroll.setFitToWidth(true);
        photosScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        photosScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        photosScroll.setPrefViewportHeight(110);
        photosScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        photosStatusLabel.getStyleClass().add("status-label");

        statusLabel.getStyleClass().add("status-label");
        statusLabel.setVisible(false);

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        VBox content = new VBox(16,
                titleText, metaText, descText,
                new Separator(),
                ingLabel, ingredientsList,
                new Separator(),
                stepsLabel, stepsList,
                new Separator(),
                photosLabel, photosScroll, photosStatusLabel,
                statusLabel
        );
        content.setPadding(new Insets(16, 24, 24, 24));
        scroll.setContent(content);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(actionBar, scroll);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void load(RecipeDtos.RecipeDto recipe) {
        currentRecipe = recipe;
        titleText.setText(recipe.title());
        metaText.setText(buildMeta(recipe));
        descText.setText(recipe.description() != null ? recipe.description() : "");
        ingredientsList.getChildren().clear();
        stepsList.getChildren().clear();
        photosList.getChildren().clear();
        photosStatusLabel.setText("");
        statusLabel.setText("Cargando...");
        statusLabel.setVisible(true);
        actionBar.setVisible(true);
        actionBar.setManaged(true);
        updateFavButton(recipe.id());

        Thread.ofVirtual().start(() -> {
            try {
                var ingredients = context.getRecipeRepository().loadIngredients(recipe.id());
                var steps = context.getRecipeRepository().loadSteps(recipe.id());
                Platform.runLater(() -> renderContent(ingredients, steps));
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    statusLabel.setText("No se pudieron cargar los detalles.");
                    statusLabel.setVisible(true);
                });
            }
        });
    }

    public void showEmpty() {
        currentRecipe = null;
        titleText.setText("Selecciona una receta");
        metaText.setText("");
        descText.setText("");
        ingredientsList.getChildren().clear();
        stepsList.getChildren().clear();
        photosList.getChildren().clear();
        photosStatusLabel.setText("");
        currentPhotos = new ArrayList<>();
        statusLabel.setVisible(false);
        actionBar.setVisible(false);
        actionBar.setManaged(false);
    }

    // ── Render ────────────────────────────────────────────────────────────────

    private void renderContent(List<RecipeDtos.RecipeIngredientDto> ingredients,
                               List<RecipeDtos.RecipeStepDto> steps) {
        this.currentIngredients = ingredients != null ? ingredients : new ArrayList<>();
        this.currentSteps = steps != null ? steps : new ArrayList<>();
        statusLabel.setVisible(false);
        ingredientsList.getChildren().clear();
        for (var ing : ingredients) {
            String qty = ing.quantity() != null ? ing.quantity().toString() : "";
            String unit = ing.unit() != null ? " " + ing.unit() : "";
            Label l = new Label("• " + ing.name() + (qty.isBlank() ? "" : " — " + qty + unit));
            l.getStyleClass().add("ingredient-item");
            ingredientsList.getChildren().add(l);
        }
        if (ingredients.isEmpty())
            ingredientsList.getChildren().add(noDataLabel("Sin ingredientes."));

        stepsList.getChildren().clear();
        int num = 1;
        for (var step : steps) {
            VBox stepBox = new VBox(4);
            stepBox.getStyleClass().add("step-box");
            Label numLabel = new Label("Paso " + num++);
            numLabel.getStyleClass().add("step-number");
            Label desc = new Label(step.instruction());
            desc.setWrapText(true);
            desc.getStyleClass().add("step-desc");
            if (step.timerMinutes() != null) {
                Label dur = new Label(step.timerMinutes() + " min");
                dur.getStyleClass().add("recipe-meta");
                stepBox.getChildren().addAll(numLabel, desc, dur);
            } else {
                stepBox.getChildren().addAll(numLabel, desc);
            }
            stepsList.getChildren().add(stepBox);
        }
        if (steps.isEmpty())
            stepsList.getChildren().add(noDataLabel("Sin pasos."));

        loadAndRenderPhotos();
    }

    private void loadAndRenderPhotos() {
        if (currentRecipe == null) return;

        RecipeDtos.RecipeDto recipe = currentRecipe;
        photosStatusLabel.setText("Cargando fotos...");
        photosList.getChildren().clear();

        Thread.ofVirtual().start(() -> {
            try {
                String familyId = familyId(recipe);
                var photos = context.getRecipeRepository().loadPhotos(familyId, recipe.id());
                Platform.runLater(() -> {
                    if (currentRecipe == null || !recipe.id().equals(currentRecipe.id())) return;
                    currentPhotos = photos != null ? photos : new ArrayList<>();
                    renderPhotos();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> photosStatusLabel.setText("No se pudieron cargar las fotos."));
            }
        });
    }

    private void renderPhotos() {
        photosList.getChildren().clear();

        if (currentPhotos.isEmpty()) {
            photosStatusLabel.setText("Sin fotos.");
            return;
        }

        photosStatusLabel.setText(currentPhotos.size() + " foto" + (currentPhotos.size() == 1 ? "" : "s"));
        for (var photo : currentPhotos) {
            photosList.getChildren().add(buildPhotoNode(photo));
        }
    }

    private Node buildPhotoNode(RecipeDtos.RecipePhotoResponse photo) {
        ImageView imageView = new ImageView(new Image(photo.url(), true));
        imageView.setFitWidth(110);
        imageView.setFitHeight(80);
        imageView.setPreserveRatio(false);
        imageView.setStyle("-fx-background-color: #FAF7F2; -fx-border-color: #C17D52; -fx-border-radius: 4;");

        MenuItem deleteItem = new MenuItem("Eliminar foto");
        deleteItem.setOnAction(e -> deletePhoto(photo));
        ContextMenu menu = new ContextMenu(deleteItem);
        imageView.setOnContextMenuRequested(e -> menu.show(imageView, e.getScreenX(), e.getScreenY()));

        VBox box = new VBox(4, imageView);
        if (photo.caption() != null && !photo.caption().isBlank()) {
            Label caption = new Label(photo.caption());
            caption.getStyleClass().add("recipe-meta");
            caption.setMaxWidth(110);
            caption.setWrapText(true);
            box.getChildren().add(caption);
        }
        return box;
    }

    private void chooseAndUploadPhoto() {
        if (currentRecipe == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Añadir foto");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imágenes", "*.jpg", "*.jpeg", "*.png")
        );
        File file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) return;

        RecipeDtos.RecipeDto recipe = currentRecipe;
        String contentType = contentType(file);
        addPhotoBtn.setDisable(true);
        photosStatusLabel.setText("Subiendo foto...");

        Thread.ofVirtual().start(() -> {
            try {
                context.getRecipeRepository().uploadPhoto(familyId(recipe), recipe.id(), file, contentType, null);
                Platform.runLater(() -> {
                    addPhotoBtn.setDisable(false);
                    loadAndRenderPhotos();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    addPhotoBtn.setDisable(false);
                    photosStatusLabel.setText("Error al subir foto: " + ex.getMessage());
                });
            }
        });
    }

    private void deletePhoto(RecipeDtos.RecipePhotoResponse photo) {
        if (currentRecipe == null) return;

        RecipeDtos.RecipeDto recipe = currentRecipe;
        photosStatusLabel.setText("Eliminando foto...");

        Thread.ofVirtual().start(() -> {
            try {
                context.getRecipeRepository().deletePhoto(familyId(recipe), recipe.id(), photo.id());
                Platform.runLater(this::loadAndRenderPhotos);
            } catch (Exception ex) {
                Platform.runLater(() -> photosStatusLabel.setText("Error al eliminar foto: " + ex.getMessage()));
            }
        });
    }

    // ── Cooking mode ──────────────────────────────────────────────────────────

    private void openCookingMode() {
        if (currentRecipe == null) return;
        CookingView.open(getScene().getWindow(), currentRecipe, currentSteps);
    }

    // ── Favorite ──────────────────────────────────────────────────────────────

    private void updateFavButton(String recipeId) {
        boolean isFav = context.getFavoriteRepository().findByRecipeId(recipeId).isPresent();
        favBtn.setText(isFav ? "♥  Favorito" : "♡  Favorito");
        favBtn.setStyle(isFav ? "-fx-text-fill: -color-error;" : "");
    }

    private void toggleFavorite() {
        if (currentRecipe == null) return;
        String recipeId = currentRecipe.id();
        favBtn.setDisable(true);
        Thread.ofVirtual().start(() -> {
            try {
                var existing = context.getFavoriteRepository().findByRecipeId(recipeId);
                if (existing.isPresent()) {
                    context.getFavoriteRepository().remove(existing.get().id());
                } else {
                    context.getFavoriteRepository().add(recipeId);
                }
                Platform.runLater(() -> {
                    updateFavButton(recipeId);
                    favBtn.setDisable(false);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error al cambiar favorito: " + ex.getMessage());
                    statusLabel.setVisible(true);
                    favBtn.setDisable(false);
                });
            }
        });
    }

    // ── Edit ──────────────────────────────────────────────────────────────────

    private void openEditForm() {
        if (currentRecipe == null) return;
        statusLabel.setText("Cargando datos para editar...");
        statusLabel.setVisible(true);

        Thread.ofVirtual().start(() -> {
            try {
                var ingredients = context.getRecipeRepository().loadIngredients(currentRecipe.id());
                var steps = context.getRecipeRepository().loadSteps(currentRecipe.id());
                Platform.runLater(() -> {
                    statusLabel.setVisible(false);
                    var dialog = RecipeFormDialog.forEdit(
                            getScene().getWindow(), context,
                            currentRecipe, ingredients, steps,
                            updated -> {
                                load(updated);
                                onRecipeChanged.run();
                            }
                    );
                    dialog.show();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    statusLabel.setText("No se pudieron cargar los datos.");
                    statusLabel.setVisible(true);
                });
            }
        });
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    private void confirmDelete() {
        if (currentRecipe == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Eliminar receta");
        confirm.setHeaderText("¿Eliminar \"" + currentRecipe.title() + "\"?");
        confirm.setContentText("Esta acción no se puede deshacer.");
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        confirm.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) doDelete();
        });
    }

    private void doDelete() {
        String recipeId = currentRecipe.id();
        statusLabel.setText("Eliminando...");
        statusLabel.setVisible(true);

        Thread.ofVirtual().start(() -> {
            try {
                context.getRecipeRepository().delete(recipeId);
                Platform.runLater(() -> {
                    showEmpty();
                    onRecipeChanged.run();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error al eliminar: " + ex.getMessage());
                    statusLabel.setVisible(true);
                });
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void copyToClipboard() {
        if (currentRecipe == null) return;
        var sb = new StringBuilder();

        sb.append("🍳  ").append(currentRecipe.title()).append("\n");

        // Meta
        var meta = new ArrayList<String>();
        if (currentRecipe.servings() != null) meta.add(currentRecipe.servings() + " porciones");
        int totalMin = (currentRecipe.prepMinutes() != null ? currentRecipe.prepMinutes() : 0)
                     + (currentRecipe.cookMinutes() != null ? currentRecipe.cookMinutes() : 0);
        if (totalMin > 0) meta.add(totalMin + " min");
        if (currentRecipe.difficulty() != null) meta.add(currentRecipe.difficulty());
        if (!meta.isEmpty()) sb.append(String.join("  ·  ", meta)).append("\n");

        if (currentRecipe.description() != null && !currentRecipe.description().isBlank()) {
            sb.append("\n").append(currentRecipe.description()).append("\n");
        }

        // Ingredients
        if (!currentIngredients.isEmpty()) {
            sb.append("\n🥗  Ingredientes\n");
            for (var ing : currentIngredients) {
                sb.append("• ").append(ing.name());
                if (ing.quantity() != null) {
                    sb.append("  —  ").append(java.math.BigDecimal.valueOf(ing.quantity()).stripTrailingZeros().toPlainString());
                    if (ing.unit() != null && !ing.unit().isBlank()) sb.append(" ").append(ing.unit());
                }
                sb.append("\n");
            }
        }

        // Steps
        if (!currentSteps.isEmpty()) {
            sb.append("\n👨‍🍳  Preparación\n");
            int num = 1;
            for (var step : currentSteps) {
                sb.append(num++).append(". ").append(step.instruction());
                if (step.timerMinutes() != null) sb.append("  [").append(step.timerMinutes()).append(" min]");
                sb.append("\n");
            }
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(sb.toString().trim());
        Clipboard.getSystemClipboard().setContent(content);
        statusLabel.setText("Receta copiada al portapapeles ✓");
        statusLabel.setVisible(true);
    }

    private void exportToFile() {
        if (currentRecipe == null) return;

        var sb = new StringBuilder();
        sb.append("🍳  ").append(currentRecipe.title()).append("\n");

        var meta = new java.util.ArrayList<String>();
        if (currentRecipe.servings() != null) meta.add(currentRecipe.servings() + " porciones");
        int totalMin = (currentRecipe.prepMinutes() != null ? currentRecipe.prepMinutes() : 0)
                     + (currentRecipe.cookMinutes() != null ? currentRecipe.cookMinutes() : 0);
        if (totalMin > 0) meta.add(totalMin + " min");
        if (currentRecipe.difficulty() != null) meta.add(currentRecipe.difficulty());
        if (!meta.isEmpty()) sb.append(String.join("  ·  ", meta)).append("\n");

        if (currentRecipe.description() != null && !currentRecipe.description().isBlank()) {
            sb.append("\n").append(currentRecipe.description()).append("\n");
        }

        if (!currentIngredients.isEmpty()) {
            sb.append("\n🥗  Ingredientes\n");
            for (var ing : currentIngredients) {
                sb.append("• ").append(ing.name());
                if (ing.quantity() != null) {
                    sb.append("  —  ").append(java.math.BigDecimal.valueOf(ing.quantity()).stripTrailingZeros().toPlainString());
                    if (ing.unit() != null && !ing.unit().isBlank()) sb.append(" ").append(ing.unit());
                }
                sb.append("\n");
            }
        }

        if (!currentSteps.isEmpty()) {
            sb.append("\n👨‍🍳  Preparación\n");
            int num = 1;
            for (var step : currentSteps) {
                sb.append(num++).append(". ").append(step.instruction());
                if (step.timerMinutes() != null) sb.append("  [").append(step.timerMinutes()).append(" min]");
                sb.append("\n");
            }
        }

        String text = sb.toString().trim();
        String safeTitle = currentRecipe.title().replaceAll("[\\\\/:*?\"<>|]", "_").trim();

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar receta");
        chooser.setInitialFileName(safeTitle + ".txt");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Texto plano", "*.txt"));
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;

        try {
            Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
            statusLabel.setText("Receta exportada: " + file.getName() + " ✓");
            statusLabel.setVisible(true);
        } catch (Exception ex) {
            statusLabel.setText("Error al exportar: " + ex.getMessage());
            statusLabel.setVisible(true);
        }
    }

    private Label noDataLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("no-data-label");
        return l;
    }

    private String familyId(RecipeDtos.RecipeDto recipe) {
        return recipe.familyId() != null ? recipe.familyId() : context.getSession().getFamilyId();
    }

    private String contentType(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private String buildMeta(RecipeDtos.RecipeDto r) {
        StringBuilder sb = new StringBuilder();
        if (r.servings() != null) sb.append(r.servings()).append(" personas  ");
        if (r.prepMinutes() != null) sb.append("Prep: ").append(r.prepMinutes()).append(" min  ");
        if (r.cookMinutes() != null) sb.append("Cocción: ").append(r.cookMinutes()).append(" min  ");
        if (r.difficulty() != null) sb.append(r.difficulty());
        return sb.toString().trim();
    }
}
