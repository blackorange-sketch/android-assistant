package com.androidassistant.tools

import android.content.Context
import android.media.AudioManager

class VolumeTool(context: Context) {

    private val audio =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun up() {
        audio.adjustVolume(
            AudioManager.ADJUST_RAISE,
            AudioManager.FLAG_SHOW_UI
        )
    }

    fun down() {
        audio.adjustVolume(
            AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
    }

    fun mute() {
        audio.adjustVolume(
            AudioManager.ADJUST_MUTE,
            AudioManager.FLAG_SHOW_UI
        )
    }
}
