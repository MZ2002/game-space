package com.dlssgamespace.gamelibrary.ui

import android.graphics.Bitmap
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dlssgamespace.core.format.TelemetryFormatters
import com.dlssgamespace.gamelibrary.model.GameSortOption
import com.dlssgamespace.gamelibrary.model.GameViewMode
import com.dlssgamespace.gamelibrary.model.LibraryGameItem
import com.dlssgamespace.uicomponents.components.AppIconTile
import com.dlssgamespace.uicomponents.components.DlssCard
import com.dlssgamespace.uicomponents.components.StatusPill
import com.dlssgamespace.uicomponents.components.TelemetryStatus
import com.dlssgamespace.uicomponents.theme.DlssTheme

@Composable
fun GameLibraryScreen(
  uiState: GameLibraryUiState,
  onSearchQueryChanged: (String) -> Unit,
  onSortOptionSelected: (GameSortOption) -> Unit,
  onFavoritesOnlyToggled: () -> Unit,
  onViewModeToggled: () -> Unit,
  onRescanClicked: () -> Unit,
  onAddGameClicked: () -> Unit,
  onOpenGameDetails: (LibraryGameItem) -> Unit,
  onLaunchGame: (LibraryGameItem) -> Unit,
  onToggleFavorite: (LibraryGameItem) -> Unit,
  onRequestRemove: (LibraryGameItem) -> Unit,
  onConfirmRemove: () -> Unit,
  onCancelRemove: () -> Unit,
  onManualSearchChanged: (String) -> Unit,
  onAddManualCandidate: (com.dlssgamespace.gamelibrary.model.LaunchableAppCandidate) -> Unit,
  onDismissAddSheet: () -> Unit,
  loadIcon: suspend (String) -> Bitmap?,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(
      modifier = Modifier.fillMaxSize().widthIn(max = 960.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Spacer(modifier = Modifier.height(4.dp))

      // Top Header & Actions
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Game Library",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = "${uiState.totalVisibleCount} game(s) in library",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onRescanClicked,
            enabled = !uiState.isScanning,
            modifier = Modifier.size(48.dp).testTag("rescan_games_button"),
          ) {
            if (uiState.isScanning) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
              )
            } else {
              Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Rescan installed games",
                tint = MaterialTheme.colorScheme.onBackground,
              )
            }
          }

          IconButton(
            onClick = onViewModeToggled,
            modifier = Modifier.size(48.dp).testTag("toggle_view_mode_button"),
          ) {
            Icon(
              imageVector =
                if (uiState.viewMode == GameViewMode.GRID) {
                  Icons.AutoMirrored.Rounded.ViewList
                } else {
                  Icons.Rounded.GridView
                },
              contentDescription =
                if (uiState.viewMode == GameViewMode.GRID) "Switch to list view"
                else "Switch to grid view",
              tint = MaterialTheme.colorScheme.onBackground,
            )
          }

          Button(
            onClick = onAddGameClicked,
            modifier = Modifier.padding(start = 4.dp).testTag("add_game_button"),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Add,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = "Add",
              style = MaterialTheme.typography.labelLarge,
              modifier = Modifier.padding(start = 4.dp),
            )
          }
        }
      }

      // Search Bar
      OutlinedTextField(
        value = uiState.searchQuery,
        onValueChange = onSearchQueryChanged,
        modifier = Modifier.fillMaxWidth().testTag("game_search_input"),
        placeholder = { Text("Search games or package names...") },
        leadingIcon = {
          Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
        },
        trailingIcon = {
          if (uiState.searchQuery.isNotEmpty()) {
            IconButton(onClick = { onSearchQueryChanged("") }) {
              Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear search")
            }
          }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
          ),
      )

      // Sort & Filter Chips
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        GameSortOption.entries.forEach { option ->
          val selected = uiState.sortOption == option
          FilterChip(
            selected = selected,
            onClick = { onSortOptionSelected(option) },
            label = {
              Text(text = "Sort: ${option.label}", style = MaterialTheme.typography.labelMedium)
            },
            modifier = Modifier.testTag("sort_chip_${option.name.lowercase()}"),
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.primary,
              ),
          )
        }

        FilterChip(
          selected = uiState.favoritesOnly,
          onClick = onFavoritesOnlyToggled,
          label = { Text("Favorites Only", style = MaterialTheme.typography.labelMedium) },
          leadingIcon = {
            Icon(
              imageVector =
                if (uiState.favoritesOnly) Icons.Rounded.Star else Icons.Rounded.StarOutline,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
          },
          modifier = Modifier.testTag("favorites_filter_chip"),
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.primary,
              selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
            ),
        )
      }

      if (uiState.scanError != null) {
        DlssCard(status = TelemetryStatus.CRITICAL, modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Scan warning: ${uiState.scanError}",
            style = MaterialTheme.typography.bodyMedium,
            color = DlssTheme.semantic.critical,
          )
        }
      }

      // Main Content (Empty / Grid / List)
      if (uiState.games.isEmpty() && !uiState.isScanning) {
        EmptyLibraryState(
          hasSearchOrFilter = uiState.searchQuery.isNotBlank() || uiState.favoritesOnly,
          onAddGameClicked = onAddGameClicked,
          onRescanClicked = onRescanClicked,
        )
      } else if (uiState.viewMode == GameViewMode.GRID) {
        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 160.dp),
          modifier = Modifier.fillMaxSize().testTag("games_grid"),
          contentPadding = PaddingValues(bottom = 24.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(uiState.games, key = { it.packageName }) { game ->
            GameGridCard(
              game = game,
              loadIcon = loadIcon,
              onOpenDetails = { onOpenGameDetails(game) },
              onLaunch = { onLaunchGame(game) },
              onToggleFavorite = { onToggleFavorite(game) },
              onRequestRemove = { onRequestRemove(game) },
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize().testTag("games_list"),
          contentPadding = PaddingValues(bottom = 24.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(uiState.games, key = { it.packageName }) { game ->
            GameListCard(
              game = game,
              loadIcon = loadIcon,
              onOpenDetails = { onOpenGameDetails(game) },
              onLaunch = { onLaunchGame(game) },
              onToggleFavorite = { onToggleFavorite(game) },
              onRequestRemove = { onRequestRemove(game) },
            )
          }
        }
      }
    }
  }

  // Add Game Modal Sheet
  if (uiState.isAddSheetOpen) {
    AddGameModalSheet(
      searchQuery = uiState.manualSearchQuery,
      candidates = uiState.addCandidates,
      onSearchQueryChanged = onManualSearchChanged,
      onAddCandidate = onAddManualCandidate,
      onDismiss = onDismissAddSheet,
      loadIcon = loadIcon,
    )
  }

  // Safe Remove Confirmation Dialog
  val pendingRemove = uiState.pendingRemoveGame
  if (pendingRemove != null) {
    AlertDialog(
      onDismissRequest = onCancelRemove,
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      title = {
        Text(
          text = "Remove from library?",
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onBackground,
        )
      },
      text = {
        Text(
          text =
            "\"${pendingRemove.displayName}\" will only be hidden in DLSS GAME SPACE. It will NOT be uninstalled or modified on your device, and will stay hidden across rescans unless you add it again manually.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
      confirmButton = {
        Button(
          onClick = onConfirmRemove,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = DlssTheme.semantic.critical,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
          modifier = Modifier.testTag("confirm_remove_game_button"),
        ) {
          Text("Remove from Library")
        }
      },
      dismissButton = {
        TextButton(
          onClick = onCancelRemove,
          modifier = Modifier.testTag("cancel_remove_game_button"),
        ) {
          Text("Cancel")
        }
      },
    )
  }
}

@Composable
private fun GameGridCard(
  game: LibraryGameItem,
  loadIcon: suspend (String) -> Bitmap?,
  onOpenDetails: () -> Unit,
  onLaunch: () -> Unit,
  onToggleFavorite: () -> Unit,
  onRequestRemove: () -> Unit,
) {
  DlssCard(
    modifier = Modifier.fillMaxWidth().testTag("game_card_${game.packageName}"),
    highlighted = game.isFavorite,
    onClick = onOpenDetails,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top,
    ) {
      AppIconTile(
        packageName = game.packageName,
        contentDescription = "${game.displayName} icon",
        loadIcon = loadIcon,
        size = 52.dp,
      )

      Row {
        IconButton(
          onClick = onToggleFavorite,
          modifier = Modifier.size(48.dp).testTag("favorite_button_${game.packageName}"),
        ) {
          Icon(
            imageVector = if (game.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
            contentDescription =
              if (game.isFavorite) "Remove ${game.displayName} from favorites"
              else "Favorite ${game.displayName}",
            tint =
              if (game.isFavorite) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(
          onClick = onRequestRemove,
          modifier = Modifier.size(48.dp).testTag("remove_button_${game.packageName}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = "Remove ${game.displayName} from library",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text(
      text = game.displayName,
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.onBackground,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = game.packageName,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )

    Spacer(modifier = Modifier.height(10.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      StatusPill(
        text = if (game.isSystemCategoryGame) "GAME" else "MANUAL",
        status = if (game.isSystemCategoryGame) TelemetryStatus.NORMAL else TelemetryStatus.UNKNOWN,
      )
      Text(
        text = "v${game.versionName}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = TelemetryFormatters.formatLastPlayed(game.lastPlayedAt),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      TextButton(
        onClick = onLaunch,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier.testTag("launch_game_${game.packageName}"),
      ) {
        Icon(
          imageVector = Icons.Rounded.PlayArrow,
          contentDescription = "Launch ${game.displayName}",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = "LAUNCH",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}

@Composable
private fun GameListCard(
  game: LibraryGameItem,
  loadIcon: suspend (String) -> Bitmap?,
  onOpenDetails: () -> Unit,
  onLaunch: () -> Unit,
  onToggleFavorite: () -> Unit,
  onRequestRemove: () -> Unit,
) {
  DlssCard(
    modifier = Modifier.fillMaxWidth().testTag("game_list_card_${game.packageName}"),
    highlighted = game.isFavorite,
    onClick = onOpenDetails,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      AppIconTile(
        packageName = game.packageName,
        contentDescription = "${game.displayName} icon",
        loadIcon = loadIcon,
        size = 52.dp,
      )

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = game.displayName,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          StatusPill(
            text = if (game.isSystemCategoryGame) "GAME" else "MANUAL",
            status =
              if (game.isSystemCategoryGame) TelemetryStatus.NORMAL else TelemetryStatus.UNKNOWN,
          )
        }

        Text(
          text = game.packageName,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "v${game.versionName}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = TelemetryFormatters.formatLastPlayed(game.lastPlayedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (game.totalPlayTimeMs > 0L) {
            Text(
              text = TelemetryFormatters.formatGamingDuration(game.totalPlayTimeMs),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = onLaunch,
          modifier = Modifier.size(48.dp).testTag("launch_game_${game.packageName}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = "Launch ${game.displayName}",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
        IconButton(
          onClick = onToggleFavorite,
          modifier = Modifier.size(48.dp).testTag("favorite_button_${game.packageName}"),
        ) {
          Icon(
            imageVector = if (game.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
            contentDescription =
              if (game.isFavorite) "Remove ${game.displayName} from favorites"
              else "Favorite ${game.displayName}",
            tint =
              if (game.isFavorite) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(
          onClick = onRequestRemove,
          modifier = Modifier.size(48.dp).testTag("remove_button_${game.packageName}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = "Remove ${game.displayName} from library",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun EmptyLibraryState(
  hasSearchOrFilter: Boolean,
  onAddGameClicked: () -> Unit,
  onRescanClicked: () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxSize().padding(vertical = 24.dp),
    contentAlignment = Alignment.TopCenter,
  ) {
    DlssCard(modifier = Modifier.fillMaxWidth().testTag("empty_library_state")) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.SportsEsports,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(44.dp),
        )
        Text(
          text = if (hasSearchOrFilter) "No matching games" else "No games detected yet",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text =
            if (hasSearchOrFilter) {
              "Try clearing your search query or disabling the Favorites filter."
            } else {
              "No installed apps declare ApplicationInfo.CATEGORY_GAME on this device. Tap Add Game to include any installed app manually, or Rescan."
            },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasSearchOrFilter) {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onAddGameClicked) { Text("Add Game") }
            OutlinedButton(onClick = onRescanClicked) { Text("Rescan") }
          }
        }
      }
    }
  }
}
