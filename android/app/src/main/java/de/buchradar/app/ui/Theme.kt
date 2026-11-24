package de.buchradar.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Colours derived from the launcher icon (navy book, teal radar, orange dot).
 * Contrast of text/container pairs is at least WCAG AA (4.5:1).
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF00695F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F2E4),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF2B4A6F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E4FF),
    onSecondaryContainer = Color(0xFF001C38),
    tertiary = Color(0xFF8F4E00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC0),
    onTertiaryContainer = Color(0xFF2E1500),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FAF8),
    onBackground = Color(0xFF161D1C),
    surface = Color(0xFFF5FAF8),
    onSurface = Color(0xFF161D1C),
    surfaceVariant = Color(0xFFDAE5E1),
    onSurfaceVariant = Color(0xFF3F4946),
    outline = Color(0xFF6F7976),
    outlineVariant = Color(0xFFBEC9C5),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF5F2),
    surfaceContainer = Color(0xFFE9EFEC),
    surfaceContainerHigh = Color(0xFFE3EAE7),
    surfaceContainerHighest = Color(0xFFDEE4E1),
    inverseSurface = Color(0xFF2B3230),
    inverseOnSurface = Color(0xFFECF2EF),
    inversePrimary = Color(0xFF8AD5C8)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AD5C8),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFFA6F2E4),
    secondary = Color(0xFFA9C8F5),
    onSecondary = Color(0xFF0C3157),
    secondaryContainer = Color(0xFF2B4A6F),
    onSecondaryContainer = Color(0xFFD3E4FF),
    tertiary = Color(0xFFFFB877),
    onTertiary = Color(0xFF4C2700),
    tertiaryContainer = Color(0xFF6D3A00),
    onTertiaryContainer = Color(0xFFFFDCC0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1513),
    onBackground = Color(0xFFDDE4E1),
    surface = Color(0xFF0E1513),
    onSurface = Color(0xFFDDE4E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C5),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4946),
    surfaceContainerLowest = Color(0xFF090F0E),
    surfaceContainerLow = Color(0xFF161D1C),
    surfaceContainer = Color(0xFF1A2120),
    surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF2F3635),
    inverseSurface = Color(0xFFDDE4E1),
    inverseOnSurface = Color(0xFF2B3230),
    inversePrimary = Color(0xFF00695F)
)

/** Semantic colours Material 3 does not define (purchase offer = success). */
@Immutable
data class StatusColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val neutralContainer: Color,
    val onNeutralContainer: Color
)

private val LightStatus = StatusColors(
    success = Color(0xFF1B6D2F), onSuccess = Color.White,
    successContainer = Color(0xFFC8F0C9), onSuccessContainer = Color(0xFF002107),
    neutralContainer = Color(0xFFE3EAE7), onNeutralContainer = Color(0xFF3F4946)
)

private val DarkStatus = StatusColors(
    success = Color(0xFF8EDA92), onSuccess = Color(0xFF00390F),
    successContainer = Color(0xFF0F5320), onSuccessContainer = Color(0xFFC8F0C9),
    neutralContainer = Color(0xFF2F3635), onNeutralContainer = Color(0xFFBEC9C5)
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

private val base = Typography()

/** Slightly larger body text than the Material default for easy reading. */
private val AppTypography = base.copy(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = base.labelLarge.copy(fontSize = 15.sp, lineHeight = 20.sp),
    // Material's small styles (11–12 sp) are used by text-field labels, chips and notes: keep them readable.
    labelMedium = base.labelMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = base.labelSmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = base.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp)
)

@Composable
fun BuchRadarTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content
        )
    }
}
