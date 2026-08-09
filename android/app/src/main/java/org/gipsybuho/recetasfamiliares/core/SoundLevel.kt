package org.gipsybuho.recetasfamiliares.core

/** Cuanto sonido quiere el usuario. Se elige en Perfil. */
enum class SoundLevel {

    /** Ninguno. Valor por defecto: la aplicacion nace callada. */
    SILENCIO,
    /** Solo lo que tiene consecuencias: guardado, error, borrado, avisos, cocina. */
    IMPORTANTES,
    /** Tambien la navegacion y los cambios de estado menores. */
    TODOS;

    fun allows(effect: SoundEffect): Boolean = when (this) {
        SILENCIO -> false
        IMPORTANTES -> effect.important
        TODOS -> true
    }

    companion object {
        /** Tolerante a valores corruptos o de una version futura. */
        fun fromPreference(stored: String?, fallback: SoundLevel): SoundLevel {
            if (stored.isNullOrBlank()) return fallback
            return runCatching { valueOf(stored.trim().uppercase()) }.getOrDefault(fallback)
        }
    }
}
