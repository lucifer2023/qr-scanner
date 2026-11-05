package com.serialscanner.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("serials", text))
}

class Feedback(private val ctx: Context) {
    private val tone: ToneGenerator? =
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull()

    @Suppress("DEPRECATION")
    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31)
            ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        else ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }.getOrNull()

    @Suppress("DEPRECATION")
    fun beep() {
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
        runCatching {
            if (Build.VERSION.SDK_INT >= 26)
                vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            else vibrator?.vibrate(80)
        }
    }

    fun release() {
        runCatching { tone?.release() }
    }
}
