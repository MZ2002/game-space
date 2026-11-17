package com.example.feature.dashboard

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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dlssgamespace.core.root.RootEnvironmentInfo
import com.dlssgamespace.uicomponents.components.DlssCard
import com.dlssgamespace.uicomponents.components.StatusPill
import com.dlssgamespace.uicomponents.components.TelemetryStatus
import com.dlssgamespace.uicomponents.theme.DlssTheme

@Composable
fun ControlHubScreen(
  rootInfo: RootEnvironmentInfo,
  onOpenCpu: () -> Unit,
  onOpenGpu: () -> Unit,
  onOpenThermal: () -> Unit,
  onOpenMemory: () -> Unit,
  onOpenMonitor: () -> Unit,
  onOpenProfiles: () -> Unit,
  onOpenOverlay: () -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("control_hub_screen"),
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
        Column {
          Text("Control Center", style = MaterialTheme.typography.headlineMedium)
          Text(
            text = "Hardware tuning, thermal safety & real-time telemetry",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        StatusPill(
          text = if (rootInfo.isRootGranted) "ROOT" else "LIMITED",
          status = if (rootInfo.isRootGranted) TelemetryStatus.NORMAL else TelemetryStatus.WARNING,
        )
      }
    }

    item {
      ControlDestinationCard(
        title = "CPU Controller",
        subtitle = "Cluster frequencies, governors, load & thermal throttling",
        icon = Icons.Rounded.DeveloperBoard,
        blueAccent = true,
        tag = "control_card_cpu",
        onClick = onOpenCpu,
      )
    }
    item {
      ControlDestinationCard(
        title = "GPU Controller",
        subtitle = "Adreno KGSL / devfreq clocks, utilization, power state & Vulkan",
        icon = Icons.Rounded.GraphicEq,
        blueAccent = false,
        tag = "control_card_gpu",
        onClick = onOpenGpu,
      )
    }
    item {
      ControlDestinationCard(
        title = "AI Thermal Engine",
        subtitle = "Thermal ON/OFF profile switch, 5 adaptive modes & live thermal chart",
        icon = Icons.Rounded.Thermostat,
        blueAccent = false,
        tag = "control_card_thermal",
        onClick = onOpenThermal,
      )
    }
    item {
      ControlDestinationCard(
        title = "RAM & Memory Manager",
        subtitle = "RAM, zRAM, swap, swappiness, LMK & game process priority",
        icon = Icons.Rounded.Memory,
        blueAccent = true,
        tag = "control_card_memory",
        onClick = onOpenMemory,
      )
    }
    item {
      ControlDestinationCard(
        title = "System Monitor & FPS",
        subtitle = "Real-time charts for FPS, frame time, CPU, GPU, RAM & thermals",
        icon = Icons.Rounded.MonitorHeart,
        blueAccent = true,
        tag = "control_card_monitor",
        onClick = onOpenMonitor,
      )
    }
    item {
      ControlDestinationCard(
        title = "Performance Profiles",
        subtitle = "Customize Battery Saver, Balanced, Performance & Extreme Gaming",
        icon = Icons.Rounded.Tune,
        blueAccent = false,
        tag = "control_card_profiles",
        onClick = onOpenProfiles,
      )
    }
    item {
      ControlDestinationCard(
        title = "Floating Gaming Overlay",
        subtitle = "In-game draggable telemetry HUD, quick switches & opacity",
        icon = Icons.Rounded.Layers,
        blueAccent = true,
        tag = "control_card_overlay",
        onClick = onOpenOverlay,
      )
    }

    item { Spacer(modifier = Modifier.height(8.dp)) }
  }
}

@Composable
private fun ControlDestinationCard(
  title: String,
  subtitle: String,
  icon: ImageVector,
  blueAccent: Boolean,
  tag: String,
  onClick: () -> Unit,
) {
  DlssCard(
    onClick = onClick,
    blueAccent = blueAccent,
    highlighted = !blueAccent,
    modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp).testTag(tag),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint =
          if (blueAccent) DlssTheme.semantic.blueAccent else MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(30.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = "Open $title",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
