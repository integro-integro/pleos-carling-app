package work.integro.carling.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import work.integro.carling.data.Personality
import work.integro.carling.game.Stage
import work.integro.carling.game.Path as GrowthPath

/*
 * 설계서(CARLING 서비스 시각화) 캐릭터를 벡터로 다시 그린 것.
 * 토끼 귀 + 머리 새싹 + 반투명하게 빛나는 몸 + 큰 눈. 정식 일러스트가 나오면 이 파일만 교체한다.
 */

/** 진화 전 모든 카링의 공통 빛 — '모든 카링은 같은 알에서 태어난다'. */
val BaseGlow = Color(0xFFB8C6FF)

private val EyeNavy = Color(0xFF1E2550)
private val MouthRose = Color(0xFF8A2E52)
private val Tongue = Color(0xFFFF8FA8)
private val Blush = Color(0x66FF8FB1)
private val SproutGreen = Color(0xFF9BE59F)
private val SproutDeep = Color(0xFF5DBB78)

/** 설계서의 알: 유리처럼 빛나는 알 안에 작은 새싹 빛. */
@Composable
fun EggDrawing(size: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "egg")
    val wobble by transition.animateFloat(-5f, 5f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "wobble")
    val twinkle by transition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "twinkle")
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val c = center
        drawCircle(Brush.radialGradient(listOf(BaseGlow.copy(alpha = 0.55f), Color.Transparent), c, s * 0.5f), s * 0.5f, c)
        rotate(wobble, pivot = c + Offset(0f, s * 0.35f)) {
            val w = s * 0.56f
            val h = s * 0.74f
            val topLeft = c - Offset(w / 2, h / 2)
            drawOval(
                Brush.radialGradient(
                    listOf(Color.White, Color(0xFFDDE4FF), BaseGlow, Color(0xFF8E9BFF)),
                    center = c - Offset(w * 0.15f, h * 0.2f),
                    radius = h * 0.75f,
                ),
                topLeft,
                Size(w, h),
                alpha = 0.92f,
            )
            // 안쪽 새싹 빛
            drawCircle(
                Brush.radialGradient(listOf(Color.White, SproutGreen.copy(alpha = 0.0f)), c + Offset(0f, h * 0.12f), w * 0.3f),
                w * 0.3f,
                c + Offset(0f, h * 0.12f),
            )
            drawSprout(c + Offset(0f, h * 0.22f), w * 0.55f)
            // 유리 하이라이트
            drawOval(Color.White.copy(alpha = 0.55f), topLeft + Offset(w * 0.2f, h * 0.12f), Size(w * 0.18f, h * 0.28f))
        }
        drawSparkles(c, s * 0.42f, Color.White, twinkle)
    }
}

/**
 * 카링.
 * - 성장 단계: 크기, 귀(아기는 짧게 쫑긋, 성장 후 길게 늘어짐)
 * - 빛(진화): 몸빛·발광 색 / 진화 전에는 공통 빛
 * - 길(진화): 단골은 포근한 머플러, 탐험은 별 지도 배낭 + 반짝이는 눈
 * - 성격: 표정만 (나른=눈웃음, 도도=활짝, 루미=잔잔한 미소)
 * - 첫 드라이브 선물: 모자
 */
@Composable
fun CarlingDrawing(
    size: Dp,
    modifier: Modifier = Modifier,
    stage: Stage = Stage.BABY,
    glow: Color = BaseGlow,
    path: GrowthPath? = null,
    hat: Boolean = false,
    mood: Personality? = null,
) {
    val transition = rememberInfiniteTransition(label = "carling")
    val breath by transition.animateFloat(0.97f, 1.03f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "breath")
    val twinkle by transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "twinkle")
    val stageScale = when (stage) {
        Stage.EGG, Stage.BABY -> 0.78f
        Stage.GROWN -> 0.9f
        Stage.EVOLVED -> 1f
    }
    val baby = stage == Stage.BABY || stage == Stage.EGG
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val r = s * 0.27f * stageScale
        val c = Offset(center.x, center.y + s * 0.08f)

        // 바깥 발광
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.6f), Color.Transparent), c, r * 2.1f), r * 2.1f, c)
        drawOval(glow.copy(alpha = 0.25f), c + Offset(-r * 1.1f, r * 0.95f), Size(r * 2.2f, r * 0.35f))

        scale(1f, breath, pivot = c + Offset(0f, r)) {
            if (path == GrowthPath.EXPLORER) drawBackpack(c, r)
            drawEars(c, r, glow, baby)
            drawBody(c, r, glow)
            drawFeet(c, r, glow)
            drawFace(c, r, mood, sparkle = path == GrowthPath.EXPLORER)
            if (path == GrowthPath.REGULAR) drawMuffler(c, r)
            if (path == GrowthPath.EXPLORER) drawStrap(c, r)
            drawSprout(c + Offset(r * 0.05f, -r * 0.88f), r * 0.75f)
            if (hat) drawHat(c, r)
        }
        drawSparkles(c, r * 1.75f, lerp(Color.White, glow, 0.3f), twinkle)
    }
}

