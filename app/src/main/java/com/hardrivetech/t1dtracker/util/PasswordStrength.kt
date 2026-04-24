package com.hardrivetech.t1dtracker.util

object PasswordStrength {
    data class Assessment(val score: Int, val label: String)

    fun assess(password: String): Assessment {
        if (password.isEmpty()) return Assessment(0, "Very weak")
        var score = 0
        if (password.length >= 8) score++
        if (password.length >= 12) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isLowerCase() }) score++
        if (password.any { "!@#\$%^&*()-_=+[]{}|;:,.<>?/`~".contains(it) }) score++
        val capped = score.coerceAtMost(5)
        val label = when (capped) {
            0, 1 -> "Very weak"
            2 -> "Weak"
            3 -> "Fair"
            4 -> "Good"
            else -> "Strong"
        }
        return Assessment(capped, label)
    }
}
