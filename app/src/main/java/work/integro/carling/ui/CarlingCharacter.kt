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
 * 서비스 시각화 v1 캐릭터를 벡터로 그린 것.
 * 반투명 하늘색 젤리 블롭 + 가슴 중앙에 초록빛으로 뛰는 작은 심장(하트) + 큰 눈·분홍 볼.
 * 계열/시간대 색은 glow 로 몸에 번지게 하고, 심장은 늘 초록으로 둔다('미래에서 온 작은 심장').
 * 정식 일러스트가 나오면 이 파일만 교체한다.
 */

/** 진화 전 모든 카링의 공통 몸빛 — '모두 같은 젤리 알에서 시작한다'. 하늘색. */
val BaseGlow = Color(0xFF9FD4FF)

private val EyeNavy = Color(0xFF1B2440)
private val EyeSoft = Color(0xFF34406E)
private val MouthRose = Color(0xFF8A2E52)
private val Tongue = Color(0xFFFF8FA8)
private val Blush = Color(0x73FF93B8)

// 작은 심장 — 초록 하트
private val HeartGreen = Color(0xFF34E0A4)
private val HeartDeep = Color(0xFF17B583)
private val HeartLight = Color(0xFFB7FFE6)

/** 서비스 시각화 v1 의 알: 유리처럼 빛나는 하늘색 젤리 알 안에서 초록 심장이 뛴다. */
@Composable
fun EggDrawing(size: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "egg")
    val wobble by transition.animateFloat(-4f, 4f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "wobble")
    val beat by transition.animateFloat(0.86f, 1.14f, infiniteRepeatable(tween(820), RepeatMode.Reverse), label = "beat")
    val twinkle by transition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "twinkle")
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val c = center
        drawCircle(Brush.radialGradient(listOf(BaseGlow.copy(alpha = 0.5f), Color.Transparent), c, s * 0.5f), s * 0.5f, c)
        rotate(wobble, pivot = c + Offset(0f, s * 0.36f)) {
            val w = s * 0.58f
            val h = s * 0.76f
            val topLeft = c - Offset(w / 2, h / 2)
            // 바닥 그림자
            drawOval(Color(0x33000000), c + Offset(-w * 0.42f, h * 0.44f), Size(w * 0.84f, h * 0.12f))
            // 젤리 알 본체 (반투명)
            drawOval(
                Brush.radialGradient(
                    listOf(Color.White, lerp(Color.White, BaseGlow, 0.35f), lerp(Color.White, BaseGlow, 0.8f), lerp(BaseGlow, Color(0xFF4FA6EE), 0.4f)),
                    center = c - Offset(w * 0.16f, h * 0.22f),
                    radius = h * 0.78f,
                ),
                topLeft,
                Size(w, h),
                alpha = 0.9f,
            )
            drawOval(BaseGlow.copy(alpha = 0.5f), topLeft, Size(w, h), style = Stroke(s * 0.012f))
            // 안에서 뛰는 초록 심장
            val heartC = c + Offset(0f, h * 0.1f)
            drawCircle(Brush.radialGradient(listOf(HeartLight.copy(alpha = 0.9f), Color.Transparent), heartC, w * 0.42f * beat), w * 0.42f * beat, heartC)
            drawHeart(heartC, w * 0.17f * beat)
            // 유리 하이라이트
            drawOval(Color.White.copy(alpha = 0.6f), topLeft + Offset(w * 0.18f, h * 0.1f), Size(w * 0.2f, h * 0.3f))
            drawCircle(Color.White.copy(alpha = 0.75f), w * 0.05f, topLeft + Offset(w * 0.68f, h * 0.2f))
        }
        drawSparkles(c, s * 0.42f, Color.White, twinkle)
    }
}

