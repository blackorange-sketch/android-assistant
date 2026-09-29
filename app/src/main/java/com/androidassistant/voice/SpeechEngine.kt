package com.androidassistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class SpeechEngine(
    context: Context
) {

    private var ready = false

    private val tts = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("uk", "UA"))
            ready = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED

            tts.setSpeechRate(1.0f)
            tts.setPitch(1.0f)
        }
    }

    fun speak(text: String) {
        if (!ready) return

        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "assistant_response"
        )
    }

    fun stop() {
        tts.stop()
    }

    fun destroy() {
        tts.stop()
        tts.shutdown()
    }
}
