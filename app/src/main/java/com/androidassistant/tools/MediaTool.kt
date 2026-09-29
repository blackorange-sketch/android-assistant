package com.androidassistant.tools

import android.content.Context
import android.view.KeyEvent
import android.media.AudioManager

class MediaTool(context: Context) {

    private val audio =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun send(keyCode: Int) {
        audio.dispatchMediaKeyEvent(
            KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        )

        audio.dispatchMediaKeyEvent(
            KeyEvent(KeyEvent.ACTION_UP, keyCode)
        )
    }

    fun playPause() {
        send(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun next() {
        send(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previous() {
        send(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }
}
