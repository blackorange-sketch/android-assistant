package com.androidassistant.asr

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/** Розпізнає вже записаний фрагмент: 16 кГц, моно, float у діапазоні -1..1. */
interface AsrEngine {
    fun transcribe(samples: FloatArray): String
    fun release()
}

const val ASR_SAMPLE_RATE = 16000

class SherpaEngine(dir: File, kind: EngineKind) : AsrEngine {

    private val recognizer: OfflineRecognizer

    init {
        fun f(name: String): String {
            val file = File(dir, name)
            require(file.isFile) { "У моделі бракує файлу $name" }
            return file.absolutePath
        }

        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        val modelConfig = when (kind) {
            EngineKind.MOONSHINE -> OfflineModelConfig(
                moonshine = OfflineMoonshineModelConfig(
                    encoder = f("encoder_model.ort"),
                    mergedDecoder = f("decoder_model_merged.ort"),
                ),
                tokens = f("tokens.txt"),
                numThreads = threads,
            )
            EngineKind.NEMO_TRANSDUCER -> OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = f("encoder.int8.onnx"),
                    decoder = f("decoder.int8.onnx"),
                    joiner = f("joiner.int8.onnx"),
                ),
                tokens = f("tokens.txt"),
                modelType = "nemo_transducer",
                numThreads = threads,
            )
            EngineKind.VOSK -> error("Vosk не є моделлю sherpa-onnx")
        }

        recognizer = OfflineRecognizer(
            assetManager = null,
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = ASR_SAMPLE_RATE, featureDim = 80),
                modelConfig = modelConfig,
            ),
        )
    }

    override fun transcribe(samples: FloatArray): String {
        if (samples.isEmpty()) return ""
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, ASR_SAMPLE_RATE)
            recognizer.decode(stream)
            return recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    override fun release() = recognizer.release()
}

class VoskEngine(dir: File) : AsrEngine {

    private val model = Model(dir.absolutePath)

    override fun transcribe(samples: FloatArray): String {
        if (samples.isEmpty()) return ""
        val pcm = ShortArray(samples.size) { i -> (samples[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort() }
        val recognizer = Recognizer(model, ASR_SAMPLE_RATE.toFloat())
        try {
            recognizer.acceptWaveForm(pcm, pcm.size)
            return JSONObject(recognizer.finalResult).optString("text").trim()
        } finally {
            recognizer.close()
        }
    }

    override fun release() = model.close()
}
