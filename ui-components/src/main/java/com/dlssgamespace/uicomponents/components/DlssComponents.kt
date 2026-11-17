package com.dlssgamespace.uicomponents.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dlssgamespace.uicomponents.theme.DlssTheme
import kotlin.math.cos
import kotlin.math.sin

enum class TelemetryStatus {
  NORMAL,
  WARNING,
  CRITICAL,
  UNKNOWN,
}

@Composable
fun DlssBrandEmblem(modifier: Modifier = Modifier, size: Dp = 36.dp) {
  val primary = MaterialTheme.colorScheme.primary
  val secondary = DlssTheme.semantic.blueAccent
  val white = MaterialTheme.colorScheme.onBackground

  Canvas(modifier = modifier.size(size)) {
    val cx = this.size.width / 2f
    val cy = this.size.height / 2f
    val radius = (this.size.minDimension / 2f) * 0.88f

    val hexPath = Path()
    for (i in 0 until 6) {
      val angle = Math.toRadians((60 * i - 90).toDouble())
      val x = cx + (radius * cos(angle)).toFloat()
      val y = cy + (radius * sin(angle)).toFloat()
      if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
    }
    hexPath.close()

    drawPath(
      path = hexPath,
      brush =
        Brush.linearGradient(
          colors = listOf(primary.copy(alpha = 0.22f), secondary.copy(alpha = 0.22f))
        ),
    )
    drawPath(
      path = hexPath,
      brush = Brush.linearGradient(colors = listOf(primary, secondary)),
      style = Stroke(width = this.size.minDimension * 0.08f, cap = StrokeCap.Round),
    )

    val chevron =
      Path().apply {
        moveTo(cx - radius * 0.34f, cy - radius * 0.38f)
        lineTo(cx + radius * 0.44f, cy)
        lineTo(cx - radius * 0.34f, cy + radius * 0.38f)
        lineTo(cx - radius * 0.12f, cy)
        close()
      }
    drawPath(path = chevron, color = white)

    drawCircle(
      color = primary,
      radius = radius * 0.12f,
      center = Offset(cx - radius * 0.38f, cy),
    )
  }
}

@Composable
fun DlssCard(
  modifier: Modifier = Modifier,
  highlighted: Boolean = false,
  blueAccent: Boolean = false,
  status: TelemetryStatus = TelemetryStatus.NORMAL,
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val borderColor =
    when {
      status == TelemetryStatus.CRITICAL -> DlssTheme.semantic.critical.copy(alpha = 0.78f)
      status == TelemetryStatus.WARNING -> DlssTheme.semantic.warning.copy(alpha = 0.65f)
      blueAccent -> DlssTheme.semantic.blueAccent.copy(alpha = 0.65f)
      highlighted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
      else -> DlssTheme.semantic.subtleBorder
    }

  val containerColor =
    if (highlighted || blueAccent) MaterialTheme.colorScheme.surfaceContainerHigh
    else MaterialTheme.colorScheme.surfaceVariant

  if (onClick != null) {
    Surface(
      onClick = onClick,
      modifier = modifier,
      shape = MaterialTheme.shapes.large,
      color = containerColor,
      border = BorderStroke(1.dp, borderColor),
    ) {
      Column(modifier = Modifier.padding(16.dp), content = content)
    }
  } else {
    Surface(
      modifier = modifier,
      shape = MaterialTheme.shapes.large,
      color = containerColor,
      border = BorderStroke(1.dp, borderColor),
    ) {
      Column(modifier = Modifier.padding(16.dp), content = content)
    }
  }
}

@Composable
fun StatusPill(
  text: String,
  modifier: Modifier = Modifier,
  status: TelemetryStatus = TelemetryStatus.NORMAL,
  accentOverride: Color? = null,
) {
  val (bg, fg) =
    when {
      accentOverride != null -> accentOverride.copy(alpha = 0.18f) to accentOverride
      status == TelemetryStatus.CRITICAL ->
        DlssTheme.semantic.criticalContainer to DlssTheme.semantic.critical
      status == TelemetryStatus.WARNING ->
        DlssTheme.semantic.warningContainer to DlssTheme.semantic.warning
      status == TelemetryStatus.UNKNOWN ->
        MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
      else ->
        DlssTheme.semantic.healthyContainer to DlssTheme.semantic.healthy
    }

  Box(
    modifier =
      modifier
        .clip(RoundedCornerShape(50))
        .background(bg)
        .padding(horizontal = 10.dp, vertical = 4.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = fg,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
fun TelemetryProgressBar(
  progress: Float?,
  modifier: Modifier = Modifier,
  status: TelemetryStatus = TelemetryStatus.NORMAL,
  height: Dp = 8.dp,
  useBlueAccent: Boolean = false,
) {
  val clampedTarget = (progress ?: 0f).coerceIn(0f, 1f)
  val animatedProgress by
    animateFloatAsState(
      targetValue = clampedTarget,
      animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
      label = "telemetry_progress",
    )

  val barColor =
    when {
      progress == null -> MaterialTheme.colorScheme.outlineVariant
      status == TelemetryStatus.CRITICAL -> DlssTheme.semantic.critical
      status == TelemetryStatus.WARNING -> DlssTheme.semantic.warning
      useBlueAccent -> DlssTheme.semantic.blueAccent
      else -> DlssTheme.semantic.healthy
    }

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(height)
        .clip(RoundedCornerShape(50))
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
  ) {
    if (progress != null && animatedProgress > 0f) {
      Box(
        modifier =
          Modifier.fillMaxWidth(animatedProgress)
            .fillMaxHeight()
            .clip(RoundedCornerShape(50))
            .background(barColor)
      )
    }
  }
}

@Composable
fun SectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  useBlueAccent: Boolean = false,
  trailing: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.weight(1f),
    ) {
      Box(
        modifier =
          Modifier.width(3.dp)
            .height(18.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
              if (useBlueAccent) DlssTheme.semantic.blueAccent
              else MaterialTheme.colorScheme.primary
            )
      )
      Spacer(modifier = Modifier.width(10.dp))
      Column {
        Text(
          text = title.uppercase(),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
    if (trailing != null) {
      trailing()
    }
  }
}
