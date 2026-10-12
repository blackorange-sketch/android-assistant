package com.androidassistant.nlu

import com.androidassistant.tools.ToolCall
import kotlin.math.roundToInt

/**
 * Перетворює розпізнану фразу на [ToolCall] за простими правилами.
 * Повертає null, якщо команду не впізнано (тут згодом підключиться локальна LLM).
 */
class CommandParser {

    fun parse(text: String): ToolCall? {
        val tokens = TextNormalizer.tokens(text).filterNot { it in wakeWords }
        if (tokens.isEmpty()) return null
        val compact = tokens.joinToString("")

        return parseFlashlight(tokens)
            ?: parseVolume(tokens)
            ?: parseTimer(tokens)
            ?: parseAlarm(tokens)
            ?: parseMedia(tokens)
            ?: parseSettingsPanel(tokens, compact)
            ?: parseInfo(tokens)
            ?: parseOpenApp(tokens)
    }

    // ---------- Ліхтарик ----------

    private fun parseFlashlight(tokens: List<String>): ToolCall? {
        if (tokens.none { it.startsWith("ліхтар") || it.startsWith("фонар") }) return null
        val state = when (onOff(tokens)) {
            true -> "on"
            false -> "off"
            null -> "toggle"
        }
        return ToolCall("flashlight", mapOf("state" to state))
    }

    // ---------- Гучність ----------

    private fun parseVolume(tokens: List<String>): ToolCall? {
        fun volume(action: String, percent: Int? = null) = ToolCall(
            "volume",
            buildMap {
                put("action", action)
                if (percent != null) put("percent", percent.toString())
            }
        )

        if (tokens.any { it.startsWith("голосніш") || it.startsWith("гучніш") }) return volume("up")
        if (tokens.any { it.startsWith("тихіш") }) return volume("down")

        val mentionsVolume = tokens.any { it.startsWith("гучн") || it.startsWith("звук") }
        if (!mentionsVolume) return null

        if (tokens.any { it.startsWith("збільш") || it.startsWith("підвищ") || it.startsWith("додай") }) {
            return volume("up")
        }
        if (tokens.any { it.startsWith("зменш") || it.startsWith("зниз") || it.startsWith("пониз") }) {
            return volume("down")
        }
        if (tokens.any { it.startsWith("максим") }) return volume("set", 100)
        if (tokens.any { it.startsWith("мінім") }) return volume("set", 10)

        UkrainianNumbers.find(tokens).firstOrNull()?.let { span ->
            return volume("set", span.value.coerceIn(0, 100))
        }
        return when (onOff(tokens)) {
            false -> volume("mute")
            true -> volume("unmute")
            null -> null
        }
    }

    // ---------- Музика ----------

    private fun parseMedia(tokens: List<String>): ToolCall? {
        fun media(action: String) = ToolCall("media", mapOf("action" to action))

        if (tokens.any { it.startsWith("наступн") }) return media("next")
        if (tokens.any { it.startsWith("попередн") }) return media("previous")
        if (tokens.any { it.startsWith("пауз") || it == "стоп" }) return media("pause")

        val aboutMusic = tokens.any {
            it.startsWith("музик") || it.startsWith("пісн") || it.startsWith("трек") || it.startsWith("плеєр")
        }
        if (tokens.any { it.startsWith("продовж") || it.startsWith("відтвор") || it.startsWith("грай") }) {
            return media("play")
        }
        if (!aboutMusic) return null
        if (tokens.any { it.startsWith("зупин") }) return media("pause")
        return when (onOff(tokens)) {
            true -> media("play")
            false -> media("pause")
            null -> media("toggle")
        }
    }

    // ---------- Таймер ----------

    private fun parseTimer(tokens: List<String>): ToolCall? {
        if (tokens.none { it.startsWith("таймер") || it.startsWith("засіч") }) return null
        val seconds = parseDurationSeconds(tokens) ?: return ToolCall("timer")
        return ToolCall("timer", mapOf("seconds" to seconds.toString()))
    }

    // ---------- Будильник ----------

    private fun parseAlarm(tokens: List<String>): ToolCall? {
        if (tokens.none { it.startsWith("будильник") || it.startsWith("розбуди") }) return null

        if ("через" in tokens) {
            parseDurationSeconds(tokens)?.let { return ToolCall("alarm", mapOf("in_seconds" to it.toString())) }
        }
        parseClockTime(tokens)?.let { (h, m) ->
            return ToolCall("alarm", mapOf("hour" to h.toString(), "minute" to m.toString()))
        }
        // «відкрий будильник» без часу — хай це обробить відкриття застосунку
        if (openVerbEnd(tokens) != null) return null
        return ToolCall("alarm")
    }

    // ---------- Системні панелі ----------

    private fun parseSettingsPanel(tokens: List<String>, compact: String): ToolCall? {
        val panel = when {
            "вайфа" in compact || "wifi" in compact || "вайфай" in compact -> "wifi"
            "блют" in compact || "bluetooth" in compact -> "bluetooth"
            tokens.any { it.startsWith("авіарежим") || it.startsWith("польот") } -> "airplane"
            tokens.any { it.startsWith("інтернет") } -> "internet"
            else -> return null
        }
        return ToolCall("settings_panel", mapOf("panel" to panel))
    }

