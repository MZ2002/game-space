package com.example.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dlssgamespace.core.hardware.MemoryManager
import com.dlssgamespace.core.hardware.MemoryManagerSnapshot
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

class MemoryViewModel(
  private val memoryManager: MemoryManager,
  private val rootEngine: RootEngine,
  private val preferencesStore: AppPreferencesStore,
) : ViewModel() {

  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 6)
  val messages: SharedFlow<String> = _messages.asSharedFlow()

  val rootInfo: StateFlow<RootEnvironmentInfo> = rootEngine.environment

  val snapshot: StateFlow<MemoryManagerSnapshot?> =
    flow {
        while (currentCoroutineContext().isActive) {
          emit(memoryManager.readSnapshot())
          delay(preferencesStore.preferences.first().pollingIntervalMs)
        }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0L),
        initialValue = null,
      )

  fun setSwappiness(value: Int) {
    viewModelScope.launch {
      val res = memoryManager.setSwappiness(value)
      if (res.succeeded) {
        preferencesStore.setTweakSessionDirty(true)
        _messages.tryEmit("vm.swappiness set to $value")
      } else {
        _messages.tryEmit(res.stderr)
      }
    }
  }

  fun trimPageCache() {
    viewModelScope.launch {
      val res = memoryManager.trimPageCacheSafely()
      _messages.tryEmit(
        if (res.succeeded) "Clean page cache flushed safely (no processes killed)"
        else res.stderr
      )
    }
  }

  fun boostProcessPriority(packageName: String) {
    viewModelScope.launch {
      val res = memoryManager.boostGameProcessPriority(packageName, niceValue = -5)
      _messages.tryEmit(if (res.succeeded) "Prioritized $packageName (${res.stdout})" else res.stderr)
    }
  }

  class Factory(
    private val memoryManager: MemoryManager,
    private val rootEngine: RootEngine,
    private val preferencesStore: AppPreferencesStore,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return MemoryViewModel(memoryManager, rootEngine, preferencesStore) as T
    }
  }
}
