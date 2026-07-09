package org.gipsybuho.recetasfamiliares.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.gipsybuho.recetasfamiliares.families.FamilyRepository;
import org.gipsybuho.recetasfamiliares.menus.MenuItemRepository;
import org.gipsybuho.recetasfamiliares.notes.FamilyNoteRepository;
import org.gipsybuho.recetasfamiliares.recipes.RecipeRepository;
import org.gipsybuho.recetasfamiliares.shopping.ShoppingListRepository;
import org.gipsybuho.recetasfamiliares.stock.StockItemRepository;
import org.gipsybuho.recetasfamiliares.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("dev")
@SpringBootTest(properties = {
        "spring.datasource.url=${DB_TEST_URL:jdbc:postgresql://localhost:5432/recetas_familiares_test}",
        "spring.datasource.username=${DB_TEST_USERNAME:recetas_app}",
        "spring.datasource.password=${DB_TEST_PASSWORD:}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.security.jwt.secret=test-only-change-this-secret-32-bytes-minimum",
        "app.dev.seed-data.enabled=true",
        "app.dev.seed-data.email=seed-test@recetas.local",
        "app.dev.seed-data.password=seed-test-password"
})
class DevDataSeederTest {

    @Autowired
    private DevDataSeeder devDataSeeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private StockItemRepository stockItemRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private ShoppingListRepository shoppingListRepository;

    @Autowired
    private FamilyNoteRepository familyNoteRepository;

    @Test
    void createsDemoDataWhenEnabledInDevProfile() {
        assertThat(userRepository.existsByEmailIgnoreCaseAndDeletedFalse("seed-test@recetas.local")).isTrue();
        assertThat(familyRepository.count()).isEqualTo(1);
        assertThat(recipeRepository.count()).isEqualTo(1);
        assertThat(stockItemRepository.count()).isEqualTo(3);
        assertThat(menuItemRepository.count()).isEqualTo(2);
        assertThat(shoppingListRepository.count()).isEqualTo(1);
        assertThat(familyNoteRepository.count()).isEqualTo(1);
    }

    @Test
    void doesNotDuplicateDemoDataWhenRunMoreThanOnce() {
        devDataSeeder.run();

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(familyRepository.count()).isEqualTo(1);
        assertThat(recipeRepository.count()).isEqualTo(1);
        assertThat(stockItemRepository.count()).isEqualTo(3);
        assertThat(menuItemRepository.count()).isEqualTo(2);
        assertThat(shoppingListRepository.count()).isEqualTo(1);
        assertThat(familyNoteRepository.count()).isEqualTo(1);
    }
}
