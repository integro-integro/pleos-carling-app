package work.integro.carling.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import work.integro.carling.game.TimeBucket
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** 시간대별 하늘색(위·가운데·아래)과 지평선 빛. */
private class Sky(val top: Color, val mid: Color, val bottom: Color, val far: Color, val near: Color, val glow: Color, val night: Boolean)

private fun skyOf(bucket: TimeBucket): Sky = when (bucket) {
    TimeBucket.MORNING -> Sky(Color(0xFFFCEBE6), Color(0xFFE9E5FF), Color(0xFFD9DCFF), Color(0xFFE6E2FF), Color(0xFFD2D0FA), Color(0xFFFFD9C7), false)
    TimeBucket.DAY -> Sky(Color(0xFFE7F1FF), Color(0xFFDFE2FF), Color(0xFFD0D5FB), Color(0xFFE3E6FF), Color(0xFFCDD0F8), Color(0xFFBFE8FF), false)
    TimeBucket.EVENING -> Sky(Color(0xFFEDE3FF), Color(0xFFD9D1FB), Color(0xFFC2BEF2), Color(0xFFDCD4FB), Color(0xFFBDB6EE), Color(0xFFF6D7F0), false)
    TimeBucket.NIGHT -> Sky(Color(0xFF23265E), Color(0xFF30347A), Color(0xFF474D9C), Color(0xFF3A3F8C), Color(0xFF2E3278), Color(0xFF7FE7C4), true)
}

/**
 * Pleos 화면처럼 차분한 라벤더 블루 그라데이션 배경. 지평선 두 겹 위에 빛나는 선이 지나가고,
 * 시간대마다 하늘색이 바뀌어 같은 화면이 계속되지 않는다. 큰 빛 덩어리가 아주 천천히 흐른다.
 * [still]이면(주행 중) 아무것도 움직이지 않는다.
 */
@Composable
fun Backdrop(bucket: TimeBucket, still: Boolean, modifier: Modifier = Modifier) {
    val sky = remember(bucket) { skyOf(bucket) }
    val drift = if (still) 0f else {
        val t = rememberInfiniteTransition(label = "backdrop")
        val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart), label = "drift")
        v
    }
    val stars = remember { List(70) { Triple(Random(it).nextFloat(), Random(it * 7 + 3).nextFloat() * 0.55f, Random(it * 13 + 1).nextFloat()) } }
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(0f to sky.top, 0.55f to sky.mid, 1f to sky.bottom))
        // 천천히 흐르는 빛 덩어리
        val a = (drift * 2 * PI).toFloat()
        orb(Offset(size.width * (0.22f + 0.05f * sin(a)), size.height * 0.28f), size.minDimension * 0.55f, sky.glow.copy(alpha = if (sky.night) 0.18f else 0.45f))
        orb(Offset(size.width * (0.80f - 0.04f * sin(a + 1.7f)), size.height * (0.20f + 0.03f * sin(a * 2))), size.minDimension * 0.45f, Color.White.copy(alpha = if (sky.night) 0.06f else 0.5f))
        if (sky.night) {
            stars.forEach { (x, y, s) ->
                val twinkle = 0.45f + 0.55f * ((sin((drift * 12 + s * 6.28f) * PI.toFloat()) + 1f) / 2f)
                drawCircle(Color.White.copy(alpha = 0.35f + 0.5f * s * twinkle), 1.2f + s * 2.2f, Offset(x * size.width, y * size.height))
            }
        }
        horizon(0.60f, 0.07f, sky.far, sky.glow.copy(alpha = 0.9f), phase = 0.3f)
        horizon(0.72f, 0.05f, sky.near, Color.White.copy(alpha = if (sky.night) 0.35f else 0.85f), phase = 1.4f)
        road(sky.night)
    }
}

private fun DrawScope.orb(center: Offset, radius: Float, color: Color) {
    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, radius), radius, center)
}

