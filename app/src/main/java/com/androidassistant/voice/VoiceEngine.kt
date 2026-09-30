package com.androidassistant.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sqrt

class VoiceEngine(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onWakeWord: () -> Unit
) {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val MIN_SPEECH_MS = 250
        private const val SILENCE_MS = 1200
        private const val MAX_RECORDING_MS = 10000
        private const val NOISE_CALIBRATION_MS = 300
        private const val PRE_ROLL_MS = 500
        private const val NOISE_MULTIPLIER = 2.2f
        private const val MIN_RMS_THRESHOLD = 0.008f
    }

    private val localAsr = LocalAsrEngine(context)

    private val recognizer =
        SpeechRecognizer.createSpeechRecognizer(context)

    private val handler =
        Handler(Looper.getMainLooper())

    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor()

    @Volatile
    private var wakeMode = false

    @Volatile
    private var commandMode = false

    @Volatile
    private var wakeTriggered = false

    @Volatile
    private var recording = false

    private var audioRecord: AudioRecord? = null

    private val speechIntent = Intent(
        RecognizerIntent.ACTION_RECOGNIZE_SPEECH
    ).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )

        putExtra(
            RecognizerIntent.EXTRA_PARTIAL_RESULTS,
            true
        )

        putExtra(
            RecognizerIntent.EXTRA_MAX_RESULTS,
            3
        )
    }

    init {
        recognizer.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
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
                    if (!wakeMode || wakeTriggered) return

                    val matches =
                        partialResults?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    if (
                        matches?.any {
                            containsWakeWord(it)
                        } == true
                    ) {
                        triggerWakeWord()
                    }
                }

                override fun onResults(results: Bundle?) {
                    onListeningChanged(false)

                    if (!wakeMode) return

                    val matches =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    if (
                        !wakeTriggered &&
                        matches?.any {
                            containsWakeWord(it)
                        } == true
                    ) {
                        triggerWakeWord()
                    } else {
                        restartWakeMode()
                    }
                }

                override fun onError(error: Int) {
                    onListeningChanged(false)

                    if (wakeMode) {
                        restartWakeMode()
                    }
                }

                override fun onRmsChanged(rmsdB: Float) = Unit

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
        OrionLogger.log("VoiceEngine: start command recognition")
        stopWakeRecognizer()

        if (
            context.checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onError("Немає дозволу на мікрофон")
            return
        }

        if (recording) return

        commandMode = true
        wakeMode = false
        wakeTriggered = false
        recording = true

        onListeningChanged(true)

        executor.execute {
            recordAndRecognize()
        }
    }

    fun startWakeWord(language: String = "uk-UA") {
        if (recording) {
            stopRecording()
        }

        commandMode = false
        wakeMode = true
        wakeTriggered = false

        speechIntent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE,
            language
        )

        try {
            recognizer.startListening(speechIntent)
        } catch (_: Exception) {
            restartWakeMode()
        }
    }

    private fun triggerWakeWord() {
        if (wakeTriggered) return

        wakeTriggered = true
        wakeMode = false
        commandMode = false

        try {
            recognizer.stopListening()
        } catch (_: Exception) {
        }

        onListeningChanged(false)
        onWakeWord()
    }

    private fun recordAndRecognize() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBufferSize <= 0) {
            finishWithError("Не вдалося ініціалізувати мікрофон")
            return
        }

        val bufferSize = maxOf(
            minBufferSize * 2,
            SAMPLE_RATE / 2
        )

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (_: Exception) {
            finishWithError("Не вдалося відкрити мікрофон")
            return
        }

        audioRecord = record

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            audioRecord = null
            finishWithError("Мікрофон недоступний")
            return
        }

        val samples = ArrayList<Float>()
        val preRoll = ArrayList<Float>()
        val buffer = ShortArray(SAMPLE_RATE / 20)

        var totalMs = 0
        var speechMs = 0
        var silenceMs = 0

        var noiseSum = 0.0
        var noiseChunks = 0
        var noiseFloor = 0.004f
        var speechStarted = false

        try {
            OrionLogger.log("AudioRecord: startRecording")
            record.startRecording()

            while (
                recording &&
                totalMs < MAX_RECORDING_MS
            ) {
                val read = record.read(
                    buffer,
                    0,
                    buffer.size
                )

                if (read <= 0) continue

                var energy = 0.0
                val chunk = FloatArray(read)

                for (i in 0 until read) {
                    val value = buffer[i] / 32768.0f
                    chunk[i] = value
                    energy += value * value
                }

                val rms = sqrt(energy / read).toFloat()
                val chunkMs = read * 1000 / SAMPLE_RATE
                totalMs += chunkMs

                if (!speechStarted && totalMs <= NOISE_CALIBRATION_MS) {
                    noiseSum += rms
                    noiseChunks++

                    if (noiseChunks > 0) {
                        noiseFloor = (noiseSum / noiseChunks).toFloat()
                    }
                }

                val startThreshold = maxOf(
                    MIN_RMS_THRESHOLD,
                    noiseFloor * NOISE_MULTIPLIER
                )

                val endThreshold = maxOf(
                    MIN_RMS_THRESHOLD * 0.75f,
                    noiseFloor * 1.35f
                )

                val threshold =
                    if (speechStarted) endThreshold
                    else startThreshold

                if (rms >= threshold) {
                    if (!speechStarted) {
                        speechStarted = true

                        OrionLogger.log(
                            "VAD: speech started rms=$rms startThreshold=$startThreshold endThreshold=$endThreshold noise=$noiseFloor"
                        )

                        samples.addAll(preRoll)
                        preRoll.clear()
                    }

                    for (value in chunk) {
                        samples.add(value)
                    }

                    speechMs += chunkMs
                    silenceMs = 0
                } else {
                    if (!speechStarted) {
                        for (value in chunk) {
                            preRoll.add(value)
                        }

                        val maxPreRollSamples =
                            SAMPLE_RATE * PRE_ROLL_MS / 1000

                        if (preRoll.size > maxPreRollSamples) {
                            val removeCount =
                                preRoll.size - maxPreRollSamples

                            repeat(removeCount) {
                                preRoll.removeAt(0)
                            }
                        }
                    } else {
                        for (value in chunk) {
                            samples.add(value)
                        }

                        silenceMs += chunkMs
                    }
                }

                if (
                    speechStarted &&
                    speechMs >= MIN_SPEECH_MS &&
                    silenceMs >= SILENCE_MS
                ) {
                    OrionLogger.log(
                        "VAD: speech ended silenceMs=$silenceMs"
                    )

                    val trimSamples =
                        SAMPLE_RATE * SILENCE_MS / 1000

                    if (samples.size > trimSamples) {
                        repeat(
                            minOf(
                                trimSamples,
                                samples.size
                            )
                        ) {
                            samples.removeAt(samples.lastIndex)
                        }
                    }

                    break
                }
            }

            OrionLogger.log(
                "VAD: totalMs=$totalMs speechMs=$speechMs silenceMs=$silenceMs noise=$noiseFloor"
            )
        } catch (_: Exception) {
            finishWithError("Помилка запису з мікрофона")
            return
        } finally {
            try {
                record.stop()
            } catch (_: Exception) {
            }

            record.release()
            audioRecord = null
        }

        recording = false
        OrionLogger.log("AudioRecord: finished samples=${samples.size} speechMs=$speechMs")
        onListeningChanged(false)

        if (speechMs < MIN_SPEECH_MS) {
            finishWithError("Не почув команду")
            return
        }

        val audio = FloatArray(samples.size)

        for (i in samples.indices) {
            audio[i] = samples[i]
        }

        executor.execute {
            try {
                OrionLogger.log("VoiceEngine: calling Whisper")
                val result = localAsr.transcribe(audio)
                OrionLogger.log("VoiceEngine: Whisper returned")

                if (result.isBlank()) {
                    finishWithError(
                        "Не вдалося розпізнати команду"
                    )
                } else {
                    commandMode = false
                    onResult(result)
                }
            } catch (e: Exception) {
                finishWithError(
                    "Помилка локального Whisper: ${
                        e.message ?: "невідома помилка"
                    }"
                )
            }
        }
    }

    private fun finishWithError(message: String) {
        recording = false
        commandMode = false

        handler.post {
            onListeningChanged(false)
            onError(message)
        }
    }

    fun stop() {
        recording = false
        commandMode = false
        wakeMode = false
        wakeTriggered = false

        stopWakeRecognizer()
        stopRecording()
    }

    private fun stopRecording() {
        recording = false

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }

        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }

        audioRecord = null
    }

    private fun stopWakeRecognizer() {
        try {
            recognizer.cancel()
        } catch (_: Exception) {
        }
    }

    private fun restartWakeMode() {
        if (!wakeMode) return

        handler.postDelayed({
            if (wakeMode && !recording) {
                try {
                    recognizer.startListening(speechIntent)
                } catch (_: Exception) {
                    restartWakeMode()
                }
            }
        }, 150)
    }

    private fun containsWakeWord(text: String): Boolean {
        val normalized =
            text.lowercase()
                .replace("ё", "е")
                .trim()

        return normalized.contains("оріон") ||
                normalized.contains("орион")
    }

    fun destroy() {
        recording = false
        wakeMode = false
        commandMode = false
        wakeTriggered = false

        handler.removeCallbacksAndMessages(null)

        stopWakeRecognizer()
        stopRecording()

        localAsr.destroy()

        try {
            recognizer.destroy()
        } catch (_: Exception) {
        }

        executor.shutdownNow()
    }
}