    // ---------- Довідка: час, дата, батарея ----------

    private fun parseInfo(tokens: List<String>): ToolCall? {
        val what = when {
            ("котра" in tokens && tokens.any { it.startsWith("годин") }) ||
                ("скільки" in tokens && "часу" in tokens) ||
                ("який" in tokens && "час" in tokens) -> "time"

            tokens.any { it == "число" || it == "дата" || it == "дату" } ||
                ("який" in tokens && "день" in tokens) -> "date"

            tokens.any { it.startsWith("заряд") || it.startsWith("батаре") || it.startsWith("акумулятор") } -> "battery"
            else -> return null
        }
        return ToolCall("info", mapOf("what" to what))
    }

    // ---------- Відкрити застосунок ----------

    private fun parseOpenApp(tokens: List<String>): ToolCall? {
        val afterVerb = openVerbEnd(tokens) ?: return null
        val query = tokens.drop(afterVerb).filterNot { it in fillerWords }
        if (query.isEmpty()) return null
        return ToolCall("open_app", mapOf("query" to query.joinToString(" ")))
    }

    // ---------- Спільні помічники ----------

    /**
     * Індекс токена одразу після дієслова «відкрий/запусти/покажи» або null.
     * Терпимо до помилок розпізнавання: «відкриє», «відкрить», «від країн» (два токени, що разом дають «відкр…»).
     */
    private fun openVerbEnd(tokens: List<String>): Int? {
        for (i in tokens.indices) {
            val t = tokens[i]
            if (openVerbPrefixes.any { t.startsWith(it) }) return i + 1
            val joined = t + tokens.getOrElse(i + 1) { "" }
            if (t.length <= 4 && joined.startsWith("відкр")) return i + 2
        }
        return null
    }

    /** true — увімкнути, false — вимкнути, null — не сказано. */
    private fun onOff(tokens: List<String>): Boolean? = when {
        tokens.any { t -> offPrefixes.any { t.startsWith(it) } } -> false
        tokens.any { t -> onPrefixes.any { t.startsWith(it) } } -> true
        else -> null
    }

    companion object {
        private val offPrefixes = listOf("вимкн", "виключ", "вируб", "погас", "загас", "прибер")
        private val onPrefixes = listOf("увімкн", "вімкн", "ввімкн", "включ", "запал", "вруб", "засвіт")

        private val openVerbPrefixes = listOf("відкри", "відкрі", "відкрой", "запуст", "запуск", "покаж")
        private val wakeWords = setOf("оріон", "орион", "оріоне")
        private val fillerWords = setOf(
            "будь", "ласка", "мені", "мене", "додаток", "застосунок", "програму", "програма", "апку",
        )

        private val unitStems = listOf("секунд" to 1, "хвилин" to 60, "годин" to 3600)

        private fun unitOf(token: String): Int? {
            if (token.startsWith("годинник")) return null
            return unitStems.firstOrNull { token.startsWith(it.first) }?.second
        }

        /** «п'ять хвилин», «півгодини», «півтори години», «година двадцять хвилин» → секунди. */
        fun parseDurationSeconds(tokens: List<String>): Int? {
            val spans = UkrainianNumbers.find(tokens)
            var total = 0.0
            var found = false
            for ((i, token) in tokens.withIndex()) {
                if (token.startsWith("пів") && token.length > 3 && !token.startsWith("півтор")) {
                    unitOf(token.substring(3))?.let { unit ->
                        total += unit * 0.5
                        found = true
                    }
                    continue
                }
                val unit = unitOf(token) ?: continue
                val previous = tokens.getOrNull(i - 1)
                val span = spans.firstOrNull { it.end == i }
                val amount = when {
                    span != null -> span.value.toDouble()
                    previous == "пів" -> 0.5
                    previous == "півтори" || previous == "півтора" -> 1.5
                    else -> 1.0
                }
                total += amount * unit
                found = true
            }
            val seconds = total.roundToInt()
            return if (found && seconds > 0) seconds else null
        }

        /** «сьому тридцять», «о дев'ятій вечора», «пів на восьму», «7 30» → (година, хвилина). */
        fun parseClockTime(tokens: List<String>): Pair<Int, Int>? {
            var hour: Int
            var minute = 0

            val halfIndex = tokens.indices.firstOrNull { i ->
                tokens[i] == "пів" && tokens.getOrNull(i + 1) == "на"
            }
            val halfHour = halfIndex?.let { tokens.getOrNull(it + 2)?.let(UkrainianNumbers::wordValue) }
            if (halfHour != null) {
                hour = (halfHour - 1 + 24) % 24
                minute = 30
            } else {
                val spans = UkrainianNumbers.find(tokens)
                val first = spans.firstOrNull() ?: return null
                hour = first.value
                spans.getOrNull(1)?.let { if (it.value in 0..59) minute = it.value }
            }

            when {
                "вечора" in tokens && hour in 1..11 -> hour += 12
                "дня" in tokens && hour in 1..6 -> hour += 12
                "ночі" in tokens && hour == 12 -> hour = 0
            }
            if (hour == 24) hour = 0
            if (hour !in 0..23 || minute !in 0..59) return null
            return hour to minute
        }
    }
}
