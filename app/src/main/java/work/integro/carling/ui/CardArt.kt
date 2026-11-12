package work.integro.carling.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import work.integro.carling.game.TimeBucket
import work.integro.carling.game.TripPurpose
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/*
 * 트립카드 머리 그림. 운행 목적마다 다른 풍경, 출발 시간대마다 다른 하늘(시안 v1, 2026-11-12).
 * 그림 파일 없이 코드로 그린다. [seed]가 같으면 같은 그림이 나온다(카드마다 건물·별 배치가 조금씩 다르다).
 */

private val Skies = mapOf(
    TimeBucket.MORNING to listOf(Color(0xFFFFD9C7), Color(0xFFFBE3EF), Color(0xFFE9E5FF)),
    TimeBucket.DAY to listOf(Color(0xFF9FD3FF), Color(0xFFC9E6FF), Color(0xFFEEF6FF)),
    TimeBucket.EVENING to listOf(Color(0xFFA996F5), Color(0xFFE7A9D6), Color(0xFFFFD1B0)),
    TimeBucket.NIGHT to listOf(Color(0xFF1E2160), Color(0xFF30347A), Color(0xFF4B4FA8)),
)

/** 시간대에 맞춰 풍경 색을 물들인다. */
private fun Color.at(t: TimeBucket): Color = when (t) {
    TimeBucket.NIGHT -> lerp(this, Color(0xFF20245A), 0.62f)
    TimeBucket.EVENING -> lerp(this, Color(0xFF7A5FB0), 0.22f)
    TimeBucket.MORNING -> lerp(this, Color(0xFFFFB59A), 0.12f)
    TimeBucket.DAY -> this
}

/** 목적이 아직 없으면 하늘과 언덕만 그린다(목적을 고르면 풍경이 드러난다). */
fun DrawScope.drawTripScene(purpose: TripPurpose?, time: TimeBucket, seed: Int) {
    val r = Random(seed)
    val w = size.width
    val h = size.height
    val sky = Skies.getValue(time)
    drawRect(Brush.verticalGradient(0f to sky[0], 0.55f to sky[1], 1f to sky[2]))
    val horizon = h * 0.64f

    if (time == TimeBucket.NIGHT) {
        repeat(60) { drawCircle(Color.White.copy(alpha = 0.3f + r.nextFloat() * 0.7f), r.nextFloat() * 1.4f * h / 120 + 0.6f, Offset(r.nextFloat() * w, r.nextFloat() * horizon * 0.85f)) }
        drawCircle(Color(0xFFFFF6C9), h * 0.07f, Offset(w * 0.78f, h * 0.2f))
        drawCircle(sky[0], h * 0.065f, Offset(w * 0.795f, h * 0.185f))
    } else {
        val sx = when (time) { TimeBucket.MORNING -> w * 0.25f; TimeBucket.EVENING -> w * 0.7f; else -> w * 0.8f }
        val sy = if (time == TimeBucket.DAY) h * 0.2f else horizon - h * 0.06f
        val core = if (time == TimeBucket.DAY) Color(0xFFFFFBE6) else Color(0xFFFFF1C9)
        drawCircle(Brush.radialGradient(listOf(core, (if (time == TimeBucket.DAY) Color(0xFFFFF6D0) else Color(0xFFFFD9A0)).copy(alpha = 0.8f), Color.Transparent), Offset(sx, sy), h * 0.35f), h * 0.35f, Offset(sx, sy))
        drawCircle(core, h * 0.06f, Offset(sx, sy))
        val cloud = if (time == TimeBucket.EVENING) Color(0x99FFE3EF) else Color(0xCCFFFFFF)
        repeat(3) { cloud(r.nextFloat() * w, h * (0.1f + r.nextFloat() * 0.25f), h * (0.05f + r.nextFloat() * 0.04f), cloud) }
    }

    when (purpose) {
        TripPurpose.COMMUTE -> commute(w, h, horizon, time, r)
        TripPurpose.FIELD -> field(w, h, horizon, time, r)
        TripPurpose.FAMILY -> family(w, h, horizon, time, r)
        TripPurpose.TRAVEL -> travel(w, h, horizon, time, r)
        TripPurpose.HOBBY -> hobby(w, h, horizon, time, r)
        TripPurpose.OTHER -> other(w, h, horizon, time, r)
        null -> {
            hills(w, h, horizon, 10f, Color(0xFFD7D3FB).at(time), 1.1f)
            glowLine(w, horizon - 4, time)
        }
    }
}

