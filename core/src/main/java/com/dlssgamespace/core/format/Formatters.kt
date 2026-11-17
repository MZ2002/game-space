package com.dlssgamespace.core.format

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object TelemetryFormatters {
  private const val UNAVAILABLE = "N/A"
  private const val BYTES_IN_MB = 1024.0 * 1024.0
  private const val BYTES_IN_GB = 1024.0 * 1024.0 * 1024.0

  fun formatBytes(bytes: Long?): String {
    if (bytes == null || bytes < 0L) return UNAVAILABLE
    return when {
      bytes >= BYTES_IN_GB -> String.format(Locale.US, "%.1f GB", bytes / BYTES_IN_GB)
      bytes >= BYTES_IN_MB -> String.format(Locale.US, "%.0f MB", bytes / BYTES_IN_MB)
      else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    }
  }

  fun formatPercent(fraction: Float?): String {
    if (fraction == null) return UNAVAILABLE
    return "${(fraction.coerceIn(0f, 1f) * 100f).roundToInt()}%"
  }

  fun formatTemperatureCelsius(celsius: Float?): String {
    if (celsius == null) return UNAVAILABLE
    return String.format(Locale.US, "%.1f °C", celsius)
  }

  fun formatGamingDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "0m"
    val totalSeconds = durationMs / 1000L
    if (totalSeconds < 60L) return "< 1m"
    val totalMinutes = totalSeconds / 60L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
      hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
      hours > 0L -> "${hours}h"
      else -> "${minutes}m"
    }
  }

  fun formatLastPlayed(timestampMs: Long?, nowMs: Long = System.currentTimeMillis()): String {
    if (timestampMs == null || timestampMs <= 0L) return "Never played"
    val diff = (nowMs - timestampMs).coerceAtLeast(0L)
    val minutes = diff / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
      minutes < 1L -> "Just now"
      minutes < 60L -> "${minutes}m ago"
      hours < 24L -> "${hours}h ago"
      days < 7L -> "${days}d ago"
      else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestampMs))
    }
  }
}