/**
 * 카링 (아기~성장).
 * - stage: 크기
 * - glow: 몸빛(계열·시간대 색) / 진화 전에는 공통 하늘빛
 * - mood: 표정만 (나른=눈웃음, 도도=활짝, 루미=잔잔한 미소)
 * - hat: 첫 드라이브 선물 모자
 * - path: 탐험이면 눈에 작은 별 반짝임
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
    val beat by transition.animateFloat(0.84f, 1.16f, infiniteRepeatable(tween(820), RepeatMode.Reverse), label = "beat")
    val twinkle by transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "twinkle")
    val stageScale = when (stage) {
        Stage.EGG, Stage.BABY -> 0.8f
        Stage.GROWN -> 0.92f
        Stage.EVOLVED -> 1f
    }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val r = s * 0.3f * stageScale
        val c = Offset(center.x, center.y + s * 0.06f)

        // 바깥 발광 + 바닥 그림자
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.55f), Color.Transparent), c, r * 2.0f), r * 2.0f, c)
        drawOval(Color(0x2E000000), c + Offset(-r * 1.0f, r * 1.02f), Size(r * 2.0f, r * 0.3f))

        scale(1f, breath, pivot = c + Offset(0f, r)) {
            drawArms(c, r, glow)
            drawFeet(c, r, glow)
            drawBlobBody(c, r, glow)
            drawHeartCore(c, r, beat)
            drawFace(c, r, mood, sparkle = path == GrowthPath.EXPLORER)
            if (hat) drawHat(c, r)
        }
        drawSparkles(c, r * 1.7f, lerp(Color.White, glow, 0.3f), twinkle)
    }
}

private fun bodyBrush(c: Offset, r: Float, glow: Color) = Brush.radialGradient(
    listOf(Color.White, lerp(Color.White, glow, 0.3f), lerp(Color.White, glow, 0.72f), lerp(glow, Color(0xFF4FA6EE), 0.35f)),
    center = c - Offset(r * 0.38f, r * 0.55f),
    radius = r * 1.85f,
)

/** 둥근 젤리 몸통 — 반투명 광택. */
private fun DrawScope.drawBlobBody(c: Offset, r: Float, glow: Color) {
    val w = r * 2.15f
    val h = r * 2.05f
    val topLeft = c - Offset(w / 2, h / 2)
    drawOval(bodyBrush(c, r, glow), topLeft, Size(w, h), alpha = 0.93f)
    // 반투명 테두리 빛
    drawOval(glow.copy(alpha = 0.5f), topLeft, Size(w, h), style = Stroke(r * 0.045f))
    // 위쪽 큰 광택
    drawOval(Color.White.copy(alpha = 0.62f), c + Offset(-r * 0.62f, -r * 0.82f), Size(r * 0.7f, r * 0.4f))
    // 작은 반짝 점
    drawCircle(Color.White.copy(alpha = 0.8f), r * 0.08f, c + Offset(r * 0.5f, -r * 0.7f))
}

/** 양옆의 작고 뭉툭한 젤리 팔. */
private fun DrawScope.drawArms(c: Offset, r: Float, glow: Color) {
    val arm = lerp(Color.White, glow, 0.5f)
    for (side in listOf(-1f, 1f)) {
        val a = c + Offset(side * r * 1.0f, r * 0.42f)
        rotate(side * 18f, pivot = a) {
            drawOval(arm, a - Offset(r * 0.28f, r * 0.22f), Size(r * 0.56f, r * 0.44f), alpha = 0.95f)
        }
    }
}

private fun DrawScope.drawFeet(c: Offset, r: Float, glow: Color) {
    val foot = lerp(Color.White, glow, 0.5f)
    drawOval(foot, c + Offset(-r * 0.66f, r * 0.82f), Size(r * 0.56f, r * 0.34f))
    drawOval(foot, c + Offset(r * 0.1f, r * 0.82f), Size(r * 0.56f, r * 0.34f))
}

/** 가슴 중앙에서 뛰는 초록 심장. */
private fun DrawScope.drawHeartCore(c: Offset, r: Float, beat: Float) {
    val hc = c + Offset(0f, r * 0.52f)
    drawCircle(Brush.radialGradient(listOf(HeartLight.copy(alpha = 0.85f), Color.Transparent), hc, r * 0.7f * beat), r * 0.7f * beat, hc)
    drawHeart(hc, r * 0.3f * beat)
}

private fun DrawScope.drawHeart(center: Offset, hw: Float) {
    val p = Path().apply {
        moveTo(center.x, center.y + hw * 0.95f)
        cubicTo(center.x - hw * 1.5f, center.y - hw * 0.1f, center.x - hw * 0.65f, center.y - hw * 1.15f, center.x, center.y - hw * 0.3f)
        cubicTo(center.x + hw * 0.65f, center.y - hw * 1.15f, center.x + hw * 1.5f, center.y - hw * 0.1f, center.x, center.y + hw * 0.95f)
        close()
    }
    drawPath(p, Brush.radialGradient(listOf(HeartLight, HeartGreen, HeartDeep), center - Offset(hw * 0.3f, hw * 0.4f), hw * 1.8f))
    drawPath(p, Color.White.copy(alpha = 0.5f), style = Stroke(hw * 0.08f))
}

