package com.example.feature.cpu

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dlssgamespace.core.format.TelemetryFormatters
import com.dlssgamespace.core.hardware.CpuClusterInfo
import com.dlssgamespace.core.hardware.CpuProfilePreset
import com.dlssgamespace.core.hardware.CpuSnapshot
import com.dlssgamespace.core.root.RootEnvironmentInfo
import com.dlssgamespace.uicomponents.components.DlssCard
import com.dlssgamespace.uicomponents.components.SectionHeader
import com.dlssgamespace.uicomponents.components.StatusPill
import com.dlssgamespace.uicomponents.components.TelemetryProgressBar
import com.dlssgamespace.uicomponents.components.TelemetryStatus
import kotlin.math.roundToInt

@Composable
fun CpuControllerScreen(
  snapshot: CpuSnapshot?,
  rootInfo: RootEnvironmentInfo,
  onBack: () -> Unit,
  onApplyPreset: (CpuProfilePreset) -> Unit,
  onSelectGovernor: (CpuClusterInfo, String) -> Unit,
  onSelectMinFreq: (CpuClusterInfo, Long) -> Unit,
  onSelectMaxFreq: (CpuClusterInfo, Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("cpu_controller_screen"),
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
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
          Column {
            Text("CPU Controller", style = MaterialTheme.typography.headlineSmall)
            Text(
              text = snapshot?.hardwareModel ?: "Reading CPU topology...",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        StatusPill(
          text = if (rootInfo.isRootGranted) "WRITABLE" else "READ-ONLY",
          status = if (rootInfo.isRootGranted) TelemetryStatus.NORMAL else TelemetryStatus.WARNING,
        )
      }
    }

    item {
      DlssCard(
        blueAccent = true,
        status = if (snapshot?.isThrottling == true) TelemetryStatus.WARNING else TelemetryStatus.NORMAL,
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Usage: ${snapshot?.usagePercent?.let { "${it.roundToInt()}%" } ?: "N/A"}",
            style = MaterialTheme.typography.headlineMedium,
          )
          StatusPill(
            text = if (snapshot?.isThrottling == true) "THROTTLING" else "NO THROTTLE",
            status = if (snapshot?.isThrottling == true) TelemetryStatus.WARNING else TelemetryStatus.NORMAL,
          )
        }
        Spacer(modifier = Modifier.height(8.dp))
        TelemetryProgressBar(
          progress = snapshot?.usagePercent?.let { it / 100f },
          useBlueAccent = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text =
            "Cores: ${snapshot?.coreCount ?: "?"} · Load Avg: ${snapshot?.loadAvg ?: "N/A"} · Temp: ${TelemetryFormatters.formatTemperatureCelsius(snapshot?.cpuTempCelsius)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (snapshot?.isThrottling == true) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = snapshot.throttlingReason,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
          )
        }
      }
    }

    item {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        SectionHeader(
          title = "CPU Profiles",
          subtitle =
            if (rootInfo.isRootGranted) "Applies validated frequencies & governors"
            else "Grant Root access in Root Manager to unlock kernel writes",
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          CpuProfilePreset.entries.forEach { preset ->
            FilterChip(
              selected = false,
              enabled = rootInfo.isRootGranted,
              onClick = { onApplyPreset(preset) },
              label = { Text(preset.label) },
              modifier = Modifier.testTag("cpu_preset_${preset.id}"),
            )
          }
        }
      }
    }

    val clusters = snapshot?.clusters.orEmpty()
    if (clusters.isEmpty()) {
      item {
        DlssCard(modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
          Text(
            text = "No cpufreq cluster nodes readable on this device/sandbox.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      items(clusters, key = { it.clusterId }) { cluster ->
        CpuClusterCard(
          cluster = cluster,
          isRootGranted = rootInfo.isRootGranted,
          onSelectGovernor = { gov -> onSelectGovernor(cluster, gov) },
          onSelectMinFreq = { f -> onSelectMinFreq(cluster, f) },
          onSelectMaxFreq = { f -> onSelectMaxFreq(cluster, f) },
          modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
        )
      }
    }
  }
}

@Composable
private fun CpuClusterCard(
  cluster: CpuClusterInfo,
  isRootGranted: Boolean,
  onSelectGovernor: (String) -> Unit,
  onSelectMinFreq: (Long) -> Unit,
  onSelectMaxFreq: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  DlssCard(modifier = modifier) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(cluster.name, style = MaterialTheme.typography.titleMedium)
        Text(
          text = "Cores: ${cluster.coreIds.joinToString(",")} · Gov: ${cluster.currentGovernor}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      StatusPill(
        text = cluster.currentFreqKhz?.let { "${it / 1000} MHz" } ?: "N/A",
        status = TelemetryStatus.NORMAL,
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    val hwMax = cluster.hardwareMaxKhz?.toFloat() ?: 1f
    val curFrac = cluster.currentFreqKhz?.let { (it / hwMax).coerceIn(0f, 1f) }
    TelemetryProgressBar(progress = curFrac)

    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text =
        "Scaling Range: ${cluster.scalingMinKhz?.div(1000) ?: "?"} – ${cluster.scalingMaxKhz?.div(1000) ?: "?"} MHz (HW max ${cluster.hardwareMaxKhz?.div(1000) ?: "?"} MHz)",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (cluster.availableGovernors.isNotEmpty()) {
      Spacer(modifier = Modifier.height(10.dp))
      Text("Governor", style = MaterialTheme.typography.labelMedium)
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        cluster.availableGovernors.forEach { gov ->
          FilterChip(
            selected = cluster.currentGovernor == gov,
            enabled = isRootGranted,
            onClick = { onSelectGovernor(gov) },
            label = { Text(gov, style = MaterialTheme.typography.labelSmall) },
          )
        }
      }
    }

    if (cluster.availableFreqsKhz.isNotEmpty()) {
      Spacer(modifier = Modifier.height(8.dp))
      Text("Min Frequency (MHz)", style = MaterialTheme.typography.labelMedium)
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        cluster.availableFreqsKhz.forEach { freq ->
          FilterChip(
            selected = cluster.scalingMinKhz == freq,
            enabled = isRootGranted,
            onClick = { onSelectMinFreq(freq) },
            label = { Text("${freq / 1000}", style = MaterialTheme.typography.labelSmall) },
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))
      Text("Max Frequency (MHz)", style = MaterialTheme.typography.labelMedium)
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        cluster.availableFreqsKhz.forEach { freq ->
          FilterChip(
            selected = cluster.scalingMaxKhz == freq,
            enabled = isRootGranted,
            onClick = { onSelectMaxFreq(freq) },
            label = { Text("${freq / 1000}", style = MaterialTheme.typography.labelSmall) },
          )
        }
      }
    }
  }
}
