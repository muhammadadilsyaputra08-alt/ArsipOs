package id.arsipos.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Design system ArsipOS: navy (arsip/tepercaya) + sky (cloud Telegram) + amber (aksen). Semua pasangan teks lulus WCAG AA. */
private val LightColors = lightColorScheme(
    primary = Color(0xFF1E3A8A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBE5FF),
    onPrimaryContainer = Color(0xFF0B1F4B),
    secondary = Color(0xFF0369A1),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6EEFB),
    onSecondaryContainer = Color(0xFF023047),
    tertiary = Color(0xFFB45309),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE6ECF6),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFF94A3B8),
    error = Color(0xFFB91C1C),
    onError = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C0FF),
    onPrimary = Color(0xFF0B1F4B),
    primaryContainer = Color(0xFF2A4AA8),
    onPrimaryContainer = Color(0xFFDBE5FF),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF023047),
    secondaryContainer = Color(0xFF0B4A6F),
    onSecondaryContainer = Color(0xFFD6EEFB),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF3B2300),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE5EAF5),
    surface = Color(0xFF111A2E),
    onSurface = Color(0xFFE5EAF5),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFFB6C2D9),
    outline = Color(0xFF64748B),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A)
)

private val BaseType = Typography()
private val ArsipTypography = BaseType.copy(
    headlineSmall = BaseType.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseType.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseType.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseType.labelLarge.copy(fontWeight = FontWeight.Medium)
)

private val ArsipShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ArsipTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = ArsipTypography,
        shapes = ArsipShapes,
        content = content
    )
}
