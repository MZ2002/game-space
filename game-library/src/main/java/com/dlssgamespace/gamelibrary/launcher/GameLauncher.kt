package com.dlssgamespace.gamelibrary.launcher

import android.content.Context
import android.content.Intent
import com.dlssgamespace.database.repository.GameLibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GameSessionTracker(private val repository: GameLibraryRepository) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  @Volatile private var activePackageName: String? = null
  @Volatile private var launchTimestampMs: Long = 0L

  fun onGameLaunched(packageName: String, nowMs: Long = System.currentTimeMillis()) {
    activePackageName = packageName
    launchTimestampMs = nowMs
    scope.launch { repository.recordGameLaunch(packageName, nowMs) }
  }

  fun onHostResumed(nowMs: Long = System.currentTimeMillis()) {
    val pkg = activePackageName ?: return
    val startedAt = launchTimestampMs
    activePackageName = null
    launchTimestampMs = 0L

    if (startedAt <= 0L) return
    val durationMs = (nowMs - startedAt).coerceAtLeast(0L)
    if (durationMs >= MIN_SESSION_DURATION_MS) {
      scope.launch {
        repository.recordCompletedSession(
          packageName = pkg,
          startedAt = startedAt,
          endedAt = nowMs,
          durationMs = durationMs,
        )
      }
    }
  }

  private companion object {
    const val MIN_SESSION_DURATION_MS = 1_000L
  }
}

class GameLauncher(
  private val context: Context,
  private val sessionTracker: GameSessionTracker,
) {
  fun launchGame(packageName: String): Result<Unit> {
    return runCatching {
      val launchIntent =
        context.packageManager.getLaunchIntentForPackage(packageName)
          ?: throw IllegalStateException("No launchable activity found for $packageName")
      launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(launchIntent)
      sessionTracker.onGameLaunched(packageName)
    }
  }
}
