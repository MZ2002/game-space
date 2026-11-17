package com.example.feature.gpu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dlssgamespace.core.hardware.GpuController
import com.dlssgamespace.core.hardware.GpuProfilePreset
import com.dlssgamespace.core.hardware.GpuSnapshot
import com.dlssgamespace.core.root.RootEngine
import com.dlssgamespace.core.root.RootEnvironmentInfo
import com.dlssgamespace.core.system.AppPreferencesStore
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

class GpuViewModel(
  private val gpuController: GpuController,
  private val rootEngine: RootEngine,
  private val preferencesStore: AppPreferencesStore,
) : ViewModel() {

  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val messages: SharedFlow<String> = _messages.asSharedFlow()

  val rootInfo: StateFlow<RootEnvironmentInfo> = rootEngine.environment

  val snapshot: StateFlow<GpuSnapshot?> =
    flow {
        while (currentCoroutineContext().isActive) {
          emit(gpuController.readSnapshot())
          val interval = preferencesStore.preferences.first().pollingIntervalMs
          delay(interval)
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0L),
        initialValue = null,
      )

  fun applyPreset(preset: GpuProfilePreset) {
    viewModelScope.launch {
      val results = gpuController.applyProfile(preset)
      val ok = results.count { it.succeeded }
      val fail = results.count { !it.succeeded }
      if (ok > 0) preferencesStore.setTweakSessionDirty(true)
      _messages.tryEmit("GPU ${preset.label}: $ok applied, $fail unsupported/denied")
    }
  }

  fun setGovernor(governor: String) {
    viewModelScope.launch {
      val res = gpuController.setGpuGovernor(governor)
      if (res.succeeded) {
        preferencesStore.setTweakSessionDirty(true)
        _messages.tryEmit("GPU governor set to $governor")
      } else {
        _messages.tryEmit(res.stderr)
      }
    }
  }

  fun setMinClock(freqMhz: Long) {
    viewModelScope.launch {
      val res = gpuController.setGpuMinClockMhz(freqMhz)
      if (res.succeeded) {
        preferencesStore.setTweakSessionDirty(true)
        _messages.tryEmit("GPU min clock set to $freqMhz MHz")
      } else {
        _messages.tryEmit(res.stderr)
      }
    }
  }

  fun setMaxClock(freqMhz: Long) {
    viewModelScope.launch {
      val res = gpuController.setGpuMaxClockMhz(freqMhz)
      if (res.succeeded) {
        preferencesStore.setTweakSessionDirty(true)
        _messages.tryEmit("GPU max clock set to $freqMhz MHz")
      } else {
        _messages.tryEmit(res.stderr)
      }
    }
  }

  class Factory(
    private val gpuController: GpuController,
    private val rootEngine: RootEngine,
    private val preferencesStore: AppPreferencesStore,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return GpuViewModel(gpuController, rootEngine, preferencesStore) as T
    }
  }
}
