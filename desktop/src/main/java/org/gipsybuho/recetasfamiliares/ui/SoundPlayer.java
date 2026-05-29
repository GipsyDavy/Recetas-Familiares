package org.gipsybuho.recetasfamiliares.ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.SourceDataLine;
import java.util.prefs.Preferences;

public final class SoundPlayer {

    private static final float SAMPLE_RATE = 44_100f;
    private static final Preferences PREFS = Preferences.userRoot().node("recetas");

    private SoundPlayer() {}

    public static boolean isSoundEnabled() {
        return PREFS.getBoolean("sound", false);
    }

    public static void setSoundEnabled(boolean enabled) {
        PREFS.putBoolean("sound", enabled);
    }

    public static void playConfirm() {
        play(() -> tone(880, 0.120, 0.30));
    }

    public static void playDelete() {
        play(() -> tone(220, 0.150, 0.25));
    }

    public static void playTimerComplete() {
        play(() -> {
            tone(660, 0.100, 0.35);
            tone(880, 0.150, 0.35);
        });
    }

    private static void play(Runnable sound) {
        if (!isSoundEnabled()) return;
        Thread.ofVirtual().start(sound);
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
            // Sound is optional UX feedback.
        }
    }
}
