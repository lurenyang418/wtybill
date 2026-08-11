package tech.wtybill.app.ui.room

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import tech.wtybill.app.danmaku.DanmakuMessage
import tech.wtybill.app.danmaku.DanmakuTrackSnapshot
import kotlin.math.roundToInt

@Composable
fun DanmakuOverlay(
    tracks: List<DanmakuTrackSnapshot>,
    textSize: Int = 16,
    opacity: Float = 0.85f,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp)) {
        val trackHeight = maxHeight / tracks.size.coerceAtLeast(1)
        tracks.forEachIndexed { trackIndex, track ->
            key(trackIndex) {
                DanmakuTrack(
                    track = track,
                    textSize = textSize,
                    opacity = opacity,
                    trackY = trackHeight * trackIndex,
                    viewportWidth = maxWidth,
                )
            }
        }
    }
}

@Composable
private fun DanmakuTrack(
    track: DanmakuTrackSnapshot,
    textSize: Int,
    opacity: Float,
    trackY: androidx.compose.ui.unit.Dp,
    viewportWidth: androidx.compose.ui.unit.Dp,
) {
    track.messages.forEach { message ->
        key(message.id) {
            DanmakuTrackMessage(message, textSize, opacity, trackY, viewportWidth)
        }
    }
}

@Composable
private fun DanmakuTrackMessage(
    message: DanmakuMessage,
    textSize: Int,
    opacity: Float,
    trackY: androidx.compose.ui.unit.Dp,
    viewportWidth: androidx.compose.ui.unit.Dp,
) {
    val label = if (message.username.isBlank()) message.text else "${message.username}: ${message.text}"
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val style = LocalTextStyle.current.copy(fontSize = textSize.sp, fontWeight = FontWeight.Medium)
    val measuredWidth = remember(label, textSize) {
        textMeasurer.measure(AnnotatedString(label), style = style).size.width.toFloat()
    }
    val viewportPx = with(density) { viewportWidth.toPx() }
    val yPx = with(density) { trackY.toPx() }
    val x = remember(message.id, viewportPx) { Animatable(viewportPx) }
    LaunchedEffect(message.id, viewportPx) {
        x.snapTo(viewportPx)
        val distance = viewportPx + measuredWidth + with(density) { 32.dp.toPx() }
        val duration = (distance / with(density) { 55.dp.toPx() } * 1000).roundToInt().coerceIn(4500, 14000)
        x.animateTo(-measuredWidth - with(density) { 16.dp.toPx() }, tween(durationMillis = duration))
    }
    Text(
        text = label,
        color = Color(message.color).copy(alpha = opacity),
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = Modifier.offset { IntOffset(x.value.roundToInt(), yPx.roundToInt()) },
    )
}
