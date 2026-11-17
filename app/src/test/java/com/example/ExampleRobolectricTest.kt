package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dlssgamespace.core.format.TelemetryFormatters
import com.dlssgamespace.core.telemetry.DeviceTelemetryProvider
import com.dlssgamespace.database.DlssDatabase
import com.dlssgamespace.database.model.ScannedGameMetadata
import com.dlssgamespace.database.repository.GameLibraryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  private lateinit var context: Context
  private lateinit var database: DlssDatabase
  private lateinit var repository: GameLibraryRepository

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    database =
      Room.inMemoryDatabaseBuilder(context, DlssDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    repository = GameLibraryRepository(database)
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun `app name matches GAME SPACE`() {
    val appName = context.getString(R.string.app_name)
    assertEquals("GAME SPACE", appName)
  }

  @Test
  fun `hidden game does not reappear after rescan`() = runTest {
    val scanned =
      listOf(
        ScannedGameMetadata(
          packageName = "com.test.gameone",
          displayName = "Apex Arena",
          versionName = "1.2.0",
          versionCode = 12L,
          isSystemCategoryGame = true,
        ),
        ScannedGameMetadata(
          packageName = "com.test.gametwo",
          displayName = "Drift Racer",
          versionName = "2.0.1",
          versionCode = 20L,
          isSystemCategoryGame = true,
        ),
      )

    repository.syncInstalledApps(scanned, nowMs = 1_000L)
    assertEquals(2, repository.observeVisibleGames().first().size)

    // Hide game one (remove from library)
    repository.hideGame("com.test.gameone")
    val afterHide = repository.observeVisibleGames().first()
    assertEquals(1, afterHide.size)
    assertEquals("com.test.gametwo", afterHide.first().packageName)

    // Rescan with updated metadata — hidden game must stay hidden!
    val rescan =
      listOf(
        scanned[0].copy(versionName = "1.3.0", versionCode = 13L),
        scanned[1],
      )
    repository.syncInstalledApps(rescan, nowMs = 2_000L)

    val afterRescan = repository.observeVisibleGames().first()
    assertEquals(1, afterRescan.size)
    assertFalse(afterRescan.any { it.packageName == "com.test.gameone" })

    // Manual re-add restores it
    repository.addManualGame(rescan[0], nowMs = 3_000L)
    val afterManualAdd = repository.observeVisibleGames().first()
    assertEquals(2, afterManualAdd.size)
    assertTrue(afterManualAdd.any { it.packageName == "com.test.gameone" && it.isManuallyAdded })
  }

  @Test
  fun `telemetry provider reads non-crashing device snapshot`() {
    val provider = DeviceTelemetryProvider(context)
    val snapshot = provider.readSnapshot()
    assertNotEquals("", snapshot.hardware.deviceName)
    assertNotEquals("", snapshot.hardware.androidVersion)
    assertNotEquals("", snapshot.hardware.chipset)
  }

  @Test
  fun `formatters handle unavailable values cleanly`() {
    assertEquals("N/A", TelemetryFormatters.formatBytes(null))
    assertEquals("N/A", TelemetryFormatters.formatPercent(null))
    assertEquals("N/A", TelemetryFormatters.formatTemperatureCelsius(null))
    assertEquals("0m", TelemetryFormatters.formatGamingDuration(0L))
    assertEquals("1h 5m", TelemetryFormatters.formatGamingDuration(65L * 60_000L))
  }
}
