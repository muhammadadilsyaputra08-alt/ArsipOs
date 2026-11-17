package id.arsipos.app

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TAU = (2.0 * PI).toFloat()

/** Latar bertema: lukisan prosedural (tanpa gambar/aset) + lapisan penyamar agar teks tetap terbaca. */
@Composable
fun ThemedBackground(themeId: String, dark: Boolean, intensity: Float, animated: Boolean) {
    val bg = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize().background(bg)) {
        Crossfade(targetState = themeId to dark, animationSpec = tween(700), label = "bg") { (id, d) ->
            ArtCanvas(id, d, animated)
        }
        Box(Modifier.fillMaxSize().background(bg.copy(alpha = ((1f - intensity) * 0.92f).coerceIn(0f, 1f))))
    }
}

@Composable
private fun rememberPhase(animated: Boolean, millis: Int): State<Float> {
    if (!animated) return remember { mutableStateOf(0.3f) }
    val t = rememberInfiniteTransition(label = "phase")
    return t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(millis, easing = LinearEasing), RepeatMode.Restart),
        label = "p"
    )
}

@Composable
fun ArtCanvas(id: String, dark: Boolean, animated: Boolean, modifier: Modifier = Modifier.fillMaxSize()) {
    val phase = rememberPhase(animated, 24000)
    Canvas(modifier) {
        val p = phase.value // dibaca di fase gambar: hanya menggambar ulang, tanpa rekomposisi
        when (id) {
            "space" -> drawSpace(p)
            "nature" -> drawNature(p, dark)
            "ocean" -> drawOcean(p, dark)
            else -> drawClassic(p, dark)
        }
    }
}

// ---------------------------------------------------------------- Klasik

private fun DrawScope.drawClassic(p: Float, dark: Boolean) {
    val w = size.width
    val h = size.height
    val top = if (dark) Color(0xFF0B1220) else Color(0xFFF8FAFC)
    val bottom = if (dark) Color(0xFF14224A) else Color(0xFFDCE8FB)
    drawRect(Brush.verticalGradient(listOf(top, bottom)))
    val a = p * TAU
    val sky = Color(0xFF38BDF8)
    val navy = Color(0xFF1E3A8A)
    val c1 = Offset(w * (0.25f + 0.08f * sin(a)), h * (0.22f + 0.05f * cos(a)))
    val c2 = Offset(w * (0.8f + 0.06f * cos(a)), h * (0.75f + 0.06f * sin(a)))
    drawCircle(Brush.radialGradient(listOf(sky.copy(alpha = if (dark) 0.22f else 0.28f), Color.Transparent), c1, w * 0.6f), w * 0.6f, c1)
    drawCircle(Brush.radialGradient(listOf(navy.copy(alpha = if (dark) 0.45f else 0.18f), Color.Transparent), c2, w * 0.7f), w * 0.7f, c2)
}

// ---------------------------------------------------------------- Luar Angkasa

private class Star(val x: Float, val y: Float, val r: Float, val tw: Int, val off: Float, val tint: Color)

private val STARS: List<Star> = run {
    val rnd = kotlin.random.Random(11)
    val tints = listOf(Color.White, Color(0xFFCFE3FF), Color(0xFFFFE9C7), Color(0xFFE2D4FF))
    List(110) {
        Star(rnd.nextFloat(), rnd.nextFloat(), 0.6f + rnd.nextFloat() * 1.8f, 1 + rnd.nextInt(4), rnd.nextFloat(), tints[rnd.nextInt(tints.size)])
    }
}

