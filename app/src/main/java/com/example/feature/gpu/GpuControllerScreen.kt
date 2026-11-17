package com.example.feature.gpu

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
import com.dlssgamespace.core.hardware.GpuProfilePreset
import com.dlssgamespace.core.hardware.GpuSnapshot
import com.dlssgamespace.core.root.RootEnvironmentInfo
import com.dlssgamespace.uicomponents.components.DlssCard
import com.dlssgamespace.uicomponents.components.SectionHeader
import com.dlssgamespace.uicomponents.components.StatusPill
import com.dlssgamespace.uicomponents.components.TelemetryProgressBar
import com.dlssgamespace.uicomponents.components.TelemetryStatus
import kotlin.math.roundToInt

@Composable
fun GpuControllerScreen(
  snapshot: GpuSnapshot?,
  rootInfo: RootEnvironmentInfo,
  onBack: () -> Unit,
  onApplyPreset: (GpuProfilePreset) -> Unit,
  onSelectGovernor: (String) -> Unit,
  onSelectMinClock: (Long) -> Unit,
  onSelectMaxClock: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("gpu_controller_screen"),
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
            Text("GPU Controller", style = MaterialTheme.typography.headlineSmall)
            Text(
              text = snapshot?.gpuModel ?: "Scanning GPU sysfs...",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        StatusPill(
          text =
            when {
              snapshot?.isSupported != true -> "UNSUPPORTED"
              rootInfo.isRootGranted -> "WRITABLE"
              else -> "READ-ONLY"
            },
          status =
            when {
              snapshot?.isSupported != true -> TelemetryStatus.UNKNOWN
              rootInfo.isRootGranted -> TelemetryStatus.NORMAL
              else -> TelemetryStatus.WARNING
            },
        )
      }
    }

    item {
      DlssCard(
        highlighted = true,
        status = if (snapshot?.isThrottling == true) TelemetryStatus.WARNING else TelemetryStatus.NORMAL,
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(
              text = snapshot?.currentFreqMhz?.let { "$it MHz" } ?: "N/A",
              style = MaterialTheme.typography.headlineMedium,
            )
            Text(
              text = "Power State: ${snapshot?.powerState ?: "N/A"}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          StatusPill(
            text = snapshot?.utilizationPercent?.let { "LOAD ${it.roundToInt()}%" } ?: "LOAD N/A",
            status = if (snapshot?.isThrottling == true) TelemetryStatus.WARNING else TelemetryStatus.NORMAL,
          )
        }

        Spacer(modifier = Modifier.height(10.dp))
        TelemetryProgressBar(progress = snapshot?.utilizationPercent?.let { it / 100f })
        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text =
            "Sysfs: ${snapshot?.sysfsRootPath ?: "N/A"}\n" +
              "Range: ${snapshot?.minFreqMhz ?: "?"} – ${snapshot?.maxFreqMhz ?: "?"} MHz · Temp: ${TelemetryFormatters.formatTemperatureCelsius(snapshot?.gpuTempCelsius)}\n" +
              "Throttling: ${if (snapshot?.isThrottling == true) "ACTIVE (PL ${snapshot.thermalPowerLevel})" else "None"} · ${snapshot?.vulkanVersion ?: "N/A"} · ${snapshot?.openGlEsVersion ?: "N/A"}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        SectionHeader(
          title = "GPU Performance Profiles",
          subtitle = "Validated against discovered KGSL / devfreq nodes",
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          GpuProfilePreset.entries.forEach { preset ->
            FilterChip(
              selected = false,
              enabled = rootInfo.isRootGranted && snapshot?.isSupported == true,
              onClick = { onApplyPreset(preset) },
              label = { Text(preset.label) },
            )
          }
        }
      }
    }

    if (snapshot?.availableGovernors?.isNotEmpty() == true) {
      item {
        DlssCard(modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
          Text("GPU Governor (${snapshot.currentGovernor})", style = MaterialTheme.typography.titleMedium)
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            snapshot.availableGovernors.forEach { gov ->
              FilterChip(
                selected = snapshot.currentGovernor == gov,
                enabled = rootInfo.isRootGranted,
                onClick = { onSelectGovernor(gov) },
                label = { Text(gov) },
              )
            }
          }
        }
      }
    }

    if (snapshot?.availableFreqsMhz?.isNotEmpty() == true) {
      item {
        DlssCard(modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
          Text("GPU Min Clock (MHz)", style = MaterialTheme.typography.titleMedium)
          Spacer(modifier = Modifier.height(6.dp))
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            snapshot.availableFreqsMhz.forEach { mhz ->
              FilterChip(
                selected = snapshot.minFreqMhz == mhz,
                enabled = rootInfo.isRootGranted,
                onClick = { onSelectMinClock(mhz) },
                label = { Text("$mhz") },
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))
          Text("GPU Max Clock (MHz)", style = MaterialTheme.typography.titleMedium)
          Spacer(modifier = Modifier.height(6.dp))
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            snapshot.availableFreqsMhz.forEach { mhz ->
              FilterChip(
                selected = snapshot.maxFreqMhz == mhz,
                enabled = rootInfo.isRootGranted,
                onClick = { onSelectMaxClock(mhz) },
                label = { Text("$mhz") },
              )
            }
          }
        }
      }
    }
  }
}
