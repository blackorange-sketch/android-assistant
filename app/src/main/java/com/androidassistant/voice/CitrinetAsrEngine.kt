package com.androidassistant.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig

class CitrinetAsrEngine(private val context: Context) {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val FEATURE_DIM = 80
        private const val MODEL_DIR = "models/mova-citrinet"
        private const val MODEL = "$MODEL_DIR/model.int8.onnx"
        private const val TOKENS = "$MODEL_DIR/tokens.txt"
        private const val THREADS = 4
    }

    private var recognizer: OfflineRecognizer? = null

    @Synchronized
    private fun initialize() {
        if (recognizer != null) return

        OrionLogger.log("=== CITRINET INITIALIZE ===")
        OrionLogger.log("Loading model: $MODEL")

        OrionLogger.log("Creating Citrinet OfflineRecognizer")

        recognizer = OfflineRecognizer.fromNemoCtc(
            assetManager = context.assets,
            model = MODEL,
            tokens = TOKENS,
            numThreads = THREADS,
            sampleRate = SAMPLE_RATE,
            featureDim = FEATURE_DIM,
            decodingMethod = "greedy_search",
            provider = "cpu"
        )

        OrionLogger.log("Citrinet recognizer ready")
    }

    fun transcribe(samples: FloatArray): String {
        OrionLogger.log("Citrinet transcribe: samples=${samples.size}")

        if (samples.isEmpty()) return ""

        if (recognizer == null) {
            initialize()
        }

        val r = recognizer
            ?: throw IllegalStateException("Citrinet recognizer unavailable")

        val stream = r.createStream()

        try {
            stream.acceptWaveform(samples, SAMPLE_RATE)

            OrionLogger.log("Citrinet: waveform accepted")

            r.decode(stream)

            val result = r.getResult(stream)
            val text = result.text.trim()

            OrionLogger.log("Citrinet result: $text")

            return text
        } finally {
            stream.release()
        }
    }

    fun destroy() {
        OrionLogger.log("Citrinet: destroy")

        synchronized(this) {
            try {
                recognizer?.release()
            } catch (_: Throwable) {
            }

            recognizer = null
        }

        OrionLogger.log("Citrinet: destroyed")
    }
}
