package com.androidassistant.asr

import android.content.Context
import com.androidassistant.voice.OrionLogger

/**
 * Тримає в пам'яті одну завантажену модель — ту, що обрана активною.
 * Завантаження моделі займає секунди, тому викликати з фонового потоку.
 */
object AsrProvider {

    private var loadedId: String? = null
    private var engine: AsrEngine? = null

    /** Поточна активна модель або null, якщо жодна не встановлена. */
    @Synchronized
    fun engine(context: Context): Pair<AsrModel, AsrEngine>? {
        val model = ModelManager.activeModel(context) ?: run {
            release()
            return null
        }
        val current = engine
        if (current != null && loadedId == model.id) return model to current

        release()
        val dir = ModelManager.modelDir(context, model)
        val started = System.currentTimeMillis()
        val created = when (model.kind) {
            EngineKind.VOSK -> VoskEngine(dir)
            else -> SherpaEngine(dir, model.kind)
        }
        OrionLogger.log("ASR: loaded ${model.id} in ${System.currentTimeMillis() - started} ms")
        engine = created
        loadedId = model.id
        return model to created
    }

    /**
     * Розпізнати фрагмент активною моделлю. Під тим самим замком, що й release(),
     * щоб модель не звільнили посеред розпізнавання (це був би нативний краш).
     */
    @Synchronized
    fun transcribe(context: Context, samples: FloatArray): Pair<AsrModel, String>? {
        val (model, asr) = engine(context) ?: return null
        return model to asr.transcribe(samples)
    }

    @Synchronized
    fun releaseIf(modelId: String) {
        if (loadedId == modelId) release()
    }

    @Synchronized
    fun release() {
        try {
            engine?.release()
        } catch (_: Throwable) {
        }
        engine = null
        loadedId = null
    }
}
