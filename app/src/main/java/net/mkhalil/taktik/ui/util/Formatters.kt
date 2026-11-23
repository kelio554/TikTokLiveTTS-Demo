package net.mkhalil.taktik.ui.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    private val dateTime = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    private val timeOnly = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val date = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    fun formatDateTime(ts: Long): String = dateTime.format(Date(ts))
    fun formatTime(ts: Long): String = timeOnly.format(Date(ts))
    fun formatDate(ts: Long): String = date.format(Date(ts))

    fun compactNumber(value: Long): String {
        if (value < 1_000) return value.toString()
        val (v, suffix) = when {
            value < 1_000_000 -> value / 1_000.0 to "K"
            value < 1_000_000_000 -> value / 1_000_000.0 to "M"
            else -> value / 1_000_000_000.0 to "B"
        }
        val s = "%.1f".format(v)
        return (if (s.endsWith(".0")) s.dropLast(2) else s) + suffix
    }

    fun coinsToUsd(coins: Long, usdPerCoin: Double): String {
        val nf = NumberFormat.getCurrencyInstance(Locale.US)
        return nf.format(coins * usdPerCoin)
    }

    fun duration(startMs: Long, endMs: Long?): String {
        val end = endMs ?: System.currentTimeMillis()
        val seconds = ((end - startMs) / 1000).coerceAtLeast(0)
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun regionFlag(code: String?): String {
        if (code.isNullOrBlank() || code.length != 2) return ""
        val base = 0x1F1E6 - 'A'.code
        val cp1 = base + code[0].uppercaseChar().code
        val cp2 = base + code[1].uppercaseChar().code
        if (cp1 < 0x1F1E6 || cp2 < 0x1F1E6) return ""
        return String(Character.toChars(cp1)) + String(Character.toChars(cp2))
    }
}
