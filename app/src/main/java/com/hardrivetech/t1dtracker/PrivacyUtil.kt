package com.hardrivetech.t1dtracker

object PrivacyUtil {
    private val emailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val phoneRegex = Regex("\\+?\\d[\\d\\-() ]{6,}\\d")
    private val numberSeqRegex = Regex("\\d{4,}")

    fun redactPII(input: String?): String? {
        if (input == null) return null
        var out = input
        out = emailRegex.replace(out) { "[REDACTED_EMAIL]" }
        out = phoneRegex.replace(out) { "[REDACTED_PHONE]" }
        out = numberSeqRegex.replace(out) { "[REDACTED_NUMBER]" }
        return out
    }
}
