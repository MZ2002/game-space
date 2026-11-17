package com.dlssgamespace.core.telemetry

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

class DeviceTelemetryProvider(private val context: Context) {

  fun observeTelemetry(pollIntervalMs: Long = 2_500L): Flow<DeviceTelemetry> =
    callbackFlow {
        fun emitSnapshot() {
          trySend(readSnapshot())
        }

        val batteryReceiver =
          object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
              emitSnapshot()
            }
          }

        runCatching {
          context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }

        emitSnapshot()

        val pollingJob =
          launch(Dispatchers.IO) {
            while (currentCoroutineContext().isActive) {
              delay(pollIntervalMs)
              emitSnapshot()
            }
          }

        awaitClose {
          pollingJob.cancel()
          runCatching { context.unregisterReceiver(batteryReceiver) }
        }
      }
      .flowOn(Dispatchers.IO)

  fun readSnapshot(): DeviceTelemetry {
    return DeviceTelemetry(
      hardware = readHardwareInfo(),
      display = readDisplayInfo(),
      memory = readMemoryInfo(),
      storage = readStorageInfo(),
      battery = readBatteryInfo(),
      lastUpdatedAtMillis = System.currentTimeMillis(),
    )
  }

  private fun readHardwareInfo(): DeviceHardwareInfo {
    val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
    val model = Build.MODEL?.trim().orEmpty()
    val deviceName =
      when {
        manufacturer.isBlank() && model.isBlank() -> UNAVAILABLE
        model.startsWith(manufacturer, ignoreCase = true) -> model
        manufacturer.isBlank() -> model
        model.isBlank() -> manufacturer
        else ->
          "${manufacturer.replaceFirstChar { it.titlecase(Locale.getDefault()) }} $model"
      }

    val release = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: UNAVAILABLE
    val androidVersion =
      if (release == UNAVAILABLE) UNAVAILABLE else "Android $release (API ${Build.VERSION.SDK_INT})"

    val securityPatch =
      Build.VERSION.SECURITY_PATCH?.takeIf { it.isNotBlank() } ?: UNAVAILABLE

    val chipsetRaw =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Build.SOC_MODEL?.takeIf { it.isNotBlank() && !it.equals(Build.UNKNOWN, ignoreCase = true) }
          ?: Build.HARDWARE?.takeIf { it.isNotBlank() && !it.equals(Build.UNKNOWN, ignoreCase = true) }
      } else {
        Build.HARDWARE?.takeIf { it.isNotBlank() && !it.equals(Build.UNKNOWN, ignoreCase = true) }
      }

    val chipset = chipsetRaw ?: UNAVAILABLE
    val abi = Build.SUPPORTED_ABIS?.firstOrNull()?.takeIf { it.isNotBlank() } ?: UNAVAILABLE

    return DeviceHardwareInfo(
      deviceName = deviceName,
      androidVersion = androidVersion,
      apiLevel = Build.VERSION.SDK_INT,
      securityPatch = securityPatch,
      chipset = chipset,
      abi = abi,
    )
  }

  private fun readDisplayInfo(): DisplayInfo {
    return runCatching {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display: Display? = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)

        if (display != null) {
          val mode = display.mode
          val width = mode?.physicalWidth ?: 0
          val height = mode?.physicalHeight ?: 0
          val resolutionLabel =
            if (width > 0 && height > 0) {
              "$width × $height"
            } else {
              readFallbackResolution()
            }

          val rate = (mode?.refreshRate ?: display.refreshRate).takeIf { it > 0f }
          val rateLabel = formatRefreshRate(rate)
          val supported =
            display.supportedModes
              ?.mapNotNull { m -> m.refreshRate.takeIf { it > 0f }?.roundToInt() }
              ?.distinct()
              ?.sorted()
              .orEmpty()

          DisplayInfo(
            resolutionLabel = resolutionLabel,
            refreshRateHz = rate,
            refreshRateLabel = rateLabel,
            supportedRefreshRates = supported,
          )
        } else {
          DisplayInfo(
            resolutionLabel = readFallbackResolution(),
            refreshRateHz = null,
            refreshRateLabel = UNAVAILABLE,
            supportedRefreshRates = emptyList(),
          )
        }
      }
      .getOrElse {
        DisplayInfo(
          resolutionLabel = UNAVAILABLE,
          refreshRateHz = null,
          refreshRateLabel = UNAVAILABLE,
          supportedRefreshRates = emptyList(),
        )
      }
  }

  private fun readFallbackResolution(): String {
    return runCatching {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowManager != null) {
          val bounds = windowManager.maximumWindowMetrics.bounds
          if (bounds.width() > 0 && bounds.height() > 0) {
            return "${bounds.width()} × ${bounds.height()}"
          }
        }
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getRealMetrics(metrics)
        if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
          "${metrics.widthPixels} × ${metrics.heightPixels}"
        } else {
          UNAVAILABLE
        }
      }
      .getOrDefault(UNAVAILABLE)
  }

  private fun formatRefreshRate(hz: Float?): String {
    if (hz == null || hz <= 0f) return UNAVAILABLE
    val rounded = hz.roundToInt()
    return if (kotlin.math.abs(hz - rounded) < 0.1f) {
      "$rounded Hz"
    } else {
      String.format(Locale.US, "%.1f Hz", hz)
    }
  }

  private fun readMemoryInfo(): MemoryInfoSnapshot {
    return runCatching {
        val activityManager =
          context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return MemoryInfoSnapshot(null, null, null, null)
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val total = memInfo.totalMem.takeIf { it > 0L }
        val avail = memInfo.availMem.takeIf { it >= 0L }
        if (total != null && avail != null) {
          val used = (total - avail).coerceAtLeast(0L)
          val fraction = (used.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
          MemoryInfoSnapshot(
            totalBytes = total,
            usedBytes = used,
            availableBytes = avail,
            usageFraction = fraction,
          )
        } else {
          MemoryInfoSnapshot(total, null, avail, null)
        }
      }
      .getOrElse { MemoryInfoSnapshot(null, null, null, null) }
  }

  private fun readStorageInfo(): StorageInfoSnapshot {
    return runCatching {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes.takeIf { it > 0L }
        val available = stat.availableBytes.takeIf { it >= 0L }
        if (total != null && available != null) {
          val used = (total - available).coerceAtLeast(0L)
          val fraction = (used.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
          StorageInfoSnapshot(
            totalBytes = total,
            usedBytes = used,
            availableBytes = available,
            usageFraction = fraction,
          )
        } else {
          StorageInfoSnapshot(total, null, available, null)
        }
      }
      .getOrElse { StorageInfoSnapshot(null, null, null, null) }
  }

  private fun readBatteryInfo(): BatteryInfoSnapshot {
    return runCatching {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

        val levelFromIntent =
          intent?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) ((level * 100f) / scale).roundToInt() else null
          }

        val levelPercent =
          levelFromIntent
            ?: batteryManager
              ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
              ?.takeIf { it in 0..100 }

        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val tempCelsius =
          if (tempTenths != null && tempTenths != Int.MIN_VALUE && tempTenths > -500 && tempTenths < 1500) {
            tempTenths / 10f
          } else {
            null
          }

        val statusInt = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val statusLabel =
          when (statusInt) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> UNAVAILABLE
          }

        val pluggedInt = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val pluggedLabel =
          when (pluggedInt) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Adapter"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "Dock"
            else -> "Battery"
          }

        BatteryInfoSnapshot(
          levelPercent = levelPercent,
          temperatureCelsius = tempCelsius,
          statusLabel = statusLabel,
          pluggedLabel = pluggedLabel,
          isCharging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING,
        )
      }
      .getOrElse {
        BatteryInfoSnapshot(
          levelPercent = null,
          temperatureCelsius = null,
          statusLabel = UNAVAILABLE,
          pluggedLabel = UNAVAILABLE,
          isCharging = false,
        )
      }
  }

  companion object {
    const val UNAVAILABLE = "N/A"
  }
}
