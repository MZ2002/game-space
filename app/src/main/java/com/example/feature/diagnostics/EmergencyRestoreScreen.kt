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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dlssgamespace.core.system.EmergencyRestoreReport
import com.dlssgamespace.core.system.GameSpacePreferences
import com.dlssgamespace.database.entity.SystemBackupEntity
import com.dlssgamespace.uicomponents.components.DlssCard
import com.dlssgamespace.uicomponents.components.SectionHeader
import com.dlssgamespace.uicomponents.components.StatusPill
import com.dlssgamespace.uicomponents.components.TelemetryStatus

@Composable
fun EmergencyRestoreScreen(
  activeBackups: List<SystemBackupEntity>,
  preferences: GameSpacePreferences,
  lastReport: EmergencyRestoreReport?,
  onBack: () -> Unit,
  onExecuteRestore: () -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("emergency_restore_screen"),
    contentPadding = PaddingValues(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Text("Emergency Restore", style = MaterialTheme.typography.headlineSmall)
      }
    }

    item {
      DlssCard(
        status =
          if (activeBackups.isNotEmpty() || preferences.tweakSessionDirty) TelemetryStatus.CRITICAL
          else TelemetryStatus.NORMAL,
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
      ) {
        Text(
          text =
            if (activeBackups.isEmpty()) "System State: Stock / Clean"
            else "${activeBackups.size} Modified Hardware Node(s) Active",
          style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text =
            "Every CPU, GPU, Memory, and I/O node modified by GAME SPACE is backed up with its original value before any write. Tap below to revert all nodes immediately and reset active optimization profiles.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Button(
          onClick = onExecuteRestore,
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.fillMaxWidth().testTag("emergency_restore_execute_button"),
        ) {
          Icon(Icons.Rounded.Restore, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("EMERGENCY RESTORE ALL")
        }
      }
    }

    if (lastReport != null) {
      item {
        DlssCard(
          status =
            if (lastReport.failedCount == 0) TelemetryStatus.NORMAL else TelemetryStatus.WARNING,
          modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
        ) {
          Text(
            text =
              "Restore Result: ${lastReport.restoredCount}/${lastReport.totalBackups} restored (${lastReport.failedCount} failed)",
            style = MaterialTheme.typography.titleSmall,
          )
          lastReport.details.forEach { line ->
            Text(
              text = line,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    item {
      SectionHeader(
        title = "Pending Backed-Up Nodes (${activeBackups.size})",
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
      )
    }

    items(activeBackups, key = { it.nodePath }) { backup ->
      DlssCard(modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          StatusPill(text = backup.category, status = TelemetryStatus.WARNING)
          Text(
            text = "Orig: ${backup.originalValue} -> Now: ${backup.lastAppliedValue}",
            style = MaterialTheme.typography.labelSmall,
          )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = backup.nodePath,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
