package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dlssgamespace.core.hardware.CpuController
import com.dlssgamespace.core.hardware.GpuController
import com.dlssgamespace.core.hardware.MemoryManager
import com.dlssgamespace.core.hardware.StorageIoManager
import com.dlssgamespace.core.monitoring.FpsMeasurementSource
import com.dlssgamespace.core.monitoring.FpsMonitor
import com.dlssgamespace.core.monitoring.ThermalEngineController
import com.dlssgamespace.core.monitoring.ThermalMode
import com.dlssgamespace.core.root.RootAccessState
import com.dlssgamespace.core.root.RootCommandResult
import com.dlssgamespace.core.root.RootEngine
import com.dlssgamespace.core.root.RootManagerType
import com.dlssgamespace.core.root.SuShellRunner
import com.dlssgamespace.core.security.RootSecurityValidator
import com.dlssgamespace.core.system.AppPreferencesStore
import com.dlssgamespace.core.system.EmergencyRestoreManager
import com.dlssgamespace.database.DlssDatabase
import com.dlssgamespace.database.entity.GameProfileEntity
import com.dlssgamespace.database.repository.SystemControlRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RootEditionRobolectricTest {

  private lateinit var context: Context
  private lateinit var database: DlssDatabase
  private lateinit var controlRepository: SystemControlRepository
  private lateinit var preferencesStore: AppPreferencesStore

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    database =
      Room.inMemoryDatabaseBuilder(context, DlssDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    controlRepository = SystemControlRepository(database)
    preferencesStore = AppPreferencesStore(context)
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun `root engine detects Magisk when su shell grants uid 0`() = runTest {
    val fakeRunner =
      object : SuShellRunner {
        override suspend fun runCommand(
          executable: String,
          args: List<String>,
          timeoutMs: Long,
        ): RootCommandResult {
          val cmd = args.joinToString(" ")
          return when {
            cmd == "-c id" ->
              RootCommandResult(cmd, 0, "uid=0(root) gid=0(root)", "", 12L, succeeded = true)
            cmd.contains("magisk -v") ->
              RootCommandResult(cmd, 0, "27.0:MAGISK", "", 8L, succeeded = true)
            cmd.contains("busybox") ->
              RootCommandResult(cmd, 0, "/data/adb/magisk/busybox|BusyBox v1.36.1", "", 8L, succeeded = true)
            cmd == "-c getenforce" ->
              RootCommandResult(cmd, 0, "Enforcing", "", 5L, succeeded = true)
            cmd == "-c uname -r" ->
              RootCommandResult(cmd, 0, "5.4.254-TitanicExtended-vili", "", 5L, succeeded = true)
            else -> RootCommandResult(cmd, 0, "", "", 4L, succeeded = true)
          }
        }
      }

    val engine = RootEngine(controlRepository, fakeRunner)
    val info = engine.detectEnvironment(requestRootPrompt = true)

    assertTrue(info.isRootGranted)
    assertEquals(RootAccessState.GRANTED, info.accessState)
    assertEquals(RootManagerType.MAGISK, info.managerType)
    assertEquals("27.0:MAGISK", info.managerVersion)
    assertTrue(info.busyBoxAvailable)
    assertEquals("Enforcing", info.seLinuxMode)
    assertEquals("5.4.254-TitanicExtended-vili", info.kernelRelease)
  }

  @Test
  fun `root engine detects KernelSU-Next accurately`() = runTest {
    val fakeRunner =
      object : SuShellRunner {
        override suspend fun runCommand(
          executable: String,
          args: List<String>,
          timeoutMs: Long,
        ): RootCommandResult {
          val cmd = args.joinToString(" ")
          return when {
            cmd == "-c id" ->
              RootCommandResult(cmd, 0, "uid=0(root) gid=0(root)", "", 10L, succeeded = true)
            cmd.contains("ksud") ->
              RootCommandResult(cmd, 0, "ksud 1.0.5-KernelSU-Next", "", 8L, succeeded = true)
            cmd == "-c getenforce" ->
              RootCommandResult(cmd, 0, "Enforcing", "", 5L, succeeded = true)
            cmd == "-c uname -r" ->
              RootCommandResult(cmd, 0, "5.4.268-KernelSU-Next", "", 5L, succeeded = true)
            else -> RootCommandResult(cmd, 0, "", "", 4L, succeeded = true)
          }
        }
      }

    val engine = RootEngine(controlRepository, fakeRunner)
    val info = engine.detectEnvironment(requestRootPrompt = true)

    assertTrue(info.isRootGranted)
    assertEquals(RootManagerType.KERNELSU_NEXT, info.managerType)
    assertEquals("ksud 1.0.5-KernelSU-Next", info.managerVersion)
  }

  @Test
  fun `root engine falls back to limited mode when root denied and blocks writes`() = runTest {
    val deniedRunner =
      object : SuShellRunner {
        override suspend fun runCommand(
          executable: String,
          args: List<String>,
          timeoutMs: Long,
        ): RootCommandResult {
          return RootCommandResult(args.joinToString(" "), 1, "", "Permission denied", 6L, false)
        }
      }

    val engine = RootEngine(controlRepository, deniedRunner)
    val info = engine.detectEnvironment(requestRootPrompt = true)

    assertFalse(info.isRootGranted)
    assertEquals(RootAccessState.DENIED, info.accessState)

    val writeRes =
      engine.writeSysfsNode(
        path = "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor",
        value = "performance",
        category = "CPU_GOV",
      )
    assertFalse(writeRes.succeeded)
    assertEquals("NO_ROOT", writeRes.blockedReason)
  }

  @Test
  fun `security validator blocks thermal trip points battery charging and shell injection`() {
    assertTrue(
      RootSecurityValidator.validateWritePath(
        "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor"
      ) is RootSecurityValidator.ValidationResult.Valid
    )
    assertTrue(
      RootSecurityValidator.validateWritePath(
        "/sys/class/kgsl/kgsl-3d0/max_clock_mhz"
      ) is RootSecurityValidator.ValidationResult.Valid
    )
    assertTrue(
      RootSecurityValidator.validateWritePath(
        "/sys/class/thermal/thermal_zone0/trip_point_0_temp"
      ) is RootSecurityValidator.ValidationResult.Rejected
    )
    assertTrue(
      RootSecurityValidator.validateWritePath(
        "/sys/class/power_supply/battery/charging_enabled"
      ) is RootSecurityValidator.ValidationResult.Rejected
    )
    assertTrue(
      RootSecurityValidator.validateWritePath(
        "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor;rm -rf /"
      ) is RootSecurityValidator.ValidationResult.Rejected
    )
    assertTrue(
      RootSecurityValidator.validateWriteValue("performance | reboot")
        is RootSecurityValidator.ValidationResult.Rejected
    )
    assertTrue(
      RootSecurityValidator.validateWriteValue("schedutil")
        is RootSecurityValidator.ValidationResult.Valid
    )
  }

  @Test
  fun `emergency restore preserves earliest original value and restores max before min`() = runTest {
    val writeOrder = mutableListOf<String>()
    val fakeRunner =
      object : SuShellRunner {
        override suspend fun runCommand(
          executable: String,
          args: List<String>,
          timeoutMs: Long,
        ): RootCommandResult {
          val cmd = args.joinToString(" ")
          if (cmd == "-c id") {
            return RootCommandResult(cmd, 0, "uid=0(root)", "", 5L, succeeded = true)
          }
          val valueMatch = Regex("printf '%s' '([^']+)' > '([^']+)'").find(cmd)
          if (valueMatch != null) {
            val valWritten = valueMatch.groupValues[1]
            val targetPath = valueMatch.groupValues[2]
            writeOrder.add("$targetPath=$valWritten")
            return RootCommandResult(cmd, 0, valWritten, "", 5L, succeeded = true)
          }
          return RootCommandResult(cmd, 0, "", "", 5L, succeeded = true)
        }
      }

    val engine = RootEngine(controlRepository, fakeRunner)
    engine.detectEnvironment(requestRootPrompt = true)

    val minPath = "/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq"
    val maxPath = "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq"

    // Two chained writes: earliest original value must be preserved
    controlRepository.recordBackupBeforeWrite(minPath, "CPU", "300000", "1000000")
    controlRepository.recordBackupBeforeWrite(minPath, "CPU", "1000000", "1400000")
    controlRepository.recordBackupBeforeWrite(maxPath, "CPU", "1804800", "1804800")

    preferencesStore.setTweakSessionDirty(true)
    preferencesStore.setThermalEngineEnabled(true)

    val restoreManager = EmergencyRestoreManager(engine, controlRepository, preferencesStore)
    val report = restoreManager.restoreAllToOriginal()

    assertEquals(2, report.totalBackups)
    assertEquals(2, report.restoredCount)
    assertEquals(0, report.failedCount)

    // max_freq must be restored before min_freq
    assertEquals(listOf("$maxPath=1804800", "$minPath=300000"), writeOrder)
    assertTrue(controlRepository.getActiveBackupsOnce().isEmpty())

    val prefs = preferencesStore.preferences.first()
    assertFalse(prefs.tweakSessionDirty)
    assertFalse(prefs.thermalEngineEnabled)
    assertEquals("BALANCED", prefs.activePerformanceProfile)
  }

  @Test
  fun `fps monitor returns unavailable when non-root and parses SurfaceFlinger under root`() =
    runTest {
      val nonRootEngine =
        RootEngine(
          controlRepository,
          object : SuShellRunner {
            override suspend fun runCommand(
              executable: String,
              args: List<String>,
              timeoutMs: Long,
            ) = RootCommandResult("", 1, "", "denied", 2L, false)
          },
        )
      val nonRootMonitor = FpsMonitor(nonRootEngine)
      val limitedReading = nonRootMonitor.queryExternalPackageFps("com.test.game")
      assertEquals(FpsMeasurementSource.UNAVAILABLE, limitedReading.source)
      assertNull(limitedReading.fps)

      // 60 FPS synthetic SurfaceFlinger frame timestamps (~16.666ms spacing)
      val sfOutput =
        (0..10).joinToString("\n") { i ->
          val ts = 1_000_000_000L + i * 16_666_667L
          "$ts\t$ts\t$ts"
        }
      val rootEngine =
        RootEngine(
          controlRepository,
          object : SuShellRunner {
            override suspend fun runCommand(
              executable: String,
              args: List<String>,
              timeoutMs: Long,
            ): RootCommandResult {
              val cmd = args.joinToString(" ")
              return when {
                cmd == "-c id" -> RootCommandResult(cmd, 0, "uid=0(root)", "", 4L, true)
                cmd.contains("SurfaceFlinger") -> RootCommandResult(cmd, 0, sfOutput, "", 8L, true)
                else -> RootCommandResult(cmd, 0, "", "", 2L, true)
              }
            }
          },
        )
      rootEngine.detectEnvironment(requestRootPrompt = true)
      val rootMonitor = FpsMonitor(rootEngine)
      val measured = rootMonitor.queryExternalPackageFps("com.test.game")
      assertEquals(FpsMeasurementSource.SURFACE_FLINGER_ROOT, measured.source)
      assertEquals(60.0f, measured.fps ?: 0f, 0.5f)
    }

  @Test
  fun `controllers read gracefully without crashes and thermal records history`() = runTest {
    val nonRootEngine =
      RootEngine(
        controlRepository,
        object : SuShellRunner {
          override suspend fun runCommand(
            executable: String,
            args: List<String>,
            timeoutMs: Long,
          ) = RootCommandResult("", 1, "", "denied", 2L, false)
        },
      )

    val cpu = CpuController(nonRootEngine, controlRepository)
    val gpu = GpuController(context, nonRootEngine, controlRepository)
    val mem = MemoryManager(context, nonRootEngine, controlRepository)
    val io = StorageIoManager(nonRootEngine, controlRepository)
    val thermal = ThermalEngineController(context, nonRootEngine, controlRepository, cpu, gpu)

    val cpuSnap = cpu.readSnapshot()
    val gpuSnap = gpu.readSnapshot()
    val memSnap = mem.readSnapshot()
    val ioSnap = io.readSnapshot()
    val thermalSnap =
      thermal.readAndEvaluate(
        engineEnabled = false,
        mode = ThermalMode.INTELLIGENT_AUTO,
        recordToHistory = true,
      )

    assertTrue(cpuSnap.coreCount >= 1)
    assertTrue(gpuSnap.openGlEsVersion.isNotBlank())
    assertTrue((memSnap.totalRamBytes ?: 1L) > 0L)
    assertTrue(ioSnap.diskStatsSummary.isNotBlank())
    assertTrue(thermalSnap.alertMessage.isNotBlank())

    val history = controlRepository.observeRecentThermalHistory(10).first()
    assertEquals(1, history.size)
    assertEquals("OFF", history.first().activeMode)
  }

  @Test
  fun `per-game profile persists independently in Room`() = runTest {
    val profile =
      GameProfileEntity(
        packageName = "com.miHoYo.GenshinImpact",
        performanceProfile = "EXTREME_GAMING",
        thermalMode = "GAMING",
        preferredRefreshRateHz = 120,
        autoApplyOnLaunch = true,
        autoShowOverlay = true,
        boostGameProcessPriority = true,
      )
    controlRepository.saveGameProfile(profile)

    val loaded = controlRepository.getGameProfileOnce("com.miHoYo.GenshinImpact")
    assertEquals("EXTREME_GAMING", loaded?.performanceProfile)
    assertEquals(120, loaded?.preferredRefreshRateHz)
    assertTrue(loaded?.autoShowOverlay == true)
  }
}
