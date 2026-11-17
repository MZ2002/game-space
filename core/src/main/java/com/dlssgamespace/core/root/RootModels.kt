package com.dlssgamespace.core.root

enum class RootManagerType(val displayName: String) {
  MAGISK("Magisk"),
  KERNELSU_NEXT("KernelSU-Next"),
  KERNELSU("KernelSU"),
  GENERIC_SU("Generic su"),
  NONE("Not Detected (Limited Mode)"),
}

enum class RootAccessState(val label: String) {
  UNKNOWN("Not Checked"),
  CHECKING("Verifying..."),
  GRANTED("Root Granted"),
  DENIED("Permission Denied"),
  UNAVAILABLE("No Root Binary"),
}

data class RootEnvironmentInfo(
  val managerType: RootManagerType = RootManagerType.NONE,
  val managerVersion: String = "N/A",
  val suBinaryPath: String = "N/A",
  val busyBoxAvailable: Boolean = false,
  val busyBoxPath: String = "N/A",
  val busyBoxVersion: String = "N/A",
  val kernelRelease: String = "N/A",
  val kernelVersionFull: String = "N/A",
  val seLinuxMode: String = "N/A",
  val architecture: String = "N/A",
  val accessState: RootAccessState = RootAccessState.UNKNOWN,
  val lastCheckedAtMs: Long = 0L,
) {
  val isRootGranted: Boolean
    get() = accessState == RootAccessState.GRANTED
}

data class RootCommandResult(
  val command: String,
  val exitCode: Int,
  val stdout: String,
  val stderr: String,
  val durationMs: Long,
  val succeeded: Boolean,
  val blockedReason: String? = null,
)
