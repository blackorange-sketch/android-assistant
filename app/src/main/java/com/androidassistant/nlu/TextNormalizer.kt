package com.androidassistant.nlu

import java.util.Locale

object TextNormalizer {
    private val UK = Locale("uk", "UA")
    private val apostrophes = Regex("[’ʼ`‘´]")
    private val junk = Regex("[^\\p{L}\\p{N}' ]")
    private val spaces = Regex("\\s+")

    /** Нижній регістр, єдиний апостроф, без розділових знаків, одинарні пробіли. */
    fun normalize(text: String): String =
        text.lowercase(UK)
            .replace(apostrophes, "'")
            .replace(junk, " ")
            .replace(spaces, " ")
            .trim()

    fun tokens(text: String): List<String> =
        normalize(text).split(' ').filter { it.isNotEmpty() }
}
