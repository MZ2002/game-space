package com.example.feature.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dlssgamespace.core.format.TelemetryFormatters
import com.dlssgamespace.core.hardware.BlockIoInfo
import com.dlssgamespace.core.hardware.DisplayTouchSnapshot
import com.dlssgamespace.core.hardware.StorageIoSnapshot
import com.dlssgamespace.core.root.RootEnvironmentInfo
import com.dlssgamespace.core.system.GameSpacePreferences
import com.dlssgamespace.uicomponents.components.DlssCard
import com.dlssgamespace.uicomponents.components.SectionHeader
import com.dlssgamespace.uicomponents.components.StatusPill
import com.dlssgamespace.uicomponents.components.TelemetryStatus
import com.example.service.AutoGameMonitorService

@Composable
fun AdvancedSettingsScreen(
  preferences: GameSpacePreferences,
  storageIo: StorageIoSnapshot?,
  displayTouch: DisplayTouchSnapshot?,
  rootInfo: RootEnvironmentInfo,
  isAutoMonitorRunning: Boolean,
  onSelectPollingInterval: (Long) -> Unit,
  onSelectPreferredRefreshRate: (Int) -> Unit,
  onToggleAutoGameDetection: (Boolean) -> Unit,
  onSelectIoScheduler: (BlockIoInfo, String) -> Unit,
  onSelectReadAhead: (BlockIoInfo, Int) -> Unit,
  onOpenAbout: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var hasUsageAccess by remember {
    mutableStateOf(AutoGameMonitorService.hasUsageStatsPermission(context))
  }
  var hasNotificationPermission by remember {
    mutableStateOf(
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
    )
  }

  val notifLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasNotificationPermission = granted
    }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        hasUsageAccess = AutoGameMonitorService.hasUsageStatsPermission(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("advanced_settings_screen"),
    contentPadding = PaddingValues(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Advanced Settings", style = MaterialTheme.typography.headlineMedium)
        StatusPill(
          text = if (rootInfo.isRootGranted) "ROOT" else "LIMITED",
          status = if (rootInfo.isRootGranted) TelemetryStatus.NORMAL else TelemetryStatus.WARNING,
        )
      }
    }

    // Auto Game Detection Service
    item {
      DlssCard(
        highlighted = isAutoMonitorRunning,
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Auto Game Detection Service", style = MaterialTheme.typography.titleMedium)
            Text(
              text =
                "Detects foreground library games via UsageStats or Root, applies per-game profiles, and restores original settings on exit.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = preferences.autoGameDetectionEnabled,
            onCheckedChange = onToggleAutoGameDetection,
            modifier = Modifier.testTag("auto_game_detection_switch"),
          )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (!hasUsageAccess) {
            Button(
              onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
              modifier = Modifier.weight(1f),
            ) {
              Text("Grant Usage Access")
            }
          } else {
            StatusPill(text = "USAGE ACCESS OK", status = TelemetryStatus.NORMAL)
          }
          if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OutlinedButton(
              onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            ) {
              Text("Allow Notifications")
            }
          }
        }
      }
    }

    // Monitoring Interval
    item {
      DlssCard(blueAccent = true, modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
        Text("Telemetry Polling Interval", style = MaterialTheme.typography.titleMedium)
        Text(
          text = "Longer intervals reduce CPU wakeups and save battery.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf(1_000L to "1.0s", 2_500L to "2.5s", 5_000L to "5.0s", 10_000L to "10.0s").forEach {
            (ms, label) ->
            FilterChip(
              selected = preferences.pollingIntervalMs == ms,
              onClick = { onSelectPollingInterval(ms) },
              label = { Text(label) },
            )
          }
        }
      }
    }

    // Display & Touch Optimization
    item {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        SectionHeader(title = "Display & Touch Inspector", useBlueAccent = true)
        DlssCard(modifier = Modifier.fillMaxWidth()) {
          Text(
            text =
              "Panel: ${displayTouch?.resolutionLabel ?: "N/A"} @ ${displayTouch?.currentRefreshRateHz ?: "?"} Hz",
            style = MaterialTheme.typography.titleMedium,
          )
          val rates = displayTouch?.supportedModes?.map { it.refreshRateHz }?.distinct().orEmpty()
          if (rates.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Preferred App Refresh Rate", style = MaterialTheme.typography.labelMedium)
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              FilterChip(
                selected = preferences.preferredRefreshRateHz == 0,
                onClick = { onSelectPreferredRefreshRate(0) },
                label = { Text("Auto") },
              )
              rates.forEach { hz ->
                FilterChip(
                  selected = preferences.preferredRefreshRateHz == hz,
                  onClick = { onSelectPreferredRefreshRate(hz) },
                  label = { Text("${hz}Hz") },
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          val touchDevs = displayTouch?.touchDevices.orEmpty()
          if (touchDevs.isEmpty()) {
            Text(
              text = "Touchscreen device node not reported.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            touchDevs.forEach { td ->
              Text(
                text = "${td.name} (VID:${td.vendorId} PID:${td.productId})\n${td.sysfsTouchSummary}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }

    // Storage & I/O Optimization
    item {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        SectionHeader(title = "Storage & I/O Scheduler")
        DlssCard(modifier = Modifier.fillMaxWidth()) {
          Text(
            text =
              "Free Storage: ${TelemetryFormatters.formatBytes(storageIo?.availableBytes)} / ${TelemetryFormatters.formatBytes(storageIo?.totalBytes)}\n" +
                "Disk Stats: ${storageIo?.diskStatsSummary ?: "N/A"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          val bio = storageIo?.primaryBlockIo
          if (bio == null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Block queue sysfs unsupported on this kernel/sandbox.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "Device: /dev/${bio.deviceName} · Active: ${bio.activeScheduler}",
              style = MaterialTheme.typography.titleSmall,
            )
            if (bio.availableSchedulers.isNotEmpty()) {
              Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                bio.availableSchedulers.forEach { sched ->
                  FilterChip(
                    selected = bio.activeScheduler == sched,
                    enabled = rootInfo.isRootGranted,
                    onClick = { onSelectIoScheduler(bio, sched) },
                    label = { Text(sched) },
                  )
                }
              }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Read Ahead (${bio.readAheadKb?.let { "${it}KB" } ?: "N/A"})",
              style = MaterialTheme.typography.labelMedium,
            )
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              listOf(128, 256, 512, 1024, 2048).forEach { kb ->
                FilterChip(
                  selected = bio.readAheadKb == kb,
                  enabled = rootInfo.isRootGranted,
                  onClick = { onSelectReadAhead(bio, kb) },
                  label = { Text("${kb}KB") },
                )
              }
            }
          }
        }
      }
    }

    // About Game Space entry
    item {
      DlssCard(
        onClick = onOpenAbout,
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp).testTag("open_about_card"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Info,
              contentDescription = "About Game Space",
              tint = MaterialTheme.colorScheme.primary,
            )
            Column {
              Text("About GAME SPACE", style = MaterialTheme.typography.titleMedium)
              Text(
                text = "Compatibility targets, safety guarantees & architecture info",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
          )
        }
      }
    }
  }
}
