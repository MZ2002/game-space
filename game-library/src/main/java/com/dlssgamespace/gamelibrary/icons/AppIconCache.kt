package com.dlssgamespace.gamelibrary.icons

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppIconCache(private val context: Context) {
  private val cache = LruCache<String, Bitmap>(80)

  suspend fun loadIcon(packageName: String, sizePx: Int = 144): Bitmap? {
    cache.get(packageName)?.let { return it }

    return withContext(Dispatchers.IO) {
      runCatching {
          val drawable = context.packageManager.getApplicationIcon(packageName)
          val width = drawable.intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(sizePx) ?: sizePx
          val height = drawable.intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(sizePx) ?: sizePx
          drawable.toBitmap(width = width, height = height, config = Bitmap.Config.ARGB_8888)
        }
        .getOrNull()
        ?.also { bitmap -> cache.put(packageName, bitmap) }
    }
  }
}
