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

        OrionLogger.log("=== WHISPER DIAGNOSTIC START ===")

        try {
            val am = context.assets

            OrionLogger.log(
                "Memory: max=${Runtime.getRuntime().maxMemory()} " +
                "total=${Runtime.getRuntime().totalMemory()} " +
                "free=${Runtime.getRuntime().freeMemory()}"
            )

            OrionLogger.log("Checking model assets")

            val encoderSize =
                am.open(ENCODER).use { it.available() }

            OrionLogger.log(
                "Encoder asset OK: $encoderSize bytes"
            )

            val decoderSize =
                am.open(DECODER).use { it.available() }

            OrionLogger.log(
                "Decoder asset OK: $decoderSize bytes"
            )

            val tokensSize =
                am.open(TOKENS).use { it.available() }

            OrionLogger.log(
                "Tokens asset OK: $tokensSize bytes"
            )

            OrionLogger.log(
                "Creating OfflineWhisperModelConfig"
            )

            val whisperConfig =
                OfflineWhisperModelConfig(
                    encoder = ENCODER,
                    decoder = DECODER,
                    language = "uk",
                    task = "transcribe"
                )

            OrionLogger.log(
                "OfflineWhisperModelConfig created"
            )

            val modelConfig =
                OfflineModelConfig(
                    whisper = whisperConfig,
                    tokens = TOKENS,
                    numThreads = 1,
                    provider = "cpu",
                    modelType = "whisper"
                )

            OrionLogger.log(
                "OfflineModelConfig created"
            )

            val featureConfig =
                FeatureConfig(
                    sampleRate = SAMPLE_RATE,
                    featureDim = FEATURE_DIM
                )

            OrionLogger.log(
                "FeatureConfig created"
            )

            val recognizerConfig =
                OfflineRecognizerConfig(
                    featConfig = featureConfig,
                    modelConfig = modelConfig,
                    decodingMethod = "greedy_search"
                )

            OrionLogger.log(
                "OfflineRecognizerConfig created"
            )

            OrionLogger.log(
                "ABOUT TO CREATE OFFLINE RECOGNIZER"
            )

            recognizer =
                OfflineRecognizer(
                    assetManager = context.assets,
                    config = recognizerConfig
                )

            OrionLogger.log(
                "!!! OFFLINE RECOGNIZER CREATED SUCCESSFULLY !!!"
            )

            OrionLogger.log(
                "Memory after recognizer: " +
                "max=${Runtime.getRuntime().maxMemory()} " +
                "total=${Runtime.getRuntime().totalMemory()} " +
                "free=${Runtime.getRuntime().freeMemory()}"
            )

            OrionLogger.log(
                "=== WHISPER DIAGNOSTIC SUCCESS ==="
            )

        } catch (e: Throwable) {

            OrionLogger.error(
                "Whisper initialize Java exception",
                e
            )

            throw e
        }
    }

    fun transcribe(samples: FloatArray): String {

        OrionLogger.log(
            "Whisper diagnostic transcribe called"
        )

        if (recognizer == null) {
            initialize()
        }

        OrionLogger.log(
            "Whisper diagnostic: recognizer exists"
        )

        return "WHISPER_INIT_OK"
    }

    fun destroy() {

        OrionLogger.log(
            "Whisper: destroy"
        )

        synchronized(this) {
            try {
                recognizer?.release()
            } catch (_: Throwable) {
            }

            recognizer = null
        }

        OrionLogger.log(
            "Whisper: destroyed"
        )
    }
}
