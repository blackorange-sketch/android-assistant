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
        if (recognizer != null) return

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

        val recognizerConfig = OfflineRecognizerConfig(
            featConfig = FeatureConfig(
                sampleRate = SAMPLE_RATE,
                featureDim = FEATURE_DIM
            ),
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )

        recognizer = OfflineRecognizer(
            assetManager = context.assets,
            config = recognizerConfig
        )
    }

    fun transcribe(samples: FloatArray): String {
        OrionLogger.log("Whisper: transcribe started")
        OrionLogger.log("Whisper: samples=${samples.size}")
        if (samples.isEmpty()) {
            return "TEST_EMPTY_AUDIO"
        }

        OrionLogger.log("Whisper: initialize")
        initialize()
        OrionLogger.log("Whisper: recognizer initialized")

        val localRecognizer = recognizer
            ?: throw IllegalStateException(
                "Whisper не ініціалізований"
            )

        // TEST 1: createStream
        OrionLogger.log("Whisper: createStream")
        val stream = localRecognizer.createStream()
        OrionLogger.log("Whisper: stream created")

        // TEST 2: acceptWaveform
        OrionLogger.log("Whisper: acceptWaveform")
        stream.acceptWaveform(
            samples = samples,
            sampleRate = SAMPLE_RATE
        )

        // TEST 3: decode
        OrionLogger.log("Whisper: decode")
        localRecognizer.decode(stream)
        OrionLogger.log("Whisper: decode finished")

        // TEST 4: result
        val result =
            OrionLogger.log("Whisper: getResult")
        val result = localRecognizer.getResult(stream).text.trim()
        OrionLogger.log("Whisper: result='$result'")
        result

        stream.release()

        return if (result.isBlank()) {
            "TEST_DECODE_EMPTY"
        } else {
            result
        }
    }

    fun destroy() {
        synchronized(this) {
            recognizer?.release()
            recognizer = null
        }
    }
}
