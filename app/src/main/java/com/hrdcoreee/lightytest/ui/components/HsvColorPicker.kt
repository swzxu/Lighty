package com.hrdcoreee.lightytest.ui.components

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
 * Full HSV color picker: a saturation/value square driven by [hue], plus a hue bar.
 * Fires [onColorChange] with the new (hue, saturation, value) on any interaction.
 */
@Composable
fun HsvColorPicker(
    hue: Float,
    saturation: Float,
    value: Float,
    onColorChange: (hue: Float, saturation: Float, value: Float) -> Unit,
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
            onChange = { s, v -> onColorChange(hue, s, v) },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f)
        )
        HueBar(
            hue = hue,
            onHueChange = { h -> onColorChange(h, saturation, value) },
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
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    onChange(
                        (pos.x / size.width).coerceIn(0f, 1f),
                        (1f - pos.y / size.height).coerceIn(0f, 1f)
                    )
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        onChange(
                            (pos.x / size.width).coerceIn(0f, 1f),
                            (1f - pos.y / size.height).coerceIn(0f, 1f)
                        )
                    }
                ) { change, _ ->
                    change.consume()
                    onChange(
                        (change.position.x / size.width).coerceIn(0f, 1f),
                        (1f - change.position.y / size.height).coerceIn(0f, 1f)
                    )
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
    val hueColors = remember0to360()
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    onHueChange((pos.x / size.width).coerceIn(0f, 1f) * 360f)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        onHueChange((pos.x / size.width).coerceIn(0f, 1f) * 360f)
                    }
                ) { change, _ ->
                    change.consume()
                    onHueChange((change.position.x / size.width).coerceIn(0f, 1f) * 360f)
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

private fun remember0to360(): List<Color> = listOf(
    Color.hsv(0f, 1f, 1f),
    Color.hsv(60f, 1f, 1f),
    Color.hsv(120f, 1f, 1f),
    Color.hsv(180f, 1f, 1f),
    Color.hsv(240f, 1f, 1f),
    Color.hsv(300f, 1f, 1f),
    Color.hsv(360f, 1f, 1f),
)

