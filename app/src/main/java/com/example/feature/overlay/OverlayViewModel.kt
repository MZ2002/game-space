package com.example.feature.overlay

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dlssgamespace.core.system.AppPreferencesStore
import com.dlssgamespace.core.system.GameSpacePreferences
import com.example.service.FloatingOverlayService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OverlayViewModel(
  private val context: Context,
  private val preferencesStore: AppPreferencesStore,
) : ViewModel() {

  val preferences: StateFlow<GameSpacePreferences> =
    preferencesStore.preferences.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = GameSpacePreferences(),
    )

  val isOverlayRunning: StateFlow<Boolean> = FloatingOverlayService.isRunning

  fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(context)

  fun toggleOverlayService(enable: Boolean) {
    if (enable) {
      if (hasOverlayPermission()) {
        FloatingOverlayService.start(context)
      }
    } else {
      FloatingOverlayService.stop(context)
    }
  }

  fun updatePrefs(
    showFps: Boolean? = null,
    showCpu: Boolean? = null,
    showGpu: Boolean? = null,
    showRam: Boolean? = null,
    showTemp: Boolean? = null,
    opacity: Float? = null,
  ) {
    viewModelScope.launch {
      preferencesStore.updateOverlayPrefs(showFps, showCpu, showGpu, showRam, showTemp, opacity)
    }
  }

  class Factory(
    private val context: Context,
    private val preferencesStore: AppPreferencesStore,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return OverlayViewModel(context, preferencesStore) as T
    }
  }
}
