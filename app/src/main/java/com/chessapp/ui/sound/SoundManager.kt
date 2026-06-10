package com.chessapp.ui.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.chessapp.R

/**
 * Plays move/capture/check/end sounds via SoundPool (low-latency, fire-and-forget)
 * and triggers haptics. Respects the user's sound/haptics settings — the caller
 * passes the current flags so toggles take effect immediately.
 *
 * Bundled audio lives in res/raw (move, capture, check, game_end). They are short
 * synthesized cues; swap in your own .wav/.ogg of the same names to rebrand.
 */
class SoundManager(context: Context) {

    private val appContext = context.applicationContext

    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val move = pool.load(appContext, R.raw.move, 1)
    private val capture = pool.load(appContext, R.raw.capture, 1)
    private val check = pool.load(appContext, R.raw.check, 1)
    private val gameEnd = pool.load(appContext, R.raw.game_end, 1)

    private val vibrator: Vibrator? = run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = appContext.getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    enum class Cue { MOVE, CAPTURE, CHECK, GAME_END }

    fun play(cue: Cue, sound: Boolean, haptics: Boolean) {
        if (sound) {
            val id = when (cue) {
                Cue.MOVE -> move
                Cue.CAPTURE -> capture
                Cue.CHECK -> check
                Cue.GAME_END -> gameEnd
            }
            pool.play(id, 1f, 1f, 1, 0, 1f)
        }
        if (haptics) vibrate(cue)
    }

    private fun vibrate(cue: Cue) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val ms = when (cue) {
            Cue.MOVE -> 12L
            Cue.CAPTURE -> 28L
            Cue.CHECK -> 40L
            Cue.GAME_END -> 60L
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") v.vibrate(ms)
        }
    }

    fun release() = pool.release()
}
