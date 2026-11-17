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
    route = RouteBlue, compare = CompareViolet, routeSecondary = RouteGreen,
    routeTertiary = RouteOrange, routeQuaternary = RouteRed,
)

private val DarkExtendedColors = PaceExtendedColors(
    success = DarkSuccess, onSuccess = DarkField, successContainer = DarkSuccessContainer,
    warning = DarkWarning, onWarning = DarkField, warningContainer = DarkWarningContainer,
    error = DarkError, onError = DarkField, errorContainer = DarkErrorContainer,
    information = DarkInformation, informationContainer = DarkInformationContainer,
    route = DarkRouteBlue, compare = DarkCompareViolet, routeSecondary = DarkRouteGreen,
    routeTertiary = DarkRouteOrange, routeQuaternary = DarkRouteRed,
)

private val LocalPaceColors = staticCompositionLocalOf { LightExtendedColors }

val MaterialTheme.paceColors: PaceExtendedColors
    @Composable get() = LocalPaceColors.current

private val LightScheme = lightColorScheme(
    primary = River, onPrimary = SurfaceWhite, primaryContainer = Color(0xFFD7E9E9), onPrimaryContainer = Ink,
    secondary = QuietText, onSecondary = SurfaceWhite, secondaryContainer = Color(0xFFE0E9E7), onSecondaryContainer = Ink,
    tertiary = SignalAmber, onTertiary = Ink, background = FieldPaper, onBackground = Ink,
    surface = SurfaceWhite, onSurface = Ink, surfaceVariant = Color(0xFFE5ECE9), onSurfaceVariant = QuietText,
    outline = Color(0xFF7B8E8F), outlineVariant = Color(0xFFD0DAD7), error = Error, onError = SurfaceWhite,
    errorContainer = ErrorContainer, onErrorContainer = Color(0xFF410E0B),
)

private val DarkScheme = darkColorScheme(
    primary = DarkRiver, onPrimary = DarkField, primaryContainer = Color(0xFF294A50), onPrimaryContainer = DarkInk,
    secondary = DarkQuiet, onSecondary = DarkField, secondaryContainer = Color(0xFF34484B), onSecondaryContainer = DarkInk,
    tertiary = DarkAmber, onTertiary = DarkField, background = DarkField, onBackground = DarkInk,
    surface = DarkSurface, onSurface = DarkInk, surfaceVariant = Color(0xFF34484B), onSurfaceVariant = DarkQuiet,
    outline = Color(0xFF829391), outlineVariant = Color(0xFF465A5D), error = DarkError, onError = DarkField,
    errorContainer = DarkErrorContainer, onErrorContainer = Color(0xFFFFDAD5),
)

@Composable
fun PaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalPaceColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = PaceTypography,
            shapes = PaceShapes,
            content = content,
        )
    }
}
