package com.ycg.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Material 3 color tokens for Channel Guard.
 *
 * Seed colour is amber (#FFC107) which is also the accent used inside the
 * blocking overlay, so the app and the overlay feel like one product.
 *
 * The schemes below are static fallbacks for pre-Android 12 devices. On
 * Android 12+ we substitute the device's dynamic colour scheme via
 * [dynamicLightColorScheme] / [dynamicDarkColorScheme] in `Theme.kt`.
 */

private val Amber40 = Color(0xFFC58A00)
private val Amber80 = Color(0xFFFFD54F)
private val OnAmber40 = Color(0xFFFFFFFF)
private val OnAmber80 = Color(0xFF402D00)
private val AmberContainerLight = Color(0xFFFFE082)
private val AmberContainerDark = Color(0xFF5C4200)
private val OnAmberContainerLight = Color(0xFF402D00)
private val OnAmberContainerDark = Color(0xFFFFE082)

private val Slate40 = Color(0xFF625B71)
private val Slate80 = Color(0xFFCCC2DC)
private val SlateContainerLight = Color(0xFFE8DEF8)
private val SlateContainerDark = Color(0xFF4A4458)

private val Coral40 = Color(0xFFB3261E)
private val Coral80 = Color(0xFFFFB4AB)
private val CoralContainerLight = Color(0xFFF9DEDC)
private val CoralContainerDark = Color(0xFF8C1D18)
private val OnCoralContainerLight = Color(0xFF410002)
private val OnCoralContainerDark = Color(0xFFFFDAD6)

internal val LightColors = lightColorScheme(
    primary = Amber40,
    onPrimary = OnAmber40,
    primaryContainer = AmberContainerLight,
    onPrimaryContainer = OnAmberContainerLight,
    secondary = Slate40,
    onSecondary = Color.White,
    secondaryContainer = SlateContainerLight,
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    error = Coral40,
    onError = Color.White,
    errorContainer = CoralContainerLight,
    onErrorContainer = OnCoralContainerLight,
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E)
)

internal val DarkColors = darkColorScheme(
    primary = Amber80,
    onPrimary = OnAmber80,
    primaryContainer = AmberContainerDark,
    onPrimaryContainer = OnAmberContainerDark,
    secondary = Slate80,
    onSecondary = Color(0xFF332D41),
    secondaryContainer = SlateContainerDark,
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = Coral80,
    onError = Color(0xFF690005),
    errorContainer = CoralContainerDark,
    onErrorContainer = OnCoralContainerDark,
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99)
)
