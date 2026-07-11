package org.gipsybuho.recetasfamiliares.recipes;

import java.math.BigDecimal;
import java.util.List;

import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Siembra 5 recetas muy conocidas (2 faciles, 2 medias, 1 dificil) al crear
 * una familia nueva, para que la app no arranque vacia. Los clientes las
 * reciben por el sync normal; el usuario puede editarlas o borrarlas.
 */
@Service
public class StarterRecipeSeeder {

    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository ingredientRepository;
    private final RecipeStepRepository stepRepository;
    private final boolean enabled;

    public StarterRecipeSeeder(
            RecipeRepository recipeRepository,
            RecipeIngredientRepository ingredientRepository,
            RecipeStepRepository stepRepository,
            @org.springframework.beans.factory.annotation.Value("${app.starter-recipes.enabled:true}") boolean enabled
    ) {
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
        this.stepRepository = stepRepository;
        this.enabled = enabled;
    }

    @Transactional
    public void seedStarterRecipes(FamilyEntity family) {
        if (!enabled) {
            return;
        }
        seedTortilla(family);
        seedGazpacho(family);
        seedCarbonara(family);
        seedLentejas(family);
        seedPaella(family);
    }

    private void seedTortilla(FamilyEntity family) {
        RecipeEntity recipe = recipeRepository.save(new RecipeEntity(
                family,
                "Tortilla de patatas",
                "La clásica tortilla española: jugosa por dentro y dorada por fuera.",
                4, 15, 25, RecipeDifficulty.EASY));
        ingredients(recipe,
                ing("Patatas", "4", "unidades", null),
                ing("Huevos", "6", "unidades", null),
                ing("Cebolla", "1", "unidad", "opcional"),
                ing("Aceite de oliva", "300", "ml", "para freír"),
                ing("Sal", null, null, "al gusto"));
        steps(recipe,
                step("Pela las patatas y córtalas en láminas finas. Si usas cebolla, pícala en juliana.", null),
                step("Fríe las patatas (y la cebolla) en abundante aceite a fuego medio hasta que estén tiernas.", 20),
                step("Bate los huevos con sal, escurre las patatas y mézclalo todo. Deja reposar 5 minutos.", 5),
                step("Cuaja la tortilla en una sartén con un poco de aceite, dale la vuelta con un plato y termina de cuajar al gusto.", 6));
    }

    private void seedGazpacho(FamilyEntity family) {
        RecipeEntity recipe = recipeRepository.save(new RecipeEntity(
                family,
                "Gazpacho andaluz",
                "Sopa fría de tomate, fresquita y lista en 10 minutos de trabajo.",
                4, 10, 0, RecipeDifficulty.EASY));
        ingredients(recipe,
                ing("Tomates maduros", "1", "kg", null),
                ing("Pepino", "0.5", "unidad", null),
                ing("Pimiento verde", "1", "unidad", null),
                ing("Ajo", "1", "diente", null),
                ing("Pan del día anterior", "50", "g", null),
                ing("Aceite de oliva virgen extra", "50", "ml", null),
                ing("Vinagre de Jerez", "1", "cucharada", null),
                ing("Sal", null, null, "al gusto"));
        steps(recipe,
                step("Lava y trocea los tomates, el pepino y el pimiento. Remoja el pan en agua.", null),
                step("Tritura todas las verduras con el ajo, el pan escurrido, el aceite, el vinagre y la sal hasta que quede fino.", null),
                step("Pasa por un colador si lo quieres más fino y enfría en la nevera al menos 2 horas antes de servir.", 120));
    }

    private void seedCarbonara(FamilyEntity family) {
        RecipeEntity recipe = recipeRepository.save(new RecipeEntity(
                family,
                "Espaguetis a la carbonara",
                "La carbonara italiana auténtica: huevo, queso, guanciale y pimienta. Sin nata.",
                2, 10, 15, RecipeDifficulty.MEDIUM));
        ingredients(recipe,
                ing("Espaguetis", "200", "g", null),
                ing("Guanciale o panceta", "100", "g", null),
                ing("Yemas de huevo", "3", "unidades", null),
                ing("Queso pecorino rallado", "50", "g", "o parmesano"),
                ing("Pimienta negra recién molida", null, null, "generosa"));
        steps(recipe,
                step("Corta el guanciale en tiras y dóralo a fuego medio en una sartén sin aceite hasta que quede crujiente.", 8),
                step("Cuece la pasta en agua con sal hasta que esté al dente. Reserva un vaso del agua de cocción.", 10),
                step("Mezcla las yemas con el queso y mucha pimienta hasta formar una crema.", null),
                step("Fuera del fuego, junta la pasta con el guanciale y la crema de huevo; ajusta con agua de cocción hasta que quede sedosa.", null));
    }

