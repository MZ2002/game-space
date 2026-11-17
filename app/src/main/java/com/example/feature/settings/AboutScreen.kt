package com.example.feature.settings

import android.os.Build
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dlssgamespace.core.root.RootEnvironmentInfo
import com.dlssgamespace.uicomponents.components.DlssBrandEmblem
import com.dlssgamespace.uicomponents.components.DlssCard

@Composable
fun AboutScreen(
  appVersionName: String,
  rootInfo: RootEnvironmentInfo,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("about_screen"),
    contentPadding = PaddingValues(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Text("About GAME SPACE", style = MaterialTheme.typography.headlineSmall)
      }
    }

    item {
      DlssCard(highlighted = true, modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          DlssBrandEmblem(size = 48.dp)
          Column {
            Text("GAME SPACE — ROOT EDITION", style = MaterialTheme.typography.titleLarge)
            Text(
              text = "Version $appVersionName · Android Gaming Control Center",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text =
            "Designed for Qualcomm Snapdragon / Adreno (e.g., Xiaomi 11T Pro vili / Snapdragon 888 / Adreno 660) and ARM64 Android devices running Magisk or KernelSU-Next, with full Limited Mode fallback when Root is unavailable.",
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }

    item {
      DlssCard(blueAccent = true, modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp)) {
        Text("Device & Runtime Environment", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text =
            "Model: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n" +
              "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
              "Root Manager: ${rootInfo.managerType.displayName} (${rootInfo.accessState.label})\n" +
              "Kernel: ${rootInfo.kernelRelease}\n" +
              "Architecture: ${rootInfo.architecture}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item {
      DlssCard(modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp)) {
        Text("Safety & Integrity Guarantees", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text =
            "• Never disables Android, Qualcomm, or vendor thermal safety or battery protection.\n" +
              "• Never alters permanent boot scripts without explicit user action.\n" +
              "• Backs up every original sysfs/procfs value in Room before applying changes.\n" +
              "• Validates all governors, frequencies, and schedulers against kernel-exposed lists.\n" +
              "• Never claims unsupported hardware nodes succeeded or fabricates FPS/telemetry.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
