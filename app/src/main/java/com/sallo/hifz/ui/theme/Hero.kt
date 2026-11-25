package com.sallo.hifz.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sallo.hifz.design.HeroStyle

/**
 * The hero card's background in [d]'s style, clipped to a rounded rectangle of [corner]:
 * a gradient; a gradient under a faint lattice of eight-pointed stars; a paper card inside a double
 * frame; or one flat color with an accent bar along its start edge.
 */
fun Modifier.heroBackground(d: DesignColors, corner: Dp): Modifier {
    val shaped = this.clip(RoundedCornerShape(corner))
    return when (d.heroStyle) {
        HeroStyle.GRADIENT -> shaped.background(Brush.linearGradient(d.hero))
        HeroStyle.STARS -> shaped.background(Brush.linearGradient(d.hero)).starLattice(d)
        HeroStyle.FRAME -> shaped.background(Brush.linearGradient(d.hero)).doubleFrame(d, corner)
        HeroStyle.FLAT -> shaped.background(d.hero[1]).startBar(d)
    }
}

/** Eight-pointed stars (۞), two squares each, in staggered rows; drawn once per size. */
private fun Modifier.starLattice(d: DesignColors): Modifier = drawWithCache {
    val step = 44.dp.toPx()
    val radius = 9.dp.toPx()
    val half = radius * 0.7071f
    val path = Path()
    var row = 0
    var y = step / 3
    while (y < size.height + step) {
        var x = if (row % 2 == 0) step / 3 else step / 3 + step / 2
        while (x < size.width + step) {
            path.addRect(Rect(x - half, y - half, x + half, y + half))
            path.moveTo(x, y - radius)
            path.lineTo(x + radius, y)
            path.lineTo(x, y + radius)
            path.lineTo(x - radius, y)
            path.close()
            x += step
        }
        y += step * 0.75f
        row++
    }
    val stroke = Stroke(1.dp.toPx())
    val color = d.onHero.copy(alpha = 0.10f)
    onDrawBehind { drawPath(path, color, style = stroke) }
}

/** A thin double frame inset from the edge, with a small diamond at the middle of its top and bottom. */
private fun Modifier.doubleFrame(d: DesignColors, corner: Dp): Modifier = drawWithCache {
    val outer = 7.dp.toPx()
    val inner = 11.dp.toPx()
    val outerRadius = (corner.toPx() - outer).coerceAtLeast(0f)
    val innerRadius = (corner.toPx() - inner).coerceAtLeast(0f)
    val outerStroke = Stroke(1.6.dp.toPx())
    val innerStroke = Stroke(0.8.dp.toPx())
    val gem = 4.dp.toPx()
    val diamonds = Path().apply {
        for (cy in listOf(outer, size.height - outer)) {
            val cx = size.width / 2
            moveTo(cx, cy - gem)
            lineTo(cx + gem, cy)
            lineTo(cx, cy + gem)
            lineTo(cx - gem, cy)
            close()
        }
    }
    onDrawBehind {
        drawRoundRect(
            d.accent,
            topLeft = Offset(outer, outer),
            size = Size(size.width - 2 * outer, size.height - 2 * outer),
            cornerRadius = CornerRadius(outerRadius),
            style = outerStroke,
        )
        drawRoundRect(
            d.accent.copy(alpha = 0.55f),
            topLeft = Offset(inner, inner),
            size = Size(size.width - 2 * inner, size.height - 2 * inner),
            cornerRadius = CornerRadius(innerRadius),
            style = innerStroke,
        )
        drawPath(diamonds, d.accent)
    }
}

/** An accent bar along the start edge (the right in Arabic). */
private fun Modifier.startBar(d: DesignColors): Modifier = drawWithCache {
    val bar = 6.dp.toPx()
    onDrawBehind {
        val x = if (layoutDirection == LayoutDirection.Rtl) size.width - bar else 0f
        drawRect(d.accent, topLeft = Offset(x, 0f), size = Size(bar, size.height))
    }
}
