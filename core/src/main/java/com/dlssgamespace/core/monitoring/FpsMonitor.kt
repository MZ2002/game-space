package com.dlssgamespace.core.monitoring

import android.view.Choreographer
import com.dlssgamespace.core.root.RootEngine
import com.dlssgamespace.core.security.RootSecurityValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class FpsMeasurementSource(val badge: String) {
  SURFACE_FLINGER_ROOT("Measured (Root SurfaceFlinger)"),
  CHOREOGRAPHER_LOCAL("Measured (App UI)"),
  UNAVAILABLE("N/A (Root required for external game FPS)"),
}

data class FpsReading(
  val fps: Float?,
  val frameTimeMs: Float?,
  val source: FpsMeasurementSource,
  val targetPackage: String? = null,
)

class FpsMonitor(private val rootEngine: RootEngine) {

  /**
   * Measures real local UI frame pacing via Android Choreographer while collected.
   */
  fun observeLocalUiFps(): Flow<FpsReading> = callbackFlow {
    var frameCount = 0
    var windowStartNs = System.nanoTime()
    var lastFrameNs = windowStartNs
    var active = true

    val callback =
      object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
          if (!active) return
          frameCount++
          val frameDeltaNs = (frameTimeNanos - lastFrameNs).coerceAtLeast(1L)
          lastFrameNs = frameTimeNanos

          val elapsedNs = frameTimeNanos - windowStartNs
          if (elapsedNs >= 1_000_000_000L) {
            val fps = ((frameCount * 1_000_000_000.0) / elapsedNs.toDouble()).toFloat()
            val frameMs = (frameDeltaNs / 1_000_000.0).toFloat()
            trySend(
              FpsReading(
                fps = (fps * 10f).roundToInt() / 10f,
                frameTimeMs = (frameMs * 10f).roundToInt() / 10f,
                source = FpsMeasurementSource.CHOREOGRAPHER_LOCAL,
              )
            )
            frameCount = 0
            windowStartNs = frameTimeNanos
          }
          Choreographer.getInstance().postFrameCallback(this)
        }
      }

    Choreographer.getInstance().postFrameCallback(callback)
    awaitClose {
      active = false
      Choreographer.getInstance().removeFrameCallback(callback)
    }
  }

  /**
   * Queries real frame timing for an external foreground package when Root is granted.
   * Returns UNAVAILABLE without faking numbers if root is missing or SurfaceFlinger yields no timestamps.
   */
  suspend fun queryExternalPackageFps(packageName: String): FpsReading =
    withContext(Dispatchers.IO) {
      if (!rootEngine.environment.value.isRootGranted || !RootSecurityValidator.validatePackageName(packageName)) {
        return@withContext FpsReading(
          fps = null,
          frameTimeMs = null,
          source = FpsMeasurementSource.UNAVAILABLE,
          targetPackage = packageName,
        )
      }

      val res =
        rootEngine.executeAllowlistedCommand(
          category = "FPS_QUERY",
          shellCommand = "dumpsys SurfaceFlinger --latency '$packageName' 2>/dev/null | tail -n 64",
          timeoutMs = 2_500L,
        )

      if (!res.succeeded || res.stdout.isBlank()) {
        return@withContext FpsReading(
          fps = null,
          frameTimeMs = null,
          source = FpsMeasurementSource.UNAVAILABLE,
          targetPackage = packageName,
        )
      }

      val timestampsNs =
        res.stdout
          .lineSequence()
          .mapNotNull { line ->
            val cols = line.trim().split(Regex("\\s+"))
            cols.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0L && it != Long.MAX_VALUE }
          }
          .toList()

      if (timestampsNs.size < 4) {
        return@withContext FpsReading(
          fps = null,
          frameTimeMs = null,
          source = FpsMeasurementSource.UNAVAILABLE,
          targetPackage = packageName,
        )
      }

      val spanNs = (timestampsNs.last() - timestampsNs.first()).coerceAtLeast(1L)
      val intervals = timestampsNs.size - 1
      val avgFrameNs = spanNs.toDouble() / intervals.toDouble()
      val fps = (1_000_000_000.0 / avgFrameNs).toFloat().coerceIn(1f, 240f)
      val frameTimeMs = (avgFrameNs / 1_000_000.0).toFloat()

      FpsReading(
        fps = (fps * 10f).roundToInt() / 10f,
        frameTimeMs = (frameTimeMs * 10f).roundToInt() / 10f,
        source = FpsMeasurementSource.SURFACE_FLINGER_ROOT,
        targetPackage = packageName,
      )
    }
}
