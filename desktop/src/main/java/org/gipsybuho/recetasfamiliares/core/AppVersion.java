package org.gipsybuho.recetasfamiliares.core;

/** Version propia de la aplicacion y comparacion de versiones "1.2.3". */
public final class AppVersion {

    /**
     * Valor de reserva cuando no hay manifiesto, que es el caso al ejecutar
     * desde el IDE o desde los tests. Deliberadamente bajo: en desarrollo
     * cualquier version publicada sera mayor, pero el aviso solo se dispara
     * si el servidor tiene una configurada, asi que no molesta.
     */
    private static final String SIN_MANIFIESTO = "0.0";

    private AppVersion() {}

    /** Version de esta compilacion, leida del manifiesto del JAR. */
    public static String current() {
        String version = AppVersion.class.getPackage().getImplementationVersion();
        return (version == null || version.isBlank()) ? SIN_MANIFIESTO : version;
    }

    /** true si candidate es estrictamente mayor que current. Ante cualquier duda, false. */
    public static boolean isNewer(String candidate, String current) {
        int[] a = parse(candidate);
        int[] b = parse(current);
        if (a == null || b == null) {
            return false;
        }
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int left = i < a.length ? a[i] : 0;
            int right = i < b.length ? b[i] : 0;
            if (left != right) {
                return left > right;
            }
        }
        return false;
    }

    /** Devuelve null si la cadena no es una version de numeros separados por puntos. */
    private static int[] parse(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        String[] parts = version.trim().split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException noEsUnNumero) {
                return null;
            }
            if (numbers[i] < 0) {
                return null;
            }
        }
        return numbers;
    }
}
