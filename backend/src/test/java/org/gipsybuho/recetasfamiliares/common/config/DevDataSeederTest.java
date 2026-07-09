package org.gipsybuho.recetasfamiliares.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.gipsybuho.recetasfamiliares.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private static final String SEED_EMAIL = "seed-test@recetas.local";

    @Autowired
    private DevDataSeeder devDataSeeder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createsDemoDataWhenEnabledInDevProfile() {
        assertSeedDataCounts();
    }

    @Test
    void doesNotDuplicateDemoDataWhenRunMoreThanOnce() {
        devDataSeeder.run();

        assertSeedDataCounts();
    }

    private void assertSeedDataCounts() {
        assertThat(userRepository.existsByEmailIgnoreCaseAndDeletedFalse(SEED_EMAIL)).isTrue();
        String familyId = jdbcTemplate.queryForObject("""
                SELECT fm.family_id
                FROM family_members fm
                JOIN users u ON u.id = fm.user_id
                WHERE lower(u.email) = lower(?) AND fm.deleted = false
                """, String.class, SEED_EMAIL);

        assertThat(familyId).isNotBlank();
        assertCount("families", "id", familyId, 1);
        assertCount("recipes", "family_id", familyId, 1);
        assertCount("stock_items", "family_id", familyId, 3);
        assertCount("menu_items", "family_id", familyId, 2);
        assertCount("shopping_lists", "family_id", familyId, 1);
        assertCount("family_notes", "family_id", familyId, 1);
    }

    private void assertCount(String table, String column, String value, long expected) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Long.class,
                value
        );
        assertThat(count).isEqualTo(expected);
    }
}
