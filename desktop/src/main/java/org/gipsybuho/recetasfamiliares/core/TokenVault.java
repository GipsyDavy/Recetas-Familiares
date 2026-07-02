package org.gipsybuho.recetasfamiliares.core;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.sun.jna.platform.win32.Crypt32Util;

/**
 * Cifra valores sensibles con Windows DPAPI (ambito usuario actual) antes de
 * persistirlos en java.util.prefs (SEC-2). El valor cifrado se guarda como
 * "dpapi:&lt;base64&gt;". En plataformas sin DPAPI degrada al comportamiento
 * previo (texto plano) para no romper entornos de desarrollo Linux/macOS.
 */
final class TokenVault {

    private static final String PREFIX = "dpapi:";
    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    private TokenVault() {
    }

    static boolean isProtected(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    /** Cifra el valor para persistencia. Devuelve el valor original si DPAPI no esta disponible. */
    static String protect(String plain) {
        if (plain == null || !WINDOWS) {
            return plain;
        }
        try {
            byte[] cipher = Crypt32Util.cryptProtectData(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(cipher);
        } catch (RuntimeException | LinkageError e) {
            return plain;
        }
    }

    /**
     * Descifra un valor persistido. Valores legado en texto plano se devuelven tal cual.
     * Si el valor cifrado no puede descifrarse (otro usuario/maquina o datos corruptos)
     * devuelve null: se trata como sesion inexistente.
     */
    static String unprotect(String stored) {
        if (!isProtected(stored)) {
            return stored;
        }
        try {
            byte[] cipher = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            return new String(Crypt32Util.cryptUnprotectData(cipher), StandardCharsets.UTF_8);
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }
}
