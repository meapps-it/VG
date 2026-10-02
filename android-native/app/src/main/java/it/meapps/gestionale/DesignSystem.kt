package it.meapps.gestionale

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val BrandNavy = Color(0xFF102C48)
internal val BrandBlue = Color(0xFF245FCC)
private val BrandDanger = Color(0xFFAE343C)
internal val Negative: Color
    @Composable get() = MaterialTheme.colorScheme.error
internal val AppNavy: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface
internal val AppBlue: Color
    @Composable get() = MaterialTheme.colorScheme.primary
internal val AppAmber: Color
    @Composable get() = MaterialTheme.colorScheme.tertiary
internal val Positive: Color
    @Composable get() = MaterialTheme.colorScheme.secondary
internal val AppBackground: Color
    @Composable get() = MaterialTheme.colorScheme.background
internal val WarmSurface: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainer
internal val LegacyBorder: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant

internal val GestionaleLight =
    lightColorScheme(
        primary = BrandBlue,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE7EEFC),
        onPrimaryContainer = BrandNavy,
        secondary = Color(0xFF176A52),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE8F2EE),
        onSecondaryContainer = Color(0xFF15533F),
        tertiary = Color(0xFF88521B),
        tertiaryContainer = Color(0xFFF8EDDF),
        onTertiaryContainer = Color(0xFF704315),
        background = Color(0xFFF3F4F2),
        onBackground = Color(0xFF172D43),
        surface = Color(0xFFFCFCFA),
        onSurface = Color(0xFF172D43),
        surfaceVariant = Color(0xFFEDF0F2),
        onSurfaceVariant = Color(0xFF546372),
        surfaceContainer = Color(0xFFF0F1ED),
        surfaceContainerLow = Color(0xFFF8F9F6),
        surfaceContainerHigh = Color(0xFFE7EBEE),
        outline = Color(0xFF768593),
        outlineVariant = Color(0xFFD7DEE3),
        error = BrandDanger,
    )
internal val GestionaleDark =
    darkColorScheme(
        primary = Color(0xFFA5C4FF),
        onPrimary = Color(0xFF133A77),
        primaryContainer = Color(0xFF1B3D67),
        onPrimaryContainer = Color(0xFFD7E6FF),
        secondary = Color(0xFF93D6BD),
        onSecondary = Color(0xFF163F31),
        secondaryContainer = Color(0xFF203E34),
        onSecondaryContainer = Color(0xFFD4EEE1),
        tertiary = Color(0xFFE6BC8A),
        tertiaryContainer = Color(0xFF493A2A),
        onTertiaryContainer = Color(0xFFF4DEC5),
        background = Color(0xFF101C28),
        onBackground = Color(0xFFE1EAF3),
        surface = Color(0xFF182633),
        onSurface = Color(0xFFE1EAF3),
        surfaceVariant = Color(0xFF273A49),
        onSurfaceVariant = Color(0xFFAFBECC),
        surfaceContainer = Color(0xFF20303D),
        surfaceContainerLow = Color(0xFF182633),
        surfaceContainerHigh = Color(0xFF2B3C4B),
        outline = Color(0xFF80909F),
        outlineVariant = Color(0xFF3B4B59),
        error = Color(0xFFFFB4AB),
    )

private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
    )

internal val GestionaleTypography =
    Typography(
        displaySmall = type(32, 40, FontWeight.SemiBold),
        headlineLarge = type(28, 36, FontWeight.SemiBold),
        headlineMedium = type(24, 32, FontWeight.SemiBold),
        headlineSmall = type(22, 30, FontWeight.SemiBold),
        titleLarge = type(20, 28, FontWeight.SemiBold),
        titleMedium = type(16, 24, FontWeight.SemiBold),
        titleSmall = type(14, 20, FontWeight.SemiBold),
        bodyLarge = type(16, 24),
        bodyMedium = type(14, 22),
        bodySmall = type(12, 18),
        labelLarge = type(14, 20, FontWeight.Medium),
        labelMedium = type(12, 18, FontWeight.Medium),
        labelSmall = type(11, 16, FontWeight.Medium),
    )

internal object UiSpace {
    val screen = 16.dp
    val gap = 12.dp
    val radius = 20.dp
}
