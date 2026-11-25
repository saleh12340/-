package com.example.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object NumberFormatUtils {
    private val usSymbols = DecimalFormatSymbols(Locale.US)

    /**
     * Converts Arabic-Indic (٠-٩) and Extended Arabic-Indic / Persian (۰-۹) digits
     * into Western/English digits (0-9), and normalizes Arabic decimal/comma separators.
     */
    fun toEnglishDigits(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                '٫', ',' -> sb.append('.')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Sanitizes numeric input allowing optional leading '-' (for validation detection),
     * digits 0-9, and at most one decimal dot.
     */
    fun sanitizeDecimalInput(raw: String): String {
        val normalized = toEnglishDigits(raw).trim()
        val sb = StringBuilder()
        var hasDot = false
        for ((index, ch) in normalized.withIndex()) {
            when {
                ch in '0'..'9' -> sb.append(ch)
                ch == '.' && !hasDot -> {
                    hasDot = true
                    sb.append(ch)
                }
                ch == '-' && index == 0 -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun parseDecimal(raw: String): Double? {
        val clean = toEnglishDigits(raw).trim()
        if (clean.isEmpty() || clean == "." || clean == "-" || clean == "-.") return null
        val parsed = clean.toDoubleOrNull() ?: return null
        return if (parsed.isFinite()) parsed else null
    }

    /**
     * Formats numbers in English digits (0-9) with up to [maxDecimals] decimal places
     * for display while hiding unnecessary trailing zeros (e.g., 100.0 -> "100", 33.333 -> "33.33").
     */
    fun formatNumber(value: Double, maxDecimals: Int = 2): String {
        if (!value.isFinite()) return "0"
        val bd = BigDecimal.valueOf(value).setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros()
        val normalized = if (bd.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO else bd
        val pattern = if (maxDecimals <= 0) "#,##0" else "#,##0.${"#".repeat(maxDecimals)}"
        return DecimalFormat(pattern, usSymbols).format(normalized)
    }

    /**
     * Formats numbers without thousands commas for TextField editing in English digits.
     */
    fun formatInputNumber(value: Double, maxDecimals: Int = 2): String {
        if (!value.isFinite()) return "0"
        val bd = BigDecimal.valueOf(value).setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros()
        val normalized = if (bd.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO else bd
        return normalized.toPlainString()
    }
}
