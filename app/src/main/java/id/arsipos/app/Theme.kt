package id.arsipos.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Satu tema = pasangan skema warna terang/gelap + warna top bar. */
class ThemeSpec(
    val id: String,
    val label: String,
    val emoji: String,
    val desc: String,
    val light: ColorScheme,
    val dark: ColorScheme,
    val bar: Color,
    val darkOnly: Boolean = false
)

private val ClassicLight = lightColorScheme(
    primary = Color(0xFF1E3A8A), onPrimary = Color.White,
    primaryContainer = Color(0xFFDBE5FF), onPrimaryContainer = Color(0xFF0B1F4B),
    secondary = Color(0xFF0369A1), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6EEFB), onSecondaryContainer = Color(0xFF023047),
    tertiary = Color(0xFFB45309), onTertiary = Color.White,
    background = Color(0xFFF8FAFC), onBackground = Color(0xFF0F172A),
    surface = Color.White, onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE6ECF6), onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFF94A3B8), error = Color(0xFFB91C1C), onError = Color.White
)
private val ClassicDark = darkColorScheme(
    primary = Color(0xFFA9C0FF), onPrimary = Color(0xFF0B1F4B),
    primaryContainer = Color(0xFF2A4AA8), onPrimaryContainer = Color(0xFFDBE5FF),
    secondary = Color(0xFF7DD3FC), onSecondary = Color(0xFF023047),
    secondaryContainer = Color(0xFF0B4A6F), onSecondaryContainer = Color(0xFFD6EEFB),
    tertiary = Color(0xFFFBBF24), onTertiary = Color(0xFF3B2300),
    background = Color(0xFF0B1220), onBackground = Color(0xFFE5EAF5),
    surface = Color(0xFF111A2E), onSurface = Color(0xFFE5EAF5),
    surfaceVariant = Color(0xFF1E293B), onSurfaceVariant = Color(0xFFB6C2D9),
    outline = Color(0xFF64748B), error = Color(0xFFFCA5A5), onError = Color(0xFF450A0A)
)

private val NatureLight = lightColorScheme(
    primary = Color(0xFF2E7D32), onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F0D0), onPrimaryContainer = Color(0xFF0B2E10),
    secondary = Color(0xFF00796B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEBE3), onSecondaryContainer = Color(0xFF00302A),
    tertiary = Color(0xFF8D5A00), onTertiary = Color.White,
    background = Color(0xFFF3FAEF), onBackground = Color(0xFF10210F),
    surface = Color(0xFFFBFFF8), onSurface = Color(0xFF10210F),
    surfaceVariant = Color(0xFFDDEBD7), onSurfaceVariant = Color(0xFF42513F),
    outline = Color(0xFF7C8C78), error = Color(0xFFB3261E), onError = Color.White
)
private val NatureDark = darkColorScheme(
    primary = Color(0xFF8FD694), onPrimary = Color(0xFF06200A),
    primaryContainer = Color(0xFF1F5A27), onPrimaryContainer = Color(0xFFD7F0D0),
    secondary = Color(0xFF7FD8C8), onSecondary = Color(0xFF00302A),
    secondaryContainer = Color(0xFF0C4A42), onSecondaryContainer = Color(0xFFCDEBE3),
    tertiary = Color(0xFFFFC857), onTertiary = Color(0xFF3B2600),
    background = Color(0xFF08140D), onBackground = Color(0xFFE3EFDF),
    surface = Color(0xFF102015), onSurface = Color(0xFFE3EFDF),
    surfaceVariant = Color(0xFF1E3224), onSurfaceVariant = Color(0xFFBBCDB6),
    outline = Color(0xFF6E8570), error = Color(0xFFFFB4AB), onError = Color(0xFF410002)
)

