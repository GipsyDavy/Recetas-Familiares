package org.gipsybuho.recetasfamiliares.common.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import org.gipsybuho.recetasfamiliares.families.FamilyEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberEntity;
import org.gipsybuho.recetasfamiliares.families.FamilyMemberRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRepository;
import org.gipsybuho.recetasfamiliares.families.FamilyRole;
import org.gipsybuho.recetasfamiliares.favorites.FavoriteRecipeEntity;
import org.gipsybuho.recetasfamiliares.favorites.FavoriteRecipeRepository;
import org.gipsybuho.recetasfamiliares.menus.MenuItemEntity;
import org.gipsybuho.recetasfamiliares.menus.MenuItemRepository;
import org.gipsybuho.recetasfamiliares.menus.MenuMealType;
import org.gipsybuho.recetasfamiliares.notes.FamilyNoteEntity;
import org.gipsybuho.recetasfamiliares.notes.FamilyNoteRepository;
import org.gipsybuho.recetasfamiliares.photos.RecipePhotoEntity;
import org.gipsybuho.recetasfamiliares.photos.RecipePhotoRepository;
import org.gipsybuho.recetasfamiliares.recipes.RecipeDifficulty;
import org.gipsybuho.recetasfamiliares.recipes.RecipeEntity;
import org.gipsybuho.recetasfamiliares.recipes.RecipeIngredientEntity;
import org.gipsybuho.recetasfamiliares.recipes.RecipeIngredientRepository;
import org.gipsybuho.recetasfamiliares.recipes.RecipeRepository;
import org.gipsybuho.recetasfamiliares.recipes.RecipeStepEntity;
import org.gipsybuho.recetasfamiliares.recipes.RecipeStepRepository;
import org.gipsybuho.recetasfamiliares.shopping.ShoppingListEntity;
import org.gipsybuho.recetasfamiliares.shopping.ShoppingListItemEntity;
import org.gipsybuho.recetasfamiliares.shopping.ShoppingListItemRepository;
import org.gipsybuho.recetasfamiliares.shopping.ShoppingListRepository;
import org.gipsybuho.recetasfamiliares.stock.StockItemEntity;
import org.gipsybuho.recetasfamiliares.stock.StockItemRepository;
import org.gipsybuho.recetasfamiliares.users.UserEntity;
import org.gipsybuho.recetasfamiliares.users.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "app.dev.seed-data", name = "enabled", havingValue = "true")
public class DevDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final RecipeStepRepository recipeStepRepository;
    private final StockItemRepository stockItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final ShoppingListRepository shoppingListRepository;
    private final ShoppingListItemRepository shoppingListItemRepository;
    private final FavoriteRecipeRepository favoriteRecipeRepository;
    private final FamilyNoteRepository familyNoteRepository;
    private final RecipePhotoRepository recipePhotoRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String displayName;
    private final String familyName;

    public DevDataSeeder(
            UserRepository userRepository,
            FamilyRepository familyRepository,
            FamilyMemberRepository familyMemberRepository,
            RecipeRepository recipeRepository,
            RecipeIngredientRepository recipeIngredientRepository,
            RecipeStepRepository recipeStepRepository,
            StockItemRepository stockItemRepository,
            MenuItemRepository menuItemRepository,
            ShoppingListRepository shoppingListRepository,
            ShoppingListItemRepository shoppingListItemRepository,
            FavoriteRecipeRepository favoriteRecipeRepository,
            FamilyNoteRepository familyNoteRepository,
            RecipePhotoRepository recipePhotoRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.dev.seed-data.email}") String email,
            @Value("${app.dev.seed-data.password}") String password,
            @Value("${app.dev.seed-data.display-name}") String displayName,
            @Value("${app.dev.seed-data.family-name}") String familyName
    ) {
        this.userRepository = userRepository;
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.recipeRepository = recipeRepository;
        this.recipeIngredientRepository = recipeIngredientRepository;
        this.recipeStepRepository = recipeStepRepository;
        this.stockItemRepository = stockItemRepository;
        this.menuItemRepository = menuItemRepository;
        this.shoppingListRepository = shoppingListRepository;
        this.shoppingListItemRepository = shoppingListItemRepository;
        this.favoriteRecipeRepository = favoriteRecipeRepository;
        this.familyNoteRepository = familyNoteRepository;
        this.recipePhotoRepository = recipePhotoRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.displayName = displayName;
        this.familyName = familyName;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (password == null || password.length() < 12) {
            throw new IllegalStateException("DEV_SEED_PASSWORD must be at least 12 characters when dev seed is enabled");
        }
        if (userRepository.existsByEmailIgnoreCaseAndDeletedFalse(email)) {
            return;
        }

        UserEntity user = userRepository.save(new UserEntity(email, displayName, passwordEncoder.encode(password)));
        FamilyEntity family = familyRepository.save(new FamilyEntity(familyName));
        familyMemberRepository.save(new FamilyMemberEntity(family, user, FamilyRole.OWNER));

        RecipeEntity tortilla = recipeRepository.save(new RecipeEntity(
                family,
                user,
                "Tortilla familiar",
                "Una tortilla sencilla para probar recetas, ingredientes, pasos, favoritos y menu.",
                4,
                15,
                20,
                RecipeDifficulty.EASY
        ));
        recipeIngredientRepository.save(new RecipeIngredientEntity(tortilla, 0, "Patatas", bd("700"), "g", null));
        recipeIngredientRepository.save(new RecipeIngredientEntity(tortilla, 1, "Huevos", bd("6"), "ud", null));
        recipeIngredientRepository.save(new RecipeIngredientEntity(tortilla, 2, "Cebolla", bd("1"), "ud", "Opcional"));
        recipeStepRepository.save(new RecipeStepEntity(tortilla, 0, "Pelar y cortar las patatas en laminas finas.", null));
        recipeStepRepository.save(new RecipeStepEntity(tortilla, 1, "Freir patatas y cebolla hasta que esten tiernas.", 15));
        recipeStepRepository.save(new RecipeStepEntity(tortilla, 2, "Mezclar con los huevos batidos y cuajar en sarten.", 5));
        recipePhotoRepository.save(new RecipePhotoEntity(
                tortilla,
                0,
                "https://example.com/dev/tortilla.jpg",
                "https://example.com/dev/tortilla-thumb.jpg",
                "Foto de ejemplo para desarrollo",
                "image/jpeg",
                128000L
        ));

        stockItemRepository.save(new StockItemEntity(family, "Huevos", bd("12"), "ud", bd("4"), LocalDate.now().plusDays(10), null));
        stockItemRepository.save(new StockItemEntity(family, "Patatas", bd("2.5"), "kg", bd("1"), null, "Saco pequeno"));
        stockItemRepository.save(new StockItemEntity(family, "Leche", bd("1"), "l", bd("2"), LocalDate.now().plusDays(3), "Reponer pronto"));

        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        menuItemRepository.save(new MenuItemEntity(family, tortilla, monday.plusDays(1), MenuMealType.DINNER, "Cena rapida"));
        menuItemRepository.save(new MenuItemEntity(family, null, monday.plusDays(2), MenuMealType.LUNCH, "Comida fuera"));

        ShoppingListEntity shoppingList = shoppingListRepository.save(new ShoppingListEntity(
                family,
                "Compra semanal demo",
                monday,
                monday.plusDays(6),
                "Lista de ejemplo para probar el cliente",
                false
        ));
        shoppingListItemRepository.save(new ShoppingListItemEntity(shoppingList, 0, "Aceite de oliva", bd("1"), "l", false, null));
        shoppingListItemRepository.save(new ShoppingListItemEntity(shoppingList, 1, "Pan", bd("2"), "ud", true, null));

        favoriteRecipeRepository.save(new FavoriteRecipeEntity(family, tortilla));
        familyNoteRepository.save(new FamilyNoteEntity(
                family,
                tortilla,
                "Recuerdo familiar",
                "Esta nota sirve para probar notas asociadas a recetas en desarrollo.",
                true
        ));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