private fun bodyBrush(c: Offset, r: Float, glow: Color) = Brush.radialGradient(
    listOf(Color.White, lerp(Color.White, glow, 0.25f), lerp(Color.White, glow, 0.7f), glow),
    center = c - Offset(r * 0.35f, r * 0.45f),
    radius = r * 1.6f,
)

private fun DrawScope.drawBody(c: Offset, r: Float, glow: Color) {
    val w = r * 2.1f
    val h = r * 1.86f
    drawOval(bodyBrush(c, r, glow), c - Offset(w / 2, h / 2), Size(w, h), alpha = 0.96f)
    // 반투명 테두리 빛
    drawOval(glow.copy(alpha = 0.55f), c - Offset(w / 2, h / 2), Size(w, h), style = Stroke(r * 0.05f))
    // 위쪽 광택
    drawOval(Color.White.copy(alpha = 0.6f), c + Offset(-r * 0.55f, -r * 0.75f), Size(r * 0.55f, r * 0.28f))
}

private fun DrawScope.drawEars(c: Offset, r: Float, glow: Color, baby: Boolean) {
    val earBrush = Brush.linearGradient(
        listOf(Color.White, lerp(Color.White, glow, 0.6f), glow.copy(alpha = 0.85f)),
        start = c - Offset(0f, r * 1.5f),
        end = c + Offset(0f, r * 0.8f),
    )
    val inner = lerp(glow, Color(0xFF7FA8FF), 0.35f).copy(alpha = 0.55f)
    for (side in listOf(-1f, 1f)) {
        if (baby) {
            // 아기: 짧게 쫑긋
            val center = c + Offset(side * r * 0.5f, -r * 1.15f)
            rotate(side * 18f, pivot = center + Offset(0f, r * 0.35f)) {
                drawOval(earBrush, center - Offset(r * 0.24f, r * 0.45f), Size(r * 0.48f, r * 0.9f))
                drawOval(inner, center - Offset(r * 0.11f, r * 0.3f), Size(r * 0.22f, r * 0.6f))
            }
        } else {
            // 성장 후: 머리 위에서 옆으로 길게 늘어진 귀 (설계서 '성장한 카링')
            val pivot = c + Offset(side * r * 0.5f, -r * 0.72f)
            rotate(side * 148f, pivot = pivot) {
                drawOval(earBrush, pivot - Offset(r * 0.3f, r * 1.7f), Size(r * 0.6f, r * 1.85f))
                drawOval(inner, pivot - Offset(r * 0.13f, r * 1.5f), Size(r * 0.26f, r * 1.35f))
            }
        }
    }
}

private fun DrawScope.drawFeet(c: Offset, r: Float, glow: Color) {
    val foot = lerp(Color.White, glow, 0.45f)
    drawOval(foot, c + Offset(-r * 0.75f, r * 0.7f), Size(r * 0.5f, r * 0.32f))
    drawOval(foot, c + Offset(r * 0.25f, r * 0.7f), Size(r * 0.5f, r * 0.32f))
}