private fun DrawScope.drawFace(c: Offset, r: Float, mood: Personality?, sparkle: Boolean) {
    val eyeY = c.y - r * 0.2f
    for (side in listOf(-1f, 1f)) {
        val eye = Offset(c.x + side * r * 0.46f, eyeY)
        if (mood == Personality.NARUN) {
            // 나른: 포근한 눈웃음
            val arc = Path().apply {
                moveTo(eye.x - r * 0.2f, eye.y + r * 0.05f)
                quadraticTo(eye.x, eye.y - r * 0.2f, eye.x + r * 0.2f, eye.y + r * 0.05f)
            }
            drawPath(arc, EyeNavy, style = Stroke(r * 0.08f, cap = StrokeCap.Round))
        } else {
            drawOval(EyeNavy, eye - Offset(r * 0.2f, r * 0.26f), Size(r * 0.4f, r * 0.52f))
            drawOval(EyeSoft, eye - Offset(r * 0.14f, r * 0.02f), Size(r * 0.28f, r * 0.24f))
            drawCircle(Color.White, r * 0.1f, eye + Offset(-r * 0.06f, -r * 0.11f))
            drawCircle(Color.White, r * 0.05f, eye + Offset(r * 0.08f, r * 0.1f))
            if (sparkle) drawStar(eye + Offset(r * 0.07f, -r * 0.15f), r * 0.08f, Color.White)
        }
    }
    // 분홍 볼
    drawOval(Blush, Offset(c.x - r * 0.88f, c.y - r * 0.02f), Size(r * 0.34f, r * 0.2f))
    drawOval(Blush, Offset(c.x + r * 0.54f, c.y - r * 0.02f), Size(r * 0.34f, r * 0.2f))
    // 입
    val m = Offset(c.x, c.y + r * 0.1f)
    when (mood) {
        Personality.DODO, null -> {
            val mouth = Path().apply {
                moveTo(m.x - r * 0.15f, m.y)
                quadraticTo(m.x, m.y + r * 0.03f, m.x + r * 0.15f, m.y)
                quadraticTo(m.x + r * 0.11f, m.y + r * 0.24f, m.x, m.y + r * 0.24f)
                quadraticTo(m.x - r * 0.11f, m.y + r * 0.24f, m.x - r * 0.15f, m.y)
                close()
            }
            drawPath(mouth, MouthRose)
            drawOval(Tongue, Offset(m.x - r * 0.08f, m.y + r * 0.11f), Size(r * 0.16f, r * 0.11f))
        }
        Personality.NARUN -> {
            val smile = Path().apply {
                moveTo(m.x - r * 0.11f, m.y + r * 0.02f)
                quadraticTo(m.x, m.y + r * 0.13f, m.x + r * 0.11f, m.y + r * 0.02f)
            }
            drawPath(smile, MouthRose, style = Stroke(r * 0.055f, cap = StrokeCap.Round))
        }
        Personality.LUMI -> {
            val w = Path().apply {
                moveTo(m.x - r * 0.14f, m.y)
                quadraticTo(m.x - r * 0.07f, m.y + r * 0.11f, m.x, m.y)
                quadraticTo(m.x + r * 0.07f, m.y + r * 0.11f, m.x + r * 0.14f, m.y)
            }
            drawPath(w, MouthRose, style = Stroke(r * 0.05f, cap = StrokeCap.Round))
        }
    }
}

/** 첫 드라이브 선물 모자 — 작은 고깔. */
private fun DrawScope.drawHat(c: Offset, r: Float) {
    val pivot = c + Offset(-r * 0.4f, -r * 0.92f)
    rotate(-16f, pivot = pivot) {
        val hat = Path().apply {
            moveTo(pivot.x - r * 0.34f, pivot.y)
            lineTo(pivot.x + r * 0.34f, pivot.y)
            lineTo(pivot.x, pivot.y - r * 0.62f)
            close()
        }
        drawPath(hat, Brush.linearGradient(listOf(Color(0xFF6FE3D1), Color(0xFF3BB3A6)), pivot - Offset(0f, r * 0.62f), pivot))
        drawRoundRect(Color.White, Offset(pivot.x - r * 0.38f, pivot.y - r * 0.06f), Size(r * 0.76f, r * 0.12f), CornerRadius(r * 0.06f))
        drawCircle(Color(0xFFFFE27A), r * 0.09f, Offset(pivot.x, pivot.y - r * 0.64f))
    }
}

/** 4갈래 반짝이 — 서비스 시각화 배경의 별빛. */
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
