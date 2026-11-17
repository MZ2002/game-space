package com.example.feature.monitoring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dlssgamespace.core.monitoring.FpsMonitor
import com.dlssgamespace.core.monitoring.FpsReading
import com.dlssgamespace.core.monitoring.SystemMonitorEngine
import com.dlssgamespace.core.monitoring.UnifiedMonitorSnapshot
import com.dlssgamespace.core.system.AppPreferencesStore
import com.dlssgamespace.core.system.GameSpacePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MonitorRollingBuffers(
  val cpuUsageHistory: List<Float> = emptyList(),
  val gpuUsageHistory: List<Float> = emptyList(),
  val fpsHistory: List<Float> = emptyList(),
  val ramPercentHistory: List<Float> = emptyList(),
  val cpuTempHistory: List<Float> = emptyList(),
  val batteryTempHistory: List<Float> = emptyList(),
)

class SystemMonitorViewModel(
  monitorEngine: SystemMonitorEngine,
  fpsMonitor: FpsMonitor,
  private val preferencesStore: AppPreferencesStore,
) : ViewModel() {

  private val _buffers = MutableStateFlow(MonitorRollingBuffers())
  val buffers: StateFlow<MonitorRollingBuffers> = _buffers.asStateFlow()

  val preferences: StateFlow<GameSpacePreferences> =
    preferencesStore.preferences.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5_000L),
      initialValue = GameSpacePreferences(),
    )

  val snapshot: StateFlow<UnifiedMonitorSnapshot?> =
    monitorEngine
      .observeMonitor(recordThermalHistory = false)
      .onEach { snap ->
        val cur = _buffers.value
        _buffers.value =
          cur.copy(
            cpuUsageHistory = appendLimited(cur.cpuUsageHistory, snap.cpu.usagePercent),
            gpuUsageHistory = appendLimited(cur.gpuUsageHistory, snap.gpu.utilizationPercent),
            ramPercentHistory =
              appendLimited(cur.ramPercentHistory, snap.memory.ramUsageFraction?.times(100f)),
            cpuTempHistory = appendLimited(cur.cpuTempHistory, snap.thermal.cpuTempCelsius),
            batteryTempHistory =
              appendLimited(cur.batteryTempHistory, snap.thermal.batteryTempCelsius),
          )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0L),
        initialValue = null,
      )

  val localFps: StateFlow<FpsReading?> =
    fpsMonitor
      .observeLocalUiFps()
      .onEach { reading ->
        val cur = _buffers.value
        _buffers.value = cur.copy(fpsHistory = appendLimited(cur.fpsHistory, reading.fps))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0L),
        initialValue = null,
      )

  fun setPollingInterval(intervalMs: Long) {
    viewModelScope.launch { preferencesStore.setPollingIntervalMs(intervalMs) }
  }

  private fun appendLimited(list: List<Float>, value: Float?, maxPoints: Int = 36): List<Float> {
    if (value == null) return list
    return (list + value).takeLast(maxPoints)
  }

  class Factory(
    private val monitorEngine: SystemMonitorEngine,
    private val fpsMonitor: FpsMonitor,
    private val preferencesStore: AppPreferencesStore,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return SystemMonitorViewModel(monitorEngine, fpsMonitor, preferencesStore) as T
    }
  }
}
