package com.example.feature.dashboard

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dlssgamespace.core.format.TelemetryFormatters
import com.dlssgamespace.core.monitoring.ThermalMode
import com.dlssgamespace.core.monitoring.UnifiedMonitorSnapshot
import com.dlssgamespace.core.root.RootEnvironmentInfo
import com.dlssgamespace.core.system.GameSpacePreferences
import com.dlssgamespace.database.entity.GameEntity
import com.dlssgamespace.uicomponents.components.AppIconTile
import com.dlssgamespace.uicomponents.components.DlssBrandEmblem
import com.dlssgamespace.uicomponents.components.DlssCard
import com.dlssgamespace.uicomponents.components.SectionHeader
import com.dlssgamespace.uicomponents.components.StatusPill
import com.dlssgamespace.uicomponents.components.TelemetryProgressBar
import com.dlssgamespace.uicomponents.components.TelemetryStatus
import com.dlssgamespace.uicomponents.theme.DlssTheme
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
  snapshot: UnifiedMonitorSnapshot?,
  rootInfo: RootEnvironmentInfo,
  preferences: GameSpacePreferences,
  libraryState: DashboardLibraryState,
  appVersionName: String,
  onVerifyRoot: () -> Unit,
  onApplyProfile: (String) -> Unit,
  onEmergencyRestore: () -> Unit,
  onOpenGames: () -> Unit,
  onOpenGameDetails: (String) -> Unit,
  onLaunchGame: (GameEntity) -> Unit,
  onOpenCpu: () -> Unit,
  onOpenGpu: () -> Unit,
  onOpenThermal: () -> Unit,
  onOpenMemory: () -> Unit,
  onOpenRoot: () -> Unit,
  loadIcon: suspend (String) -> Bitmap?,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("home_dashboard"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // 1. Crash / Unrestored Tweaks Safety Banner
    if (preferences.tweakSessionDirty) {
      item {
        DlssCard(
          status = TelemetryStatus.WARNING,
          modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp).testTag("crash_recovery_banner"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.weight(1f),
            ) {
              Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = "Active modified nodes",
                tint = DlssTheme.semantic.warning,
              )
              Column {
                Text(
                  text = "Modified Kernel Settings Active",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                  text = "Restore original CPU/GPU/Memory values anytime for stock safety.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
              onClick = onEmergencyRestore,
              colors =
                ButtonDefaults.buttonColors(containerColor = DlssTheme.semantic.warning),
            ) {
              Icon(
                imageVector = Icons.Rounded.Restore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("Restore", color = MaterialTheme.colorScheme.onTertiary)
            }
          }
        }
      }
    }

    // 2. Hero Branding + Root Manager Banner
    item {
      DlssCard(
        modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
        highlighted = rootInfo.isRootGranted,
        blueAccent = !rootInfo.isRootGranted,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
          ) {
            DlssBrandEmblem(size = 42.dp)
            Column {
              Text(
                text = "GAME SPACE",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
              )
              Text(
                text = "v$appVersionName · ${rootInfo.managerType.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          StatusPill(
            text = if (rootInfo.isRootGranted) "ROOT ACTIVE" else "LIMITED MODE",
            status = if (rootInfo.isRootGranted) TelemetryStatus.NORMAL else TelemetryStatus.WARNING,
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val hw = snapshot?.deviceTelemetry?.hardware
        val disp = snapshot?.deviceTelemetry?.display
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          MetaDatum(
            label = "Device",
            value = hw?.deviceName ?: "N/A",
            modifier = Modifier.weight(1f),
          )
          MetaDatum(
            label = "SoC / Chipset",
            value = hw?.chipset ?: "N/A",
            modifier = Modifier.weight(1f),
          )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          MetaDatum(
            label = "OS & Kernel",
            value =
              if (hw != null) "${hw.androidVersion}\n${rootInfo.kernelRelease}"
              else rootInfo.kernelRelease,
            modifier = Modifier.weight(1f),
          )
          MetaDatum(
            label = "Display",
            value =
              if (disp != null) "${disp.resolutionLabel}\n${disp.refreshRateLabel}"
              else "N/A",
            modifier = Modifier.weight(1f),
          )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (!rootInfo.isRootGranted) {
            Button(
              onClick = onVerifyRoot,
              modifier = Modifier.weight(1f).testTag("verify_root_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Security,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Grant / Verify Root")
            }
          }
          OutlinedButton(
            onClick = onOpenRoot,
            modifier = Modifier.weight(1f).testTag("open_root_manager_button"),
          ) {
            Text(if (rootInfo.isRootGranted) "Root Manager" else "Root Info")
          }
        }
      }
    }

    // 3. Quick Performance Profile Switcher
    item {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        SectionHeader(
          title = "Active Performance Profile",
          subtitle =
            "Thermal: ${
              if (preferences.thermalEngineEnabled) ThermalMode.fromId(preferences.thermalMode).label
              else "OFF (Device Stock Safety)"
            }",
        )

        val profiles =
          listOf(
            "BATTERY_SAVER" to "Battery",
            "BALANCED" to "Balanced",
            "PERFORMANCE" to "Performance",
            "EXTREME_GAMING" to "Extreme",
          )

        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          profiles.forEach { (id, label) ->
            val selected = preferences.activePerformanceProfile == id
            FilterChip(
              selected = selected,
              onClick = { onApplyProfile(id) },
              label = { Text(label, style = MaterialTheme.typography.labelMedium) },
              modifier = Modifier.testTag("quick_profile_$id"),
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                  selectedLabelColor = MaterialTheme.colorScheme.primary,
                ),
            )
          }
        }
      }
    }

    // 4. Live CPU & GPU Control Cards (Tap to open controllers)
    item {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        SectionHeader(
          title = "Hardware Telemetry",
          subtitle = "Live polling while visible (${preferences.pollingIntervalMs}ms)",
          useBlueAccent = true,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          val cpu = snapshot?.cpu
          val cpuPeakMhz = cpu?.clusters?.mapNotNull { it.currentFreqKhz }?.maxOrNull()?.let { "${it / 1000} MHz" } ?: "N/A"
          val cpuUsageText = cpu?.usagePercent?.let { "${it.roundToInt()}%" } ?: "N/A"
          DlssCard(
            onClick = onOpenCpu,
            blueAccent = true,
            status = if (cpu?.isThrottling == true) TelemetryStatus.WARNING else TelemetryStatus.NORMAL,
            modifier = Modifier.weight(1f).testTag("dashboard_cpu_card"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Rounded.DeveloperBoard,
                contentDescription = "CPU Controller",
                tint = DlssTheme.semantic.blueAccent,
                modifier = Modifier.size(20.dp),
              )
              Text(
                text = cpuUsageText,
                style = MaterialTheme.typography.labelMedium,
                color = DlssTheme.semantic.blueBright,
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "CPU (${cpu?.coreCount ?: "?"} Cores)",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              text = "Peak: $cpuPeakMhz",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = "Temp: ${TelemetryFormatters.formatTemperatureCelsius(cpu?.cpuTempCelsius)}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          val gpu = snapshot?.gpu
          val gpuClock = gpu?.currentFreqMhz?.let { "$it MHz" } ?: "N/A"
          val gpuBusy = gpu?.utilizationPercent?.let { "${it.roundToInt()}%" } ?: "N/A"
          DlssCard(
            onClick = onOpenGpu,
            highlighted = true,
            status = if (gpu?.isThrottling == true) TelemetryStatus.WARNING else TelemetryStatus.NORMAL,
            modifier = Modifier.weight(1f).testTag("dashboard_gpu_card"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = "GPU Controller",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
              Text(
                text = gpuBusy,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = gpu?.gpuModel ?: "GPU",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onBackground,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "Clock: $gpuClock",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = "Temp: ${TelemetryFormatters.formatTemperatureCelsius(gpu?.gpuTempCelsius)}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        // RAM & Storage Bars
        val mem = snapshot?.memory
        val ramStatus =
          when {
            mem?.ramUsageFraction == null -> TelemetryStatus.UNKNOWN
            mem.ramUsageFraction!! >= 0.90f -> TelemetryStatus.CRITICAL
            mem.ramUsageFraction!! >= 0.75f -> TelemetryStatus.WARNING
            else -> TelemetryStatus.NORMAL
          }
        DlssCard(
          onClick = onOpenMemory,
          status = ramStatus,
          modifier = Modifier.fillMaxWidth().testTag("ram_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Memory,
                contentDescription = "RAM Manager",
                tint = DlssTheme.semantic.blueAccent,
                modifier = Modifier.size(18.dp),
              )
              Text("RAM & zRAM", style = MaterialTheme.typography.titleMedium)
            }
            Text(
              text =
                if (mem?.usedRamBytes != null && mem.totalRamBytes != null) {
                  "${TelemetryFormatters.formatBytes(mem.usedRamBytes)} / ${TelemetryFormatters.formatBytes(mem.totalRamBytes)} (${TelemetryFormatters.formatPercent(mem.ramUsageFraction)})"
                } else {
                  "N/A"
                },
              style = MaterialTheme.typography.bodySmall,
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          TelemetryProgressBar(
            progress = mem?.ramUsageFraction,
            status = ramStatus,
            useBlueAccent = true,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text =
              "Free: ${TelemetryFormatters.formatBytes(mem?.availableRamBytes)} · zRAM: ${TelemetryFormatters.formatBytes(mem?.zramDiskSizeBytes)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        val storage = snapshot?.deviceTelemetry?.storage
        val storageStatus =
          when {
            storage?.usageFraction == null -> TelemetryStatus.UNKNOWN
            storage.usageFraction!! >= 0.92f -> TelemetryStatus.CRITICAL
            storage.usageFraction!! >= 0.80f -> TelemetryStatus.WARNING
            else -> TelemetryStatus.NORMAL
          }
        DlssCard(
          status = storageStatus,
          modifier = Modifier.fillMaxWidth().testTag("storage_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.SdStorage,
                contentDescription = "Internal storage",
                tint = DlssTheme.semantic.blueAccent,
                modifier = Modifier.size(18.dp),
              )
              Text("Internal Storage", style = MaterialTheme.typography.titleMedium)
            }
            Text(
              text =
                if (storage?.usedBytes != null && storage.totalBytes != null) {
                  "${TelemetryFormatters.formatBytes(storage.usedBytes)} / ${TelemetryFormatters.formatBytes(storage.totalBytes)}"
                } else {
                  "N/A"
                },
              style = MaterialTheme.typography.bodySmall,
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          TelemetryProgressBar(progress = storage?.usageFraction, status = storageStatus)
        }

        // Battery + Thermal Card Row
        val battery = snapshot?.deviceTelemetry?.battery
        val thermal = snapshot?.thermal
        val batTempStatus =
          when {
            battery?.temperatureCelsius == null -> TelemetryStatus.UNKNOWN
            battery.temperatureCelsius!! >= 45f -> TelemetryStatus.CRITICAL
            battery.temperatureCelsius!! >= 40f -> TelemetryStatus.WARNING
            else -> TelemetryStatus.NORMAL
          }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          DlssCard(
            modifier = Modifier.weight(1f).testTag("battery_level_card"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector =
                  if (battery?.isCharging == true) Icons.Rounded.BatteryChargingFull
                  else Icons.Rounded.BatteryStd,
                contentDescription = "Battery level",
                tint = DlssTheme.semantic.healthy,
                modifier = Modifier.size(20.dp),
              )
              StatusPill(text = battery?.statusLabel ?: "N/A")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = battery?.levelPercent?.let { "$it%" } ?: "N/A",
              style = MaterialTheme.typography.headlineMedium,
            )
            Text(
              text = battery?.pluggedLabel ?: "N/A",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          DlssCard(
            onClick = onOpenThermal,
            status = batTempStatus,
            modifier = Modifier.weight(1f).testTag("battery_temp_card"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Rounded.Thermostat,
                contentDescription = "AI Thermal Engine",
                tint =
                  when (batTempStatus) {
                    TelemetryStatus.CRITICAL -> DlssTheme.semantic.critical
                    TelemetryStatus.WARNING -> DlssTheme.semantic.warning
                    else -> MaterialTheme.colorScheme.primary
                  },
                modifier = Modifier.size(20.dp),
              )
              StatusPill(
                text = thermal?.alertLevel?.label?.uppercase() ?: "N/A",
                status = batTempStatus,
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = TelemetryFormatters.formatTemperatureCelsius(battery?.temperatureCelsius),
              style = MaterialTheme.typography.headlineMedium,
            )
            Text(
              text = "Battery · Thermal Engine",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }

    // 5. Library Summary Metrics
    item {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        SectionHeader(
          title = "Game Library Summary",
          trailing = {
            TextButton(
              onClick = onOpenGames,
              modifier = Modifier.testTag("home_open_library_button"),
            ) {
              Text("All Games")
              Spacer(modifier = Modifier.width(4.dp))
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "Open Games",
                modifier = Modifier.size(16.dp),
              )
            }
          },
        )
        val summary = libraryState.summary
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          SummaryTile(Icons.Rounded.SportsEsports, "Games", summary.totalGames.toString(), Modifier.weight(1f))
          SummaryTile(Icons.Rounded.Star, "Favorites", summary.favoriteGames.toString(), Modifier.weight(1f))
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          SummaryTile(Icons.Rounded.History, "Recent", summary.recentlyPlayedCount.toString(), Modifier.weight(1f))
          SummaryTile(
            Icons.Rounded.Timer,
            "Play Time",
            TelemetryFormatters.formatGamingDuration(summary.totalGamingTimeMs),
            Modifier.weight(1f),
          )
        }
      }
    }

    // 6. Recently Played & Favorites
    if (libraryState.recentlyPlayed.isNotEmpty()) {
      item {
        Column(
          modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          SectionHeader(title = "Recently Played")
          LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(libraryState.recentlyPlayed, key = { it.packageName }) { game ->
              DashboardGameCard(
                game = game,
                subtitle = TelemetryFormatters.formatLastPlayed(game.lastPlayedAt),
                loadIcon = loadIcon,
                onOpenDetails = { onOpenGameDetails(game.packageName) },
                onLaunch = { onLaunchGame(game) },
              )
            }
          }
        }
      }
    }

    if (libraryState.favoriteGames.isNotEmpty()) {
      item {
        Column(
          modifier = Modifier.fillMaxWidth().widthIn(max = 920.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          SectionHeader(title = "Favorites")
          LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(libraryState.favoriteGames, key = { it.packageName }) { game ->
              DashboardGameCard(
                game = game,
                subtitle = "v${game.versionName}",
                loadIcon = loadIcon,
                onOpenDetails = { onOpenGameDetails(game.packageName) },
                onLaunch = { onLaunchGame(game) },
              )
            }
          }
        }
      }
    }

    item { Spacer(modifier = Modifier.height(8.dp)) }
  }
}

@Composable
private fun MetaDatum(label: String, value: String, modifier: Modifier = Modifier) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      text = label.uppercase(),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
      color = MaterialTheme.colorScheme.onBackground,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun SummaryTile(
  icon: ImageVector,
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  DlssCard(modifier = modifier) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(text = value, style = MaterialTheme.typography.headlineMedium)
  }
}

@Composable
private fun DashboardGameCard(
  game: GameEntity,
  subtitle: String,
  loadIcon: suspend (String) -> Bitmap?,
  onOpenDetails: () -> Unit,
  onLaunch: () -> Unit,
) {
  DlssCard(
    modifier = Modifier.width(164.dp),
    highlighted = game.isFavorite,
    onClick = onOpenDetails,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AppIconTile(
        packageName = game.packageName,
        contentDescription = "${game.displayName} icon",
        loadIcon = loadIcon,
        size = 44.dp,
        cornerRadius = 12.dp,
      )
      TextButton(
        onClick = onLaunch,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.PlayArrow,
          contentDescription = "Launch ${game.displayName}",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
      }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = game.displayName,
      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = subtitle,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
