package com.dlssgamespace.uicomponents.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

data class ChartSeries(
  val label: String,
  val values: List<Float>,
  val color: Color,
  val unit: String = "",
)

@Composable
fun InteractivePerformanceChart(
  title: String,
  seriesList: List<ChartSeries>,
  minValue: Float = 0f,
  maxValue: Float = 100f,
  modifier: Modifier = Modifier,
  chartHeight: Dp = 150.dp,
  emptyLabel: String = "Collecting real-time samples...",
) {
  val sampleCount = seriesList.maxOfOrNull { it.values.size } ?: 0
  var inspectedIndex by remember(sampleCount) { mutableIntStateOf(-1) }

  DlssCard(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
      )
      if (sampleCount > 0) {
        val activeIdx =
          if (inspectedIndex in 0 until sampleCount) inspectedIndex else sampleCount - 1
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          seriesList.forEach { series ->
            val v = series.values.getOrNull(activeIdx)
            Text(
              text =
                if (v != null) "${series.label}: ${String.format(Locale.US, "%.1f", v)}${series.unit}"
                else "${series.label}: N/A",
              style = MaterialTheme.typography.labelSmall,
              color = series.color,
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    if (sampleCount < 2) {
      Text(
        text = emptyLabel,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 28.dp),
      )
    } else {
      val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
      val activeIdx = if (inspectedIndex in 0 until sampleCount) inspectedIndex else -1

      Canvas(
        modifier =
          Modifier.fillMaxWidth()
            .height(chartHeight)
            .pointerInput(sampleCount) {
              detectTapGestures { offset ->
                val ratio = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                inspectedIndex = (ratio * (sampleCount - 1)).roundToInt()
              }
            }
            .pointerInput(sampleCount) {
              detectDragGestures(
                onDragEnd = { inspectedIndex = -1 },
                onDragCancel = { inspectedIndex = -1 },
              ) { change, _ ->
                change.consume()
                val ratio = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                inspectedIndex = (ratio * (sampleCount - 1)).roundToInt()
              }
            }
      ) {
        val w = size.width
        val h = size.height
        val range = (maxValue - minValue).coerceAtLeast(1f)

        // Horizontal reference grid lines
        for (step in 0..4) {
          val y = h * (step / 4f)
          drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 1.dp.toPx(),
          )
        }

        seriesList.forEach { series ->
          val pts = series.values
          if (pts.size < 2) return@forEach

          val strokePath = Path()
          val fillPath = Path()
          val stepX = w / (pts.size - 1).coerceAtLeast(1)

          pts.forEachIndexed { idx, value ->
            val x = idx * stepX
            val normalized = ((value - minValue) / range).coerceIn(0f, 1f)
            val y = h - (normalized * h)
            if (idx == 0) {
              strokePath.moveTo(x, y)
              fillPath.moveTo(x, h)
              fillPath.lineTo(x, y)
            } else {
              strokePath.lineTo(x, y)
              fillPath.lineTo(x, y)
            }
          }

          fillPath.lineTo((pts.size - 1) * stepX, h)
          fillPath.close()

          drawPath(
            path = fillPath,
            brush =
              Brush.verticalGradient(
                colors = listOf(series.color.copy(alpha = 0.24f), Color.Transparent)
              ),
          )
          drawPath(
            path = strokePath,
            color = series.color,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
          )
        }

        if (activeIdx in 0 until sampleCount) {
          val cursorX = activeIdx * (w / (sampleCount - 1).coerceAtLeast(1))
          drawLine(
            color = Color.White.copy(alpha = 0.55f),
            start = Offset(cursorX, 0f),
            end = Offset(cursorX, h),
            strokeWidth = 1.dp.toPx(),
          )
        }
      }
    }
  }
}
