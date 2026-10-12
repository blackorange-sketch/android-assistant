package com.androidassistant.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NlpHelpersTest {

    @Test
    fun numbers() {
        fun values(text: String) = UkrainianNumbers.find(TextNormalizer.tokens(text)).map { it.value }
        assertEquals(listOf(25), values("двадцять п'ять"))
        assertEquals(listOf(7, 30), values("сім тридцять"))
        assertEquals(listOf(21), values("двадцять першу"))
        assertEquals(listOf(125), values("сто двадцять п'ять"))
        assertEquals(listOf(9), values("о дев'ятій"))
    }

    @Test
    fun plurals() {
        assertEquals("1 хвилину", UkrainianText.duration(60))
        assertEquals("2 хвилини 30 секунд", UkrainianText.duration(150))
        assertEquals("5 хвилин", UkrainianText.duration(300))
        assertEquals("1 годину 11 хвилин", UkrainianText.duration(4260))
    }

    @Test
    fun appMatching() {
        val apps = listOf("Telegram", "YouTube", "Viber", "Chrome", "Камера", "Spotify", "Google Maps")
            .map { AppNameMatcher.Candidate(it, it) }
        fun match(spoken: String) = AppNameMatcher.bestMatch(spoken, apps)?.label

        assertEquals("Telegram", match("телеграм"))
        assertEquals("YouTube", match("ютуб"))
        assertEquals("Viber", match("вайбер"))
        assertEquals("Chrome", match("хром"))
        assertEquals("Камера", match("камеру"))
        assertEquals("Spotify", match("спотіфай"))
        assertNull(match("калькулятор"))
    }
}