    private void seedLentejas(FamilyEntity family) {
        RecipeEntity recipe = recipeRepository.save(new RecipeEntity(
                family,
                "Lentejas estofadas con chorizo",
                "Plato de cuchara de toda la vida, reconfortante y completo.",
                4, 15, 45, RecipeDifficulty.MEDIUM));
        ingredients(recipe,
                ing("Lentejas pardinas", "400", "g", null),
                ing("Chorizo", "1", "unidad", null),
                ing("Cebolla", "1", "unidad", null),
                ing("Zanahoria", "2", "unidades", null),
                ing("Patata", "1", "unidad", null),
                ing("Ajo", "2", "dientes", null),
                ing("Pimentón dulce", "1", "cucharadita", null),
                ing("Hoja de laurel", "1", "unidad", null),
                ing("Aceite de oliva", "2", "cucharadas", null),
                ing("Sal", null, null, "al gusto"));
        steps(recipe,
                step("Sofríe la cebolla, el ajo y la zanahoria picados en el aceite hasta que la cebolla esté transparente.", 8),
                step("Añade el pimentón, remueve unos segundos y agrega las lentejas, el chorizo en rodajas, la patata en trozos y el laurel.", null),
                step("Cubre con agua fría dos dedos por encima y cuece a fuego suave hasta que las lentejas estén tiernas. Sala al final.", 40));
    }

    private void seedPaella(FamilyEntity family) {
        RecipeEntity recipe = recipeRepository.save(new RecipeEntity(
                family,
                "Paella de pollo y verduras",
                "Arroz al estilo valenciano casero: sofrito lento, buen caldo y reposo final.",
                4, 25, 35, RecipeDifficulty.HARD));
        ingredients(recipe,
                ing("Arroz redondo", "320", "g", null),
                ing("Pollo troceado", "600", "g", null),
                ing("Judía verde plana", "150", "g", null),
                ing("Garrofón o alubia grande", "100", "g", "opcional"),
                ing("Tomate rallado", "2", "unidades", null),
                ing("Pimentón dulce", "1", "cucharadita", null),
                ing("Azafrán o colorante", null, null, "unas hebras"),
                ing("Caldo de pollo", "1", "litro", "aprox. 3 partes por 1 de arroz"),
                ing("Aceite de oliva", "60", "ml", null),
                ing("Sal", null, null, "al gusto"));
        steps(recipe,
                step("Dora el pollo salado en la paella con el aceite a fuego medio-alto hasta que tome buen color.", 12),
                step("Añade las judías y el garrofón y rehógalos unos minutos con el pollo.", 5),
                step("Incorpora el tomate rallado y el pimentón; sofríe hasta que el tomate pierda el agua.", 6),
                step("Vierte el caldo caliente con el azafrán y deja hervir para que el pollo termine de hacerse.", 10),
                step("Reparte el arroz, ajusta de sal y cuece sin remover: fuerte los primeros minutos y suave después, hasta consumir el caldo.", 18),
                step("Aparta del fuego y deja reposar tapada con un paño antes de servir.", 5));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private record Ing(String name, String quantity, String unit, String note) {}

    private record Step(String instruction, Integer timerMinutes) {}

    private static Ing ing(String name, String quantity, String unit, String note) {
        return new Ing(name, quantity, unit, note);
    }

    private static Step step(String instruction, Integer timerMinutes) {
        return new Step(instruction, timerMinutes);
    }

    private void ingredients(RecipeEntity recipe, Ing... items) {
        int position = 1;
        for (Ing item : items) {
            ingredientRepository.save(new RecipeIngredientEntity(
                    recipe,
                    position++,
                    item.name(),
                    item.quantity() != null ? new BigDecimal(item.quantity()) : null,
                    item.unit(),
                    item.note()));
        }
    }

    private void steps(RecipeEntity recipe, Step... items) {
        int position = 1;
        for (Step item : List.of(items)) {
            stepRepository.save(new RecipeStepEntity(recipe, position++, item.instruction(), item.timerMinutes()));
        }
    }
}