private val SpaceDark = darkColorScheme(
    primary = Color(0xFFB4A5FF), onPrimary = Color(0xFF1A0F57),
    primaryContainer = Color(0xFF3A2D95), onPrimaryContainer = Color(0xFFE4DEFF),
    secondary = Color(0xFF6FE3FF), onSecondary = Color(0xFF00313C),
    secondaryContainer = Color(0xFF0B4A5C), onSecondaryContainer = Color(0xFFC9F3FF),
    tertiary = Color(0xFFFFC857), onTertiary = Color(0xFF3B2600),
    background = Color(0xFF070A1C), onBackground = Color(0xFFE6E8FA),
    surface = Color(0xFF121536), onSurface = Color(0xFFE6E8FA),
    surfaceVariant = Color(0xFF1F2350), onSurfaceVariant = Color(0xFFC0C4E6),
    outline = Color(0xFF6D72A8), error = Color(0xFFFFB4AB), onError = Color(0xFF410002)
)

private val OceanLight = lightColorScheme(
    primary = Color(0xFF0369A1), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBFF), onPrimaryContainer = Color(0xFF00283F),
    secondary = Color(0xFF0E7490), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCEFF7), onSecondaryContainer = Color(0xFF00313C),
    tertiary = Color(0xFFB4491F), onTertiary = Color.White,
    background = Color(0xFFEFFAFF), onBackground = Color(0xFF07202D),
    surface = Color(0xFFF9FEFF), onSurface = Color(0xFF07202D),
    surfaceVariant = Color(0xFFD5EAF3), onSurfaceVariant = Color(0xFF3D5663),
    outline = Color(0xFF7A98A6), error = Color(0xFFB3261E), onError = Color.White
)
private val OceanDark = darkColorScheme(
    primary = Color(0xFF7CD0FF), onPrimary = Color(0xFF00283F),
    primaryContainer = Color(0xFF0B5A85), onPrimaryContainer = Color(0xFFCDEBFF),
    secondary = Color(0xFF63D9EE), onSecondary = Color(0xFF00313C),
    secondaryContainer = Color(0xFF0B4F5E), onSecondaryContainer = Color(0xFFCCEFF7),
    tertiary = Color(0xFFFFB38A), onTertiary = Color(0xFF3F1700),
    background = Color(0xFF031521), onBackground = Color(0xFFDDF1FA),
    surface = Color(0xFF0A2231), onSurface = Color(0xFFDDF1FA),
    surfaceVariant = Color(0xFF123347), onSurfaceVariant = Color(0xFFB4CDDA),
    outline = Color(0xFF5E8296), error = Color(0xFFFFB4AB), onError = Color(0xFF410002)
)

val THEMES = listOf(
    ThemeSpec("classic", "Klasik", "🗄", "Navy bersih, fokus dan tepercaya", ClassicLight, ClassicDark, Color(0xFF1E3A8A)),
    ThemeSpec("nature", "Alam", "🌿", "Hutan, bukit, matahari & kunang-kunang", NatureLight, NatureDark, Color(0xFF1B5E20)),
    ThemeSpec("space", "Luar Angkasa", "🪐", "Galaksi, bintang berkedip & planet", SpaceDark, SpaceDark, Color(0xFF1A1450), darkOnly = true),
    ThemeSpec("ocean", "Samudra", "🌊", "Ombak tenang & gelembung naik", OceanLight, OceanDark, Color(0xFF075985))
)

fun themeById(id: String): ThemeSpec = THEMES.firstOrNull { it.id == id } ?: THEMES.first()

/** mode: 0 = ikut sistem, 1 = terang, 2 = gelap. Tema darkOnly selalu gelap. */
@Composable
fun isDarkFor(spec: ThemeSpec, mode: Int): Boolean {
    val sys = isSystemInDarkTheme()
    return spec.darkOnly || when (mode) { 1 -> false; 2 -> true; else -> sys }
}

val LocalBarColor = staticCompositionLocalOf { Color(0xFF1E3A8A) }
val LocalThemeDark = staticCompositionLocalOf { false }

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
fun ArsipTheme(themeId: String, mode: Int, content: @Composable () -> Unit) {
    val spec = themeById(themeId)
    val dark = isDarkFor(spec, mode)
    CompositionLocalProvider(LocalBarColor provides spec.bar, LocalThemeDark provides dark) {
        MaterialTheme(
            colorScheme = if (dark) spec.dark else spec.light,
            typography = ArsipTypography,
            shapes = ArsipShapes,
            content = content
        )
    }
}
