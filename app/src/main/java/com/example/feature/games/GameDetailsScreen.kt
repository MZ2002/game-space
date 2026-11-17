package com.example.feature.games

import android.graphics.Bitmap
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dlssgamespace.core.format.TelemetryFormatters
import com.dlssgamespace.core.monitoring.ThermalMode
import com.dlssgamespace.uicomponents.components.AppIconTile
import com.dlssgamespace.uicomponents.components.DlssCard
import com.dlssgamespace.uicomponents.components.SectionHeader
import com.dlssgamespace.uicomponents.components.StatusPill
import com.dlssgamespace.uicomponents.components.TelemetryStatus

@Composable
fun GameDetailsScreen(
  uiState: GameDetailsUiState,
  onBack: () -> Unit,
  onLaunch: () -> Unit,
  onToggleFavorite: () -> Unit,
  onSelectPerformanceProfile: (String) -> Unit,
  onSelectThermalMode: (String) -> Unit,
  onSelectRefreshRate: (Int) -> Unit,
  onToggleAutoApply: (Boolean) -> Unit,
  onToggleAutoOverlay: (Boolean) -> Unit,
  onToggleBoostPriority: (Boolean) -> Unit,
  loadIcon: suspend (String) -> Bitmap?,
  modifier: Modifier = Modifier,
) {
  val game = uiState.game
  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("game_details_screen"),
    contentPadding = PaddingValues(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = "Back to Game Library",
            )
          }
          Text("Game Details", style = MaterialTheme.typography.headlineSmall)
        }
        if (game != null) {
          IconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
            Icon(
              imageVector = if (game.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
              contentDescription = "Toggle favorite",
              tint =
                if (game.isFavorite) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    item {
      DlssCard(
        highlighted = true,
        modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          AppIconTile(
            packageName = game?.packageName ?: uiState.profile.packageName,
            contentDescription = "${game?.displayName ?: "Game"} icon",
            loadIcon = loadIcon,
            size = 64.dp,
          )
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
              text = game?.displayName ?: uiState.profile.packageName,
              style = MaterialTheme.typography.headlineSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = uiState.profile.packageName,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              StatusPill(
                text = if (game?.isSystemCategoryGame == true) "GAME" else "MANUAL",
                status = TelemetryStatus.NORMAL,
              )
              Text(
                text =
                  "v${game?.versionName ?: "N/A"} · APK ${TelemetryFormatters.formatBytes(uiState.apkSizeBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "PLAY COUNT",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = "${game?.playCount ?: 0}",
              style = MaterialTheme.typography.titleLarge,
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "TOTAL TIME",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = TelemetryFormatters.formatGamingDuration(game?.totalPlayTimeMs ?: 0L),
              style = MaterialTheme.typography.titleLarge,
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "LAST PLAYED",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = TelemetryFormatters.formatLastPlayed(game?.lastPlayedAt),
              style = MaterialTheme.typography.titleMedium,
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Button(
          onClick = onLaunch,
          modifier = Modifier.fillMaxWidth().testTag("game_details_launch_button"),
        ) {
          Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Launch With Profile")
        }
      }
    }

    item {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        SectionHeader(
          title = "Per-Game Performance Profile",
          subtitle = "Saved by package name without modifying game files",
          useBlueAccent = true,
        )

        DlssCard(blueAccent = true, modifier = Modifier.fillMaxWidth()) {
          Text("Performance Mode", style = MaterialTheme.typography.titleSmall)
          Spacer(modifier = Modifier.height(6.dp))
          val perfModes =
            listOf(
              "BATTERY_SAVER" to "Battery Saver",
              "BALANCED" to "Balanced",
              "PERFORMANCE" to "Performance",
              "EXTREME_GAMING" to "Extreme Gaming",
            )
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            perfModes.forEach { (id, label) ->
              FilterChip(
                selected = uiState.profile.performanceProfile == id,
                onClick = { onSelectPerformanceProfile(id) },
                label = { Text(label) },
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))
          Text("Thermal Mode", style = MaterialTheme.typography.titleSmall)
          Spacer(modifier = Modifier.height(6.dp))
          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ThermalMode.entries.forEach { mode ->
              FilterChip(
                selected = uiState.profile.thermalMode == mode.id,
                onClick = { onSelectThermalMode(mode.id) },
                label = { Text(mode.label) },
              )
            }
          }

          if (uiState.availableRefreshRates.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Preferred Refresh Rate", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              FilterChip(
                selected = uiState.profile.preferredRefreshRateHz == 0,
                onClick = { onSelectRefreshRate(0) },
                label = { Text("System Default") },
              )
              uiState.availableRefreshRates.forEach { hz ->
                FilterChip(
                  selected = uiState.profile.preferredRefreshRateHz == hz,
                  onClick = { onSelectRefreshRate(hz) },
                  label = { Text("${hz}Hz") },
                )
              }
            }
          }
        }
      }
    }

    item {
      DlssCard(modifier = Modifier.fillMaxWidth().widthIn(max = 880.dp)) {
        ProfileToggleRow(
          title = "Auto-Apply Profile on Launch",
          subtitle = "Automatically configures CPU/GPU/Thermal when Auto Game Detection spots this game",
          checked = uiState.profile.autoApplyOnLaunch,
          onCheckedChange = onToggleAutoApply,
        )
        Spacer(modifier = Modifier.height(10.dp))
        ProfileToggleRow(
          title = "Auto-Show Floating Overlay",
          subtitle = "Displays in-game telemetry HUD while playing (requires Overlay permission)",
          checked = uiState.profile.autoShowOverlay,
          onCheckedChange = onToggleAutoOverlay,
        )
        Spacer(modifier = Modifier.height(10.dp))
        ProfileToggleRow(
          title = "Boost Process Priority (Root)",
          subtitle = "Applies safe nice -5 scheduling priority to game process when Root is granted",
          checked = uiState.profile.boostGameProcessPriority,
          onCheckedChange = onToggleBoostPriority,
        )
      }
    }
  }
}

@Composable
private fun ProfileToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(text = title, style = MaterialTheme.typography.titleSmall)
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
