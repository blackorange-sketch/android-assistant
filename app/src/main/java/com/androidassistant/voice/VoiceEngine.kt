package com.androidassistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class VoiceEngine(
    context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit
) {

    private val recognizer: SpeechRecognizer =
        SpeechRecognizer.createSpeechRecognizer(context)

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    init {
        recognizer.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                onListeningChanged(true)
            }

            override fun onBeginningOfSpeech() {
                onListeningChanged(true)
            }

            override fun onEndOfSpeech() {
                onListeningChanged(false)
            }

            override fun onResults(results: Bundle?) {
                onListeningChanged(false)

                val matches =
                    results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )

                val result = matches?.firstOrNull()

                if (!result.isNullOrBlank()) {
                    onResult(result)
                } else {
                    onError("Не вдалося розпізнати команду")
                }
            }

            override fun onError(error: Int) {
                onListeningChanged(false)

                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO ->
                        "Помилка мікрофона"

                    SpeechRecognizer.ERROR_CLIENT ->
                        "Помилка розпізнавання"

                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Немає дозволу на мікрофон"

                    SpeechRecognizer.ERROR_NETWORK ->
                        "Помилка мережі"

                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Перевищено час очікування мережі"

                    SpeechRecognizer.ERROR_NO_MATCH ->
                        "Не почув команду"

                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                        "Розпізнавач зайнятий"

                    SpeechRecognizer.ERROR_SERVER ->
                        "Помилка сервера розпізнавання"

                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "Не почув голос"

                    else ->
                        "Помилка розпізнавання: $error"
                }

                onError(message)
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    fun start(language: String = "uk-UA") {
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE,
            language
        )

        recognizer.startListening(intent)
    }

    fun destroy() {
        recognizer.destroy()
    }
}
