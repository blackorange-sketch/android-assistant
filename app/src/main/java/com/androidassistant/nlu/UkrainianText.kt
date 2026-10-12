package com.androidassistant.nlu

object UkrainianText {

    /** plural(5, "хвилина", "хвилини", "хвилин") → "хвилин" */
    fun plural(n: Int, one: String, few: String, many: String): String {
        val mod100 = n % 100
        val mod10 = n % 10
        return when {
            mod100 in 11..14 -> many
            mod10 == 1 -> one
            mod10 in 2..4 -> few
            else -> many
        }
    }

    /** 330 → "5 хвилин 30 секунд" */
    fun duration(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = totalSeconds % 3600 / 60
        val s = totalSeconds % 60
        val parts = mutableListOf<String>()
        if (h > 0) parts += "$h ${plural(h, "годину", "години", "годин")}"
        if (m > 0) parts += "$m ${plural(m, "хвилину", "хвилини", "хвилин")}"
        if (s > 0 || parts.isEmpty()) parts += "$s ${plural(s, "секунду", "секунди", "секунд")}"
        return parts.joinToString(" ")
    }
}