private fun DrawScope.drawFace(c: Offset, r: Float, mood: Personality?, sparkle: Boolean) {
    val eyeY = c.y - r * 0.02f
    for (side in listOf(-1f, 1f)) {
        val eye = Offset(c.x + side * r * 0.4f, eyeY)
        if (mood == Personality.NARUN) {
            // 나른: 포근한 눈웃음
            val arc = Path().apply {
                moveTo(eye.x - r * 0.16f, eye.y + r * 0.04f)
                quadraticTo(eye.x, eye.y - r * 0.16f, eye.x + r * 0.16f, eye.y + r * 0.04f)
            }
            drawPath(arc, EyeNavy, style = Stroke(r * 0.07f, cap = StrokeCap.Round))
        } else {
            drawOval(EyeNavy, eye - Offset(r * 0.17f, r * 0.21f), Size(r * 0.34f, r * 0.42f))
            drawOval(Color(0xFF3B4C9A), eye - Offset(r * 0.12f, r * 0.02f), Size(r * 0.24f, r * 0.2f))
            drawCircle(Color.White, r * 0.085f, eye + Offset(-r * 0.05f, -r * 0.09f))
            drawCircle(Color.White, r * 0.04f, eye + Offset(r * 0.07f, r * 0.08f))
            if (sparkle) drawStar(eye + Offset(r * 0.06f, -r * 0.13f), r * 0.07f, Color.White)
        }
    }
    // 볼
    drawOval(Blush, Offset(c.x - r * 0.82f, c.y + r * 0.14f), Size(r * 0.3f, r * 0.17f))
    drawOval(Blush, Offset(c.x + r * 0.52f, c.y + r * 0.14f), Size(r * 0.3f, r * 0.17f))
    // 입
    val m = Offset(c.x, c.y + r * 0.26f)
    when (mood) {
        Personality.DODO, null -> {
            val mouth = Path().apply {
                moveTo(m.x - r * 0.14f, m.y)
                quadraticTo(m.x, m.y + r * 0.03f, m.x + r * 0.14f, m.y)
                quadraticTo(m.x + r * 0.1f, m.y + r * 0.22f, m.x, m.y + r * 0.22f)
                quadraticTo(m.x - r * 0.1f, m.y + r * 0.22f, m.x - r * 0.14f, m.y)
                close()
            }
            drawPath(mouth, MouthRose)
            drawOval(Tongue, Offset(m.x - r * 0.07f, m.y + r * 0.1f), Size(r * 0.14f, r * 0.1f))
        }
        Personality.NARUN -> {
            val smile = Path().apply {
                moveTo(m.x - r * 0.1f, m.y + r * 0.02f)
                quadraticTo(m.x, m.y + r * 0.12f, m.x + r * 0.1f, m.y + r * 0.02f)
            }
            drawPath(smile, MouthRose, style = Stroke(r * 0.05f, cap = StrokeCap.Round))
        }
        Personality.LUMI -> {
            // 잔잔한 'ω' 미소
            val w = Path().apply {
                moveTo(m.x - r * 0.13f, m.y)
                quadraticTo(m.x - r * 0.065f, m.y + r * 0.1f, m.x, m.y)
                quadraticTo(m.x + r * 0.065f, m.y + r * 0.1f, m.x + r * 0.13f, m.y)
            }
            drawPath(w, MouthRose, style = Stroke(r * 0.045f, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.drawSprout(base: Offset, size: Float) {
    val tip = base + Offset(size * 0.04f, -size * 0.38f)
    val stem = Path().apply {
        moveTo(base.x, base.y)
        quadraticTo(base.x - size * 0.06f, base.y - size * 0.2f, tip.x, tip.y)
    }
    drawPath(stem, SproutDeep, style = Stroke(size * 0.06f, cap = StrokeCap.Round))
    val leaf = Brush.linearGradient(listOf(SproutGreen, SproutDeep), tip - Offset(size * 0.3f, size * 0.2f), tip)
    rotate(-35f, pivot = tip) {
        drawOval(leaf, tip - Offset(size * 0.34f, size * 0.12f), Size(size * 0.34f, size * 0.2f))
    }
    rotate(30f, pivot = tip) {
        drawOval(leaf, tip - Offset(0f, size * 0.12f), Size(size * 0.3f, size * 0.18f))
    }
}

private fun DrawScope.drawMuffler(c: Offset, r: Float) {
    val knit = Color(0xFFFF8A80)
    val knitDark = Color(0xFFE76F6F)
    val top = c.y + r * 0.5f
    drawRoundRect(knit, Offset(c.x - r * 0.95f, top), Size(r * 1.9f, r * 0.3f), CornerRadius(r * 0.15f))
    for (i in 0..5) {
        val x = c.x - r * 0.75f + i * r * 0.3f
        drawLine(knitDark, Offset(x, top + r * 0.05f), Offset(x, top + r * 0.25f), r * 0.03f)
    }
    // 늘어진 끝
    drawRoundRect(knit, Offset(c.x + r * 0.35f, top + r * 0.15f), Size(r * 0.26f, r * 0.6f), CornerRadius(r * 0.1f))
    drawLine(knitDark, Offset(c.x + r * 0.37f, top + r * 0.68f), Offset(c.x + r * 0.59f, top + r * 0.68f), r * 0.04f)
}

private fun DrawScope.drawBackpack(c: Offset, r: Float) {
    val bag = Color(0xFF2E3A80)
    val topLeft = c + Offset(r * 0.6f, -r * 0.35f)
    drawRoundRect(bag, topLeft, Size(r * 0.85f, r * 1.05f), CornerRadius(r * 0.22f))
    drawRoundRect(Color(0xFF3D4CA0), topLeft + Offset(r * 0.08f, r * 0.08f), Size(r * 0.69f, r * 0.35f), CornerRadius(r * 0.15f))
    // 별 지도
    val gold = Color(0xFFFFE27A)
    drawStar(topLeft + Offset(r * 0.3f, r * 0.62f), r * 0.09f, gold)
    drawStar(topLeft + Offset(r * 0.58f, r * 0.8f), r * 0.06f, gold)
    drawStar(topLeft + Offset(r * 0.6f, r * 0.5f), r * 0.05f, gold)
    drawLine(gold.copy(alpha = 0.6f), topLeft + Offset(r * 0.3f, r * 0.62f), topLeft + Offset(r * 0.6f, r * 0.5f), r * 0.015f)
    drawLine(gold.copy(alpha = 0.6f), topLeft + Offset(r * 0.6f, r * 0.5f), topLeft + Offset(r * 0.58f, r * 0.8f), r * 0.015f)
}

private fun DrawScope.drawStrap(c: Offset, r: Float) {
    // 몸 오른쪽 가장자리에만 짧게 — 얼굴을 가리지 않도록
    val strap = Path().apply {
        moveTo(c.x + r * 0.95f, c.y + r * 0.05f)
        quadraticTo(c.x + r * 0.7f, c.y + r * 0.45f, c.x + r * 0.62f, c.y + r * 0.78f)
    }
    drawPath(strap, Color(0xFF2E3A80), style = Stroke(r * 0.09f, cap = StrokeCap.Round))
}

private fun DrawScope.drawHat(c: Offset, r: Float) {
    val pivot = c + Offset(-r * 0.5f, -r * 0.82f)
    rotate(-22f, pivot = pivot) {
        val hat = Path().apply {
            moveTo(pivot.x - r * 0.32f, pivot.y)
            lineTo(pivot.x + r * 0.32f, pivot.y)
            lineTo(pivot.x, pivot.y - r * 0.6f)
            close()
        }
        drawPath(hat, Brush.linearGradient(listOf(Color(0xFF6FE3D1), Color(0xFF3BB3A6)), pivot - Offset(0f, r * 0.6f), pivot))
        drawRoundRect(Color.White, Offset(pivot.x - r * 0.36f, pivot.y - r * 0.06f), Size(r * 0.72f, r * 0.12f), CornerRadius(r * 0.06f))
        drawCircle(Color(0xFFFFE27A), r * 0.09f, Offset(pivot.x, pivot.y - r * 0.62f))
    }
}

/** 4갈래 반짝이 — 설계서 배경의 별빛. */
private fun DrawScope.drawStar(at: Offset, size: Float, color: Color) {
    val p = Path().apply {
        moveTo(at.x, at.y - size)
        quadraticTo(at.x, at.y, at.x + size, at.y)
        quadraticTo(at.x, at.y, at.x, at.y + size)
        quadraticTo(at.x, at.y, at.x - size, at.y)
        quadraticTo(at.x, at.y, at.x, at.y - size)
        close()
    }
    drawPath(p, color)
}

private fun DrawScope.drawSparkles(c: Offset, radius: Float, color: Color, twinkle: Float) {
    val spots = listOf(
        Offset(-0.95f, -0.75f) to 0.10f,
        Offset(0.9f, -0.9f) to 0.07f,
        Offset(1.05f, 0.25f) to 0.05f,
        Offset(-1.05f, 0.35f) to 0.06f,
    )
    spots.forEachIndexed { i, (dir, scale) ->
        val t = if (i % 2 == 0) twinkle else 1.3f - twinkle
        drawStar(c + Offset(dir.x * radius, dir.y * radius), radius * scale * t, color.copy(alpha = 0.9f * t.coerceIn(0f, 1f)))
    }
}
