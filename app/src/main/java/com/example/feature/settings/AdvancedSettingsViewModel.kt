package com.example.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dlssgamespace.core.hardware.BlockIoInfo
import com.dlssgamespace.core.hardware.DisplayTouchManager
import com.dlssgamespace.core.hardware.DisplayTouchSnapshot
import com.dlssgamespace.core.hardware.StorageIoManager
import com.dlssgamespace.core.hardware.StorageIoSnapshot
import com.dlssgamespace.core.root.RootEngine
import com.dlssgamespace.core.root.RootEnvironmentInfo
import com.dlssgamespace.core.system.AppPreferencesStore
import com.dlssgamespace.core.system.GameSpacePreferences
import com.example.service.AutoGameMonitorService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdvancedSettingsViewModel(
  private val context: Context,
  private val preferencesStore: AppPreferencesStore,
  private val storageIoManager: StorageIoManager,
  private val displayTouchManager: DisplayTouchManager,
  private val rootEngine: RootEngine,
) : ViewModel() {

  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 6)
  val messages: SharedFlow<String> = _messages.asSharedFlow()

  private val _storageIo = MutableStateFlow<StorageIoSnapshot?>(null)
  val storageIo: StateFlow<StorageIoSnapshot?> = _storageIo.asStateFlow()

  private val _displayTouch = MutableStateFlow<DisplayTouchSnapshot?>(null)
  val displayTouch: StateFlow<DisplayTouchSnapshot?> = _displayTouch.asStateFlow()

  val rootInfo: StateFlow<RootEnvironmentInfo> = rootEngine.environment

  val preferences: StateFlow<GameSpacePreferences> =
    preferencesStore.preferences.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = GameSpacePreferences(),
    )

  val isAutoMonitorRunning: StateFlow<Boolean> = AutoGameMonitorService.isRunning

  init {
    refreshHardwareDetails()
  }

  fun refreshHardwareDetails() {
    viewModelScope.launch {
      _storageIo.value = storageIoManager.readSnapshot()
      _displayTouch.value = displayTouchManager.readSnapshot()
    }
  }

  fun setPollingInterval(intervalMs: Long) {
    viewModelScope.launch {
      preferencesStore.setPollingIntervalMs(intervalMs)
      _messages.tryEmit("Monitoring interval updated to ${intervalMs}ms")
    }
  }

  fun setPreferredRefreshRate(hz: Int) {
    viewModelScope.launch {
      preferencesStore.setPreferredRefreshRateHz(hz)
      _messages.tryEmit(if (hz == 0) "Display rate set to System Default" else "Preferred refresh rate: ${hz}Hz")
    }
  }

  fun setAutoGameDetection(enabled: Boolean) {
    viewModelScope.launch {
      preferencesStore.setAutoGameDetectionEnabled(enabled)
      if (enabled) {
        AutoGameMonitorService.start(context)
        _messages.tryEmit("Auto Game Detection Service started")
      } else {
        AutoGameMonitorService.stop(context)
        _messages.tryEmit("Auto Game Detection Service stopped")
      }
    }
  }

  fun setIoScheduler(blockIo: BlockIoInfo, scheduler: String) {
    viewModelScope.launch {
      val res = storageIoManager.setIoScheduler(blockIo, scheduler)
      if (res.succeeded) {
        preferencesStore.setTweakSessionDirty(true)
        _messages.tryEmit("${blockIo.deviceName} I/O scheduler set to $scheduler")
        refreshHardwareDetails()
      } else {
        _messages.tryEmit(res.stderr)
      }
    }
  }

  fun setReadAheadKb(blockIo: BlockIoInfo, kb: Int) {
    viewModelScope.launch {
      val res = storageIoManager.setReadAheadKb(blockIo, kb)
      if (res.succeeded) {
        preferencesStore.setTweakSessionDirty(true)
        _messages.tryEmit("${blockIo.deviceName} read_ahead_kb set to ${kb}KB")
        refreshHardwareDetails()
      } else {
        _messages.tryEmit(res.stderr)
      }
    }
  }

  class Factory(
    private val context: Context,
    private val preferencesStore: AppPreferencesStore,
    private val storageIoManager: StorageIoManager,
    private val displayTouchManager: DisplayTouchManager,
    private val rootEngine: RootEngine,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return AdvancedSettingsViewModel(
        context,
        preferencesStore,
        storageIoManager,
        displayTouchManager,
        rootEngine,
      )
        as T
    }
  }
}
