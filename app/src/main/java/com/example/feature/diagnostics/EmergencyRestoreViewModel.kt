package com.example.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dlssgamespace.core.system.AppPreferencesStore
import com.dlssgamespace.core.system.EmergencyRestoreManager
import com.dlssgamespace.core.system.EmergencyRestoreReport
import com.dlssgamespace.core.system.GameSpacePreferences
import com.dlssgamespace.database.entity.SystemBackupEntity
import com.dlssgamespace.database.repository.SystemControlRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EmergencyRestoreViewModel(
  controlRepository: SystemControlRepository,
  preferencesStore: AppPreferencesStore,
  private val restoreManager: EmergencyRestoreManager,
) : ViewModel() {

  val activeBackups: StateFlow<List<SystemBackupEntity>> =
    controlRepository.observeActiveBackups().stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = emptyList(),
    )

  val preferences: StateFlow<GameSpacePreferences> =
    preferencesStore.preferences.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = GameSpacePreferences(),
    )

  private val _lastReport = MutableStateFlow<EmergencyRestoreReport?>(null)
  val lastReport: StateFlow<EmergencyRestoreReport?> = _lastReport.asStateFlow()

  fun executeEmergencyRestore() {
    viewModelScope.launch {
      _lastReport.value = restoreManager.restoreAllToOriginal()
    }
  }

  class Factory(
    private val controlRepository: SystemControlRepository,
    private val preferencesStore: AppPreferencesStore,
    private val restoreManager: EmergencyRestoreManager,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return EmergencyRestoreViewModel(controlRepository, preferencesStore, restoreManager) as T
    }
  }
}
