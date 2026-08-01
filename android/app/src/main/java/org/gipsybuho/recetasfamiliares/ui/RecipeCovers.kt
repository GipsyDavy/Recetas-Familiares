package org.gipsybuho.recetasfamiliares.ui

/**
 * Regla de portada, identica a la del backend: se prefiere el thumbnail y se cae a la
 * imagen original si no lo hay. Null cuando ninguna de las dos es utilizable.
 */
fun preferredCoverUrl(thumbnailUrl: String?, url: String?): String? =
    thumbnailUrl?.takeIf { it.isNotBlank() } ?: url?.takeIf { it.isNotBlank() }
