package com.example

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dlssgamespace.uicomponents.theme.DlssGameSpaceTheme
import com.example.ui.navigation.DlssGameSpaceApp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )

    val container = (application as DlssApplication).container
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        container.preferencesStore.preferences
          .map { it.preferredRefreshRateHz }
          .distinctUntilChanged()
          .collect { targetHz -> applyPreferredRefreshRate(targetHz) }
      }
    }

    setContent {
      DlssGameSpaceTheme {
        DlssGameSpaceApp(container = container)
      }
    }
  }

  override fun onResume() {
    super.onResume()
    (application as DlssApplication).container.sessionTracker.onHostResumed()
  }

  private fun applyPreferredRefreshRate(targetHz: Int) {
    val activeDisplay: Display? =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display
      } else {
        @Suppress("DEPRECATION") windowManager.defaultDisplay
      }
    val currentMode = activeDisplay?.mode
    val targetModeId =
      if (targetHz <= 0 || activeDisplay == null || currentMode == null) {
        0
      } else {
        activeDisplay.supportedModes
          .firstOrNull { mode ->
            mode.physicalWidth == currentMode.physicalWidth &&
              mode.physicalHeight == currentMode.physicalHeight &&
              mode.refreshRate.roundToInt() == targetHz
          }
          ?.modeId ?: 0
      }
    val params = window.attributes
    if (params.preferredDisplayModeId != targetModeId) {
      params.preferredDisplayModeId = targetModeId
      window.attributes = params
    }
  }
}
