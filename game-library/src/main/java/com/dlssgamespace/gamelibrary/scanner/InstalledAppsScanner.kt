package com.dlssgamespace.gamelibrary.scanner

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import com.dlssgamespace.database.model.ScannedGameMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppsScanner(private val context: Context) {

  suspend fun scanLaunchableApps(): Result<List<ScannedGameMetadata>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val pm = context.packageManager
        val mainIntent =
          Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }

        val resolveInfos: List<ResolveInfo> =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
          } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(mainIntent, 0)
          }

        val selfPackage = context.packageName
        val uniqueApps = LinkedHashMap<String, ScannedGameMetadata>()

        for (info in resolveInfos) {
          val activityInfo = info.activityInfo ?: continue
          val packageName = activityInfo.packageName ?: continue
          if (packageName == selfPackage || uniqueApps.containsKey(packageName)) continue

          val appInfo = activityInfo.applicationInfo
          val displayName =
            info.loadLabel(pm)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
              ?: appInfo?.loadLabel(pm)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
              ?: packageName

          val pkgInfo =
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
                } else {
                  @Suppress("DEPRECATION") pm.getPackageInfo(packageName, 0)
                }
              }
              .getOrNull()

          val versionName = pkgInfo?.versionName?.takeIf { it.isNotBlank() } ?: "N/A"
          val versionCode = pkgInfo?.longVersionCode ?: 0L
          val isCategoryGame =
            (appInfo?.category ?: ApplicationInfo.CATEGORY_UNDEFINED) == ApplicationInfo.CATEGORY_GAME

          uniqueApps[packageName] =
            ScannedGameMetadata(
              packageName = packageName,
              displayName = displayName,
              versionName = versionName,
              versionCode = versionCode,
              isSystemCategoryGame = isCategoryGame,
            )
        }

        uniqueApps.values.sortedBy { it.displayName.lowercase() }
      }
    }
}
