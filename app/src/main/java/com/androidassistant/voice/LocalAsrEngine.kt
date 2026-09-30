package com.androidassistant.voice

import android.content.Context

class LocalAsrEngine(private val context: Context) {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val MODEL_ASSET = "models/whisper-small/ggml-small-q5_1.bin"

        init {
            System.loadLibrary("orion_whisper")
        }
    }

    private var nativeContext: Long = 0

    @Synchronized
    private fun initialize() {
        if (nativeContext != 0L) return

        OrionLogger.log("=== WHISPER.CPP INITIALIZE ===")
        OrionLogger.log("Loading model: $MODEL_ASSET")

        nativeContext = nativeInit(context.assets, MODEL_ASSET)

        if (nativeContext == 0L) {
            throw IllegalStateException("Не вдалося завантажити whisper.cpp model")
        }

        OrionLogger.log("whisper.cpp context ready")
    }

    fun transcribe(samples: FloatArray): String {
        OrionLogger.log("whisper.cpp transcribe: samples=${samples.size}")

        if (samples.isEmpty()) return ""

        if (nativeContext == 0L) {
            initialize()
        }

        val result = nativeTranscribe(
            nativeContext,
            samples,
            SAMPLE_RATE,
            4
        )

        OrionLogger.log("whisper.cpp result: $result")
        return result.trim()
    }

    fun destroy() {
        OrionLogger.log("whisper.cpp destroy")

        synchronized(this) {
            if (nativeContext != 0L) {
                nativeFree(nativeContext)
                nativeContext = 0
            }
        }

        OrionLogger.log("whisper.cpp destroyed")
    }

    private external fun nativeInit(
        assetManager: android.content.res.AssetManager,
        assetPath: String
    ): Long

    private external fun nativeTranscribe(
        context: Long,
        samples: FloatArray,
        sampleRate: Int,
        threads: Int
    ): String

    private external fun nativeFree(context: Long)
}
