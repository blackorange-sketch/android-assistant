package com.androidassistant.tools

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

/** Керування будь-яким плеєром через медіакнопки системи. */
class MediaTool(context: Context) : Tool {
    override val name = "media"
    override val description = "Керування музикою. Аргумент action: play | pause | toggle | next | previous."

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun execute(args: Map<String, String>): ToolResult {
        val (key, speech) = when (args["action"]) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY to "Відтворюю"
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE to "Пауза"
            "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "Готово"
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT to "Наступний трек"
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS to "Попередній трек"
            else -> return ToolResult.fail("Не зрозумів, що зробити з музикою")
        }
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
        return ToolResult.ok(speech)
    }
}
