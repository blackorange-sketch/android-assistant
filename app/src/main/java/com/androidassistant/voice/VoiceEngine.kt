package com.androidassistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class VoiceEngine(
    context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onWakeWord: () -> Unit
) {

    private val recognizer =
        SpeechRecognizer.createSpeechRecognizer(context)

    private val handler = Handler(Looper.getMainLooper())

    private var wakeMode = false
    private var commandMode = false
    private var wakeTriggered = false

    private val intent = Intent(
        RecognizerIntent.ACTION_RECOGNIZE_SPEECH
    ).apply {

        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )

        // Часткові результати потрібні для швидкого wake word.
        putExtra(
            RecognizerIntent.EXTRA_PARTIAL_RESULTS,
            true
        )

        putExtra(
            RecognizerIntent.EXTRA_MAX_RESULTS,
            3
        )

        // Довше чекати перед завершенням голосового вводу.
        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
            5000L
        )

        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
            3500L
        )

        putExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
            1500L
        )
    }

    init {
        recognizer.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {
                    onListeningChanged(true)
                }

                override fun onBeginningOfSpeech() {
                    onListeningChanged(true)
                }

                override fun onEndOfSpeech() {
                    onListeningChanged(false)
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {
                    if (!wakeMode || wakeTriggered) {
                        return
                    }

                    val matches =
                        partialResults?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    if (
                        matches?.any { containsWakeWord(it) } == true
                    ) {
                        wakeTriggered = true

                        wakeMode = false
                        commandMode = false

                        // Не чекаємо завершення всієї фрази.
                        recognizer.stopListening()

                        onListeningChanged(false)
                        onWakeWord()
                    }
                }

                override fun onResults(
                    results: Bundle?
                ) {
                    onListeningChanged(false)

                    val matches =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    if (wakeMode) {

                        if (
                            !wakeTriggered &&
                            matches?.any {
                                containsWakeWord(it)
                            } == true
                        ) {
                            wakeTriggered = true
                            wakeMode = false
                            commandMode = false

                            onWakeWord()
                        } else {
                            restartWakeMode()
                        }

                        return
                    }

                    if (commandMode) {
                        commandMode = false

                        val result =
                            matches?.firstOrNull()

                        if (!result.isNullOrBlank()) {
                            onResult(result)
                        } else {
                            onError(
                                "Не вдалося розпізнати команду"
                            )
                        }
                    }
                }

                override fun onError(
                    error: Int
                ) {
                    onListeningChanged(false)

                    if (wakeMode) {
                        restartWakeMode()
                        return
                    }

                    commandMode = false

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
                            "Помилка сервера"

                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "Не почув голос"

                        else ->
                            "Помилка розпізнавання: $error"
                    }

                    onError(message)
                }

                override fun onRmsChanged(
                    rmsdB: Float
                ) = Unit

                override fun onBufferReceived(
                    buffer: ByteArray?
                ) = Unit

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) = Unit
            }
        )
    }

    fun start(language: String = "uk-UA") {
        wakeMode = false
        commandMode = true
        wakeTriggered = false

        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE,
            language
        )

        recognizer.startListening(intent)
    }

    fun startWakeWord(language: String = "uk-UA") {
        wakeMode = true
        commandMode = false
        wakeTriggered = false

        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE,
            language
        )

        recognizer.startListening(intent)
    }

    private fun restartWakeMode() {
        if (!wakeMode) return

        handler.postDelayed({

            if (wakeMode) {
                try {
                    recognizer.startListening(intent)
                } catch (_: Exception) {
                    restartWakeMode()
                }
            }

        }, 100)
    }

    private fun containsWakeWord(
        text: String
    ): Boolean {

        val normalized = text
            .lowercase()
            .replace("ё", "е")
            .trim()

        return normalized.contains("оріон") ||
                normalized.contains("орион")
    }

    fun stop() {
        wakeMode = false
        commandMode = false
        wakeTriggered = false

        handler.removeCallbacksAndMessages(null)

        recognizer.stopListening()
    }

    fun destroy() {
        wakeMode = false
        commandMode = false
        wakeTriggered = false

        handler.removeCallbacksAndMessages(null)

        recognizer.destroy()
    }
}