/** 부드럽게 휜 지평선 한 겹: 아래는 면으로 채우고, 윗선에 빛나는 선을 긋는다. */
private fun DrawScope.horizon(yFrac: Float, amp: Float, fill: Color, line: Color, phase: Float) {
    val w = size.width
    val h = size.height
    val path = Path()
    val steps = 48
    for (i in 0..steps) {
        val x = w * i / steps
        val y = h * (yFrac - amp * sin((i.toFloat() / steps) * PI.toFloat() * 0.9f + phase) * 0.6f)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    val edge = Path().apply { addPath(path) }
    path.lineTo(w, h)
    path.lineTo(0f, h)
    path.close()
    drawPath(path, Brush.verticalGradient(listOf(fill, fill.copy(alpha = 0.6f)), startY = h * (yFrac - amp), endY = h))
    drawPath(edge, line.copy(alpha = line.alpha * 0.35f), style = Stroke(width = 10f, cap = StrokeCap.Round))
    drawPath(edge, line, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
}

/** 화면 가운데 소실점으로 모이는 옅은 도로 선. */
private fun DrawScope.road(night: Boolean) {
    val vx = size.width * 0.5f
    val vy = size.height * 0.66f
    val c = Color.White.copy(alpha = if (night) 0.12f else 0.4f)
    listOf(-0.9f, -0.35f, 0.35f, 0.9f).forEach { k ->
        drawLine(Brush.verticalGradient(listOf(Color.Transparent, c), startY = vy, endY = size.height), Offset(vx + k * 40f, vy), Offset(vx + k * size.width * 0.55f, size.height), strokeWidth = 3f)
    }
}

/** 차 안 배경 그림의 가로세로 비율(2688×1520). */
internal const val CABIN_ASPECT = 2688f / 1520f

/** 구도 × 시간대 배경 그림. 원본 생성(2026-11-12, 낮 그림을 기준으로 아침·저녁·밤을 같은 실내로). */
@androidx.annotation.DrawableRes
internal fun cabinRes(view: work.integro.carling.game.CabinView, bucket: TimeBucket): Int = when (view) {
    work.integro.carling.game.CabinView.DASH -> when (bucket) {
        TimeBucket.MORNING -> work.integro.carling.R.drawable.bg_dash_morning
        TimeBucket.DAY -> work.integro.carling.R.drawable.bg_dash_day
        TimeBucket.EVENING -> work.integro.carling.R.drawable.bg_dash_evening
        TimeBucket.NIGHT -> work.integro.carling.R.drawable.bg_dash_night
    }
    work.integro.carling.game.CabinView.DRIVER -> when (bucket) {
        TimeBucket.MORNING -> work.integro.carling.R.drawable.bg_driver_morning
        TimeBucket.DAY -> work.integro.carling.R.drawable.bg_driver_day
        TimeBucket.EVENING -> work.integro.carling.R.drawable.bg_driver_evening
        TimeBucket.NIGHT -> work.integro.carling.R.drawable.bg_driver_night
    }
}

/** 카링 탭의 차 안 배경. 시간대가 바뀌면 부드럽게 넘어간다. */
@Composable
fun CabinBackdrop(view: work.integro.carling.game.CabinView, bucket: TimeBucket, modifier: Modifier = Modifier, tone: work.integro.carling.game.CabinTone = LocalCabinTone.current) {
    val filter = remember(tone) { toneFilter(tone) }
    androidx.compose.animation.Crossfade(targetState = cabinRes(view, bucket), animationSpec = tween(900), label = "cabin", modifier = modifier) { res ->
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(res),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            colorFilter = filter,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 화면 전체가 같은 배경 톤을 쓰도록 위에서 내려 준다. */
val LocalCabinTone = androidx.compose.runtime.staticCompositionLocalOf { work.integro.carling.game.CabinTone.CALM }

/** 채도 → 채널 배율·대비 → 밝기 순서의 색 보정. */
internal fun toneFilter(t: work.integro.carling.game.CabinTone): androidx.compose.ui.graphics.ColorFilter? {
    if (t == work.integro.carling.game.CabinTone.ORIGINAL) return null
    val s = t.sat
    val lr = 0.213f; val lg = 0.715f; val lb = 0.072f
    val sat = arrayOf(
        floatArrayOf(lr * (1 - s) + s, lg * (1 - s), lb * (1 - s)),
        floatArrayOf(lr * (1 - s), lg * (1 - s) + s, lb * (1 - s)),
        floatArrayOf(lr * (1 - s), lg * (1 - s), lb * (1 - s) + s),
    )
    val mul = floatArrayOf(t.r, t.g, t.b)
    val off = 128f * (1 - t.contrast) + t.lift
    val m = FloatArray(20)
    for (row in 0 until 3) {
        for (col in 0 until 3) m[row * 5 + col] = sat[row][col] * mul[row] * t.contrast
        m[row * 5 + 4] = off
    }
    m[18] = 1f
    return androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(m))
}

/** 차 안 배경을 화면에 꽉 채워 잘라 그릴 때, 카링을 놓을 자리(화면 기준 dp). */
internal class CabinGeometry(val anchorX: androidx.compose.ui.unit.Dp, val anchorY: androidx.compose.ui.unit.Dp, val karH: androidx.compose.ui.unit.Dp, val wander: androidx.compose.ui.unit.Dp)

internal fun cabinGeometry(w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp, view: work.integro.carling.game.CabinView): CabinGeometry {
    val (imgW, imgH) = if (w / h < CABIN_ASPECT) (h * CABIN_ASPECT) to h else w to (w / CABIN_ASPECT)
    val ox = (w - imgW) / 2
    val oy = (h - imgH) / 2
    return CabinGeometry(ox + imgW * view.anchorX, oy + imgH * view.anchorY, imgH * view.heightFrac, imgW * view.wander)
}
