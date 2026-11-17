package com.example.feature.cpu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dlssgamespace.core.hardware.CpuClusterInfo
import com.dlssgamespace.core.hardware.CpuController
import com.dlssgamespace.core.hardware.CpuProfilePreset
import com.dlssgamespace.core.hardware.CpuSnapshot
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

class CpuViewModel(
  private val cpuController: CpuController,
  private val rootEngine: RootEngine,
  private val preferencesStore: AppPreferencesStore,
) : ViewModel() {

  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val messages: SharedFlow<String> = _messages.asSharedFlow()

  val rootInfo: StateFlow<RootEnvironmentInfo> = rootEngine.environment

  val snapshot: StateFlow<CpuSnapshot?> =
    flow {
        while (currentCoroutineContext().isActive) {
          emit(cpuController.readSnapshot())
          val interval = preferencesStore.preferences.first().pollingIntervalMs
          delay(interval)
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0L),
        initialValue = null,
      )

  fun applyPreset(preset: CpuProfilePreset) {
    viewModelScope.launch {
      val results = cpuController.applyProfile(preset)
      val ok = results.count { it.succeeded }
      val fail = results.count { !it.succeeded }
      if (ok > 0) preferencesStore.setTweakSessionDirty(true)
      _messages.tryEmit(
        if (results.isEmpty()) "No CPU clusters detected"
        else "CPU ${preset.label}: $ok applied, $fail unsupported/denied"
      )
    }
  }

  fun setGovernor(cluster: CpuClusterInfo, governor: String) {
    viewModelScope.launch {
      val res = cpuController.setClusterGovernor(cluster, governor)
      if (res.succeeded) {
        preferencesStore.setTweakSessionDirty(true)
        _messages.tryEmit("${cluster.name}: governor set to $governor")
      } else {
        _messages.tryEmit(res.stderr)
      }
    }
  }

  fun setMinFreq(cluster: CpuClusterInfo, freqKhz: Long) {
    viewModelScope.launch {
      val res = cpuController.setClusterMinFreq(cluster, freqKhz)
      if (res.succeeded) {
        preferencesStore.setTweakSessionDirty(true)
        _messages.tryEmit("${cluster.name}: min freq set to ${freqKhz / 1000} MHz")
      } else {
        _messages.tryEmit(res.stderr)
      }
    }
  }

  fun setMaxFreq(cluster: CpuClusterInfo, freqKhz: Long) {
    viewModelScope.launch {
      val res = cpuController.setClusterMaxFreq(cluster, freqKhz)
      if (res.succeeded) {
        preferencesStore.setTweakSessionDirty(true)
        _messages.tryEmit("${cluster.name}: max freq set to ${freqKhz / 1000} MHz")
      } else {
        _messages.tryEmit(res.stderr)
      }
    }
  }

  class Factory(
    private val cpuController: CpuController,
    private val rootEngine: RootEngine,
    private val preferencesStore: AppPreferencesStore,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return CpuViewModel(cpuController, rootEngine, preferencesStore) as T
    }
  }
}
