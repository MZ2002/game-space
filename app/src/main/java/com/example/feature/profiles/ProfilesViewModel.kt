package com.example.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dlssgamespace.core.system.AppPreferencesStore
import com.dlssgamespace.core.system.GameSpacePreferences
import com.dlssgamespace.core.system.PerformanceProfileOrchestrator
import com.dlssgamespace.database.entity.CustomPerformanceProfileEntity
import com.dlssgamespace.database.repository.SystemControlRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfilesViewModel(
  private val controlRepository: SystemControlRepository,
  private val preferencesStore: AppPreferencesStore,
  private val orchestrator: PerformanceProfileOrchestrator,
) : ViewModel() {

  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 6)
  val messages: SharedFlow<String> = _messages.asSharedFlow()

  val profiles: StateFlow<List<CustomPerformanceProfileEntity>> =
    controlRepository.observeCustomProfiles().stateIn(
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

  init {
    viewModelScope.launch { controlRepository.ensureDefaultProfilesSeeded() }
  }

  fun updateProfile(profile: CustomPerformanceProfileEntity) {
    viewModelScope.launch {
      controlRepository.saveCustomProfile(
        profile.copy(isCustomized = true, updatedAt = System.currentTimeMillis())
      )
      _messages.tryEmit("Saved custom settings for ${profile.displayName}")
    }
  }

  fun applyProfile(profileId: String) {
    viewModelScope.launch {
      val report = orchestrator.applyProfile(profileId)
      _messages.tryEmit(report.message)
    }
  }

  class Factory(
    private val controlRepository: SystemControlRepository,
    private val preferencesStore: AppPreferencesStore,
    private val orchestrator: PerformanceProfileOrchestrator,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return ProfilesViewModel(controlRepository, preferencesStore, orchestrator) as T
    }
  }
}
