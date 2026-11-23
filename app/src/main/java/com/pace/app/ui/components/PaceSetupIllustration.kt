package com.pace.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

internal enum class PaceSetupArtwork { Welcome, Activities, Units }

@Composable
internal fun PaceSetupIllustration(artwork: PaceSetupArtwork, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Canvas(
        modifier.fillMaxWidth()
            .height(if (artwork == PaceSetupArtwork.Welcome) 176.dp else 88.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.primaryContainer),
    ) {
        val unit = size.height / 100f
        scale(unit, unit, pivot = center) {
            val ink = colors.onPrimaryContainer
            val accent = colors.primary
            when (artwork) {
                PaceSetupArtwork.Welcome -> {
                    drawCircle(colors.surface, radius = 32f, center = center + Offset(18f, -4f))
                    val route = Path().apply {
                        moveTo(center.x - 95f, center.y + 37f)
                        cubicTo(center.x - 60f, center.y + 37f, center.x - 64f, center.y + 5f, center.x - 24f, center.y + 18f)
                        cubicTo(center.x + 33f, center.y + 41f, center.x + 43f, center.y + 5f, center.x + 95f, center.y + 14f)
                    }
                    drawPath(route, accent, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
                    drawCircle(accent, 3.5f, center + Offset(-67f, 28f))
                    drawCircle(accent, 3.5f, center + Offset(67f, 16f))
                    drawSport(PaceSport.Running, ink, center + Offset(8f, -9f), 2.8f)
                }
                PaceSetupArtwork.Activities -> {
                    PaceSport.entries.forEachIndexed { index, sport ->
                        val position = center + Offset((index - 1.5f) * 58f, 0f)
                        drawCircle(colors.surface, radius = 23f, center = position)
                        drawSport(sport, ink, position, 1.6f)
                    }
                }
                PaceSetupArtwork.Units -> {
                    val left = center.x - 78f
                    val baseline = center.y + 15f
                    drawLine(accent, Offset(left, baseline), Offset(left + 156f, baseline), 3f, StrokeCap.Round)
                    repeat(9) { index ->
                        val x = left + index * 19.5f
                        val height = if (index % 2 == 0) 28f else 15f
                        drawLine(ink, Offset(x, baseline - height), Offset(x, baseline), 3f, StrokeCap.Round)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PaceSportGlyph(sport: PaceSport, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawSport(sport, color, center, size.minDimension / 25f) }
}

private fun DrawScope.drawSport(sport: PaceSport, color: Color, origin: Offset, unit: Float) {
    fun point(x: Float, y: Float) = origin + Offset(x * unit, y * unit)
    fun line(fromX: Float, fromY: Float, toX: Float, toY: Float, width: Float = 2f) {
        drawLine(color, point(fromX, fromY), point(toX, toY), width * unit, StrokeCap.Round)
    }

    if (sport == PaceSport.Cycling) {
        drawCircle(color, 4.7f * unit, point(-7f, 4f), style = Stroke(1.6f * unit))
        drawCircle(color, 4.7f * unit, point(7f, 4f), style = Stroke(1.6f * unit))
        line(-7f, 4f, -2f, -3f)
        line(-2f, -3f, 1f, 4f)
        line(1f, 4f, -7f, 4f)
        line(1f, 4f, 5f, -4f)
        line(5f, -4f, 7f, 4f)
        line(4f, -5f, 7f, -5f)
        line(-4f, -4f, -1f, -4f)
        return
    }

    val stride = when (sport) {
        PaceSport.Running -> 9f
        PaceSport.Jogging -> 7f
        else -> 5f
    }
    val lean = if (sport == PaceSport.Running) 3f else 1f
    drawCircle(color, 2.4f * unit, point(lean + 1f, -8f))
    line(lean, -4f, -1f, 2f, 3.6f)
    line(lean, -4f, 4f, 0f)
    line(4f, 0f, stride * .7f, -2f)
    line(lean, -4f, -5f, -2f)
    line(-5f, -2f, -stride * .7f, 1f)
    line(-1f, 2f, -stride * .45f, 6f, 2.6f)
    line(-stride * .45f, 6f, -stride, 8f, 2.6f)
    line(-1f, 2f, stride * .55f, 5f, 2.6f)
    line(stride * .55f, 5f, stride, 9f, 2.6f)
}
