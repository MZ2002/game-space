package com.example.service

import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.dlssgamespace.core.monitoring.ThermalMode
import com.example.DlssApplication
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AutoGameMonitorService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var monitorJob: Job? = null

  private var activeGamePackage: String? = null
  private var activeGameStartMs: Long = 0L
  private var overlayAutoStarted = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    isRunning.value = true
    startForegroundNotification()
    startDetectionLoop()
  }

  override fun onDestroy() {
    super.onDestroy()
    isRunning.value = false
    monitorJob?.cancel()
    serviceScope.launch {
      withContext(NonCancellable) {
        if (activeGamePackage != null) {
          handleGameExited()
        }
      }
      serviceScope.cancel()
    }
  }

  private fun startForegroundNotification() {
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
          CHANNEL_ID,
          getString(R.string.fgs_monitor_channel_name),
          NotificationManager.IMPORTANCE_LOW,
        )
      nm.createNotificationChannel(channel)
    }

    val notification =
      NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("GAME SPACE Auto Detection")
        .setContentText("Watching for launched library games to apply per-game profiles")
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

  private fun startDetectionLoop() {
    val container = (application as DlssApplication).container
    monitorJob =
      serviceScope.launch {
        while (currentCoroutineContext().isActive) {
          val prefs = container.preferencesStore.preferences.first()
          if (!prefs.autoGameDetectionEnabled) {
            if (activeGamePackage != null) handleGameExited()
            delay(3_000L)
            continue
          }

          val foregroundPkg = detectForegroundPackage()
          if (foregroundPkg != null && foregroundPkg != packageName) {
            val games = container.gameLibraryRepository.observeVisibleGames().first()
            val matchedGame = games.firstOrNull { it.packageName == foregroundPkg }

            if (matchedGame != null && activeGamePackage != matchedGame.packageName) {
              if (activeGamePackage != null) {
                handleGameExited()
              }
              handleGameEntered(matchedGame.packageName)
            } else if (matchedGame == null && activeGamePackage != null) {
              handleGameExited()
            }
          }

          delay(2_500L)
        }
      }
  }

  private suspend fun handleGameEntered(pkg: String) {
    val container = (application as DlssApplication).container
    activeGamePackage = pkg
    activeGameStartMs = System.currentTimeMillis()
    detectedActiveGame.value = pkg

    val profile = container.systemControlRepository.getGameProfileOnce(pkg)
    if (profile == null || profile.autoApplyOnLaunch) {
      val perfId = profile?.performanceProfile ?: "BALANCED"
      val thermalId = profile?.thermalMode ?: ThermalMode.BALANCED.id
      container.preferencesStore.setThermalEngineEnabled(true)
      container.preferencesStore.setThermalMode(thermalId)
      container.profileOrchestrator.applyProfile(perfId)

      if (profile?.boostGameProcessPriority != false && container.rootEngine.environment.value.isRootGranted) {
        container.memoryManager.boostGameProcessPriority(pkg, niceValue = -5)
      }
      if (profile?.autoShowOverlay == true && Settings.canDrawOverlays(this) && !FloatingOverlayService.isRunning.value) {
        overlayAutoStarted = true
        FloatingOverlayService.start(this)
      }
    }
  }

  private suspend fun handleGameExited() {
    val pkg = activeGamePackage ?: return
    val started = activeGameStartMs
    activeGamePackage = null
    activeGameStartMs = 0L
    detectedActiveGame.value = null

    val container = (application as DlssApplication).container
    val now = System.currentTimeMillis()
    val duration = (now - started).coerceAtLeast(0L)
    if (duration >= 1_500L) {
      container.gameLibraryRepository.recordCompletedSession(pkg, started, now, duration)
    }

    container.emergencyRestoreManager.restoreAllToOriginal()
    if (overlayAutoStarted) {
      overlayAutoStarted = false
      FloatingOverlayService.stop(this)
    }
  }

  private suspend fun detectForegroundPackage(): String? {
    // Method 1: Android UsageStatsManager (when PACKAGE_USAGE_STATS is granted)
    if (hasUsageStatsPermission(this)) {
      val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
      val end = System.currentTimeMillis()
      val begin = end - 10_000L
      val events = usm?.queryEvents(begin, end)
      var lastPkg: String? = null
      if (events != null) {
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
          events.getNextEvent(event)
          if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
            lastPkg = event.packageName
          }
        }
      }
      if (lastPkg != null) return lastPkg
    }

    // Method 2: Root dumpsys window fallback
    val container = (application as DlssApplication).container
    if (container.rootEngine.environment.value.isRootGranted) {
      val res =
        container.rootEngine.executeAllowlistedCommand(
          category = "TOP_APP_QUERY",
          shellCommand = "dumpsys window displays | grep -E 'mCurrentFocus|mFocusedApp' | head -n 1",
          timeoutMs = 2_000L,
        )
      if (res.succeeded && res.stdout.isNotBlank()) {
        val match = Regex("([a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+)/").find(res.stdout)
        return match?.groupValues?.getOrNull(1)
      }
    }
    return null
  }

  companion object {
    private const val CHANNEL_ID = "game_space_auto_monitor"
    private const val NOTIFICATION_ID = 4102

    val isRunning = MutableStateFlow(false)
    val detectedActiveGame = MutableStateFlow<String?>(null)

    fun hasUsageStatsPermission(context: Context): Boolean {
      val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
      val mode =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
          )
        } else {
          @Suppress("DEPRECATION")
          appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
          )
        }
      return mode == AppOpsManager.MODE_ALLOWED
    }

    fun start(context: Context) {
      val intent = Intent(context, AutoGameMonitorService::class.java)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, AutoGameMonitorService::class.java))
    }
  }
}
