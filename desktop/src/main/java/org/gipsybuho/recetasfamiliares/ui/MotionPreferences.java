package org.gipsybuho.recetasfamiliares.ui;

import java.util.Objects;
import java.util.prefs.Preferences;

/** Preferencia local y centralizada para desactivar movimiento puramente cosmético. */
public final class MotionPreferences {

    private static final String KEY_REDUCE_MOTION = "reduceMotion";

    private final Preferences preferences;

    MotionPreferences(Preferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    public static boolean isReducedMotion() {
        return DefaultHolder.INSTANCE.read();
    }

    public static void setReducedMotion(boolean reduced) {
        DefaultHolder.INSTANCE.write(reduced);
    }

    boolean read() {
        return preferences.getBoolean(KEY_REDUCE_MOTION, false);
    }

    void write(boolean reduced) {
        preferences.putBoolean(KEY_REDUCE_MOTION, reduced);
    }

    private static final class DefaultHolder {
        private static final MotionPreferences INSTANCE = new MotionPreferences(
                Preferences.userRoot().node("recetas/ui"));
    }
}
