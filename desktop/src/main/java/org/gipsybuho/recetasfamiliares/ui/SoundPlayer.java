package org.gipsybuho.recetasfamiliares.ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.prefs.Preferences;

import org.gipsybuho.recetasfamiliares.core.SoundEffect;
import org.gipsybuho.recetasfamiliares.core.SoundLevel;

/**
 * Sonidos de la aplicacion. Tonos sintetizados, sin ficheros de audio.
 *
 * Nace en silencio y nunca suena sin que el usuario haya hecho algo. Que suene
 * y cuanto lo decide {@link SoundLevel}, que se elige en Ajustes.
 */
public final class SoundPlayer {

    private static final float SAMPLE_RATE = 44_100f;
    private static final Preferences PREFS = Preferences.userRoot().node("recetas");
    private static final String LEVEL_KEY = "soundLevel";
    /** Preferencia anterior, booleana. Solo se lee para migrar. */
    private static final String LEGACY_KEY = "sound";

    private SoundPlayer() {}

    public static SoundLevel getLevel() {
        String stored = PREFS.get(LEVEL_KEY, null);
        if (stored == null) {
            return SoundLevel.fromLegacyEnabled(PREFS.getBoolean(LEGACY_KEY, false));
        }
        return SoundLevel.fromPreference(stored, SoundLevel.SILENCIO);
    }

    public static void setLevel(SoundLevel level) {
        PREFS.put(LEVEL_KEY, level.name());
        // Se mantiene el booleano al dia para no romper una version anterior
        // que siguiera instalada en el mismo equipo.
        PREFS.putBoolean(LEGACY_KEY, level != SoundLevel.SILENCIO);
    }

    /** Reproduce el efecto si el nivel elegido lo permite. Nunca bloquea la interfaz. */
    public static void play(SoundEffect effect) {
        if (!getLevel().allows(effect)) {
            return;
        }
        Thread.ofVirtual().start(() -> render(effect));
    }

    private static void render(SoundEffect effect) {
        switch (effect) {
            case SUCCESS -> tone(880, 0.120, 0.30);
            case ERROR -> {
                tone(300, 0.120, 0.30);
                tone(200, 0.180, 0.30);
            }
            case DELETE -> tone(220, 0.150, 0.25);
            case ALERT -> {
                tone(660, 0.090, 0.30);
                tone(660, 0.090, 0.30);
            }
            case TIMER -> {
                tone(660, 0.100, 0.35);
                tone(880, 0.150, 0.35);
            }
            case STEP -> tone(520, 0.070, 0.22);
            case NAVIGATE -> tone(440, 0.050, 0.15);
            case TOGGLE -> tone(700, 0.045, 0.15);
            case OPEN -> tone(600, 0.055, 0.15);
        }
    }

    private static void tone(double frequency, double durationSec, double volume) {
        int samples = (int) (SAMPLE_RATE * durationSec);
        byte[] buf = new byte[samples];
        for (int i = 0; i < samples; i++) {
            buf[i] = (byte) (Math.sin(2 * Math.PI * frequency * i / SAMPLE_RATE) * 127 * volume);
        }

        AudioFormat format = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format);
            line.start();
            line.write(buf, 0, buf.length);
            line.drain();
        } catch (Exception ignored) {
            // El sonido es una ayuda opcional: si el equipo no tiene salida de
            // audio, la aplicacion sigue funcionando igual.
        }
    }
}
