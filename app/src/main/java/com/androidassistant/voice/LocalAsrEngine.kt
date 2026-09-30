package com.androidassistant.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig

class LocalAsrEngine(
    private val context: Context
) {

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
        OrionLogger.log("Whisper: initialize started")

        if (recognizer != null) {
            OrionLogger.log("Whisper: recognizer already initialized")
            return
        }

        val modelConfig = OfflineModelConfig(
            whisper = OfflineWhisperModelConfig(
                encoder = ENCODER,
                decoder = DECODER,
                language = "uk",
                task = "transcribe"
            ),
            tokens = TOKENS,
            numThreads = 1,
            provider = "cpu",
            modelType = "whisper"
        )

        OrionLogger.log("Whisper: creating OfflineRecognizer")

        recognizer = OfflineRecognizer(
            assetManager = context.assets,
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = SAMPLE_RATE,
                    featureDim = FEATURE_DIM
                ),
                modelConfig = modelConfig,
                decodingMethod = "greedy_search"
            )
        )

        OrionLogger.log("Whisper: OfflineRecognizer created")
    }

    fun transcribe(samples: FloatArray): String {
        OrionLogger.log(
            "Whisper: transcribe started, samples=${samples.size}"
        )

        if (samples.isEmpty()) {
            OrionLogger.log("Whisper: EMPTY AUDIO")
            return ""
        }

        OrionLogger.log("Whisper: calling initialize")
        initialize()

        val localRecognizer = recognizer
            ?: throw IllegalStateException(
                "Whisper не ініціалізований"
            )

        OrionLogger.log("Whisper: creating stream")

        val stream = localRecognizer.createStream()

        OrionLogger.log("Whisper: stream created")

        try {
            OrionLogger.log("Whisper: acceptWaveform")

            stream.acceptWaveform(
                samples = samples,
                sampleRate = SAMPLE_RATE
            )

            OrionLogger.log("Whisper: acceptWaveform finished")

            OrionLogger.log("Whisper: decode")

            localRecognizer.decode(stream)

            OrionLogger.log("Whisper: decode finished")

            OrionLogger.log("Whisper: getResult")

            val text =
                localRecognizer
                    .getResult(stream)
                    .text
                    .trim()

            OrionLogger.log(
                "Whisper: result='$text'"
            )

            return text

        } finally {
            OrionLogger.log("Whisper: releasing stream")

            stream.release()

            OrionLogger.log("Whisper: stream released")
        }
    }

    fun destroy() {
        OrionLogger.log("Whisper: destroy")

        synchronized(this) {
            recognizer?.release()
            recognizer = null
        }

        OrionLogger.log("Whisper: destroyed")
    }
}