private fun DrawScope.drawSpace(p: Float) {
    val w = size.width
    val h = size.height
    val a = p * TAU
    drawRect(Brush.verticalGradient(listOf(Color(0xFF070A1C), Color(0xFF140F3D), Color(0xFF0A1B3D))))
    // nebula melayang
    val n1 = Offset(w * (0.2f + 0.05f * sin(a)), h * (0.25f + 0.04f * cos(a)))
    val n2 = Offset(w * (0.85f + 0.04f * cos(a)), h * (0.7f + 0.05f * sin(a)))
    drawCircle(Brush.radialGradient(listOf(Color(0xFF7C4DFF).copy(alpha = 0.38f), Color.Transparent), n1, w * 0.65f), w * 0.65f, n1)
    drawCircle(Brush.radialGradient(listOf(Color(0xFF00B8D4).copy(alpha = 0.24f), Color.Transparent), n2, w * 0.6f), w * 0.6f, n2)
    // bintang berkedip
    for (s in STARS) {
        val tw = 0.5f + 0.5f * sin(TAU * (p * s.tw + s.off))
        drawCircle(s.tint.copy(alpha = 0.3f + 0.7f * tw), s.r * (0.8f + 0.4f * tw), Offset(s.x * w, s.y * h))
    }
    // planet bercincin
    val c = Offset(w * 0.78f, h * 0.2f)
    val r = w * 0.11f
    val ringW = r * 3.2f
    val ringH = r * 0.9f
    val ringTop = Offset(c.x - ringW / 2, c.y - ringH / 2)
    val ringStroke = Stroke(width = r * 0.16f)
    val ringCol = Color(0xFFFFD27A).copy(alpha = 0.75f)
    rotate(-18f, c) { drawOval(ringCol, ringTop, Size(ringW, ringH), style = ringStroke) }
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFFB86B), Color(0xFFB4532A), Color(0xFF4A1D3A)), c - Offset(r * 0.35f, r * 0.35f), r * 1.5f),
        r, c
    )
    rotate(-18f, c) { drawArc(ringCol, 0f, 180f, false, ringTop, Size(ringW, ringH), style = ringStroke) }
    // bintang jatuh (dua kali per putaran)
    val t = (p * 2f) % 1f
    if (t < 0.14f) {
        val k = t / 0.14f
        val sx = w * (0.15f + 0.45f * k)
        val sy = h * (0.08f + 0.3f * k)
        val tail = w * 0.18f
        drawLine(
            Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 1f - k)), Offset(sx - tail, sy - tail * 0.55f), Offset(sx, sy)),
            Offset(sx - tail, sy - tail * 0.55f), Offset(sx, sy), strokeWidth = 3f, cap = StrokeCap.Round
        )
    }
}

// ---------------------------------------------------------------- Alam

private fun hillY(x: Float, w: Float, h: Float, base: Float, amp: Float, freq: Float, shift: Float): Float =
    h * base + amp * sin(TAU * (freq * x / w + shift))

private fun DrawScope.hill(base: Float, amp: Float, freq: Float, shift: Float, color: Color) {
    val path = Path()
    path.moveTo(0f, size.height)
    var x = 0f
    while (x <= size.width + 12f) {
        path.lineTo(x, hillY(x, size.width, size.height, base, amp, freq, shift))
        x += 12f
    }
    path.lineTo(size.width, size.height)
    path.close()
    drawPath(path, color)
}

private val PINES = listOf(0.06f, 0.17f, 0.3f, 0.52f, 0.66f, 0.83f, 0.94f)

private fun DrawScope.pine(x: Float, baseY: Float, s: Float, sway: Float, col: Color) {
    rotate(sway, Offset(x, baseY)) {
        for (i in 0 until 3) {
            val top = baseY - s * (1.0f - i * 0.22f)
            val bw = s * (0.34f + i * 0.12f)
            val path = Path()
            path.moveTo(x, top - s * 0.05f)
            path.lineTo(x - bw, top + s * 0.42f)
            path.lineTo(x + bw, top + s * 0.42f)
            path.close()
            drawPath(path, col)
        }
    }
}

