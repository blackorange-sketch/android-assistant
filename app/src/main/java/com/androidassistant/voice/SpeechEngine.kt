package com.androidassistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class SpeechEngine(
    context: Context
) {

    private lateinit var tts: TextToSpeech
    private var ready = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {

                val languageResult =
                    tts.setLanguage(Locale("uk", "UA"))

                ready =
                    languageResult != TextToSpeech.LANG_MISSING_DATA &&
                    languageResult != TextToSpeech.LANG_NOT_SUPPORTED

                tts.setSpeechRate(1.0f)
                tts.setPitch(1.0f)
            }
        }
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return

        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "assistant_response"
        )
    }

    fun stop() {
        if (::tts.isInitialized) {
            tts.stop()
        }
    }

    fun destroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
    }
}
