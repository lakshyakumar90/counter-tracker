package com.pace.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme

@Immutable
data class PaceExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val information: Color,
    val informationContainer: Color,
    val route: Color,
    val compare: Color,
    val routeSecondary: Color,
    val routeTertiary: Color,
    val routeQuaternary: Color,
)

private val LightExtendedColors = PaceExtendedColors(
    success = Success, onSuccess = SurfaceWhite, successContainer = SuccessContainer,
    warning = Warning, onWarning = Ink, warningContainer = WarningContainer,
    error = Error, onError = SurfaceWhite, errorContainer = ErrorContainer,
    information = Information, informationContainer = InformationContainer,
    route = RouteGreen, compare = CompareViolet, routeSecondary = RouteBlue,
    routeTertiary = RouteOrange, routeQuaternary = RouteRed,
)

private val DarkExtendedColors = PaceExtendedColors(
    success = DarkSuccess, onSuccess = DarkField, successContainer = DarkSuccessContainer,
    warning = DarkWarning, onWarning = DarkField, warningContainer = DarkWarningContainer,
    error = DarkError, onError = DarkField, errorContainer = DarkErrorContainer,
    information = DarkInformation, informationContainer = DarkInformationContainer,
    route = DarkRouteGreen, compare = DarkCompareViolet, routeSecondary = DarkRouteBlue,
    routeTertiary = DarkRouteOrange, routeQuaternary = DarkRouteRed,
)

private val LocalPaceColors = staticCompositionLocalOf { LightExtendedColors }
val LocalReducedMotion = staticCompositionLocalOf { false }

val MaterialTheme.paceColors: PaceExtendedColors
    @Composable get() = LocalPaceColors.current

private val LightScheme = lightColorScheme(
    primary = Color(0xFF5C36A1), onPrimary = SurfaceWhite,
    primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF2A1749),
    secondary = Color(0xFF635475), onSecondary = SurfaceWhite,
    secondaryContainer = Color(0xFFEEE3FA), onSecondaryContainer = Color(0xFF2A2038),
    tertiary = SignalAmber, onTertiary = Ink,
    background = Color(0xFFF7F2FC), onBackground = Color(0xFF241B32),
    surface = Color(0xFFFFFAFF), onSurface = Color(0xFF241B32),
    surfaceVariant = Color(0xFFECE3F4), onSurfaceVariant = Color(0xFF463652),
    outline = Color(0xFF6C587D), outlineVariant = Color(0xFFC5B3D5),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF9F4FD),
    surfaceContainer = Color(0xFFF3EAF9), surfaceContainerHigh = Color(0xFFECE2F5),
    surfaceContainerHighest = Color(0xFFE6DAF0),
    error = Error, onError = SurfaceWhite,
    errorContainer = ErrorContainer, onErrorContainer = Color(0xFF410E0B),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFD3BCFF), onPrimary = DarkField, primaryContainer = Color(0xFF493270), onPrimaryContainer = DarkInk,
    secondary = DarkQuiet, onSecondary = DarkField, secondaryContainer = Color(0xFF34484B), onSecondaryContainer = DarkInk,
    tertiary = DarkAmber, onTertiary = DarkField, background = DarkField, onBackground = DarkInk,
    surface = DarkSurface, onSurface = DarkInk, surfaceVariant = Color(0xFF34484B), onSurfaceVariant = DarkQuiet,
    outline = Color(0xFF829391), outlineVariant = Color(0xFF465A5D), error = DarkError, onError = DarkField,
    errorContainer = DarkErrorContainer, onErrorContainer = Color(0xFFFFDAD5),
)

private val LightHighContrastScheme = LightScheme.copy(
    primary = Color(0xFF482781), onPrimary = Color.White, secondary = Color(0xFF263A3C),
    onSurface = Color(0xFF101717), onSurfaceVariant = Color(0xFF263536), outline = Color(0xFF3D4A4B),
    outlineVariant = Color(0xFF687576),
)

private val DarkHighContrastScheme = DarkScheme.copy(
    primary = Color(0xFFEBDDFF), onPrimary = Color(0xFF002E2B), secondary = Color(0xFFE5FFFF),
    onSurface = Color(0xFFFFFFFF), onSurfaceVariant = Color(0xFFE1F0EF), outline = Color(0xFFB8CDCC),
    outlineVariant = Color(0xFF91A6A5),
)

@Composable
fun PaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    highContrast: Boolean = false,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPaceColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = when {
                darkTheme && highContrast -> DarkHighContrastScheme
                darkTheme -> DarkScheme
                highContrast -> LightHighContrastScheme
                else -> LightScheme
            },
            typography = PaceTypography,
            shapes = PaceShapes,
            content = content,
        )
    }
}
