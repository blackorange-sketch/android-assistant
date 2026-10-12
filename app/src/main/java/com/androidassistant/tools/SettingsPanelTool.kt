package com.androidassistant.tools

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Android 10+ не дозволяє застосункам самим вмикати Wi-Fi чи Bluetooth,
 * тому відкриваємо системну панель, де користувач перемикає одним дотиком.
 */
class SettingsPanelTool(private val context: Context) : Tool {
    override val name = "settings_panel"
    override val description = "Відкрити системну панель. Аргумент panel: wifi | bluetooth | internet | airplane."

    override fun execute(args: Map<String, String>): ToolResult {
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val (action, speech) = when (args["panel"]) {
            "wifi" -> (if (modern) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS) to "Відкриваю Wi-Fi"
            "internet" -> (if (modern) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS) to "Відкриваю інтернет"
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS to "Відкриваю Bluetooth"
            "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS to "Відкриваю режим польоту"
            else -> return ToolResult.fail("Не знаю такої панелі налаштувань")
        }
        return try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ToolResult.ok(speech)
        } catch (e: ActivityNotFoundException) {
            ToolResult.fail("Не вдалося відкрити налаштування")
        }
    }
}
