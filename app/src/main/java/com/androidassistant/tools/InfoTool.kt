package com.androidassistant.tools

import android.content.Context
import android.os.BatteryManager
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class InfoTool(context: Context) : Tool {
    override val name = "info"
    override val description = "Сказати поточний час, дату або заряд батареї. Аргумент what: time | date | battery."

    private val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("uk", "UA"))

    override fun execute(args: Map<String, String>): ToolResult = when (args["what"]) {
        "time" -> {
            val now = LocalTime.now()
            ToolResult.ok("Зараз %d:%02d".format(now.hour, now.minute))
        }
        "date" -> ToolResult.ok("Сьогодні ${LocalDate.now().format(dateFormat)}")
        "battery" -> {
            val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val charging = if (battery.isCharging) ", заряджається" else ""
            ToolResult.ok("Заряд $level відсотків$charging")
        }
        else -> ToolResult.fail("Не зрозумів, що саме підказати")
    }
}
