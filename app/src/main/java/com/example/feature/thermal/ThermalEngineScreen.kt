package com.example.feature.thermal

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
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dlssgamespace.core.format.TelemetryFormatters
import com.dlssgamespace.core.monitoring.ThermalAlertLevel
import com.dlssgamespace.core.monitoring.ThermalMode
import com.dlssgamespace.core.monitoring.ThermalSnapshot
import com.dlssgamespace.core.system.GameSpacePreferences
import com.dlssgamespace.database.entity.ThermalHistoryEntity
import com.dlssgamespace.uicomponents.components.ChartSeries
import com.dlssgamespace.uicomponents.components.DlssCard
import com.dlssgamespace.uicomponents.components.InteractivePerformanceChart
import com.dlssgamespace.uicomponents.components.SectionHeader
import com.dlssgamespace.uicomponents.components.StatusPill
import com.dlssgamespace.uicomponents.components.TelemetryStatus
import com.dlssgamespace.uicomponents.theme.DlssTheme

@Composable
fun ThermalEngineScreen(
  snapshot: ThermalSnapshot?,
  history: List<ThermalHistoryEntity>,
  preferences: GameSpacePreferences,
  onBack: () -> Unit,
  onToggleThermalEngine: (Boolean) -> Unit,
  onSelectThermalMode: (ThermalMode) -> Unit,
  modifier: Modifier = Modifier,
) {
  val alertStatus =
    when (snapshot?.alertLevel) {
      ThermalAlertLevel.CRITICAL -> TelemetryStatus.CRITICAL
      ThermalAlertLevel.HOT, ThermalAlertLevel.WARM -> TelemetryStatus.WARNING
      ThermalAlertLevel.NORMAL -> TelemetryStatus.NORMAL
      null -> TelemetryStatus.UNKNOWN
    }

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("thermal_engine_screen"),
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
          Text("AI Thermal Engine", style = MaterialTheme.typography.headlineSmall)
        }
        StatusPill(text = snapshot?.alertLevel?.label?.uppercase() ?: "N/A", status = alertStatus)
      }
    }

    // Master ON/OFF Switch Card with hardware safety explanation
    item {
      DlssCard(
        highlighted = preferences.thermalEngineEnabled,
        status = alertStatus,
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = if (preferences.thermalEngineEnabled) "Thermal Engine: ON" else "Thermal Engine: OFF",
              style = MaterialTheme.typography.titleLarge,
            )
            Text(
              text =
                "Controls only Game Space's optimization profile. Hardware/Qualcomm thermal safety & battery protection always remain active.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = preferences.thermalEngineEnabled,
            onCheckedChange = onToggleThermalEngine,
            modifier = Modifier.testTag("thermal_master_switch"),
          )
        }

        if (snapshot?.adaptiveActionTaken != null) {
          Spacer(modifier = Modifier.height(8.dp))
          StatusPill(text = snapshot.adaptiveActionTaken!!, status = TelemetryStatus.WARNING)
        }
      }
    }

    // Temperature Summary Grid
    item {
      Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        TempMetricCard("CPU", snapshot?.cpuTempCelsius, Modifier.weight(1f))
        TempMetricCard("GPU", snapshot?.gpuTempCelsius, Modifier.weight(1f))
        TempMetricCard("Battery", snapshot?.batteryTempCelsius, Modifier.weight(1f))
      }
    }

    // 5 Adaptive Modes
    item {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        val activeMode = ThermalMode.fromId(preferences.thermalMode)
        SectionHeader(title = "Thermal Mode", subtitle = activeMode.description)
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          ThermalMode.entries.forEach { mode ->
            FilterChip(
              selected = preferences.thermalMode == mode.id,
              onClick = { onSelectThermalMode(mode) },
              label = { Text(mode.label) },
              modifier = Modifier.testTag("thermal_mode_${mode.id}"),
            )
          }
        }
      }
    }

    // Interactive Thermal History Chart
    item {
      val ordered = history.asReversed()
      val cpuSeries = ordered.mapNotNull { it.cpuTempCelsius }
      val gpuSeries = ordered.mapNotNull { it.gpuTempCelsius }
      val batSeries = ordered.mapNotNull { it.batteryTempCelsius }

      InteractivePerformanceChart(
        title = "Thermal History (°C)",
        seriesList =
          listOf(
            ChartSeries("CPU", cpuSeries, MaterialTheme.colorScheme.primary, "°C"),
            ChartSeries("GPU", gpuSeries, DlssTheme.semantic.blueAccent, "°C"),
            ChartSeries("BAT", batSeries, DlssTheme.semantic.warning, "°C"),
          ),
        minValue = 20f,
        maxValue = 100f,
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
      )
    }

    // Thermal Safety Guarantee Card
    item {
      DlssCard(blueAccent = true, modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Shield,
            contentDescription = "Safety lock",
            tint = DlssTheme.semantic.blueAccent,
          )
          Text(
            text = snapshot?.alertMessage ?: "Monitoring thermal zones...",
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }

    // Thermal Zones List
    val zones = snapshot?.zones.orEmpty()
    if (zones.isNotEmpty()) {
      item {
        SectionHeader(
          title = "Kernel Thermal Zones (${zones.size})",
          modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
          useBlueAccent = true,
        )
      }
      items(zones, key = { it.zoneName }) { z ->
        DlssCard(modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text(z.type, style = MaterialTheme.typography.titleSmall)
              Text(
                z.zoneName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(
              text = TelemetryFormatters.formatTemperatureCelsius(z.tempCelsius),
              style = MaterialTheme.typography.titleMedium,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TempMetricCard(label: String, tempC: Float?, modifier: Modifier = Modifier) {
  DlssCard(modifier = modifier) {
    Text(
      text = label.uppercase(),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = TelemetryFormatters.formatTemperatureCelsius(tempC),
      style = MaterialTheme.typography.titleLarge,
    )
  }
}