private fun DrawScope.drawNature(p: Float, dark: Boolean) {
    val w = size.width
    val h = size.height
    val a = p * TAU
    val sky = if (dark) listOf(Color(0xFF08140D), Color(0xFF0F2A1E), Color(0xFF173A2A))
    else listOf(Color(0xFF8FD3F4), Color(0xFFD5F0DD), Color(0xFFEFF9D8))
    drawRect(Brush.verticalGradient(sky))
    // matahari / bulan berdenyut
    val sc = Offset(w * 0.76f, h * 0.15f)
    val pulse = 1f + 0.05f * sin(a * 2)
    val glow = if (dark) Color(0xFFDCF5D0) else Color(0xFFFFE08A)
    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.55f), Color.Transparent), sc, w * 0.24f * pulse), w * 0.24f * pulse, sc)
    drawCircle(if (dark) Color(0xFFF1F5E9) else Color(0xFFFFD54F), w * 0.06f * pulse, sc)
    if (!dark) {
        // awan melayang (satu putaran per siklus, mulus)
        for (i in 0 until 3) {
            val cx = (((i * 0.37f + p) % 1f) * 1.5f - 0.25f) * w
            val cy = h * (0.1f + i * 0.07f)
            val r = w * (0.05f + 0.01f * i)
            val col = Color.White.copy(alpha = 0.8f)
            drawCircle(col, r, Offset(cx, cy))
            drawCircle(col, r * 1.25f, Offset(cx + r * 1.2f, cy - r * 0.2f))
            drawCircle(col, r * 0.9f, Offset(cx + r * 2.3f, cy + r * 0.05f))
            drawCircle(col, r * 0.8f, Offset(cx - r * 1.1f, cy + r * 0.15f))
        }
    }
    val c1 = if (dark) Color(0xFF14301F) else Color(0xFF9AD39A)
    val c2 = if (dark) Color(0xFF0E2417) else Color(0xFF63B26F)
    val c3 = if (dark) Color(0xFF08170F) else Color(0xFF3C8D4F)
    hill(0.62f, h * 0.035f, 1.3f, 0.1f, c1)
    hill(0.72f, h * 0.04f, 1.8f, 0.45f, c2)
    hill(0.84f, h * 0.035f, 1.1f, 0.8f, c3)
    // pohon pinus di bukit depan, bergoyang pelan
    val tree = if (dark) Color(0xFF0B2A19) else Color(0xFF2E7D32)
    PINES.forEachIndexed { i, fx ->
        val x = fx * w
        val y = hillY(x, w, h, 0.84f, h * 0.035f, 1.1f, 0.8f) + 6f
        pine(x, y, h * (0.07f + 0.015f * (i % 3)), 1.6f * sin(a * 2 + i), tree)
    }
    // partikel: kunang-kunang (gelap) / daun jatuh (terang)
    for (i in 0 until 16) {
        val fx = ((i * 0.6180339f) % 1f)
        val fy = ((i * 0.3819660f + 0.17f) % 1f)
        if (dark) {
            val x = (fx + 0.03f * sin(a * 2 + i)) * w
            val y = (0.45f + 0.5f * fy) * h + h * 0.02f * sin(a * 3 + i * 2)
            val tw = 0.5f + 0.5f * sin(a * 3 + i)
            val col = Color(0xFFD9F57A)
            drawCircle(col.copy(alpha = 0.18f * tw), 14f, Offset(x, y))
            drawCircle(col.copy(alpha = 0.4f + 0.6f * tw), 3f, Offset(x, y))
        } else {
            val k = 1 + (i % 2)
            val y = (((fy + p * k) % 1f)) * h
            val x = (fx + 0.04f * sin(a * 2 + i)) * w
            rotate(((p * 360f * k) + i * 40f) % 360f, Offset(x, y)) {
                drawOval(Color(0xFF7CB342).copy(alpha = 0.75f), Offset(x - 7f, y - 3.5f), Size(14f, 7f))
            }
        }
    }
}

// ---------------------------------------------------------------- Samudra

private fun DrawScope.wave(base: Float, amp: Float, freq: Float, phaseShift: Float, color: Color) {
    val path = Path()
    path.moveTo(0f, size.height)
    var x = 0f
    while (x <= size.width + 10f) {
        path.lineTo(x, size.height * base + amp * sin(TAU * (freq * x / size.width + phaseShift)))
        x += 10f
    }
    path.lineTo(size.width, size.height)
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawOcean(p: Float, dark: Boolean) {
    val w = size.width
    val h = size.height
    val g = if (dark) listOf(Color(0xFF031521), Color(0xFF06304A), Color(0xFF0A4A6E))
    else listOf(Color(0xFFC8EEFF), Color(0xFF63BDEB), Color(0xFF0E78B5))
    drawRect(Brush.verticalGradient(g))
    // berkas cahaya dari permukaan
    for (i in 0 until 3) {
        val x0 = w * (0.15f + 0.3f * i) + w * 0.03f * sin(p * TAU + i)
        val path = Path()
        path.moveTo(x0, 0f)
        path.lineTo(x0 + w * 0.12f, 0f)
        path.lineTo(x0 + w * 0.3f, h * 0.7f)
        path.lineTo(x0 + w * 0.12f, h * 0.7f)
        path.close()
        drawPath(path, Color.White.copy(alpha = if (dark) 0.04f else 0.1f))
    }
    // gelembung naik
    for (i in 0 until 18) {
        val fx = (i * 0.6180339f) % 1f
        val k = 1 + (i % 2)
        val y = (1f - ((p * k + i * 0.137f) % 1f)) * h
        val x = (fx + 0.02f * sin(p * TAU * 2 + i)) * w
        val r = 4f + (i % 5) * 2.5f
        drawCircle(Color.White.copy(alpha = 0.1f), r, Offset(x, y))
        drawCircle(Color.White.copy(alpha = 0.4f), r, Offset(x, y), style = Stroke(1.5f))
    }
    // empat lapis ombak, arah bergantian
    val cols = if (dark) listOf(Color(0xFF0B5A85), Color(0xFF0A4A6E), Color(0xFF083A58), Color(0xFF052B43))
    else listOf(Color(0xFFFFFFFF), Color(0xFF8ED3F5), Color(0xFF3AA0D6), Color(0xFF0B6BA8))
    for (i in 0 until 4) {
        val dir = if (i % 2 == 0) 1f else -1f
        val alpha = if (dark) 0.55f else (if (i == 0) 0.35f else 0.45f)
        wave(0.58f + 0.1f * i, h * (0.014f + 0.004f * i), 1.5f + 0.5f * i, dir * p * (i + 1) + i * 0.2f, cols[i].copy(alpha = alpha))
    }
}
