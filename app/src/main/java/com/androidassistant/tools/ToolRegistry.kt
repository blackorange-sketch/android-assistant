package com.androidassistant.tools

import android.content.Context
import android.util.Log

/** Усі доступні дії. Нова команда = новий клас [Tool] + рядок у списку нижче + правило в парсері. */
class ToolRegistry(context: Context) {
    private val app = context.applicationContext

    private val tools: Map<String, Tool> = listOf(
        FlashlightTool(app),
        VolumeTool(app),
        MediaTool(app),
        TimerTool(app),
        AlarmTool(app),
        SettingsPanelTool(app),
        InfoTool(app),
        OpenAppTool(app),
    ).associateBy { it.name }

    /** Список для майбутньої LLM: ім'я та опис кожного інструмента. */
    fun describe(): List<Pair<String, String>> = tools.values.map { it.name to it.description }

    fun execute(call: ToolCall): ToolResult {
        val tool = tools[call.name] ?: return ToolResult.fail("Невідома команда ${call.name}")
        return try {
            tool.execute(call.args)
        } catch (e: Exception) {
            Log.e("ToolRegistry", "Tool ${call.name} failed", e)
            ToolResult.fail("Сталася помилка під час виконання команди")
        }
    }

    fun release() = tools.values.forEach { runCatching { it.release() } }
}
