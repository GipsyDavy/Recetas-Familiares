package org.gipsybuho.recetasfamiliares.photos;

/** Proyeccion ligera para calcular portadas sin cargar entidades completas. */
public interface RecipeCoverProjection {

    String getRecipeId();

    String getThumbnailUrl();

    String getUrl();
}
