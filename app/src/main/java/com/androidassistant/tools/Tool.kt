package com.androidassistant.tools

/** Що треба виконати. Ту саму структуру пізніше зможе повертати локальна LLM (function calling). */
data class ToolCall(val name: String, val args: Map<String, String> = emptyMap())

/** Результат виконання: [speech] асистент проговорює вголос. */
data class ToolResult(val success: Boolean, val speech: String) {
    companion object {
        fun ok(speech: String) = ToolResult(true, speech)
        fun fail(speech: String) = ToolResult(false, speech)
    }
}

interface Tool {
    /** Унікальне ім'я, на нього посилається [ToolCall.name]. */
    val name: String

    /** Опис для людей і для майбутньої LLM: що робить і які аргументи приймає. */
    val description: String

    fun execute(args: Map<String, String>): ToolResult

    fun release() {}
}
