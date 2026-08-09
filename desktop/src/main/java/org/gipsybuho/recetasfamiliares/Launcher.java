package org.gipsybuho.recetasfamiliares;

import org.gipsybuho.recetasfamiliares.core.SingleInstance;

public class Launcher {

    public static void main(String[] args) {
        // Una sola instancia. Cuatro apiladas dejaron los ficheros del programa
        // bloqueados y la instalacion de la v1.4 fallo con el error 32 de
        // Windows, que es "el archivo esta en uso".
        SingleInstance instancia = new SingleInstance();
        if (!instancia.acquire(SingleInstance.defaultLockFile())) {
            // Se sale en silencio y de inmediato, sin avisar por pantalla. Se
            // probo con un dialogo de Swing y no llegaba a pintarse, dejando un
            // JVM de 110 MB vivo reteniendo los ficheros: exactamente el
            // problema que esto viene a evitar. Mejor que un segundo doble clic
            // no haga nada que dejar un proceso fantasma.
            System.exit(0);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(instancia::release));

        DesktopApp.main(args);
    }
}
