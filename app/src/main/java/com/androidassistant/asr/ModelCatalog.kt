package com.androidassistant.asr

enum class EngineKind { MOONSHINE, NEMO_TRANSDUCER, VOSK }

data class AsrModel(
    val id: String,
    val title: String,
    val description: String,
    /** Розмір завантаження, МБ (на диску трохи більше після розпакування). */
    val downloadMb: Int,
    val url: String,
    val kind: EngineKind,
)

/**
 * Моделі, які можна завантажити з екрана «Моделі».
 * Результати порівняння на реальному записі «відкрий налаштування» — у README.
 */
object ModelCatalog {

    private const val SHERPA = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models"

    val models = listOf(
        AsrModel(
            id = "moonshine-uk",
            title = "Moonshine — українська",
            description = "Швидка й найточніша в наших тестах. Рекомендовано для команд.",
            downloadMb = 111,
            url = "$SHERPA/sherpa-onnx-moonshine-base-uk-quantized-2026-02-27.tar.bz2",
            kind = EngineKind.MOONSHINE,
        ),
        AsrModel(
            id = "parakeet-v3",
            title = "Parakeet v3 — 25 мов",
            description = "Велика модель NVIDIA. Повільніша, потребує ~1 ГБ оперативної пам'яті.",
            downloadMb = 487,
            url = "$SHERPA/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2",
            kind = EngineKind.NEMO_TRANSDUCER,
        ),
        AsrModel(
            id = "vosk-uk",
            title = "Vosk — українська",
            description = "Класична легка модель. Підійде для слабших телефонів.",
            downloadMb = 133,
            url = "https://alphacephei.com/vosk/models/vosk-model-small-uk-v3-small.zip",
            kind = EngineKind.VOSK,
        ),
    )

    const val DEFAULT_ID = "moonshine-uk"

    fun byId(id: String?): AsrModel? = models.firstOrNull { it.id == id }
}
