package com.example.feature.overlay

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dlssgamespace.core.system.GameSpacePreferences
import com.dlssgamespace.uicomponents.components.DlssCard
import com.dlssgamespace.uicomponents.components.StatusPill
import com.dlssgamespace.uicomponents.components.TelemetryStatus
import kotlin.math.roundToInt

@Composable
fun OverlaySettingsScreen(
  preferences: GameSpacePreferences,
  isOverlayRunning: Boolean,
  onBack: () -> Unit,
  onToggleOverlay: (Boolean) -> Unit,
  onUpdatePrefs: (
    showFps: Boolean?,
    showCpu: Boolean?,
    showGpu: Boolean?,
    showRam: Boolean?,
    showTemp: Boolean?,
    opacity: Float?,
  ) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var hasPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        hasPermission = Settings.canDrawOverlays(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("overlay_settings_screen"),
    contentPadding = PaddingValues(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
          Text("Floating Gaming Overlay", style = MaterialTheme.typography.headlineSmall)
        }
        StatusPill(
          text = if (isOverlayRunning) "ACTIVE" else "STOPPED",
          status = if (isOverlayRunning) TelemetryStatus.NORMAL else TelemetryStatus.UNKNOWN,
        )
      }
    }

    item {
      DlssCard(
        highlighted = hasPermission,
        status = if (hasPermission) TelemetryStatus.NORMAL else TelemetryStatus.WARNING,
        modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp),
      ) {
        Text(
          text =
            if (hasPermission) "Overlay Permission Granted (SYSTEM_ALERT_WINDOW)"
            else "Overlay Permission Required",
          style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text =
            "Displays a lightweight draggable HUD over games with FPS, CPU/GPU/RAM telemetry, Hide, Close, and Quick Profile/Thermal switches.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (!hasPermission) {
          Button(
            onClick = {
              val intent =
                Intent(
                  Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                  Uri.parse("package:${context.packageName}"),
                )
              context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Grant Display Over Other Apps")
          }
        } else {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Button(
              onClick = { onToggleOverlay(!isOverlayRunning) },
              modifier = Modifier.weight(1f).testTag("toggle_floating_overlay_button"),
            ) {
              Text(if (isOverlayRunning) "Stop Overlay" else "Launch Floating Overlay")
            }
            OutlinedButton(
              onClick = {
                val intent =
                  Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                  )
                context.startActivity(intent)
              }
            ) {
              Text("Permission")
            }
          }
        }
      }
    }

    item {
      DlssCard(blueAccent = true, modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp)) {
        Text("Visible HUD Metrics", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OverlayToggle("FPS Counter", preferences.overlayShowFps) {
          onUpdatePrefs(it, null, null, null, null, null)
        }
        OverlayToggle("CPU Monitoring", preferences.overlayShowCpu) {
          onUpdatePrefs(null, it, null, null, null, null)
        }
        OverlayToggle("GPU Monitoring", preferences.overlayShowGpu) {
          onUpdatePrefs(null, null, it, null, null, null)
        }
        OverlayToggle("RAM Usage", preferences.overlayShowRam) {
          onUpdatePrefs(null, null, null, it, null, null)
        }
        OverlayToggle("Temperature", preferences.overlayShowTemp) {
          onUpdatePrefs(null, null, null, null, it, null)
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = "Opacity: ${(preferences.overlayOpacity * 100f).roundToInt()}%",
          style = MaterialTheme.typography.titleSmall,
        )
        Slider(
          value = preferences.overlayOpacity,
          onValueChange = { onUpdatePrefs(null, null, null, null, null, it) },
          valueRange = 0.35f..1.0f,
        )
      }
    }
  }
}

@Composable
private fun OverlayToggle(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.bodyLarge)
    Switch(checked = checked, onCheckedChange = onChanged)
  }
}
