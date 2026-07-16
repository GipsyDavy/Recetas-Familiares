package org.gipsybuho.recetasfamiliares.ui;

import javafx.scene.Scene;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

public final class ThemeManager {

    public enum AppTheme {
        BOSQUE, TERRACOTA, OCASO, MEDITERRANEO, LAVANDA,
        OLIVA, CANELA, MENTA, FRAMBUESA, NOCHE_VERANO,
        RUBI_NOCTURNO, AURORA_BOREAL, JADE_IMPERIAL,
        COBRE_LUNAR, CIRUELA_SOLAR, CORAL_ABISAL;

        public String displayName() {
            return switch (this) {
                case BOSQUE       -> "Bosque";
                case TERRACOTA    -> "Terracota";
                case OCASO        -> "Ocaso";
                case MEDITERRANEO -> "Mediterráneo";
                case LAVANDA      -> "Lavanda";
                case OLIVA        -> "Oliva";
                case CANELA       -> "Canela";
                case MENTA        -> "Menta";
                case FRAMBUESA    -> "Frambuesa";
                case NOCHE_VERANO -> "Noche de Verano";
                case RUBI_NOCTURNO -> "Rubí Nocturno";
                case AURORA_BOREAL -> "Aurora Boreal";
                case JADE_IMPERIAL -> "Jade Imperial";
                case COBRE_LUNAR   -> "Cobre Lunar";
                case CIRUELA_SOLAR -> "Ciruela Solar";
                case CORAL_ABISAL  -> "Coral Abisal";
            };
        }

        public String description() {
            return switch (this) {
                case BOSQUE        -> "Verde sereno y naturaleza";
                case TERRACOTA     -> "Arcilla cálida y hogar";
                case OCASO         -> "Atardecer suave y especias";
                case MEDITERRANEO  -> "Azul costero y luz clara";
                case LAVANDA       -> "Violeta calmado y delicado";
                case OLIVA         -> "Oliva suave y tierra";
                case CANELA        -> "Canela, crema y madera";
                case MENTA         -> "Menta fresca y luminosa";
                case FRAMBUESA     -> "Frambuesa elegante y floral";
                case NOCHE_VERANO  -> "Azul nocturno y cielo abierto";
                case RUBI_NOCTURNO -> "Carbón, borgoña y luz rubí";
                case AURORA_BOREAL -> "Índigo, menta y violeta";
                case JADE_IMPERIAL -> "Jade, celadón y cobre";
                case COBRE_LUNAR   -> "Grafito, cobre y amatista";
                case CIRUELA_SOLAR -> "Ciruela, ámbar y seda";
                case CORAL_ABISAL  -> "Océano profundo y coral";
            };
        }

        public boolean isFeatured() {
            return this == RUBI_NOCTURNO;
        }

        public boolean recommendedDark() {
            return this == RUBI_NOCTURNO;
        }

        public String cssFileName(boolean dark) {
            return "themes/theme-" + name().toLowerCase().replace("_", "-")
                    + (dark ? "-dark" : "-light") + ".css";
        }
    }

    public enum ThemeMode { LIGHT, DARK, SYSTEM }

    private static final Preferences PREFS = Preferences.userRoot().node("recetas/theme");
    private static final String KEY_THEME = "selected_theme";
    private static final String KEY_MODE  = "theme_mode";
    private static final long SYSTEM_DARK_CACHE_NANOS = TimeUnit.SECONDS.toNanos(5);

    private static final ThemeManager INSTANCE = new ThemeManager();
    public static ThemeManager getInstance() { return INSTANCE; }
    private ThemeManager() {}

    private Scene currentScene;
    private long systemDarkCheckedAt;
    private boolean cachedSystemDark;

    public void attach(Scene scene) {
        this.currentScene = scene;
        applyTheme(loadTheme(), loadMode());
    }

    public AppTheme loadTheme() {
        String name = PREFS.get(KEY_THEME, AppTheme.BOSQUE.name());
        try { return AppTheme.valueOf(name); } catch (Exception e) { return AppTheme.BOSQUE; }
    }

    public ThemeMode loadMode() {
        String name = PREFS.get(KEY_MODE, ThemeMode.SYSTEM.name());
        try { return ThemeMode.valueOf(name); } catch (Exception e) { return ThemeMode.SYSTEM; }
    }

    public boolean isDarkModeActive(ThemeMode mode) {
        return switch (mode) {
            case LIGHT  -> false;
            case DARK   -> true;
            case SYSTEM -> cachedSystemDark();
        };
    }

    public String stylesheetFor(AppTheme theme, ThemeMode mode) {
        String cssFile = theme.cssFileName(isDarkModeActive(mode));
        var url = getClass().getResource("/" + cssFile);
        return url != null ? url.toExternalForm() : null;
    }

    public void applyCurrentTheme(Scene scene) {
        if (scene == null) return;
        String sheet = stylesheetFor(loadTheme(), loadMode());
        if (sheet == null) return;
        var sheets = scene.getStylesheets();
        sheets.removeIf(s -> s.contains("/themes/"));
        if (!sheets.contains(sheet)) sheets.add(sheet);
    }

    public void applyTheme(AppTheme theme, ThemeMode mode) {
        PREFS.put(KEY_THEME, theme.name());
        PREFS.put(KEY_MODE, mode.name());
        if (currentScene == null) return;

        var sheets = currentScene.getStylesheets();
        sheets.removeIf(s -> s.contains("/themes/"));
        String sheet = stylesheetFor(theme, mode);
        if (sheet != null) sheets.add(sheet);
    }

    private synchronized boolean cachedSystemDark() {
        long now = System.nanoTime();
        if (systemDarkCheckedAt != 0 && now - systemDarkCheckedAt < SYSTEM_DARK_CACHE_NANOS) {
            return cachedSystemDark;
        }
        cachedSystemDark = detectSystemDark();
        systemDarkCheckedAt = now;
        return cachedSystemDark;
    }

    private static boolean detectSystemDark() {
        // Heuristic: check platform or default to light
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            try {
                var proc = new ProcessBuilder(
                    "reg", "query",
                    "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", "AppsUseLightTheme"
                ).redirectErrorStream(true).start();
                if (!proc.waitFor(350, TimeUnit.MILLISECONDS)) {
                    proc.destroyForcibly();
                    return false;
                }
                String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                return out.contains("0x0");
            } catch (Exception ignored) {}
        }
        return false;
    }
}
