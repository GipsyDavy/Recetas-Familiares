package org.gipsybuho.recetasfamiliares.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeManagerTest {

    private static final String[] LEGACY_THEME_IDS = {
            "BOSQUE", "TERRACOTA", "OCASO", "MEDITERRANEO", "LAVANDA",
            "OLIVA", "CANELA", "MENTA", "FRAMBUESA", "NOCHE_VERANO"
    };

    private static final Map<ThemeManager.AppTheme, String> NEW_THEME_DESCRIPTIONS = Map.of(
            ThemeManager.AppTheme.RUBI_NOCTURNO, "Carbón, borgoña y luz rubí",
            ThemeManager.AppTheme.AURORA_BOREAL, "Índigo, menta y violeta",
            ThemeManager.AppTheme.JADE_IMPERIAL, "Jade, celadón y cobre",
            ThemeManager.AppTheme.COBRE_LUNAR, "Grafito, cobre y amatista",
            ThemeManager.AppTheme.CIRUELA_SOLAR, "Ciruela, ámbar y seda",
            ThemeManager.AppTheme.CORAL_ABISAL, "Océano profundo y coral"
    );

    private static final Map<ThemeManager.AppTheme, String> NEW_THEME_NAMES = Map.of(
            ThemeManager.AppTheme.RUBI_NOCTURNO, "Rubí Nocturno",
            ThemeManager.AppTheme.AURORA_BOREAL, "Aurora Boreal",
            ThemeManager.AppTheme.JADE_IMPERIAL, "Jade Imperial",
            ThemeManager.AppTheme.COBRE_LUNAR, "Cobre Lunar",
            ThemeManager.AppTheme.CIRUELA_SOLAR, "Ciruela Solar",
            ThemeManager.AppTheme.CORAL_ABISAL, "Coral Abisal"
    );

    private static final Set<String> THEME_TOKENS = Set.of(
            "recetas-bg",
            "recetas-surface",
            "recetas-surface-alt",
            "recetas-surface-var",
            "recetas-surface-border",
            "recetas-form-bg",
            "recetas-primary",
            "recetas-primary-hover",
            "recetas-primary-fg",
            "recetas-primary-cont",
            "recetas-on-primary-cont",
            "recetas-sidebar-bg",
            "recetas-sidebar-hdr",
            "recetas-sidebar-fg",
            "recetas-sidebar-active",
            "recetas-text-primary",
            "recetas-text-second",
            "recetas-text-body",
            "recetas-text-muted",
            "recetas-border",
            "recetas-separator",
            "recetas-header-bg",
            "recetas-cell-selected",
            "recetas-error",
            "recetas-error-bg",
            "recetas-expiry-bg",
            "recetas-focus-border"
    );

    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("^\\s*(recetas-[\\w-]+)\\s*:");
    private static final Pattern TOKEN_VALUE_PATTERN =
            Pattern.compile("^\\s*(recetas-[\\w-]+)\\s*:\\s*(#[0-9a-fA-F]{6})");

    private static final List<List<String>> CONTRAST_PAIRS = List.of(
            List.of("recetas-primary-fg", "recetas-primary"),
            List.of("recetas-on-primary-cont", "recetas-primary-cont"),
            List.of("recetas-sidebar-fg", "recetas-sidebar-bg"),
            List.of("recetas-sidebar-active", "recetas-sidebar-bg"),
            List.of("recetas-text-primary", "recetas-bg"),
            List.of("recetas-text-primary", "recetas-surface"),
            List.of("recetas-text-second", "recetas-bg"),
            List.of("recetas-text-second", "recetas-surface"),
            List.of("recetas-text-body", "recetas-bg"),
            List.of("recetas-text-body", "recetas-surface"),
            List.of("recetas-text-muted", "recetas-bg"),
            List.of("recetas-text-muted", "recetas-surface"),
            List.of("recetas-error", "recetas-bg"),
            List.of("recetas-error", "recetas-surface"),
            List.of("recetas-error", "recetas-error-bg")
    );

    @Test
    void catalogoConservaPrimerosDiezIdsYAnadeSeisAlFinal() {
        ThemeManager.AppTheme[] themes = ThemeManager.AppTheme.values();

        assertEquals(16, themes.length);
        assertArrayEquals(LEGACY_THEME_IDS, Arrays.stream(themes)
                .limit(LEGACY_THEME_IDS.length)
                .map(Enum::name)
                .toArray(String[]::new));
        assertArrayEquals(new String[]{
                        "RUBI_NOCTURNO", "AURORA_BOREAL", "JADE_IMPERIAL",
                        "COBRE_LUNAR", "CIRUELA_SOLAR", "CORAL_ABISAL"
                }, Arrays.stream(themes)
                        .skip(LEGACY_THEME_IDS.length)
                        .map(Enum::name)
                        .toArray(String[]::new));
    }

    @Test
    void metadataVisibleEsCompletaYRubiEsElUnicoTemaDestacado() {
        for (ThemeManager.AppTheme theme : ThemeManager.AppTheme.values()) {
            assertFalse(theme.displayName().isBlank(), theme.name());
            assertFalse(theme.description().isBlank(), theme.name());
        }

        NEW_THEME_DESCRIPTIONS.forEach((theme, description) ->
                assertEquals(description, theme.description(), theme.name()));
        NEW_THEME_NAMES.forEach((theme, displayName) ->
                assertEquals(displayName, theme.displayName(), theme.name()));
        assertEquals(1, Arrays.stream(ThemeManager.AppTheme.values())
                .filter(ThemeManager.AppTheme::isFeatured)
                .count());
        assertEquals(1, Arrays.stream(ThemeManager.AppTheme.values())
                .filter(ThemeManager.AppTheme::recommendedDark)
                .count());
        assertTrue(ThemeManager.AppTheme.RUBI_NOCTURNO.isFeatured());
        assertTrue(ThemeManager.AppTheme.RUBI_NOCTURNO.recommendedDark());
    }

    @Test
    void existenExactamenteTreintaYDosCssDeTema() throws IOException, URISyntaxException {
        var themesResource = ThemeManager.class.getResource("/themes");
        assertNotNull(themesResource, "No se encontro el directorio de temas en el classpath");
        Path themesDirectory = Path.of(themesResource.toURI());
        Set<String> expectedFiles = new HashSet<>();
        for (ThemeManager.AppTheme theme : ThemeManager.AppTheme.values()) {
            expectedFiles.add(Path.of(theme.cssFileName(false)).getFileName().toString());
            expectedFiles.add(Path.of(theme.cssFileName(true)).getFileName().toString());
        }

        try (var files = Files.list(themesDirectory)) {
            Set<String> actualFiles = files
                    .filter(path -> path.getFileName().toString().endsWith(".css"))
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
            assertEquals(32, actualFiles.size());
            assertEquals(expectedFiles, actualFiles);
        }
    }

    @Test
    void cadaTemaTieneClaroYOscuroConContratoExactoDeTokens()
            throws IOException, URISyntaxException {
        int resources = 0;
        for (ThemeManager.AppTheme theme : ThemeManager.AppTheme.values()) {
            for (boolean dark : List.of(false, true)) {
                String fileName = theme.cssFileName(dark);
                var resource = ThemeManager.class.getResource("/" + fileName);
                assertNotNull(resource, fileName);

                List<String> tokens = new ArrayList<>();
                for (String line : Files.readAllLines(Path.of(resource.toURI()))) {
                    var matcher = TOKEN_PATTERN.matcher(line);
                    if (matcher.find()) {
                        tokens.add(matcher.group(1));
                    }
                }

                assertEquals(27, tokens.size(), fileName + " debe declarar 27 tokens");
                assertEquals(THEME_TOKENS, Set.copyOf(tokens),
                        fileName + " no cumple el contrato visual");
                resources++;
            }
        }
        assertEquals(32, resources);
    }

    @Test
    void losSeisTemasNuevosMantienenContrasteAaEnParesSemanticos()
            throws IOException, URISyntaxException {
        for (ThemeManager.AppTheme theme : NEW_THEME_NAMES.keySet()) {
            for (boolean dark : List.of(false, true)) {
                Map<String, String> colors = readThemeColors(theme, dark);
                for (List<String> pair : CONTRAST_PAIRS) {
                    double ratio = contrastRatio(colors.get(pair.get(0)), colors.get(pair.get(1)));
                    assertTrue(ratio >= 4.5,
                            () -> theme.name() + (dark ? " dark " : " light ")
                                    + pair + " obtuvo contraste " + ratio);
                }
            }
        }
    }

    private Map<String, String> readThemeColors(ThemeManager.AppTheme theme, boolean dark)
            throws IOException, URISyntaxException {
        String fileName = theme.cssFileName(dark);
        var resource = ThemeManager.class.getResource("/" + fileName);
        assertNotNull(resource, fileName);
        Map<String, String> colors = new HashMap<>();
        for (String line : Files.readAllLines(Path.of(resource.toURI()))) {
            var matcher = TOKEN_VALUE_PATTERN.matcher(line);
            if (matcher.find()) {
                colors.put(matcher.group(1), matcher.group(2));
            }
        }
        return colors;
    }

    private double contrastRatio(String first, String second) {
        double firstLuminance = luminance(first);
        double secondLuminance = luminance(second);
        return (Math.max(firstLuminance, secondLuminance) + 0.05)
                / (Math.min(firstLuminance, secondLuminance) + 0.05);
    }

    private double luminance(String hex) {
        assertNotNull(hex);
        double red = Integer.parseInt(hex.substring(1, 3), 16) / 255.0;
        double green = Integer.parseInt(hex.substring(3, 5), 16) / 255.0;
        double blue = Integer.parseInt(hex.substring(5, 7), 16) / 255.0;
        return 0.2126 * linearize(red) + 0.7152 * linearize(green) + 0.0722 * linearize(blue);
    }

    private double linearize(double channel) {
        return channel <= 0.04045
                ? channel / 12.92
                : Math.pow((channel + 0.055) / 1.055, 2.4);
    }
}