private fun DrawScope.cloud(x: Float, y: Float, s: Float, c: Color) {
    drawCircle(c, s, Offset(x, y))
    drawCircle(c, s * 1.2f, Offset(x + s * 1.1f, y - s * 0.4f))
    drawCircle(c, s * 0.9f, Offset(x + s * 2.3f, y))
    drawRect(c, Offset(x, y - s * 0.2f), Size(s * 2.3f, s * 1.1f))
}

private fun DrawScope.hills(w: Float, h: Float, y: Float, amp: Float, color: Color, phase: Float) {
    val p = Path().apply {
        moveTo(0f, h)
        var x = 0f
        while (x <= w) {
            lineTo(x, y - sin(x / w * PI.toFloat() * 1.6f + phase) * amp - sin(x / w * 7f + phase * 3f) * amp * 0.15f)
            x += 6f
        }
        lineTo(w, h)
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.glowLine(w: Float, y: Float, t: TimeBucket) =
    drawLine(if (t == TimeBucket.NIGHT) Color(0x558CFFD6) else Color(0xCCFFFFFF), Offset(0f, y), Offset(w, y), strokeWidth = 2f)

private fun DrawScope.tree(x: Float, y: Float, s: Float, color: Color, trunk: Color) {
    drawRect(trunk, Offset(x - s * 0.08f, y - s * 0.5f), Size(s * 0.16f, s * 0.5f))
    drawCircle(color, s * 0.42f, Offset(x, y - s * 0.75f))
    drawCircle(color, s * 0.3f, Offset(x - s * 0.28f, y - s * 0.55f))
    drawCircle(color, s * 0.3f, Offset(x + s * 0.28f, y - s * 0.55f))
}

private fun DrawScope.road(w: Float, h: Float, horizon: Float, color: Color, mark: Color) {
    drawPath(Path().apply { moveTo(w * 0.47f, horizon); lineTo(w * 0.53f, horizon); lineTo(w * 0.86f, h); lineTo(w * 0.14f, h); close() }, color)
    for (i in 0 until 6) {
        val t0 = i / 6f
        val t1 = t0 + 0.07f
        drawLine(mark, Offset(w / 2, horizon + (h - horizon) * t0 * t0), Offset(w / 2, horizon + (h - horizon) * t1 * t1), strokeWidth = (1 + t0 * 4) * h / 200)
    }
}

fun DrawScope.sparkle(x: Float, y: Float, s: Float, c: Color) {
    drawPath(
        Path().apply {
            moveTo(x, y - s * 2); lineTo(x + s * 0.5f, y - s * 0.5f); lineTo(x + s * 2, y); lineTo(x + s * 0.5f, y + s * 0.5f)
            lineTo(x, y + s * 2); lineTo(x - s * 0.5f, y + s * 0.5f); lineTo(x - s * 2, y); lineTo(x - s * 0.5f, y - s * 0.5f); close()
        },
        c,
    )
}

/** 출퇴근: 도시 스카이라인 두 겹, 밤엔 창문 불빛, 헤드라이트 빛 꼬리. */
private fun DrawScope.commute(w: Float, h: Float, horizon: Float, t: TimeBucket, r: Random) {
    val unit = h / 200f
    listOf(Triple(Color(0xFFB8B4EE).at(t), horizon + 4 * unit, 0.34f), Triple(Color(0xFF8E8AD8).at(t), horizon + 14 * unit, 0.46f)).forEachIndexed { layer, (col, base, hmax) ->
        var x = -10f
        while (x < w) {
            val bw = (18 + r.nextFloat() * 34) * unit
            val bh = h * (0.12f + r.nextFloat() * hmax)
            drawRect(col, Offset(x, base - bh), Size(bw, bh + h))
            if (layer == 1) {
                val lit = when (t) { TimeBucket.NIGHT -> Color(0xFFFFE6A3); TimeBucket.EVENING -> Color(0xAAFFE3C4); else -> Color(0x66FFFFFF) }
                var wy = base - bh + 6 * unit
                while (wy < base - 6 * unit) {
                    var wx = x + 4 * unit
                    while (wx < x + bw - 5 * unit) {
                        if (r.nextFloat() > (if (t == TimeBucket.NIGHT) 0.45f else 0.7f)) drawRect(lit, Offset(wx, wy), Size(3.5f * unit, 4.5f * unit))
                        wx += 8 * unit
                    }
                    wy += 9 * unit
                }
            }
            x += bw + (2 + r.nextFloat() * 6) * unit
        }
    }
    glowLine(w, horizon + 14 * unit, t)
    hills(w, h, horizon + 24 * unit, 4 * unit, Color(0xFFD7D3FB).at(t), 0.6f)
    val trail1 = Path().apply { moveTo(w * 0.05f, h * 0.9f); quadraticTo(w * 0.5f, h * 0.8f, w * 0.95f, h * 0.86f) }
    val trail2 = Path().apply { moveTo(w * 0.05f, h * 0.94f); quadraticTo(w * 0.5f, h * 0.84f, w * 0.95f, h * 0.9f) }
    drawPath(trail1, if (t == TimeBucket.NIGHT) Color(0xFF8CFFD6) else Color(0xCCFFFFFF), style = Stroke(2.5f * unit, cap = StrokeCap.Round))
    drawPath(trail2, if (t == TimeBucket.NIGHT) Color(0xFFFFB7D5) else Color(0xFFFFD9C7), style = Stroke(2.5f * unit, cap = StrokeCap.Round))
}

/** 외근: 업무 지구의 유리 타워와 고가도로, 작은 차들. */
private fun DrawScope.field(w: Float, h: Float, horizon: Float, t: TimeBucket, r: Random) {
    val unit = h / 200f
    hills(w, h, horizon + 6 * unit, 6 * unit, Color(0xFFC9D9F5).at(t), 1.2f)
    val cols = listOf(Color(0xFFA9C4F0), Color(0xFF8FB0E8), Color(0xFFBCD0F5))
    for (i in 0 until 7) {
        val bw = w * (0.07f + r.nextFloat() * 0.05f)
        val bh = h * (0.25f + r.nextFloat() * 0.35f)
        val x = w * (0.05f + i * 0.135f) + r.nextFloat() * 6
        drawRect(cols[i % 3].at(t), Offset(x, horizon - bh + 10 * unit), Size(bw, bh))
        val glass = if (t == TimeBucket.NIGHT) Color(0xCCFFE6A3) else Color(0x55FFFFFF)
        var k = 0
        while (k < bh / (12 * unit)) {
            drawRect(glass, Offset(x + 3 * unit, horizon - bh + (16 + k * 12) * unit), Size(bw - 6 * unit, 2 * unit))
            k++
        }
    }
    drawRect(Color(0xFF6F86C9).at(t), Offset(0f, h * 0.74f), Size(w, h * 0.05f))
    var px = w * 0.08f
    while (px < w) { drawRect(Color(0xFF6F86C9).at(t), Offset(px, h * 0.79f), Size(6 * unit, h)); px += w * 0.18f }
    drawRect(Color(0xFFDFE8FB).at(t), Offset(0f, h * 0.82f), Size(w, h))
    var lx = 6f
    while (lx < w) { drawRect(if (t == TimeBucket.NIGHT) Color(0xFF8CFFD6) else Color.White, Offset(lx, h * 0.755f), Size(12 * unit, 2 * unit)); lx += 26 * unit }
    listOf(Color.White, Color(0xFFC7B8FF), Color(0xFF9FD3FF)).forEachIndexed { i, c ->
        drawRoundRect(c, Offset(w * (0.15f + i * 0.3f), h * 0.715f), Size(22 * unit, 9 * unit), androidx.compose.ui.geometry.CornerRadius(4 * unit))
    }
}

/** 가족과 함께: 초록 언덕, 큰 텐트와 작은 텐트, 모닥불. */
private fun DrawScope.family(w: Float, h: Float, horizon: Float, t: TimeBucket, r: Random) {
    val unit = h / 200f
    hills(w, h, horizon - 18 * unit, 18 * unit, Color(0xFF9FC9A6).at(t), 0.4f)
    hills(w, h, horizon + 10 * unit, 10 * unit, Color(0xFF8FD19A).at(t), 2.2f)
    for (i in 0 until 5) {
        val x = w * (0.04f + r.nextFloat() * 0.22f) + if (i > 2) w * 0.7f else 0f
        tree(x, horizon + (14 + r.nextFloat() * 6) * unit, h * (0.22f + r.nextFloat() * 0.1f), (if (i % 2 == 1) Color(0xFF5FB57A) else Color(0xFF73C48A)).at(t), Color(0xFF8A6A4E).at(t))
    }
    val tx = w * 0.52f
    val ty = h * 0.86f
    val ts = h * 0.42f
    drawPath(Path().apply { moveTo(tx - ts * 0.7f, ty); lineTo(tx, ty - ts * 0.75f); lineTo(tx + ts * 0.7f, ty); close() }, Color(0xFFFFB766).at(t))
    drawPath(Path().apply { moveTo(tx - ts * 0.16f, ty); lineTo(tx, ty - ts * 0.42f); lineTo(tx + ts * 0.16f, ty); close() }, Color(0xFFE98A3C).at(t))
    drawLine(Color(0xAAFFFFFF), Offset(tx, ty - ts * 0.75f), Offset(tx, ty - ts * 0.88f), strokeWidth = 1.5f * unit)
    drawPath(Path().apply { moveTo(tx, ty - ts * 0.88f); lineTo(tx + 12 * unit, ty - ts * 0.84f); lineTo(tx, ty - ts * 0.8f); close() }, Color(0xFFFF8FB1))
    drawPath(Path().apply { moveTo(w * 0.22f, ty + 2); lineTo(w * 0.29f, ty - ts * 0.38f); lineTo(w * 0.36f, ty + 2); close() }, Color(0xFFFFD08A).at(t))
    val fc = Offset(w * 0.75f, ty - 4 * unit)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF1B0), Color(0x00FFB766)), fc, h * 0.12f), h * 0.12f, fc)
    drawPath(Path().apply { moveTo(w * 0.735f, ty); quadraticTo(w * 0.75f, ty - 22 * unit, w * 0.765f, ty); close() }, Color(0xFFFF9A4A))
}

