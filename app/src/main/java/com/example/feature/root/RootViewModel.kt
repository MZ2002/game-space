package com.example.feature.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dlssgamespace.core.root.RootEngine
import com.dlssgamespace.core.root.RootEnvironmentInfo
import com.dlssgamespace.database.entity.RootCommandLogEntity
import com.dlssgamespace.database.repository.SystemControlRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RootViewModel(
  private val rootEngine: RootEngine,
  controlRepository: SystemControlRepository,
) : ViewModel() {

  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 6)
  val messages: SharedFlow<String> = _messages.asSharedFlow()

  val rootInfo: StateFlow<RootEnvironmentInfo> = rootEngine.environment

  val recentLogs: StateFlow<List<RootCommandLogEntity>> =
    controlRepository.observeRecentRootLogs(limit = 20).stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = emptyList(),
    )

  fun refreshDetection(requestPrompt: Boolean) {
    viewModelScope.launch {
      val info = rootEngine.detectEnvironment(requestRootPrompt = requestPrompt)
      _messages.tryEmit(
        if (info.isRootGranted) "Root verified (${info.managerType.displayName})"
        else "Root state: ${info.accessState.label}"
      )
    }
  }

  class Factory(
    private val rootEngine: RootEngine,
    private val controlRepository: SystemControlRepository,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return RootViewModel(rootEngine, controlRepository) as T
    }
  }
}
