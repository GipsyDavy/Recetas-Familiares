package org.gipsybuho.recetasfamiliares.core

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Sonidos de la aplicacion. Tonos del sistema, sin ficheros de audio, igual que
 * en Desktop.
 *
 * Nace en silencio y nunca suena sin que el usuario haya hecho algo. Que suene
 * y cuanto lo decide [SoundLevel], que se elige en Perfil.
 */
object SoundPlayer {

    @Volatile
    private var level: SoundLevel = SoundLevel.SILENCIO

    /** Lo actualiza el ViewModel cuando cambia la preferencia. */
    fun setLevel(newLevel: SoundLevel) {
        level = newLevel
    }

    fun currentLevel(): SoundLevel = level

    fun play(effect: SoundEffect) {
        if (!level.allows(effect)) return
        // ToneGenerator se crea y se libera en cada uso: mantenerlo vivo reserva
        // el canal de audio y molesta a la musica que el usuario tenga sonando.
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME)
            tone.startTone(toneFor(effect), durationFor(effect))
            android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed({ runCatching { tone.release() } }, durationFor(effect) + 120L)
        }
    }

    private fun toneFor(effect: SoundEffect): Int = when (effect) {
        SoundEffect.SUCCESS -> ToneGenerator.TONE_PROP_ACK
        SoundEffect.ERROR -> ToneGenerator.TONE_SUP_ERROR
        SoundEffect.DELETE -> ToneGenerator.TONE_PROP_NACK
        SoundEffect.ALERT -> ToneGenerator.TONE_PROP_BEEP2
        SoundEffect.TIMER -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
        SoundEffect.STEP -> ToneGenerator.TONE_PROP_BEEP
        SoundEffect.NAVIGATE, SoundEffect.TOGGLE, SoundEffect.OPEN -> ToneGenerator.TONE_PROP_BEEP
    }

    private fun durationFor(effect: SoundEffect): Int = when (effect) {
        SoundEffect.TIMER, SoundEffect.ERROR -> 400
        SoundEffect.SUCCESS, SoundEffect.DELETE, SoundEffect.ALERT -> 200
        else -> 90
    }

    private const val VOLUME = 60
}
