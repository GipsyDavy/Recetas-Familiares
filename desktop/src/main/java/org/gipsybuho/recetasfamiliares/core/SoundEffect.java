package org.gipsybuho.recetasfamiliares.core;

/** Catalogo de sonidos de la aplicacion. Cada uno significa una cosa distinta. */
public enum SoundEffect {

    /** Algo se guardo o se completo bien. */
    SUCCESS(true),
    /** Algo fallo y el usuario tiene que enterarse. */
    ERROR(true),
    /** Se borro algo. */
    DELETE(true),
    /** Aviso que reclama atencion: caducidades, version nueva. */
    ALERT(true),
    /** El temporizador de cocina ha terminado. */
    TIMER(true),
    /**
     * Cambio de paso en modo cocina. Importante a proposito: se cocina con las
     * manos ocupadas y sin mirar la pantalla.
     */
    STEP(true),

    /** Cambio de pantalla. Ruido de fondo. */
    NAVIGATE(false),
    /** Marcar o desmarcar algo. */
    TOGGLE(false),
    /** Apertura de un dialogo. */
    OPEN(false);

    private final boolean important;

    SoundEffect(boolean important) {
        this.important = important;
    }

    public boolean isImportant() {
        return important;
    }
}
