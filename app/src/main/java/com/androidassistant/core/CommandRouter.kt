package com.androidassistant.core

import android.content.Context
import com.androidassistant.tools.AppTool
import com.androidassistant.tools.FlashlightTool
import com.androidassistant.tools.MediaTool
import com.androidassistant.tools.VolumeTool

class CommandRouter(context: Context) {

    private val volume = VolumeTool(context)
    private val media = MediaTool(context)
    private val flashlight = FlashlightTool(context)
    private val apps = AppTool(context)

    fun execute(command: String): String {

        val text = command
            .trim()
            .lowercase()

        return when {

            text.contains("гучніше") ||
            text.contains("збільш гучність") ||
            text.contains("прибав гучність") -> {
                volume.up()
                "Гучність збільшено"
            }

            text.contains("тихіше") ||
            text.contains("зменш гучність") -> {
                volume.down()
                "Гучність зменшено"
            }

            text.contains("без звуку") ||
            text.contains("вимкни звук") -> {
                volume.mute()
                "Звук вимкнено"
            }

            text.contains("пауза") ||
            text.contains("продовж музику") ||
            text.contains("відтворення") -> {
                media.playPause()
                "Готово"
            }

            text.contains("наступний трек") ||
            text.contains("наступна пісня") ||
            text.contains("наступний") -> {
                media.next()
                "Наступний трек"
            }

            text.contains("попередній трек") ||
            text.contains("попередня пісня") ||
            text.contains("попередній") -> {
                media.previous()
                "Попередній трек"
            }

            text.contains("увімкни ліхтарик") ||
            text.contains("включи ліхтарик") -> {
                flashlight.setEnabled(true)
                "Ліхтарик увімкнено"
            }

            text.contains("вимкни ліхтарик") ||
            text.contains("виключи ліхтарик") -> {
                flashlight.setEnabled(false)
                "Ліхтарик вимкнено"
            }

            text.contains("налаштування") -> {
                apps.openSettings()
                "Відкриваю налаштування"
            }

            text.contains("відкрий телеграм") ||
            text.contains("відкрий telegram") -> {
                if (apps.open("org.telegram.messenger")) {
                    "Відкриваю Telegram"
                } else {
                    "Telegram не знайдено"
                }
            }

            text.contains("відкрий youtube") -> {
                if (apps.open("com.google.android.youtube")) {
                    "Відкриваю YouTube"
                } else {
                    "YouTube не знайдено"
                }
            }

            else -> {
                "Не знаю цієї команди"
            }
        }
    }
}
