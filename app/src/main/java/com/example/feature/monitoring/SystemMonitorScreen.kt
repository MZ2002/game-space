package com.example.feature.monitoring

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
import com.dlssgamespace.core.monitoring.FpsReading
import com.dlssgamespace.core.monitoring.UnifiedMonitorSnapshot
import com.dlssgamespace.core.system.GameSpacePreferences
import com.dlssgamespace.uicomponents.components.ChartSeries
import com.dlssgamespace.uicomponents.components.DlssCard
import com.dlssgamespace.uicomponents.components.InteractivePerformanceChart
import com.dlssgamespace.uicomponents.components.StatusPill
import com.dlssgamespace.uicomponents.components.TelemetryStatus
import com.dlssgamespace.uicomponents.theme.DlssTheme
import kotlin.math.roundToInt

@Composable
fun SystemMonitorScreen(
  snapshot: UnifiedMonitorSnapshot?,
  localFps: FpsReading?,
  buffers: MonitorRollingBuffers,
  preferences: GameSpacePreferences,
  onBack: () -> Unit,
  onSelectInterval: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("system_monitor_screen"),
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
          Text("System Monitor", style = MaterialTheme.typography.headlineSmall)
        }
        StatusPill(text = "${preferences.pollingIntervalMs}ms", status = TelemetryStatus.NORMAL)
      }
    }

    // Polling interval quick selector
    item {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .widthIn(max = 900.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        listOf(1_000L to "1.0s", 2_500L to "2.5s", 5_000L to "5.0s").forEach { (ms, label) ->
          FilterChip(
            selected = preferences.pollingIntervalMs == ms,
            onClick = { onSelectInterval(ms) },
            label = { Text("Poll $label") },
          )
        }
      }
    }

    // FPS & Frame Time card with explicit source labeling
    item {
      DlssCard(
        highlighted = true,
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(
              text = localFps?.fps?.let { "${it.roundToInt()} FPS" } ?: "Measuring...",
              style = MaterialTheme.typography.headlineMedium,
            )
            Text(
              text =
                "Frame Time: ${localFps?.frameTimeMs?.let { "$it ms" } ?: "N/A"}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          StatusPill(
            text = localFps?.source?.badge ?: "Choreographer",
            status = TelemetryStatus.NORMAL,
          )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text =
            "Honesty note: Choreographer measures this screen's real UI frame pacing. External in-game FPS requires Root SurfaceFlinger query via the Floating Overlay.",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item {
      InteractivePerformanceChart(
        title = "FPS (Local Choreographer)",
        seriesList =
          listOf(
            ChartSeries("FPS", buffers.fpsHistory, DlssTheme.semantic.healthy, " fps")
          ),
        minValue = 0f,
        maxValue = 144f,
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
      )
    }

    item {
      InteractivePerformanceChart(
        title = "CPU, GPU & RAM Utilization (%)",
        seriesList =
          listOf(
            ChartSeries("CPU", buffers.cpuUsageHistory, DlssTheme.semantic.blueAccent, "%"),
            ChartSeries("GPU", buffers.gpuUsageHistory, MaterialTheme.colorScheme.primary, "%"),
            ChartSeries("RAM", buffers.ramPercentHistory, DlssTheme.semantic.healthy, "%"),
          ),
        minValue = 0f,
        maxValue = 100f,
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
      )
    }

    item {
      InteractivePerformanceChart(
        title = "CPU & Battery Temperature (°C)",
        seriesList =
          listOf(
            ChartSeries("CPU", buffers.cpuTempHistory, MaterialTheme.colorScheme.primary, "°C"),
            ChartSeries("BAT", buffers.batteryTempHistory, DlssTheme.semantic.warning, "°C"),
          ),
        minValue = 20f,
        maxValue = 100f,
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
      )
    }

    item {
      val bat = snapshot?.deviceTelemetry?.battery
      val cpuPeak = snapshot?.cpu?.clusters?.mapNotNull { it.currentFreqKhz }?.maxOrNull()?.let { "${it / 1000} MHz" } ?: "N/A"
      val gpuClk = snapshot?.gpu?.currentFreqMhz?.let { "$it MHz" } ?: "N/A"
      DlssCard(modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
        Text(
          text =
            "CPU Peak: $cpuPeak · GPU Clock: $gpuClk\n" +
              "Battery: ${bat?.levelPercent ?: "N/A"}% (${bat?.statusLabel ?: "N/A"}) · ${TelemetryFormatters.formatTemperatureCelsius(bat?.temperatureCelsius)}\n" +
              "Thermal Status: ${snapshot?.thermal?.alertLevel?.label ?: "N/A"}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
