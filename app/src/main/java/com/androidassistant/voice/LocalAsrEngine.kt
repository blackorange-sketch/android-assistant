package com.androidassistant.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig

class LocalAsrEngine(private val context: Context) {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val FEATURE_DIM = 80

        private const val MODEL_DIR = "models/whisper-tiny"

        private const val ENCODER =
            "$MODEL_DIR/tiny-encoder.int8.onnx"

        private const val DECODER =
            "$MODEL_DIR/tiny-decoder.int8.onnx"

        private const val TOKENS =
            "$MODEL_DIR/tiny-tokens.txt"
    }

    private var recognizer: OfflineRecognizer? = null

    @Synchronized
    fun initialize() {
        if (recognizer != null) return

        OrionLogger.log("=== WHISPER INITIALIZE ===")

        val whisperConfig = OfflineWhisperModelConfig(
            encoder = ENCODER,
            decoder = DECODER,
            language = "uk",
            task = "transcribe"
        )

        val modelConfig = OfflineModelConfig(
            whisper = whisperConfig,
            tokens = TOKENS,
            numThreads = 2,
            provider = "cpu",
            modelType = "whisper"
        )

        val featureConfig = FeatureConfig(
            sampleRate = SAMPLE_RATE,
            featureDim = FEATURE_DIM
        )

        val recognizerConfig = OfflineRecognizerConfig(
            featConfig = featureConfig,
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )

        OrionLogger.log("Creating OfflineRecognizer")

        recognizer = OfflineRecognizer(
            assetManager = context.assets,
            config = recognizerConfig
        )

        OrionLogger.log("OfflineRecognizer ready")
    }

    fun transcribe(samples: FloatArray): String {
        OrionLogger.log(
            "Whisper transcribe: samples=${samples.size}"
        )

        if (samples.isEmpty()) {
            OrionLogger.log("Whisper: empty audio")
            return ""
        }

        if (recognizer == null) {
            initialize()
        }

        val r = recognizer
            ?: throw IllegalStateException("Whisper recognizer unavailable")

        OrionLogger.log("Whisper: creating stream")

        val stream = r.createStream()

        try {
            stream.acceptWaveform(
                samples,
                SAMPLE_RATE
            )

            OrionLogger.log("Whisper: waveform accepted")

            r.decode(stream)

            OrionLogger.log("Whisper: decode finished")

            val result = r.getResult(stream)

            OrionLogger.log(
                "Whisper result: ${result.text}"
            )

            return result.text.trim()
        } finally {
            stream.release()
            OrionLogger.log("Whisper: stream released")
        }
    }

    fun destroy() {
        OrionLogger.log("Whisper: destroy")

        synchronized(this) {
            try {
                recognizer?.release()
            } catch (_: Throwable) {
            }

            recognizer = null
        }

        OrionLogger.log("Whisper: destroyed")
    }
}
