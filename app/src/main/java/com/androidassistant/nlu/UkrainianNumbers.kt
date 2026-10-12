package com.androidassistant.nlu

/**
 * Розбір чисел, сказаних словами: «двадцять п'ять», «сьому», «о дев'ятій».
 * Vosk повертає числа саме словами, тому без цього не працюють таймер, будильник і гучність.
 */
object UkrainianNumbers {

    data class Span(val value: Int, val start: Int, val end: Int) // end — не включно

    private val cardinals = mapOf(
        "нуль" to 0,
        "один" to 1, "одна" to 1, "одну" to 1, "одне" to 1, "одного" to 1, "одній" to 1,
        "два" to 2, "дві" to 2, "двох" to 2,
        "три" to 3, "трьох" to 3,
        "чотири" to 4, "чотирьох" to 4,
        "п'ять" to 5, "п'яти" to 5,
        "шість" to 6, "шести" to 6,
        "сім" to 7, "семи" to 7,
        "вісім" to 8, "восьми" to 8,
        "дев'ять" to 9, "дев'яти" to 9,
        "десять" to 10, "десяти" to 10,
        "одинадцять" to 11, "дванадцять" to 12, "тринадцять" to 13, "чотирнадцять" to 14,
        "п'ятнадцять" to 15, "шістнадцять" to 16, "сімнадцять" to 17, "вісімнадцять" to 18,
        "дев'ятнадцять" to 19,
        "двадцять" to 20, "тридцять" to 30, "сорок" to 40, "п'ятдесят" to 50,
        "шістдесят" to 60, "сімдесят" to 70, "вісімдесят" to 80, "дев'яносто" to 90,
        "сто" to 100,
    )

    // Основи порядкових числівників: «сьому», «сьомій», «шосту», «двадцять першу»
    private val ordinalStems = mapOf(
        "перш" to 1, "друг" to 2, "трет" to 3, "четверт" to 4, "п'ят" to 5,
        "шост" to 6, "сьом" to 7, "восьм" to 8, "дев'ят" to 9, "десят" to 10,
        "одинадцят" to 11, "дванадцят" to 12, "тринадцят" to 13, "чотирнадцят" to 14,
        "п'ятнадцят" to 15, "шістнадцят" to 16, "сімнадцят" to 17, "вісімнадцят" to 18,
        "дев'ятнадцят" to 19, "двадцят" to 20, "тридцят" to 30,
    )
    private val ordinalEndings = listOf("ого", "ому", "ій", "ою", "ої", "ий", "у", "ю", "а", "я", "і", "е")

    fun wordValue(word: String): Int? {
        word.toIntOrNull()?.let { return it }
        cardinals[word]?.let { return it }
        for (ending in ordinalEndings) {
            if (word.length > ending.length && word.endsWith(ending)) {
                ordinalStems[word.dropLast(ending.length)]?.let { return it }
            }
        }
        return null
    }

    /** Знаходить усі числа в токенах, складаючи «двадцять п'ять» у 25, але не «сім тридцять». */
    fun find(tokens: List<String>): List<Span> {
        val result = mutableListOf<Span>()
        var i = 0
        while (i < tokens.size) {
            val first = wordValue(tokens[i])
            if (first == null) { i++; continue }
            var total: Int = first
            var last: Int = first
            var j = i + 1
            while (j < tokens.size) {
                val next = wordValue(tokens[j]) ?: break
                if (!canAppend(last, next)) break
                total += next
                last = next
                j++
            }
            result += Span(total, i, j)
            i = j
        }
        return result
    }

    private fun canAppend(last: Int, next: Int): Boolean = when {
        last >= 100 && last % 100 == 0 -> next in 1..99
        last in 20..90 && last % 10 == 0 -> next in 1..9
        else -> false
    }
}
