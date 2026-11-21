package io.github.nuadyr.lufoss.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// Aengus design system primitives.
private val steel100 = Color(0xFFF5F6F7)
private val steel200 = Color(0xFFE9EAEC)
private val steel300 = Color(0xFFD1D3D7)
private val steel500 = Color(0xFF888A96)
private val steel600 = Color(0xFF656672)
private val steel700 = Color(0xFF43444B)
private val steel800 = Color(0xFF2C2C30)
private val steel900 = Color(0xFF1A1A1D)
private val steel950 = Color(0xFF111213)
private val periwinkle25 = Color(0xFFCFCFE7)
private val periwinkle50 = Color(0xFFBBBBDD)
private val periwinkle200 = Color(0xFF8E90C7)
private val periwinkle500 = Color(0xFF4A61A6)
private val periwinkle800 = Color(0xFF223D55)
private val periwinkle900 = Color(0xFF193142)
private val bone100 = Color(0xFFE6E3DA)
private val bone200 = Color(0xFFCDCAC1)
private val bone300 = Color(0xFFBEBBB2)

/** Colours the Material scheme has no slot for. */
@Immutable
class LufossColors(
    val route: Color,
    val pin: Color,
    val brand: Color,
    val onBrand: Color,
    val onBrandSoft: Color,
    val inkSoft: Color,
    val heading: Color
)

private val lightExtras = LufossColors(
    route = periwinkle500,
    pin = Color(0xFFB3261E),
    brand = steel900,
    onBrand = steel100,
    onBrandSoft = steel300,
    inkSoft = steel700,
    heading = steel950
)

private val darkExtras = LufossColors(
    route = periwinkle200,
    pin = Color(0xFFFF8A80),
    brand = steel900,
    onBrand = steel100,
    onBrandSoft = steel300,
    inkSoft = bone300,
    heading = bone100
)

@Suppress("ktlint:standard:property-naming")
val LocalLufossColors = staticCompositionLocalOf { lightExtras }

private val lightScheme = lightColorScheme(
    primary = periwinkle800,
    onPrimary = Color.White,
    primaryContainer = periwinkle25,
    onPrimaryContainer = periwinkle900,
    secondary = steel700,
    onSecondary = Color.White,
    background = steel100,
    onBackground = steel950,
    surface = Color.White,
    onSurface = steel950,
    surfaceVariant = steel200,
    onSurfaceVariant = steel700,
    outline = steel600,
    outlineVariant = steel300,
    error = Color(0xFFC0262D),
    onError = Color.White,
    errorContainer = Color(0xFFFBE4E7),
    onErrorContainer = Color(0xFF7A0F1F)
)

private val darkScheme = darkColorScheme(
    primary = periwinkle50,
    onPrimary = steel950,
    primaryContainer = periwinkle900,
    onPrimaryContainer = periwinkle25,
    secondary = bone300,
    onSecondary = steel950,
    background = steel950,
    onBackground = bone200,
    surface = steel900,
    onSurface = bone200,
    surfaceVariant = steel800,
    onSurfaceVariant = bone300,
    outline = steel500,
    outlineVariant = steel700,
    error = Color(0xFFFF3333),
    onError = steel950,
    errorContainer = Color(0xFF330000),
    onErrorContainer = Color(0xFFFF6666)
)

private val base = Typography()

/** Headings use a serif face, as the web page did. Drop Fraunces and Atkinson into res/font to match it exactly. */
private val lufossTypography = base.copy(
    titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold)
)

@Composable
fun LufossTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalLufossColors provides if (dark) darkExtras else lightExtras) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme else lightScheme,
            typography = lufossTypography,
            content = content
        )
    }
}
