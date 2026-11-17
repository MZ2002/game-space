package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.dlssgamespace.core.monitoring.ThermalMode
import com.example.DlssApplication
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

class FloatingOverlayService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var monitorJob: Job? = null
  private var windowManager: WindowManager? = null
  private var overlayRoot: LinearLayout? = null
  private var layoutParams: WindowManager.LayoutParams? = null

  private var telemetryView: TextView? = null
  private var headerTitleView: TextView? = null
  private var bodyContainer: LinearLayout? = null
  private var perfSwitchView: TextView? = null
  private var thermalSwitchView: TextView? = null

  private var isCollapsed = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    if (!Settings.canDrawOverlays(this)) {
      stopSelf()
      return
    }
    isRunning.value = true
    startForegroundWithNotification()
    attachOverlayWindow()
    startTelemetryUpdates()
  }

  override fun onDestroy() {
    super.onDestroy()
    isRunning.value = false
    monitorJob?.cancel()
    serviceScope.cancel()
    overlayRoot?.let { root -> runCatching { windowManager?.removeView(root) } }
    overlayRoot = null
  }

  private fun startForegroundWithNotification() {
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
          CHANNEL_ID,
          getString(R.string.fgs_overlay_channel_name),
          NotificationManager.IMPORTANCE_LOW,
        )
      nm.createNotificationChannel(channel)
    }

    val notification: Notification =
      NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("GAME SPACE Overlay Active")
        .setContentText("Monitoring live CPU, GPU, RAM, FPS & Thermals")
        .setSmallIcon(R.mipmap.ic_launcher)
        .setOngoing(true)
        .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun attachOverlayWindow() {
    val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    windowManager = wm

    val params =
      WindowManager.LayoutParams(
          WindowManager.LayoutParams.WRAP_CONTENT,
          WindowManager.LayoutParams.WRAP_CONTENT,
          WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
          PixelFormat.TRANSLUCENT,
        )
        .apply {
          gravity = Gravity.TOP or Gravity.START
          x = 40
          y = 180
        }
    layoutParams = params

    val density = resources.displayMetrics.density
    val pad = (10 * density).roundToInt()

    val root =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad, pad, pad)
        background =
          GradientDrawable().apply {
            setColor(Color.argb(228, 10, 13, 20))
            setStroke((1.5f * density).roundToInt(), Color.rgb(255, 42, 75))
            cornerRadius = 14f * density
          }
      }

    // Draggable header bar
    val headerRow =
      LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
      }

    val title =
      TextView(this).apply {
        text = "GAME SPACE"
        setTextColor(Color.rgb(255, 42, 75))
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, 0, (12 * density).roundToInt(), 0)
      }
    headerTitleView = title

    val hideBtn =
      createOverlayActionBadge("HIDE", Color.rgb(41, 121, 255)) {
        isCollapsed = !isCollapsed
        bodyContainer?.visibility = if (isCollapsed) View.GONE else View.VISIBLE
        title.text = if (isCollapsed) "GS" else "GAME SPACE"
      }

    val closeBtn =
      createOverlayActionBadge("X", Color.rgb(255, 59, 48)) {
        stopSelf()
      }.apply { setPadding((8 * density).roundToInt(), 2, 0, 2) }

    headerRow.addView(title)
    headerRow.addView(hideBtn)
    headerRow.addView(closeBtn)

    // Drag listener on header
    var initialX = 0
    var initialY = 0
    var touchX = 0f
    var touchY = 0f
    headerRow.setOnTouchListener { _, event ->
      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          initialX = params.x
          initialY = params.y
          touchX = event.rawX
          touchY = event.rawY
          true
        }
        MotionEvent.ACTION_MOVE -> {
          params.x = initialX + (event.rawX - touchX).roundToInt()
          params.y = initialY + (event.rawY - touchY).roundToInt()
          runCatching { wm.updateViewLayout(root, params) }
          true
        }
        else -> false
      }
    }

    val body =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, (6 * density).roundToInt(), 0, 0)
      }
    bodyContainer = body

    val metrics =
      TextView(this).apply {
        text = "Reading telemetry..."
        setTextColor(Color.WHITE)
        textSize = 11f
        typeface = Typeface.MONOSPACE
      }
    telemetryView = metrics

    val controlsRow =
      LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, (6 * density).roundToInt(), 0, 0)
      }

    val container = (application as DlssApplication).container
    val perfBtn =
      createOverlayActionBadge("BALANCED", Color.rgb(41, 121, 255)) {
        serviceScope.launch {
          val profiles = listOf("BATTERY_SAVER", "BALANCED", "PERFORMANCE", "EXTREME_GAMING")
          val current = perfSwitchView?.tag as? String ?: "BALANCED"
          val next = profiles[(profiles.indexOf(current) + 1) % profiles.size]
          container.profileOrchestrator.applyProfile(next)
        }
      }
    perfSwitchView = perfBtn

    val thermalBtn =
      createOverlayActionBadge("THM: BAL", Color.rgb(255, 179, 0)) {
        serviceScope.launch {
          val modes = ThermalMode.entries
          val currentId = thermalSwitchView?.tag as? String ?: ThermalMode.BALANCED.id
          val curIdx = modes.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
          val nextMode = modes[(curIdx + 1) % modes.size]
          container.preferencesStore.setThermalEngineEnabled(true)
          container.preferencesStore.setThermalMode(nextMode.id)
        }
      }.apply { setPadding((10 * density).roundToInt(), 2, 0, 2) }
    thermalSwitchView = thermalBtn

    controlsRow.addView(perfBtn)
    controlsRow.addView(thermalBtn)

    body.addView(metrics)
    body.addView(controlsRow)

    root.addView(headerRow)
    root.addView(body)

    overlayRoot = root
    runCatching { wm.addView(root, params) }
  }

  private fun createOverlayActionBadge(
    label: String,
    color: Int,
    onClick: () -> Unit,
  ): TextView {
    return TextView(this).apply {
      text = label
      setTextColor(color)
      textSize = 10f
      typeface = Typeface.DEFAULT_BOLD
      setPadding(6, 2, 6, 2)
      setOnClickListener { onClick() }
    }
  }

  private fun startTelemetryUpdates() {
    val container = (application as DlssApplication).container
    monitorJob =
      serviceScope.launch {
        combine(
            container.systemMonitorEngine.observeMonitor(recordThermalHistory = false),
            container.preferencesStore.preferences,
          ) { snap, prefs ->
            snap to prefs
          }
          .collectLatest { (snap, prefs) ->
            overlayRoot?.alpha = prefs.overlayOpacity
            val lines = mutableListOf<String>()

            if (prefs.overlayShowFps) {
              val fpsText =
                if (container.rootEngine.environment.value.isRootGranted) {
                  "FPS: Root SurfaceFlinger"
                } else {
                  "FPS: N/A (Root req)"
                }
              lines.add(fpsText)
            }
            if (prefs.overlayShowCpu) {
              val cpuPct = snap.cpu.usagePercent?.let { "${it.roundToInt()}%" } ?: "N/A"
              val maxCurMhz =
                snap.cpu.clusters.mapNotNull { it.currentFreqKhz }.maxOrNull()?.let { "${it / 1000}MHz" }
                  ?: "N/A"
              lines.add("CPU: $cpuPct @ $maxCurMhz")
            }
            if (prefs.overlayShowGpu) {
              val gpuPct = snap.gpu.utilizationPercent?.let { "${it.roundToInt()}%" } ?: "N/A"
              val gpuMhz = snap.gpu.currentFreqMhz?.let { "${it}MHz" } ?: "N/A"
              lines.add("GPU: $gpuPct @ $gpuMhz")
            }
            if (prefs.overlayShowRam) {
              val ramPct =
                snap.memory.ramUsageFraction?.let { "${(it * 100f).roundToInt()}%" } ?: "N/A"
              lines.add("RAM: $ramPct")
            }
            if (prefs.overlayShowTemp) {
              val cpuT = snap.thermal.cpuTempCelsius?.let { String.format(Locale.US, "%.0f°C", it) } ?: "N/A"
              val batT =
                snap.thermal.batteryTempCelsius?.let { String.format(Locale.US, "%.1f°C", it) } ?: "N/A"
              lines.add("TMP: CPU $cpuT | BAT $batT")
            }

            telemetryView?.text = lines.joinToString("\n").ifEmpty { "Overlay active" }
            perfSwitchView?.apply {
              tag = prefs.activePerformanceProfile
              text = prefs.activePerformanceProfile.replace("_", " ").take(8)
            }
            thermalSwitchView?.apply {
              tag = prefs.thermalMode
              text =
                if (prefs.thermalEngineEnabled) "THM:${prefs.thermalMode.take(4)}"
                else "THM:OFF"
            }
          }
      }
  }

  companion object {
    private const val CHANNEL_ID = "game_space_overlay"
    private const val NOTIFICATION_ID = 4101
    val isRunning = MutableStateFlow(false)

    fun start(context: Context) {
      val intent = Intent(context, FloatingOverlayService::class.java)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, FloatingOverlayService::class.java))
    }
  }
}
