package com.dlssgamespace.core.hardware

import android.os.Environment
import android.os.StatFs
import com.dlssgamespace.core.root.RootCommandResult
import com.dlssgamespace.core.root.RootEngine
import com.dlssgamespace.database.repository.SystemControlRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class BlockIoInfo(
  val deviceName: String,
  val sysfsQueuePath: String,
  val activeScheduler: String,
  val availableSchedulers: List<String>,
  val readAheadKb: Int?,
  val isWritable: Boolean,
)

data class StorageIoSnapshot(
  val totalBytes: Long?,
  val usedBytes: Long?,
  val availableBytes: Long?,
  val usageFraction: Float?,
  val primaryBlockIo: BlockIoInfo?,
  val diskStatsSummary: String,
)

class StorageIoManager(
  private val rootEngine: RootEngine,
  private val controlRepository: SystemControlRepository,
) {

  suspend fun readSnapshot(): StorageIoSnapshot =
    withContext(Dispatchers.IO) {
      val stat = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
      val total = stat?.totalBytes?.takeIf { it > 0L }
      val avail = stat?.availableBytes?.takeIf { it >= 0L }
      val used = if (total != null && avail != null) (total - avail).coerceAtLeast(0L) else null
      val fraction =
        if (total != null && used != null && total > 0L) {
          (used.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
          null
        }

      val blockIo = discoverPrimaryBlockDevice()
      val diskStats = readDiskStatsSummary(blockIo?.deviceName)

      StorageIoSnapshot(
        totalBytes = total,
        usedBytes = used,
        availableBytes = avail,
        usageFraction = fraction,
        primaryBlockIo = blockIo,
        diskStatsSummary = diskStats,
      )
    }

  private suspend fun discoverPrimaryBlockDevice(): BlockIoInfo? {
    val candidates = listOf("sda", "mmcblk0", "nvme0n1", "vda")
    val blockRoot = File("/sys/block")
    val chosen =
      candidates.firstOrNull { File(blockRoot, "$it/queue").exists() }
        ?: blockRoot
          .listFiles()
          ?.firstOrNull { f ->
            !f.name.startsWith("loop") && !f.name.startsWith("ram") && !f.name.startsWith("zram") &&
              File(f, "queue/scheduler").exists()
          }
          ?.name
        ?: return null

    val queuePath = "/sys/block/$chosen/queue"
    val schedRaw = rootEngine.readSysfsNode("$queuePath/scheduler")
    val active =
      schedRaw?.let { Regex("\\[([^\\]]+)]").find(it)?.groupValues?.getOrNull(1) }
        ?: schedRaw
        ?: "N/A"
    val avail =
      schedRaw
        ?.replace("[", "")
        ?.replace("]", "")
        ?.split(Regex("\\s+"))
        ?.filter { it.isNotBlank() }
        .orEmpty()

    val readAhead = rootEngine.readSysfsNode("$queuePath/read_ahead_kb")?.toIntOrNull()
    val writable = rootEngine.environment.value.isRootGranted && File("$queuePath/scheduler").exists()

    return BlockIoInfo(
      deviceName = chosen,
      sysfsQueuePath = queuePath,
      activeScheduler = active,
      availableSchedulers = avail,
      readAheadKb = readAhead,
      isWritable = writable,
    )
  }

  suspend fun setIoScheduler(blockIo: BlockIoInfo, scheduler: String): RootCommandResult {
    if (blockIo.availableSchedulers.isNotEmpty() && !blockIo.availableSchedulers.contains(scheduler)) {
      return RootCommandResult(
        command = "set io scheduler $scheduler",
        exitCode = -1,
        stdout = "",
        stderr = "Scheduler '$scheduler' not supported by ${blockIo.deviceName}",
        durationMs = 0L,
        succeeded = false,
        blockedReason = "UNSUPPORTED_SCHEDULER",
      )
    }
    val path = "${blockIo.sysfsQueuePath}/scheduler"
    val res = rootEngine.writeSysfsNode(path, scheduler, "IO_SCHEDULER")
    if (res.succeeded && blockIo.activeScheduler != "N/A") {
      controlRepository.recordBackupBeforeWrite(path, "IO", blockIo.activeScheduler, scheduler)
    }
    return res
  }

  suspend fun setReadAheadKb(blockIo: BlockIoInfo, readAheadKb: Int): RootCommandResult {
    val safeKb = readAheadKb.coerceIn(128, 4096)
    val path = "${blockIo.sysfsQueuePath}/read_ahead_kb"
    val original = rootEngine.readSysfsNode(path) ?: blockIo.readAheadKb?.toString()
    val res = rootEngine.writeSysfsNode(path, safeKb.toString(), "IO_READAHEAD")
    if (res.succeeded && original != null) {
      controlRepository.recordBackupBeforeWrite(path, "IO", original, safeKb.toString())
    }
    return res
  }

  private suspend fun readDiskStatsSummary(deviceName: String?): String {
    val raw = rootEngine.readSysfsNode("/proc/diskstats") ?: return "N/A"
    val target = deviceName ?: "sda"
    val line =
      raw.lineSequence().firstOrNull { l -> l.trim().split(Regex("\\s+")).getOrNull(2) == target }
        ?: return "N/A"
    val cols = line.trim().split(Regex("\\s+"))
    val reads = cols.getOrNull(3) ?: "0"
    val writes = cols.getOrNull(7) ?: "0"
    val ioInFlight = cols.getOrNull(11) ?: "0"
    return "$target · Reads: $reads · Writes: $writes · Queue: $ioInFlight"
  }
}
