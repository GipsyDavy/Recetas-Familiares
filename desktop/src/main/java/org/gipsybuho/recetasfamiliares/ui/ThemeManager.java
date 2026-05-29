package org.gipsybuho.recetasfamiliares.ui;

import javafx.scene.Scene;

import java.util.Objects;
import java.util.prefs.Preferences;

public final class ThemeManager {

    public enum AppTheme {
        BOSQUE, TERRACOTA, OCASO, MEDITERRANEO, LAVANDA,
        OLIVA, CANELA, MENTA, FRAMBUESA, NOCHE_VERANO;

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
            };
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

    private static final ThemeManager INSTANCE = new ThemeManager();
    public static ThemeManager getInstance() { return INSTANCE; }
    private ThemeManager() {}

    private Scene currentScene;

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

    public void applyTheme(AppTheme theme, ThemeMode mode) {
        PREFS.put(KEY_THEME, theme.name());
        PREFS.put(KEY_MODE, mode.name());
        if (currentScene == null) return;

        boolean dark = switch (mode) {
            case LIGHT  -> false;
            case DARK   -> true;
            case SYSTEM -> isSystemDark();
        };

        var sheets = currentScene.getStylesheets();
        sheets.removeIf(s -> s.contains("/themes/"));
        String cssFile = theme.cssFileName(dark);
        var url = getClass().getResource("/" + cssFile);
        if (url != null) sheets.add(url.toExternalForm());
    }

    private static boolean isSystemDark() {
        // Heuristic: check platform or default to light
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            try {
                var proc = Runtime.getRuntime().exec(new String[]{
                    "reg", "query",
                    "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", "AppsUseLightTheme"
                });
                String out = new String(proc.getInputStream().readAllBytes());
                return out.contains("0x0");
            } catch (Exception ignored) {}
        }
        return false;
    }
}
