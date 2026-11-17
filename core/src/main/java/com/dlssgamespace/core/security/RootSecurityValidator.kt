package com.dlssgamespace.core.security

object RootSecurityValidator {

  private val approvedReadPrefixes =
    listOf(
      "/sys/devices/system/cpu/",
      "/sys/class/kgsl/",
      "/sys/devices/platform/",
      "/sys/class/devfreq/",
      "/sys/class/thermal/",
      "/sys/devices/virtual/thermal/",
      "/sys/block/",
      "/sys/module/lowmemorykiller/",
      "/sys/kernel/",
      "/proc/",
    )

  private val approvedWritePrefixes =
    listOf(
      "/sys/devices/system/cpu/",
      "/sys/class/kgsl/",
      "/sys/devices/platform/",
      "/sys/class/devfreq/",
      "/sys/block/",
      "/proc/sys/vm/",
    )

  private val forbiddenShellTokens = listOf("..", ";", "|", "&", "`", "$", ">", "<", "\n", "\r", "\u0000")

  private val safeIdentifierRegex = Regex("^[a-zA-Z0-9_.-]{1,64}$")
  private val safeIntegerRegex = Regex("^-?[0-9]{1,12}$")

  sealed interface ValidationResult {
    data class Valid(val normalized: String) : ValidationResult

    data class Rejected(val reason: String) : ValidationResult
  }

  fun validateReadPath(path: String): ValidationResult {
    val trimmed = path.trim()
    if (trimmed.isEmpty() || forbiddenShellTokens.any { trimmed.contains(it) }) {
      return ValidationResult.Rejected("Unsafe characters in sysfs/procfs path")
    }
    if (approvedReadPrefixes.none { trimmed.startsWith(it) }) {
      return ValidationResult.Rejected("Path outside approved telemetry sysfs/procfs prefixes")
    }
    return ValidationResult.Valid(trimmed)
  }

  fun validateWritePath(path: String): ValidationResult {
    val trimmed = path.trim()
    if (trimmed.isEmpty() || forbiddenShellTokens.any { trimmed.contains(it) }) {
      return ValidationResult.Rejected("Unsafe characters in writable sysfs/procfs path")
    }
    // Never allow touching thermal trip points, thermal zone disable, or battery charging safety
    val lower = trimmed.lowercase()
    if (
      lower.contains("trip_point") ||
        lower.contains("/thermal_zone") ||
        lower.contains("battery") ||
        lower.contains("charging") ||
        lower.contains("voltage")
    ) {
      return ValidationResult.Rejected("Safety lock: hardware thermal/voltage/battery protection nodes cannot be modified")
    }
    if (approvedWritePrefixes.none { trimmed.startsWith(it) }) {
      return ValidationResult.Rejected("Write path not in hardware control allowlist")
    }
    return ValidationResult.Valid(trimmed)
  }

  fun validateWriteValue(value: String): ValidationResult {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || forbiddenShellTokens.any { trimmed.contains(it) }) {
      return ValidationResult.Rejected("Unsafe characters in sysfs value")
    }
    if (!safeIntegerRegex.matches(trimmed) && !safeIdentifierRegex.matches(trimmed)) {
      return ValidationResult.Rejected("Value must be numeric or safe alphanumeric identifier")
    }
    return ValidationResult.Valid(trimmed)
  }

  fun validatePid(pid: Int): Boolean = pid in 1..4_194_304

  fun validatePackageName(packageName: String): Boolean =
    Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$").matches(packageName)

  fun validateNiceValue(nice: Int): Boolean = nice in -10..19
}
