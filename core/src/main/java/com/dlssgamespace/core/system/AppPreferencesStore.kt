package com.dlssgamespace.core.system

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gameSpaceDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "game_space_prefs")

data class GameSpacePreferences(
  val pollingIntervalMs: Long = 2_500L,
  val activePerformanceProfile: String = "BALANCED",
  val thermalEngineEnabled: Boolean = false,
  val thermalMode: String = "BALANCED",
  val autoGameDetectionEnabled: Boolean = false,
  val overlayShowFps: Boolean = true,
  val overlayShowCpu: Boolean = true,
  val overlayShowGpu: Boolean = true,
  val overlayShowRam: Boolean = true,
  val overlayShowTemp: Boolean = true,
  val overlayOpacity: Float = 0.88f,
  val preferredRefreshRateHz: Int = 0,
  val tweakSessionDirty: Boolean = false,
)

class AppPreferencesStore(private val context: Context) {

  private object Keys {
    val POLLING_INTERVAL_MS = longPreferencesKey("polling_interval_ms")
    val ACTIVE_PROFILE = stringPreferencesKey("active_performance_profile")
    val THERMAL_ENABLED = booleanPreferencesKey("thermal_engine_enabled")
    val THERMAL_MODE = stringPreferencesKey("thermal_mode")
    val AUTO_GAME_DETECTION = booleanPreferencesKey("auto_game_detection_enabled")
    val OVERLAY_FPS = booleanPreferencesKey("overlay_show_fps")
    val OVERLAY_CPU = booleanPreferencesKey("overlay_show_cpu")
    val OVERLAY_GPU = booleanPreferencesKey("overlay_show_gpu")
    val OVERLAY_RAM = booleanPreferencesKey("overlay_show_ram")
    val OVERLAY_TEMP = booleanPreferencesKey("overlay_show_temp")
    val OVERLAY_OPACITY = floatPreferencesKey("overlay_opacity")
    val PREFERRED_REFRESH_HZ = intPreferencesKey("preferred_refresh_rate_hz")
    val TWEAK_SESSION_DIRTY = booleanPreferencesKey("tweak_session_dirty")
  }

  val preferences: Flow<GameSpacePreferences> =
    context.gameSpaceDataStore.data.map { prefs ->
      GameSpacePreferences(
        pollingIntervalMs = (prefs[Keys.POLLING_INTERVAL_MS] ?: 2_500L).coerceIn(1_000L, 10_000L),
        activePerformanceProfile = prefs[Keys.ACTIVE_PROFILE] ?: "BALANCED",
        thermalEngineEnabled = prefs[Keys.THERMAL_ENABLED] ?: false,
        thermalMode = prefs[Keys.THERMAL_MODE] ?: "BALANCED",
        autoGameDetectionEnabled = prefs[Keys.AUTO_GAME_DETECTION] ?: false,
        overlayShowFps = prefs[Keys.OVERLAY_FPS] ?: true,
        overlayShowCpu = prefs[Keys.OVERLAY_CPU] ?: true,
        overlayShowGpu = prefs[Keys.OVERLAY_GPU] ?: true,
        overlayShowRam = prefs[Keys.OVERLAY_RAM] ?: true,
        overlayShowTemp = prefs[Keys.OVERLAY_TEMP] ?: true,
        overlayOpacity = (prefs[Keys.OVERLAY_OPACITY] ?: 0.88f).coerceIn(0.35f, 1.0f),
        preferredRefreshRateHz = prefs[Keys.PREFERRED_REFRESH_HZ] ?: 0,
        tweakSessionDirty = prefs[Keys.TWEAK_SESSION_DIRTY] ?: false,
      )
    }

  suspend fun setPollingIntervalMs(intervalMs: Long) {
    context.gameSpaceDataStore.edit {
      it[Keys.POLLING_INTERVAL_MS] = intervalMs.coerceIn(1_000L, 10_000L)
    }
  }

  suspend fun setActivePerformanceProfile(profileId: String) {
    context.gameSpaceDataStore.edit { it[Keys.ACTIVE_PROFILE] = profileId }
  }

  suspend fun setThermalEngineEnabled(enabled: Boolean) {
    context.gameSpaceDataStore.edit { it[Keys.THERMAL_ENABLED] = enabled }
  }

  suspend fun setThermalMode(modeId: String) {
    context.gameSpaceDataStore.edit { it[Keys.THERMAL_MODE] = modeId }
  }

  suspend fun setAutoGameDetectionEnabled(enabled: Boolean) {
    context.gameSpaceDataStore.edit { it[Keys.AUTO_GAME_DETECTION] = enabled }
  }

  suspend fun updateOverlayPrefs(
    showFps: Boolean? = null,
    showCpu: Boolean? = null,
    showGpu: Boolean? = null,
    showRam: Boolean? = null,
    showTemp: Boolean? = null,
    opacity: Float? = null,
  ) {
    context.gameSpaceDataStore.edit { prefs ->
      showFps?.let { prefs[Keys.OVERLAY_FPS] = it }
      showCpu?.let { prefs[Keys.OVERLAY_CPU] = it }
      showGpu?.let { prefs[Keys.OVERLAY_GPU] = it }
      showRam?.let { prefs[Keys.OVERLAY_RAM] = it }
      showTemp?.let { prefs[Keys.OVERLAY_TEMP] = it }
      opacity?.let { prefs[Keys.OVERLAY_OPACITY] = it.coerceIn(0.35f, 1.0f) }
    }
  }

  suspend fun setPreferredRefreshRateHz(hz: Int) {
    context.gameSpaceDataStore.edit { it[Keys.PREFERRED_REFRESH_HZ] = hz.coerceAtLeast(0) }
  }

  suspend fun setTweakSessionDirty(dirty: Boolean) {
    context.gameSpaceDataStore.edit { it[Keys.TWEAK_SESSION_DIRTY] = dirty }
  }
}
