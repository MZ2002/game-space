package com.example.feature.thermal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dlssgamespace.core.monitoring.ThermalEngineController
import com.dlssgamespace.core.monitoring.ThermalMode
import com.dlssgamespace.core.monitoring.ThermalSnapshot
import com.dlssgamespace.core.system.AppPreferencesStore
import com.dlssgamespace.core.system.EmergencyRestoreManager
import com.dlssgamespace.core.system.GameSpacePreferences
import com.dlssgamespace.database.entity.ThermalHistoryEntity
import com.dlssgamespace.database.repository.SystemControlRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ThermalViewModel(
  private val thermalController: ThermalEngineController,
  private val controlRepository: SystemControlRepository,
  private val preferencesStore: AppPreferencesStore,
  private val emergencyRestoreManager: EmergencyRestoreManager,
) : ViewModel() {

  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 6)
  val messages: SharedFlow<String> = _messages.asSharedFlow()

  val preferences: StateFlow<GameSpacePreferences> =
    preferencesStore.preferences.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = GameSpacePreferences(),
    )

  val thermalHistory: StateFlow<List<ThermalHistoryEntity>> =
    controlRepository.observeRecentThermalHistory(limit = 45).stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = emptyList(),
    )

  val snapshot: StateFlow<ThermalSnapshot?> =
    flow {
        while (currentCoroutineContext().isActive) {
          val prefs = preferencesStore.preferences.first()
          emit(
            thermalController.readAndEvaluate(
              engineEnabled = prefs.thermalEngineEnabled,
              mode = ThermalMode.fromId(prefs.thermalMode),
              recordToHistory = true,
            )
          )
          delay(prefs.pollingIntervalMs)
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0L),
        initialValue = null,
      )

  fun setThermalEngineEnabled(enabled: Boolean) {
    viewModelScope.launch {
      preferencesStore.setThermalEngineEnabled(enabled)
      if (!enabled) {
        emergencyRestoreManager.restoreAllToOriginal()
        _messages.tryEmit(
          "Thermal Engine OFF: app thermal overrides restored; stock Android/Qualcomm safety active."
        )
      } else {
        _messages.tryEmit("AI Thermal Engine ON: adaptive rule & history protection enabled.")
      }
    }
  }

  fun selectThermalMode(mode: ThermalMode) {
    viewModelScope.launch {
      preferencesStore.setThermalMode(mode.id)
      _messages.tryEmit("Thermal mode set to ${mode.label}")
    }
  }

  class Factory(
    private val thermalController: ThermalEngineController,
    private val controlRepository: SystemControlRepository,
    private val preferencesStore: AppPreferencesStore,
    private val emergencyRestoreManager: EmergencyRestoreManager,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return ThermalViewModel(
        thermalController,
        controlRepository,
        preferencesStore,
        emergencyRestoreManager,
      )
        as T
    }
  }
}
