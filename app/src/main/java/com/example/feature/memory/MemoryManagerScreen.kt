package com.example.feature.memory

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
import androidx.compose.material3.Button
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
import com.dlssgamespace.core.hardware.MemoryManagerSnapshot
import com.dlssgamespace.core.root.RootEnvironmentInfo
import com.dlssgamespace.uicomponents.components.DlssCard
import com.dlssgamespace.uicomponents.components.SectionHeader
import com.dlssgamespace.uicomponents.components.StatusPill
import com.dlssgamespace.uicomponents.components.TelemetryProgressBar
import com.dlssgamespace.uicomponents.components.TelemetryStatus

@Composable
fun MemoryManagerScreen(
  snapshot: MemoryManagerSnapshot?,
  rootInfo: RootEnvironmentInfo,
  onBack: () -> Unit,
  onSetSwappiness: (Int) -> Unit,
  onTrimPageCache: () -> Unit,
  onBoostProcess: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("memory_manager_screen"),
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
          Text("RAM & Memory Manager", style = MaterialTheme.typography.headlineSmall)
        }
        StatusPill(
          text = if (rootInfo.isRootGranted) "ROOT" else "READ-ONLY",
          status = if (rootInfo.isRootGranted) TelemetryStatus.NORMAL else TelemetryStatus.WARNING,
        )
      }
    }

    item {
      DlssCard(blueAccent = true, modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
        Text(
          text =
            "RAM: ${TelemetryFormatters.formatBytes(snapshot?.usedRamBytes)} / ${TelemetryFormatters.formatBytes(snapshot?.totalRamBytes)}",
          style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TelemetryProgressBar(progress = snapshot?.ramUsageFraction, useBlueAccent = true)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text =
            "Available: ${TelemetryFormatters.formatBytes(snapshot?.availableRamBytes)} · Cached: ${TelemetryFormatters.formatBytes(snapshot?.cachedBytes)}\n" +
              "Swap: ${TelemetryFormatters.formatBytes(snapshot?.swapUsedBytes)} / ${TelemetryFormatters.formatBytes(snapshot?.swapTotalBytes)}\n" +
              "zRAM: ${TelemetryFormatters.formatBytes(snapshot?.zramDiskSizeBytes)} (${snapshot?.zramAlgorithm ?: "N/A"}) · Compressed: ${TelemetryFormatters.formatBytes(snapshot?.zramComprDataBytes)}\n" +
              "LMK: ${snapshot?.lmkMinFree ?: "N/A"}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item {
      DlssCard(modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
        Text(
          text = "vm.swappiness (${snapshot?.swappiness?.toString() ?: "N/A"})",
          style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf(10, 25, 40, 60, 80, 100).forEach { value ->
            FilterChip(
              selected = snapshot?.swappiness == value,
              enabled = rootInfo.isRootGranted,
              onClick = { onSetSwappiness(value) },
              label = { Text("$value") },
            )
          }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
          onClick = onTrimPageCache,
          enabled = rootInfo.isRootGranted,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Flush Clean Page Cache (Safe Sync)")
        }
      }
    }

    val procs = snapshot?.topProcesses.orEmpty()
    item {
      SectionHeader(
        title = "Running Processes (${procs.size})",
        subtitle = "No system processes are force-killed",
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
      )
    }
    items(procs, key = { "${it.pid}_${it.name}" }) { proc ->
      DlssCard(modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(proc.name, style = MaterialTheme.typography.titleSmall)
            Text(
              text =
                "PID ${proc.pid} · RSS ${proc.rssKb?.let { "${it / 1024} MB" } ?: "N/A"} · Nice ${proc.nice?.toString() ?: "N/A"}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (rootInfo.isRootGranted && proc.name.contains('.')) {
            FilterChip(
              selected = false,
              onClick = { onBoostProcess(proc.name) },
              label = { Text("Nice -5") },
            )
          }
        }
      }
    }
  }
}
