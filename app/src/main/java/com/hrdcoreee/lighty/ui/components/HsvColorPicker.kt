package com.hrdcoreee.lighty.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Full HSV color picker.
 *
 * The two axes are intentionally decoupled: the saturation/value square reports
 * only [onSaturationValueChange] and the hue bar reports only [onHueChange].
 * Neither control ever re-sends the other dimension, so adjusting one can't drag
 * the other back to a stale value. The current [hue]/[saturation]/[value] come
 * straight from the caller's state and are only used for rendering.
 */
@Composable
fun HsvColorPicker(
    hue: Float,
    saturation: Float,
    value: Float,
    onHueChange: (Float) -> Unit,
    onSaturationValueChange: (saturation: Float, value: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SaturationValuePanel(
            hue = hue,
            saturation = saturation,
            value = value,
            onChange = onSaturationValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f)
        )
        HueBar(
            hue = hue,
            onHueChange = onHueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
        )
    }
}

@Composable
private fun SaturationValuePanel(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (saturation: Float, value: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep the gesture handlers pointing at the latest callback even though
    // pointerInput(Unit) never restarts.
    val currentOnChange by rememberUpdatedState(onChange)

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                fun report(x: Float, y: Float) = currentOnChange(
                    (x / size.width).coerceIn(0f, 1f),
                    (1f - y / size.height).coerceIn(0f, 1f)
                )
                detectTapGestures { pos -> report(pos.x, pos.y) }
            }
            .pointerInput(Unit) {
                fun report(x: Float, y: Float) = currentOnChange(
                    (x / size.width).coerceIn(0f, 1f),
                    (1f - y / size.height).coerceIn(0f, 1f)
                )
                detectDragGestures(
                    onDragStart = { pos -> report(pos.x, pos.y) }
                ) { change, _ ->
                    change.consume()
                    report(change.position.x, change.position.y)
                }
            }
    ) {
        val hueColor = Color.hsv(hue, 1f, 1f)
        // Saturation left→right, value top→bottom.
        drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

        val cx = (saturation * size.width).coerceIn(0f, size.width)
        val cy = ((1f - value) * size.height).coerceIn(0f, size.height)
        drawThumb(Offset(cx, cy), Color.hsv(hue, saturation, value))
    }
}

@Composable
private fun HueBar(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnHueChange by rememberUpdatedState(onHueChange)
    val hueColors = hueSpectrum()

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                fun report(x: Float) = currentOnHueChange((x / size.width).coerceIn(0f, 1f) * 360f)
                detectTapGestures { pos -> report(pos.x) }
            }
            .pointerInput(Unit) {
                fun report(x: Float) = currentOnHueChange((x / size.width).coerceIn(0f, 1f) * 360f)
                detectDragGestures(
                    onDragStart = { pos -> report(pos.x) }
                ) { change, _ ->
                    change.consume()
                    report(change.position.x)
                }
            }
    ) {
        drawRect(Brush.horizontalGradient(hueColors))
        val cx = (hue / 360f * size.width).coerceIn(0f, size.width)
        drawThumb(Offset(cx, size.height / 2f), Color.hsv(hue, 1f, 1f))
    }
}

private fun DrawScope.drawThumb(center: Offset, fill: Color) {
    // Outer ring for contrast on any background, then a filled dot of the picked color.
    drawCircle(Color.Black.copy(alpha = 0.25f), radius = 14.dp.toPx(), center = center)
    drawCircle(Color.White, radius = 12.dp.toPx(), center = center, style = Stroke(width = 3.dp.toPx()))
    drawCircle(fill, radius = 9.dp.toPx(), center = center)
}

private fun hueSpectrum(): List<Color> = listOf(
    Color.hsv(0f, 1f, 1f),
    Color.hsv(60f, 1f, 1f),
    Color.hsv(120f, 1f, 1f),
    Color.hsv(180f, 1f, 1f),
    Color.hsv(240f, 1f, 1f),
    Color.hsv(300f, 1f, 1f),
    Color.hsv(360f, 1f, 1f),
)
