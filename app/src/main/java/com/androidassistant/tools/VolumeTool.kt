package com.androidassistant.tools

import android.content.Context
import android.media.AudioManager
import kotlin.math.roundToInt

class VolumeTool(context: Context) : Tool {
    override val name = "volume"
    override val description =
        "Гучність медіа. Аргументи: action = up | down | mute | unmute | set; percent = 0..100 для set."

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val stream = AudioManager.STREAM_MUSIC
    private val flags = AudioManager.FLAG_SHOW_UI

    override fun execute(args: Map<String, String>): ToolResult {
        val max = audio.getStreamMaxVolume(stream)
        return when (args["action"]) {
            "up" -> {
                repeat(2) { audio.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, flags) }
                ToolResult.ok("Гучність ${currentPercent(max)} відсотків")
            }
            "down" -> {
                repeat(2) { audio.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, flags) }
                ToolResult.ok("Гучність ${currentPercent(max)} відсотків")
            }
            "mute" -> {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, flags)
                ToolResult.ok("Звук вимкнено")
            }
            "unmute" -> {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, flags)
                ToolResult.ok("Звук увімкнено")
            }
            "set" -> {
                val percent = args["percent"]?.toIntOrNull()?.coerceIn(0, 100)
                    ?: return ToolResult.fail("Не почув, на скільки відсотків поставити гучність")
                audio.setStreamVolume(stream, (max * percent / 100.0).roundToInt(), flags)
                ToolResult.ok("Гучність $percent відсотків")
            }
            else -> ToolResult.fail("Не зрозумів, що зробити з гучністю")
        }
    }

    private fun currentPercent(max: Int): Int =
        if (max == 0) 0 else (audio.getStreamVolume(stream) * 100.0 / max).roundToInt()
}
