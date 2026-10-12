package com.androidassistant.nlu

import com.androidassistant.tools.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandParserTest {

    private val parser = CommandParser()

    private fun call(name: String, vararg args: Pair<String, String>) = ToolCall(name, mapOf(*args))

    @Test
    fun flashlight() {
        assertEquals(call("flashlight", "state" to "on"), parser.parse("Увімкни ліхтарик"))
        assertEquals(call("flashlight", "state" to "off"), parser.parse("вимкни ліхтарик"))
        assertEquals(call("flashlight", "state" to "toggle"), parser.parse("ліхтарик"))
    }

    @Test
    fun volume() {
        assertEquals(call("volume", "action" to "up"), parser.parse("зроби голосніше"))
        assertEquals(call("volume", "action" to "down"), parser.parse("тихіше"))
        assertEquals(call("volume", "action" to "set", "percent" to "50"), parser.parse("гучність п'ятдесят відсотків"))
        assertEquals(call("volume", "action" to "set", "percent" to "35"), parser.parse("постав гучність на тридцять п'ять"))
        assertEquals(call("volume", "action" to "mute"), parser.parse("вимкни звук"))
    }

    @Test
    fun timer() {
        assertEquals(call("timer", "seconds" to "300"), parser.parse("постав таймер на п'ять хвилин"))
        assertEquals(call("timer", "seconds" to "1800"), parser.parse("таймер на півгодини"))
        assertEquals(call("timer", "seconds" to "5400"), parser.parse("таймер на півтори години"))
        assertEquals(call("timer", "seconds" to "90"), parser.parse("таймер на одну хвилину тридцять секунд"))
        assertEquals(call("timer", "seconds" to "3600"), parser.parse("таймер на годину"))
        assertEquals(call("timer"), parser.parse("постав таймер"))
    }

    @Test
    fun alarm() {
        assertEquals(call("alarm", "hour" to "7", "minute" to "30"), parser.parse("будильник на сьому тридцять"))
        assertEquals(call("alarm", "hour" to "21", "minute" to "0"), parser.parse("розбуди мене о дев'ятій вечора"))
        assertEquals(call("alarm", "hour" to "7", "minute" to "30"), parser.parse("будильник на пів на восьму"))
        assertEquals(call("alarm", "hour" to "6", "minute" to "45"), parser.parse("будильник на 6:45"))
        assertEquals(call("alarm", "in_seconds" to "1200"), parser.parse("будильник через двадцять хвилин"))
        assertEquals(call("open_app", "query" to "будильник"), parser.parse("відкрий будильник"))
    }

    @Test
    fun panelsAndInfo() {
        assertEquals(call("settings_panel", "panel" to "wifi"), parser.parse("увімкни вай фай"))
        assertEquals(call("settings_panel", "panel" to "bluetooth"), parser.parse("відкрий блютуз"))
        assertEquals(call("info", "what" to "time"), parser.parse("котра година"))
        assertEquals(call("info", "what" to "date"), parser.parse("яке сьогодні число"))
        assertEquals(call("info", "what" to "battery"), parser.parse("який заряд батареї"))
    }

    @Test
    fun openApp() {
        assertEquals(call("open_app", "query" to "телеграм"), parser.parse("відкрий мені телеграм будь ласка"))
        assertEquals(call("open_app", "query" to "ютуб"), parser.parse("запусти ютуб"))
        assertNull(parser.parse("як справи"))
        assertNull(parser.parse(""))
    }

    @Test
    fun media() {
        assertEquals(call("media", "action" to "pause"), parser.parse("пауза"))
        assertEquals(call("media", "action" to "next"), parser.parse("наступний трек"))
        assertEquals(call("media", "action" to "previous"), parser.parse("попередня пісня"))
        assertEquals(call("media", "action" to "play"), parser.parse("увімкни музику"))
        assertEquals(call("media", "action" to "pause"), parser.parse("зупини музику"))
    }

    /** Реальні результати моделей на записі «відкрий налаштування». */
    @Test
    fun toleratesRecognitionErrors() {
        assertEquals(call("open_app", "query" to "налаштування"), parser.parse("відкриє налаштування"))
        assertEquals(call("open_app", "query" to "налаштування"), parser.parse("Відкрить налаштування."))
        assertEquals(call("open_app", "query" to "налаштування"), parser.parse("від країн налаштування"))
        assertEquals(call("open_app", "query" to "на влаштування"), parser.parse("Відкрить на влаштування."))
        assertEquals(call("flashlight", "state" to "on"), parser.parse("Оріон, увімкни ліхтарик"))
    }
}
