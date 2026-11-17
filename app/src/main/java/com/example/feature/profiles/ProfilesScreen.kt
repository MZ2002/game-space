package com.example.feature.profiles

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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dlssgamespace.core.system.GameSpacePreferences
import com.dlssgamespace.database.entity.CustomPerformanceProfileEntity
import com.dlssgamespace.uicomponents.components.DlssCard
import com.dlssgamespace.uicomponents.components.StatusPill
import com.dlssgamespace.uicomponents.components.TelemetryStatus
import kotlin.math.roundToInt

@Composable
fun ProfilesScreen(
  profiles: List<CustomPerformanceProfileEntity>,
  preferences: GameSpacePreferences,
  onBack: () -> Unit,
  onUpdateProfile: (CustomPerformanceProfileEntity) -> Unit,
  onApplyProfile: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("profiles_screen"),
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
        Column {
          Text("Performance Profiles", style = MaterialTheme.typography.headlineSmall)
          Text(
            text = "Customize CPU/GPU/Memory targets for each profile",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    items(profiles, key = { it.profileId }) { profile ->
      val isActive = preferences.activePerformanceProfile == profile.profileId
      DlssCard(
        highlighted = isActive,
        modifier = Modifier.fillMaxWidth().widthIn(max = 900.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(profile.displayName, style = MaterialTheme.typography.titleLarge)
            Text(
              text =
                "Gov: ${profile.cpuGovernorPreference} · Thermal: ${profile.thermalTargetMode}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (isActive) {
            StatusPill(text = "ACTIVE", status = TelemetryStatus.NORMAL)
          }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text("Governor Preference", style = MaterialTheme.typography.labelMedium)
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          listOf("powersave", "schedutil", "walt", "performance").forEach { gov ->
            FilterChip(
              selected = profile.cpuGovernorPreference == gov,
              onClick = { onUpdateProfile(profile.copy(cpuGovernorPreference = gov)) },
              label = { Text(gov, style = MaterialTheme.typography.labelSmall) },
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("CPU Max Freq Target: ${profile.cpuMaxFreqPercent}%", style = MaterialTheme.typography.labelMedium)
        Slider(
          value = profile.cpuMaxFreqPercent.toFloat(),
          onValueChange = { pct ->
            onUpdateProfile(profile.copy(cpuMaxFreqPercent = pct.roundToInt()))
          },
          valueRange = 40f..100f,
        )

        Text("GPU Clock Bias Target: ${profile.gpuBiasPercent}%", style = MaterialTheme.typography.labelMedium)
        Slider(
          value = profile.gpuBiasPercent.toFloat(),
          onValueChange = { pct ->
            onUpdateProfile(profile.copy(gpuBiasPercent = pct.roundToInt()))
          },
          valueRange = 30f..100f,
        )

        Spacer(modifier = Modifier.height(6.dp))
        Button(
          onClick = { onApplyProfile(profile.profileId) },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(if (isActive) "Re-apply ${profile.displayName}" else "Activate ${profile.displayName}")
        }
      }
    }
  }
}