/** 여행: 눈 덮인 봉우리와 호수 반사, 침엽수. */
private fun DrawScope.travel(w: Float, h: Float, horizon: Float, t: TimeBucket, r: Random) {
    val peaks = listOf(0.12f to 0.42f, 0.36f to 0.30f, 0.62f to 0.38f, 0.88f to 0.28f)
    drawPath(Path().apply { moveTo(0f, horizon); peaks.forEach { (x, y) -> lineTo(w * x, h * y) }; lineTo(w, horizon); close() }, Color(0xFFA9B8EA).at(t))
    peaks.forEach { (x, y) ->
        drawPath(
            Path().apply {
                moveTo(w * x, h * y); lineTo(w * x - h * 0.08f, h * y + h * 0.09f); lineTo(w * x - h * 0.02f, h * y + h * 0.07f)
                lineTo(w * x + h * 0.02f, h * y + h * 0.1f); lineTo(w * x + h * 0.08f, h * y + h * 0.09f); close()
            },
            Color(0xE6FFFFFF),
        )
    }
    hills(w, h, horizon - 2, h * 0.05f, Color(0xFF6E8FC9).at(t), 1.0f)
    drawRect(Brush.verticalGradient(listOf(Color(0xFFBFE3FF).at(t), Color(0xFF7FB8EA).at(t)), startY = horizon, endY = h), Offset(0f, horizon + 4), Size(w, h))
    repeat(9) { drawRect(Color(0x88FFFFFF), Offset(r.nextFloat() * w, horizon + 10 + r.nextFloat() * (h - horizon - 14)), Size(h * (0.09f + r.nextFloat() * 0.15f), h / 125)) }
    hills(w, h, h * 0.94f, h * 0.03f, Color(0xFF5FA37A).at(t), 0.2f)
    for (i in 0 until 4) {
        val x = w * (0.02f + i * 0.05f)
        drawPath(Path().apply { moveTo(x, h * 0.95f); lineTo(x + h * 0.04f, h * 0.72f); lineTo(x + h * 0.08f, h * 0.95f); close() }, Color(0xFF3F8A63).at(t))
    }
}

