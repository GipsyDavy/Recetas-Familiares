package org.gipsybuho.recetasfamiliares.core;

/** Cuanto sonido quiere el usuario. Se elige en Ajustes. */
public enum SoundLevel {

    /** Ninguno. Es el valor por defecto: la aplicacion nace callada. */
    SILENCIO,
    /** Solo lo que tiene consecuencias: guardado, error, borrado, avisos, cocina. */
    IMPORTANTES,
    /** Tambien la navegacion y los cambios de estado menores. */
    TODOS;

    public boolean allows(SoundEffect effect) {
        return switch (this) {
            case SILENCIO -> false;
            case IMPORTANTES -> effect.isImportant();
            case TODOS -> true;
        };
    }

    /** Tolerante a valores corruptos o de una version futura. */
    public static SoundLevel fromPreference(String stored, SoundLevel fallback) {
        if (stored == null || stored.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(stored.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException desconocido) {
            return fallback;
        }
    }

    /**
     * La preferencia antigua era un booleano. Quien lo tuviera activado pasa a
     * IMPORTANTES: ni se queda mudo de golpe ni empieza a oirlo todo.
     */
    public static SoundLevel fromLegacyEnabled(boolean enabled) {
        return enabled ? IMPORTANTES : SILENCIO;
    }
}
