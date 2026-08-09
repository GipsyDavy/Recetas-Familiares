package org.gipsybuho.recetasfamiliares.core;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Impide que la aplicacion se abra dos veces.
 *
 * No es un capricho: cuatro instancias apiladas dejaron los ficheros del
 * programa bloqueados y el instalador de la v1.4 fallo con el error 32 de
 * Windows, que es "el archivo esta en uso". Con una sola instancia, el
 * instalador puede cerrarla y actualizar.
 *
 * El bloqueo es un fichero con {@link FileLock}: el sistema lo suelta solo si
 * el proceso muere de golpe, asi que un cierre forzado no deja la aplicacion
 * inarrancable.
 */
public final class SingleInstance {

    private FileChannel channel;
    private FileLock lock;

    /** Ruta por defecto del bloqueo, dentro del directorio de datos del usuario. */
    public static Path defaultLockFile() {
        String base = System.getenv("LOCALAPPDATA");
        Path dir = (base == null || base.isBlank())
                ? Paths.get(System.getProperty("user.home"), ".recetas-familiares")
                : Paths.get(base, "RecetasFamiliares");
        return dir.resolve("app.lock");
    }

    /**
     * @return true si esta es la unica instancia, o si el bloqueo no se pudo
     *         crear. Ante la duda se deja arrancar: es peor una aplicacion que
     *         no abre que dos ventanas abiertas.
     */
    public boolean acquire(Path lockFile) {
        try {
            Path parent = lockFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            channel = FileChannel.open(
                    lockFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            lock = channel.tryLock();
            if (lock == null) {
                closeQuietly();
                return false;
            }
            return true;
        } catch (OverlappingFileLockException yaBloqueadoEnEsteProceso) {
            closeQuietly();
            return false;
        } catch (IOException noSePudoCrear) {
            closeQuietly();
            return true;
        }
    }

    public void release() {
        try {
            if (lock != null) {
                lock.release();
            }
        } catch (IOException ignored) {
            // Al cerrar la aplicacion da igual: el sistema suelta el bloqueo.
        }
        closeQuietly();
    }

    private void closeQuietly() {
        try {
            if (channel != null) {
                channel.close();
            }
        } catch (IOException ignored) {
            // Sin nada que hacer.
        }
        channel = null;
        lock = null;
    }
}
