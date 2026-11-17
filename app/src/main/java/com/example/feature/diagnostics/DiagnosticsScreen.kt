package com.example.feature.diagnostics

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dlssgamespace.database.entity.RootCommandLogEntity
import com.dlssgamespace.uicomponents.components.DlssCard
import com.dlssgamespace.uicomponents.components.SectionHeader
import com.dlssgamespace.uicomponents.components.StatusPill
import com.dlssgamespace.uicomponents.components.TelemetryStatus

@Composable
fun DiagnosticsScreen(
  probes: List<DiagnosticProbeItem>,
  logs: List<RootCommandLogEntity>,
  onBack: () -> Unit,
  onRunProbe: () -> Unit,
  onClearLogs: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var filterMode by remember { mutableStateOf("ALL") }
  val filteredLogs =
    remember(logs, filterMode) {
      when (filterMode) {
        "OK" -> logs.filter { it.succeeded }
        "ERR" -> logs.filter { !it.succeeded }
        else -> logs
      }
    }

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("diagnostics_screen"),
    contentPadding = PaddingValues(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp),
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
          Text("Logs & Diagnostics", style = MaterialTheme.typography.headlineSmall)
        }
        IconButton(onClick = onRunProbe, modifier = Modifier.size(48.dp)) {
          Icon(Icons.Rounded.Refresh, contentDescription = "Re-run hardware probe")
        }
      }
    }

    item {
      SectionHeader(
        title = "Kernel & Hardware Compatibility",
        useBlueAccent = true,
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
      )
    }

    items(probes, key = { it.subsystem }) { probe ->
      val status =
        when (probe.status) {
          "PASS" -> TelemetryStatus.NORMAL
          "LIMITED" -> TelemetryStatus.WARNING
          else -> TelemetryStatus.UNKNOWN
        }
      DlssCard(status = status, modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(probe.subsystem, style = MaterialTheme.typography.titleSmall)
            Text(
              text = probe.detail,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          StatusPill(text = probe.status, status = status)
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        SectionHeader(
          title = "Root Audit Log (${filteredLogs.size})",
          modifier = Modifier.weight(1f),
        )
        if (logs.isNotEmpty()) {
          TextButton(onClick = onClearLogs) { Text("Clear") }
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        listOf("ALL" to "All", "OK" to "Success", "ERR" to "Failed").forEach { (id, label) ->
          FilterChip(
            selected = filterMode == id,
            onClick = { filterMode = id },
            label = { Text(label) },
          )
        }
      }
    }

    items(filteredLogs, key = { it.id }) { log ->
      DlssCard(
        status = if (log.succeeded) TelemetryStatus.NORMAL else TelemetryStatus.CRITICAL,
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(log.category, style = MaterialTheme.typography.labelMedium)
          StatusPill(
            text = if (log.succeeded) "OK (${log.durationMs}ms)" else "EXIT ${log.exitCode}",
            status = if (log.succeeded) TelemetryStatus.NORMAL else TelemetryStatus.CRITICAL,
          )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(log.command, style = MaterialTheme.typography.bodySmall)
        Text(
          text = log.outputPreview,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
