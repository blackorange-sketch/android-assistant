package com.androidassistant.nlu

import java.util.Locale

/**
 * Зіставляє сказану назву («телеграм», «ютуб», «вайбер») з підписами застосунків
 * («Telegram», «YouTube», «Viber»): транслітерація кількома варіантами + нечітке порівняння.
 */
object AppNameMatcher {

    private const val THRESHOLD = 0.65

    private val ambiguous = mapOf(
        'г' to listOf("h", "g"),
        'х' to listOf("kh", "h", "ch"),
        'и' to listOf("y", "i"),
        'й' to listOf("y", "i"),
        'ю' to listOf("yu", "you", "u"),
        'є' to listOf("ye", "e"),
        'ї' to listOf("yi", "i"),
        'я' to listOf("ya", "a"),
    )

    private val simple = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'ґ' to "g", 'д' to "d", 'е' to "e", 'ж' to "zh",
        'з' to "z", 'і' to "i", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
        'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'ц' to "ts",
        'ч' to "ch", 'ш' to "sh", 'щ' to "shch", 'ь' to "", '\'' to "",
    )

    data class Candidate<T>(val label: String, val payload: T)

    fun <T> bestMatch(spoken: String, candidates: List<Candidate<T>>): Candidate<T>? {
        val variants = spokenVariants(spoken)
        if (variants.isEmpty()) return null
        var best: Candidate<T>? = null
        var bestScore = 0.0
        for (candidate in candidates) {
            val score = score(variants, candidate.label)
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best.takeIf { bestScore >= THRESHOLD }
    }

    fun score(variants: List<String>, label: String): Double {
        val clean = squash(label)
        if (clean.isEmpty()) return 0.0
        val labelForms = buildList {
            add(clean)
            label.lowercase(Locale.ROOT).split(' ', '-', '.', ':').map { squash(it) }.filter { it.length >= 3 }.forEach { add(it) }
        }
        var best = 0.0
        for (v in variants) for (l in labelForms) {
            best = maxOf(best, similarity(v, l))
            if (v.length >= 4 && l.startsWith(v)) best = maxOf(best, 0.9)
        }
        return best
    }

    /** Кирилиця як є + латинські варіанти транслітерації. */
    fun spokenVariants(spoken: String): List<String> {
        val base = squash(spoken)
        if (base.isEmpty()) return emptyList()
        val result = linkedSetOf(base)

        // Для кожної неоднозначної літери обираємо один варіант на все слово
        val letters = ambiguous.keys.filter { it in base }
        var combos = listOf(emptyMap<Char, String>())
        for (letter in letters) {
            combos = combos.flatMap { combo -> ambiguous.getValue(letter).map { combo + (letter to it) } }
            if (combos.size > 64) break
        }
        for (combo in combos.take(64)) {
            val latin = buildString {
                for (ch in base) append(combo[ch] ?: simple[ch] ?: ch.toString())
            }
            result += latin
        }
        return result.toList()
    }

    private fun squash(text: String): String =
        TextNormalizer.normalize(text).replace(" ", "")

    fun similarity(a: String, b: String): Double {
        val max = maxOf(a.length, b.length)
        if (max == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / max
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }
}