/** 취미: 벚꽃 나무가 늘어선 길과 흩날리는 꽃잎. */
private fun DrawScope.hobby(w: Float, h: Float, horizon: Float, t: TimeBucket, r: Random) {
    hills(w, h, horizon, h * 0.03f, Color(0xFFC9E8B8).at(t), 0.8f)
    road(w, h, horizon, Color(0xFFF3E7F3).at(t), Color(0xAAFFFFFF))
    val pinks = listOf(Color(0xFFFFC2D9), Color(0xFFFFB0CD), Color(0xFFFFD6E6))
    for (side in 0 until 2) for (i in 0 until 4) {
        val k = i / 4f
        val x = if (side == 1) w * (0.62f + k * 0.36f) else w * (0.38f - k * 0.36f)
        val y = horizon + (h - horizon) * k * 0.9f + 6
        val s = h * (0.18f + k * 0.32f)
        drawRect(Color(0xFF9A6F6A).at(t), Offset(x - s * 0.05f, y - s * 0.55f), Size(s * 0.1f, s * 0.55f))
        repeat(5) { drawCircle(pinks[i % 3].at(t), s * (0.2f + r.nextFloat() * 0.12f), Offset(x + (r.nextFloat() - 0.5f) * s * 0.7f, y - s * (0.6f + r.nextFloat() * 0.35f))) }
    }
    repeat(26) { drawOval(Color(0xDDFFE0EC), Offset(r.nextFloat() * w, r.nextFloat() * h), Size(h / 45, h / 75)) }
}

/** 기타: 지평선 끝까지 이어지는 민트 길과 반짝임. */
private fun DrawScope.other(w: Float, h: Float, horizon: Float, t: TimeBucket, r: Random) {
    hills(w, h, horizon, h * 0.04f, Color(0xFFCFC9F7).at(t), 1.4f)
    glowLine(w, horizon - 2, t)
    road(w, h, horizon, Color(0xFFE8E5FB).at(t), if (t == TimeBucket.NIGHT) Color(0xFF8CFFD6) else Color(0xFF3CCB9A))
    val c = if (t == TimeBucket.NIGHT) Color(0xFF8CFFD6) else Color.White
    repeat(14) { sparkle(r.nextFloat() * w, r.nextFloat() * horizon * 0.9f, h / 100 * (2 + r.nextFloat() * 3), c) }
}
