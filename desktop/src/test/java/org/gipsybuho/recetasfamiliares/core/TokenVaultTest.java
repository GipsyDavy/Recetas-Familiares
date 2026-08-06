package org.gipsybuho.recetasfamiliares.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobertura de TokenVault (SEC-2). Los tests de cifrado real solo corren en
 * Windows, que es donde existe DPAPI; el resto de plataformas ejercita la rama
 * de degradacion a texto plano.
 */
class TokenVaultTest {

    private static final String JWT_LIKE =
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyLTEiLCJleHAiOjE3NTAwMDAwMDB9"
            + ".4pQ0Nn3sZ8kM1vT7wXqYbLcRfGhJiKlMnOpQrStUvWx";

    // --- Comportamiento independiente de plataforma ---

    @Test
    void isProtectedSoloReconoceElPrefijoExactoDpapi() {
        assertTrue(TokenVault.isProtected("dpapi:AQAAAA=="));
        assertFalse(TokenVault.isProtected(null));
        assertFalse(TokenVault.isProtected(""));
        assertFalse(TokenVault.isProtected(JWT_LIKE));
        assertFalse(TokenVault.isProtected("DPAPI:AQAAAA=="));
        assertFalse(TokenVault.isProtected(" dpapi:AQAAAA=="));
    }

    @Test
    void protectYUnprotectPropaganNullSinTocarDpapi() {
        assertNull(TokenVault.protect(null));
        assertNull(TokenVault.unprotect(null));
    }

    @Test
    void unprotectDevuelveTalCualLosValoresLegadoEnTextoPlano() {
        assertEquals(JWT_LIKE, TokenVault.unprotect(JWT_LIKE));
        assertEquals("", TokenVault.unprotect(""));
    }

    // --- Rama DPAPI (solo Windows) ---

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void elValorCifradoVuelveIntactoTrasDescifrarlo() {
        assertEquals(JWT_LIKE, TokenVault.unprotect(TokenVault.protect(JWT_LIKE)));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void elCifradoConservaLosCaracteresNoAscii() {
        String conAcentos = "sesión-de-mamá-año-2026-ñÑáéíóú";

        assertEquals(conAcentos, TokenVault.unprotect(TokenVault.protect(conAcentos)));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void elValorPersistidoNoContieneElTokenLegible() {
        String stored = TokenVault.protect(JWT_LIKE);

        assertTrue(TokenVault.isProtected(stored));
        assertFalse(stored.contains(JWT_LIKE));

        byte[] cipher = Base64.getDecoder().decode(stored.substring("dpapi:".length()));
        assertFalse(new String(cipher, StandardCharsets.UTF_8).contains(JWT_LIKE));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void cifrarDosVecesElMismoValorNoProduceElMismoTexto() {
        assertNotEquals(TokenVault.protect(JWT_LIKE), TokenVault.protect(JWT_LIKE));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void unprotectDevuelveNullSiElBlobCifradoEstaCorrupto() {
        String stored = TokenVault.protect(JWT_LIKE);
        byte[] cipher = Base64.getDecoder().decode(stored.substring("dpapi:".length()));
        cipher[cipher.length - 1] ^= 0x7F;
        String corrupto = "dpapi:" + Base64.getEncoder().encodeToString(cipher);

        assertNull(TokenVault.unprotect(corrupto));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void unprotectDevuelveNullSiElContenidoNoEsBase64() {
        assertNull(TokenVault.unprotect("dpapi:esto-no-es-base64-valido!!!"));
    }

    // --- Rama sin DPAPI (Linux/macOS de desarrollo y CI) ---

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void sinDpapiElValorSeDevuelveSinCifrarYSigueSiendoLegible() {
        String stored = TokenVault.protect(JWT_LIKE);

        assertEquals(JWT_LIKE, stored);
        assertFalse(TokenVault.isProtected(stored));
        assertEquals(JWT_LIKE, TokenVault.unprotect(stored));
    }
}
