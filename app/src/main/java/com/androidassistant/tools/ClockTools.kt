package com.androidassistant.tools

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.androidassistant.nlu.UkrainianText
import java.time.LocalTime

class TimerTool(private val context: Context) : Tool {
    override val name = "timer"
    override val description = "Поставити таймер у застосунку «Годинник». Аргумент seconds."

    override fun execute(args: Map<String, String>): ToolResult {
        val seconds = args["seconds"]?.toIntOrNull()?.takeIf { it > 0 }
            ?: return ToolResult.fail("На скільки поставити таймер? Наприклад: таймер на п'ять хвилин")

        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ToolResult.ok("Таймер на ${UkrainianText.duration(seconds)}")
        } catch (e: ActivityNotFoundException) {
            ToolResult.fail("Не знайшов застосунок годинника для таймера")
        }
    }
}

class AlarmTool(private val context: Context) : Tool {
    override val name = "alarm"
    override val description =
        "Поставити будильник. Аргументи: hour і minute, або in_seconds — через скільки секунд."

    override fun execute(args: Map<String, String>): ToolResult {
        val time: LocalTime = when {
            args["in_seconds"] != null -> {
                val seconds = args["in_seconds"]!!.toLongOrNull()
                    ?: return ToolResult.fail("Не зрозумів, через скільки поставити будильник")
                LocalTime.now().plusSeconds(seconds)
            }
            args["hour"] != null -> {
                val hour = args["hour"]?.toIntOrNull()
                val minute = args["minute"]?.toIntOrNull() ?: 0
                if (hour == null || hour !in 0..23 || minute !in 0..59) {
                    return ToolResult.fail("Не зрозумів час для будильника")
                }
                LocalTime.of(hour, minute)
            }
            else -> return ToolResult.fail("На котру поставити будильник? Наприклад: будильник на сьому тридцять")
        }

        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, time.hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, time.minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ToolResult.ok("Будильник на %d:%02d".format(time.hour, time.minute))
        } catch (e: ActivityNotFoundException) {
            ToolResult.fail("Не знайшов застосунок годинника для будильника")
        }
    }
}
