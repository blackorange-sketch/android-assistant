package com.androidassistant.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig

class MoonshineAsrEngine(private val context: Context) {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val FEATURE_DIM = 80
        private const val MODEL_DIR = "models/moonshine-uk"
        private const val ENCODER = "$MODEL_DIR/encoder_model.ort"
        private const val MERGED_DECODER = "$MODEL_DIR/decoder_model_merged.ort"
        private const val TOKENS = "$MODEL_DIR/tokens.txt"
    }

    private var recognizer: OfflineRecognizer? = null

    @Synchronized
    private fun initialize() {
        if (recognizer != null) return

        OrionLogger.log("=== MOONSHINE INITIALIZE ===")
        OrionLogger.log("Encoder: $ENCODER")
        OrionLogger.log("Merged decoder: $MERGED_DECODER")
        OrionLogger.log("Tokens: $TOKENS")

        val moonshineConfig = OfflineMoonshineModelConfig(
            encoder = ENCODER,
            mergedDecoder = MERGED_DECODER,
        )

        val modelConfig = OfflineModelConfig(
            moonshine = moonshineConfig,
            tokens = TOKENS,
        )

        val recognizerConfig = OfflineRecognizerConfig(
            modelConfig = modelConfig,
        )

        OrionLogger.log("Creating Moonshine OfflineRecognizer")
        recognizer = OfflineRecognizer(
            assetManager = context.assets,
            config = recognizerConfig,
        )
        OrionLogger.log("Moonshine recognizer ready")
    }

    fun transcribe(samples: FloatArray): String {
        OrionLogger.log("Moonshine transcribe: samples=${samples.size}")

        if (samples.isEmpty()) return ""

        if (recognizer == null) {
            initialize()
        }

        val r = recognizer
            ?: throw IllegalStateException("Moonshine recognizer unavailable")

        val stream = r.createStream()

        try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            OrionLogger.log("Moonshine: waveform accepted")

            r.decode(stream)

            val result = r.getResult(stream)

            OrionLogger.log("Moonshine tokens count=${result.tokens.size}")
            OrionLogger.log(
                "Moonshine tokens=${result.tokens.joinToString("|")}"
            )

            val text = result.text.trim()
            OrionLogger.log("Moonshine result: $text")

            return text
        } finally {
            stream.release()
        }
    }

    fun destroy() {
        OrionLogger.log("Moonshine: destroy")

        synchronized(this) {
            try {
                recognizer?.release()
            } catch (_: Throwable) {
            }

            recognizer = null
        }

        OrionLogger.log("Moonshine: destroyed")
    }
}
