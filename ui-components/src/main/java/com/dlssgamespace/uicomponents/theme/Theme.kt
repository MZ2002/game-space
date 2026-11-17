package com.dlssgamespace.uicomponents.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class DlssSemanticColors(
  val healthy: Color = DlssColors.HealthyGreen,
  val healthyContainer: Color = DlssColors.HealthyContainer,
  val warning: Color = DlssColors.AmberWarning,
  val warningContainer: Color = DlssColors.AmberContainer,
  val critical: Color = DlssColors.RedCritical,
  val criticalContainer: Color = DlssColors.RedContainer,
  val blueAccent: Color = DlssColors.CyberBlue,
  val blueBright: Color = DlssColors.CyberBlueBright,
  val blueContainer: Color = DlssColors.CyberBlueContainer,
  val cardElevated: Color = DlssColors.SurfaceElevated,
  val subtleBorder: Color = DlssColors.OutlineSubtle,
)

val LocalDlssSemanticColors = staticCompositionLocalOf { DlssSemanticColors() }

private val DlssDarkColorScheme =
  darkColorScheme(
    primary = DlssColors.CrimsonRed,
    onPrimary = DlssColors.OnCrimson,
    primaryContainer = DlssColors.CrimsonContainer,
    onPrimaryContainer = DlssColors.OnCrimsonContainer,
    secondary = DlssColors.CyberBlue,
    onSecondary = DlssColors.OnCyberBlue,
    secondaryContainer = DlssColors.CyberBlueContainer,
    onSecondaryContainer = DlssColors.OnCyberBlueContainer,
    tertiary = DlssColors.CyberBlueBright,
    onTertiary = DlssColors.Black,
    tertiaryContainer = DlssColors.CyberBlueContainer,
    onTertiaryContainer = DlssColors.CyberBlueBright,
    background = DlssColors.Black,
    onBackground = DlssColors.White,
    surface = DlssColors.SurfaceDark,
    onSurface = DlssColors.White,
    surfaceVariant = DlssColors.SurfaceCard,
    onSurfaceVariant = DlssColors.TextSecondary,
    surfaceContainerLowest = DlssColors.Black,
    surfaceContainerLow = DlssColors.SurfaceDark,
    surfaceContainer = DlssColors.SurfaceCard,
    surfaceContainerHigh = DlssColors.SurfaceElevated,
    surfaceContainerHighest = DlssColors.SurfaceHighest,
    outline = DlssColors.OutlineBright,
    outlineVariant = DlssColors.OutlineSubtle,
    error = DlssColors.RedCritical,
    onError = DlssColors.White,
    errorContainer = DlssColors.RedContainer,
    onErrorContainer = DlssColors.RedCritical,
  )

val DlssShapes =
  Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
  )

@Composable
fun DlssGameSpaceTheme(content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalDlssSemanticColors provides DlssSemanticColors()) {
    MaterialTheme(
      colorScheme = DlssDarkColorScheme,
      typography = DlssTypography,
      shapes = DlssShapes,
      content = content,
    )
  }
}

object DlssTheme {
  val semantic: DlssSemanticColors
    @Composable get() = LocalDlssSemanticColors.current
}
