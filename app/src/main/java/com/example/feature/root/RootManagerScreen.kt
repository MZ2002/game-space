package com.example.feature.root

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
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dlssgamespace.core.root.RootEnvironmentInfo
import com.dlssgamespace.database.entity.RootCommandLogEntity
import com.dlssgamespace.uicomponents.components.DlssCard
import com.dlssgamespace.uicomponents.components.SectionHeader
import com.dlssgamespace.uicomponents.components.StatusPill
import com.dlssgamespace.uicomponents.components.TelemetryStatus

@Composable
fun RootManagerScreen(
  rootInfo: RootEnvironmentInfo,
  recentLogs: List<RootCommandLogEntity>,
  onRequestRoot: () -> Unit,
  onRescanNonInteractive: () -> Unit,
  onOpenEmergencyRestore: () -> Unit,
  onOpenDiagnostics: () -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("root_manager_screen"),
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
        Text("Root Manager", style = MaterialTheme.typography.headlineMedium)
        StatusPill(
          text = rootInfo.accessState.label.uppercase(),
          status = if (rootInfo.isRootGranted) TelemetryStatus.NORMAL else TelemetryStatus.WARNING,
        )
      }
    }

    item {
      DlssCard(
        highlighted = rootInfo.isRootGranted,
        blueAccent = !rootInfo.isRootGranted,
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
      ) {
        Text(
          text = "Root Manager: ${rootInfo.managerType.displayName}",
          style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text =
            "Manager Version: ${rootInfo.managerVersion}\n" +
              "su Binary: ${rootInfo.suBinaryPath}\n" +
              "BusyBox: ${if (rootInfo.busyBoxAvailable) "${rootInfo.busyBoxPath} (${rootInfo.busyBoxVersion})" else "Not Detected"}\n" +
              "Kernel: ${rootInfo.kernelRelease} (${rootInfo.architecture})\n" +
              "SELinux: ${rootInfo.seLinuxMode}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Button(
            onClick = onRequestRoot,
            modifier = Modifier.weight(1f).testTag("root_grant_button"),
          ) {
            Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(" Verify / Request Root")
          }
          OutlinedButton(onClick = onRescanNonInteractive) {
            Text("Detect")
          }
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        DlssCard(
          onClick = onOpenEmergencyRestore,
          status = TelemetryStatus.CRITICAL,
          modifier = Modifier.weight(1f).testTag("open_emergency_restore_card"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Restore,
            contentDescription = "Emergency Restore",
            tint = MaterialTheme.colorScheme.error,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text("Emergency Restore", style = MaterialTheme.typography.titleMedium)
          Text(
            text = "Revert all modified sysfs nodes to stock backup",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        DlssCard(
          onClick = onOpenDiagnostics,
          blueAccent = true,
          modifier = Modifier.weight(1f).testTag("open_diagnostics_card"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Assignment,
            contentDescription = "Logs & Diagnostics",
            tint = MaterialTheme.colorScheme.secondary,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text("Logs & Diagnostics", style = MaterialTheme.typography.titleMedium)
          Text(
            text = "Command audit log & kernel node compatibility",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    item {
      DlssCard(modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
        Text("Root Security Policy", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text =
            "All root writes are filtered through an allowlist of CPU/GPU/Memory/IO sysfs paths, sanitized against shell injection, timed out if stalled, backed up before modification, and verified via kernel read-back. Hardware thermal safety & battery trip points are permanently locked.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item {
      SectionHeader(
        title = "Recent Root Command Log (${recentLogs.size})",
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
      )
    }

    if (recentLogs.isEmpty()) {
      item {
        DlssCard(modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
          Text(
            text = "No root commands executed yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      items(recentLogs, key = { it.id }) { log ->
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
              text = if (log.succeeded) "OK ${log.durationMs}ms" else "ERR ${log.exitCode}",
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
}
