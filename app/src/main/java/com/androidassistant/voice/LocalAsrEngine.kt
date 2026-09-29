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

    private var recognizer: OfflineRecognizer? = null

    fun initialize() {
        if (recognizer != null) return

        val modelDir = "models/whisper-tiny"

        val modelConfig = OfflineModelConfig(
            whisper = OfflineWhisperModelConfig(
                encoder = "$modelDir/tiny-encoder.int8.onnx",
                decoder = "$modelDir/tiny-decoder.int8.onnx",
                language = "uk",
                task = "transcribe"
            ),
            tokens = "$modelDir/tiny-tokens.txt",
            numThreads = 4,
            provider = "cpu",
            modelType = "whisper"
        )

        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(
                sampleRate = 16000,
                featureDim = 80
            ),
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )

        recognizer = OfflineRecognizer(
            assetManager = context.assets,
            config = config
        )
    }

    fun destroy() {
        recognizer?.release()
        recognizer = null
    }
}
