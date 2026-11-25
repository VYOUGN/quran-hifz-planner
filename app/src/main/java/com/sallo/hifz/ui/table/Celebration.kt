package com.sallo.hifz.ui.table

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sallo.hifz.ui.theme.LocalDesign
import kotlinx.coroutines.delay
import kotlin.random.Random

@Stable
class CelebrationState {
    var message by mutableStateOf("")
        private set
    var big by mutableStateOf(false)
        private set
    var key by mutableIntStateOf(0)
        private set

    fun fire(message: String, big: Boolean) {
        this.message = message
        this.big = big
        key++
    }
}

@Composable
fun rememberCelebration(): CelebrationState = remember { CelebrationState() }

val encouragements = listOf(
    "ما شاء الله، أحسنت!",
    "بارك الله فيك، استمر",
    "زادك الله حرصاً",
    "ثبّتك الله وأعانك",
    "خطوة جديدة نحو الختم",
)

fun milestoneMessage(fraction: Double): String = when (fraction) {
    1.0 -> "تمّت الخطة! تقبّل الله منك 🎉"
    0.75 -> "وصلت ثلاثة أرباع الخطة 🎉"
    0.5 -> "وصلت نصف الخطة، ما شاء الله 🎉"
    else -> "أنجزت ربع الخطة، استمر 🎉"
}

private const val DURATION = 2.6f

private class Particle(
    val x: Float,
    val vx: Float,
    val vy: Float,
    val size: Float,
    val color: Color,
    val spin: Float,
    val delay: Float,
)

/** Confetti and a message pill; draws nothing until the first [CelebrationState.fire]. */
@Composable
fun CelebrationOverlay(state: CelebrationState) {
    val key = state.key
    if (key == 0) return
    Confetti(key, if (state.big) 140 else 60)
    var visible by remember(key) { mutableStateOf(true) }
    LaunchedEffect(key) {
        delay(2300)
        visible = false
    }
    Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(visible, enter = fadeIn() + scaleIn(initialScale = 0.7f), exit = fadeOut()) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 8.dp,
            ) {
                Text(
                    "✨ ${state.message}",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = if (state.big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun Confetti(key: Int, count: Int) {
    val colors = listOf(LocalDesign.current.accent, Color(0xFF2BA38A), Color(0xFF7CC9DE), Color(0xFFF07C7C), Color(0xFFB8A9F0), Color(0xFFFFE08A))
    val particles = remember(key) {
        val r = Random(key)
        List(count) {
            Particle(
                x = r.nextFloat(),
                vx = (r.nextFloat() - 0.5f) * 0.25f,
                vy = 0.2f + r.nextFloat() * 0.4f,
                size = 8f + r.nextFloat() * 12f,
                color = colors[r.nextInt(colors.size)],
                spin = (r.nextFloat() - 0.5f) * 900f,
                delay = r.nextFloat() * 0.4f,
            )
        }
    }
    var t by remember(key) { mutableFloatStateOf(0f) }
    LaunchedEffect(key) {
        val start = withFrameMillis { it }
        while (t < DURATION) {
            withFrameMillis { now -> t = (now - start) / 1000f }
        }
    }
    if (t >= DURATION) return
    Canvas(Modifier.fillMaxSize()) {
        val alpha = (1f - t / DURATION).coerceIn(0f, 1f)
        particles.forEach { p ->
            val life = t - p.delay
            if (life <= 0f) return@forEach
            val x = (p.x + p.vx * life) * size.width
            val y = -40f + (p.vy * life + 0.3f * life * life) * size.height
            rotate(p.spin * life, pivot = Offset(x, y)) {
                drawRect(p.color.copy(alpha = alpha), topLeft = Offset(x - p.size / 2, y - p.size / 4), size = Size(p.size, p.size / 2))
            }
        }
    }
}
