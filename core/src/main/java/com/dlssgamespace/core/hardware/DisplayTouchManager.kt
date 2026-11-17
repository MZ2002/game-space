package com.dlssgamespace.core.hardware

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.view.Display
import android.view.InputDevice
import com.dlssgamespace.core.root.RootEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

data class DisplayModeOption(
  val modeId: Int,
  val width: Int,
  val height: Int,
  val refreshRateHz: Int,
)

data class TouchDeviceInfo(
  val name: String,
  val vendorId: Int,
  val productId: Int,
  val sysfsTouchControlSupported: Boolean,
  val sysfsTouchSummary: String,
)

data class DisplayTouchSnapshot(
  val resolutionLabel: String,
  val currentRefreshRateHz: Int?,
  val supportedModes: List<DisplayModeOption>,
  val touchDevices: List<TouchDeviceInfo>,
)

class DisplayTouchManager(
  private val context: Context,
  private val rootEngine: RootEngine,
) {

  suspend fun readSnapshot(): DisplayTouchSnapshot =
    withContext(Dispatchers.IO) {
      val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
      val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)

      val currentMode = display?.mode
      val resolution =
        if (currentMode != null && currentMode.physicalWidth > 0) {
          "${currentMode.physicalWidth} × ${currentMode.physicalHeight}"
        } else {
          "N/A"
        }
      val currentHz = (currentMode?.refreshRate ?: display?.refreshRate)?.takeIf { it > 0f }?.roundToInt()

      val modes =
        display
          ?.supportedModes
          ?.map { m ->
            DisplayModeOption(
              modeId = m.modeId,
              width = m.physicalWidth,
              height = m.physicalHeight,
              refreshRateHz = m.refreshRate.roundToInt(),
            )
          }
          ?.distinctBy { "${it.width}x${it.height}@${it.refreshRateHz}" }
          ?.sortedByDescending { it.refreshRateHz }
          .orEmpty()

      val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
      val xiaomiTouchNode =
        listOf("/dev/xiaomi-touch", "/sys/class/touch/touch_dev/bump_sample_rate", "/sys/devices/virtual/touch/touch_dev")
          .firstOrNull { File(it).exists() }

      val touchDevices =
        inputManager
          ?.inputDeviceIds
          ?.toList()
          ?.mapNotNull { id -> inputManager.getInputDevice(id) }
          ?.filter { dev -> (dev.sources and InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN }
          ?.map { dev ->
            TouchDeviceInfo(
              name = dev.name ?: "Touchscreen",
              vendorId = dev.vendorId,
              productId = dev.productId,
              sysfsTouchControlSupported = xiaomiTouchNode != null && rootEngine.environment.value.isRootGranted,
              sysfsTouchSummary =
                if (xiaomiTouchNode != null) {
                  "Kernel touch interface detected ($xiaomiTouchNode)"
                } else {
                  "Unsupported: Kernel does not expose writable touch sampling nodes"
                },
            )
          }
          .orEmpty()

      DisplayTouchSnapshot(
        resolutionLabel = resolution,
        currentRefreshRateHz = currentHz,
        supportedModes = modes,
        touchDevices = touchDevices,
      )
    }
}
