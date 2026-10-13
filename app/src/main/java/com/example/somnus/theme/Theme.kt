package com.example.somnus.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val SomnusDarkColorScheme = darkColorScheme(
    primary = AstralBlue80,
    onPrimary = Color(0xFF041633),
    primaryContainer = AstralBlueContainer,
    onPrimaryContainer = AstralBlueOnContainer,
    secondary = MoonlightTeal80,
    onSecondary = Color(0xFF002F3A),
    secondaryContainer = MoonlightTealContainer,
    onSecondaryContainer = MoonlightTealOnContainer,
    tertiary = StarAmber80,
    background = MidnightDeepBackground,
    onBackground = FrostWhite,
    surface = MidnightCardSurface,
    onSurface = FrostWhite,
    surfaceVariant = MidnightCardSurfaceVariant,
    onSurfaceVariant = SlateMuted,
    outline = MidnightBorder,
    error = NightCoralError,
    errorContainer = NightCoralErrorContainer,
    onError = Color.White
)

private val SomnusLightColorScheme = lightColorScheme(
    primary = DeepNavyPrimary,
    onPrimary = Color.White,
    primaryContainer = DeepNavyPrimaryContainer,
    onPrimaryContainer = DeepNavyOnPrimaryContainer,
    secondary = SlateSecondary,
    onSecondary = Color.White,
    secondaryContainer = SlateSecondaryContainer,
    tertiary = Color(0xFFB45309),
    background = SoftIceBackground,
    onBackground = CharcoalText,
    surface = LightSurface,
    onSurface = CharcoalText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = CharcoalSubtext,
    outline = LightBorder,
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

@Composable
fun SomnusTheme(
    darkTheme: Boolean = true, // Default to dark-first midnight-blue identity
    dynamicColor: Boolean = false, // Keep bespoke midnight aesthetic by default
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> SomnusDarkColorScheme
        else -> SomnusLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
