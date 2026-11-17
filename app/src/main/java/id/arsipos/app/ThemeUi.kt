package id.arsipos.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/** Panel pengaturan tema: kartu pratinjau, mode terang/gelap, intensitas & animasi latar. */
@Composable
fun ThemePicker(vm: MainVM) {
    val haptic = LocalHapticFeedback.current
    val spec = themeById(vm.themeId)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tema & latar", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            THEMES.forEach { t ->
                val sel = t.id == vm.themeId
                val edge by animateColorAsState(
                    if (sel) MaterialTheme.colorScheme.primary else Color.Transparent, label = "tb"
                )
                val sc by animateFloatAsState(if (sel) 1f else 0.94f, label = "ts")
                Box(
                    Modifier.width(112.dp).height(160.dp).scale(sc)
                        .clip(RoundedCornerShape(18.dp))
                        .border(BorderStroke(3.dp, edge), RoundedCornerShape(18.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.chooseTheme(t.id)
                        }
                ) {
                    ArtCanvas(t.id, t.darkOnly || vm.themeMode == 2, animated = false, modifier = Modifier.fillMaxSize())
                    Box(
                        Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                            .padding(8.dp, 18.dp, 8.dp, 8.dp)
                    ) {
                        Column {
                            Text(t.emoji + " " + t.label, color = Color.White, style = MaterialTheme.typography.labelLarge)
                            Text(t.desc, color = Color(0xFFE2E8F0), style = MaterialTheme.typography.labelSmall, maxLines = 2)
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Sistem", "Terang", "Gelap").forEachIndexed { i, label ->
                FilterChip(
                    selected = (if (spec.darkOnly) 2 else vm.themeMode) == i,
                    enabled = !spec.darkOnly,
                    onClick = { vm.chooseMode(i) },
                    label = { Text(label) }
                )
            }
        }
        if (spec.darkOnly) Text("Tema ini selalu gelap.", style = MaterialTheme.typography.bodySmall)
        Text("Intensitas latar: ${(vm.bgIntensity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = vm.bgIntensity,
            onValueChange = { vm.chooseIntensity(it) },
            onValueChangeFinished = { vm.commitIntensity() },
            valueRange = 0.2f..1f
        )
        Row(Modifier.clickable { vm.chooseAnimated(!vm.bgAnimated) }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(vm.bgAnimated, { vm.chooseAnimated(it) })
            Text("Animasi latar (nonaktifkan untuk hemat baterai)")
        }
    }
}
