package work.integro.carling.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import work.integro.carling.data.Personality
import work.integro.carling.game.CarlingItem
import work.integro.carling.game.GrowthRules
import work.integro.carling.game.Lineage
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

/** 일러스트 한 장에 숨쉬기와 심장 박동, 그림자, 모자를 입혀 그린다. */
@Composable
internal fun ArtDrawing(
    art: CarlingArt,
    size: Dp,
    modifier: Modifier,
    res: Int = art.res,
    rockDegrees: Float = 0f,
    item: CarlingItem? = null,
    beating: Boolean = true,
    overlay: (DrawScope.(s: Float) -> Unit)? = null,
) {
    val transition = rememberInfiniteTransition(label = "art")
    val breath by transition.animateFloat(0.985f, 1.015f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "breath")
    val beat by transition.animateFloat(0.82f, 1.18f, infiniteRepeatable(tween(820), RepeatMode.Reverse), label = "beat")
    val twinkle by transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "twinkle")
    val rock by transition.animateFloat(-1f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "rock")
    Box(modifier.size(size)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = art.scale * if (art.mirrored) -1f else 1f
                    scaleY = art.scale * breath
                    rotationZ = rock * rockDegrees
                    transformOrigin = TransformOrigin(0.5f, 0.86f)
                },
        ) {
            art.shadowAt?.let { at ->
                Canvas(Modifier.fillMaxSize()) {
                    val s = this.size.minDimension
                    drawOval(Color(0x38000000), Offset(at.x * s - s * 0.2f, at.y * s - s * 0.03f), Size(s * 0.4f, s * 0.06f))
                }
            }
            Image(painterResource(res), contentDescription = null, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val s = this.size.minDimension
                if (beating) {
                    val at = Offset(art.heart.x * s, art.heart.y * s)
                    val glow = art.heartRadius * s * beat
                    drawCircle(Brush.radialGradient(listOf(HeartLight.copy(alpha = 0.6f), Color.Transparent), at, glow), glow, at)
                }
                overlay?.invoke(this, s)
                val body = art.bodyCenter
                if (item != null && body != null) drawItem(item, Offset(body.x * s, body.y * s), art.bodyRadius * s)
                drawSparkles(center, s * 0.42f, Color.White, twinkle)
            }
        }
    }
}

/** 서비스 시각화 v1 의 알: 진주빛 젤리 알 안에서 초록 심장이 뛴다. */
@Composable
fun EggDrawing(size: Dp, modifier: Modifier = Modifier) = ArtDrawing(EggArt, size, modifier, rockDegrees = 4f)

/**
 * 카링. 일러스트가 있는 단계는 일러스트로, 아직 없는 계열의 진화 단계는 벡터로 그린다.
 * - stageIndex: 성장 단계(0=아기 ~ 9=진화 3)
 * - asleep: 자는 중 — 자는 모습 일러스트가 맞는 단계에서만 바뀐다
 * - glow: 벡터 그림의 몸빛(계열 색)
 * - mood: 벡터 그림의 표정 (나른=눈웃음, 도도=활짝, 루미=잔잔한 미소)
 * - item: 착용한 아이템. 아이템이 열리기 전 단계에서는 그리지 않는다
 * - path: 탐험이면 눈에 작은 별 반짝임
 */
@Composable
fun CarlingDrawing(
    size: Dp,
    modifier: Modifier = Modifier,
    stageIndex: Int = 0,
    glow: Color = BaseGlow,
    path: GrowthPath? = null,
    item: CarlingItem? = null,
    mood: Personality? = null,
    lineage: Lineage? = null,
    asleep: Boolean = false,
) {
    val art = carlingArt(stageIndex, lineage)
    val shownItem = item.takeIf { GrowthRules.itemsUnlocked(stageIndex) }
    if (art != null) {
        val sleeping = asleep && stageIndex == SLEEP_ART_STAGE && (lineage == null || lineage == Lineage.WIND)
        ArtDrawing(art, size, modifier, res = if (sleeping) SleepArtRes else art.res, item = shownItem, beating = !sleeping)
        return
    }
    val stage = GrowthRules.sizeStage(hatched = true, stageIndex = stageIndex)
    val evoTier = GrowthRules.evoTier(stageIndex)
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

        val tier = if (lineage != null) evoTier else 0
        scale(1f, breath, pivot = c + Offset(0f, r)) {
            if (tier > 0) drawLineageBack(c, r, lineage!!, tier)
            drawArms(c, r, glow)
            drawFeet(c, r, glow)
            drawBlobBody(c, r, glow)
            drawHeartCore(c, r, beat)
            drawFace(c, r, mood, sparkle = path == GrowthPath.EXPLORER)
            if (tier > 0) drawLineageFront(c, r, lineage!!, tier)
            if (shownItem != null) drawItem(shownItem, c, r)
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

/** 착용한 아이템을 몸 위에 그린다. [c]·[r]은 젤리 몸의 중심과 반지름. */
private fun DrawScope.drawItem(item: CarlingItem, c: Offset, r: Float) = when (item) {
    CarlingItem.PARTY_HAT -> drawHat(c, r)
    CarlingItem.RIBBON -> drawRibbon(c, r)
    CarlingItem.CROWN -> drawCrown(c, r)
}

private fun DrawScope.drawRibbon(c: Offset, r: Float) {
    val at = c + Offset(r * 0.52f, -r * 0.78f)
    val pink = Brush.linearGradient(listOf(Color(0xFFFF9CC2), Color(0xFFE5507E)), at - Offset(r * 0.4f, r * 0.3f), at + Offset(r * 0.4f, r * 0.3f))
    for (side in listOf(-1f, 1f)) {
        val wing = Path().apply {
            moveTo(at.x, at.y)
            cubicTo(at.x + side * r * 0.2f, at.y - r * 0.36f, at.x + side * r * 0.5f, at.y - r * 0.3f, at.x + side * r * 0.5f, at.y)
            cubicTo(at.x + side * r * 0.5f, at.y + r * 0.3f, at.x + side * r * 0.2f, at.y + r * 0.34f, at.x, at.y)
            close()
        }
        drawPath(wing, pink)
    }
    drawCircle(Color(0xFFFFD0E2), r * 0.11f, at)
}

private fun DrawScope.drawCrown(c: Offset, r: Float) {
    val top = c.y - r * 1.0f
    val w = r * 0.58f
    val h = r * 0.44f
    val crown = Path().apply {
        moveTo(c.x - w, top)
        lineTo(c.x - w * 1.1f, top - h)
        lineTo(c.x - w * 0.45f, top - h * 0.5f)
        lineTo(c.x, top - h * 1.2f)
        lineTo(c.x + w * 0.45f, top - h * 0.5f)
        lineTo(c.x + w * 1.1f, top - h)
        lineTo(c.x + w, top)
        close()
    }
    drawPath(crown, Brush.verticalGradient(listOf(Color(0xFFFFEFA8), Color(0xFFF2B93B)), top - h * 1.2f, top))
    drawRoundRect(Color(0xFFE29A22), Offset(c.x - w, top - r * 0.03f), Size(w * 2, r * 0.11f), CornerRadius(r * 0.05f))
    drawCircle(Color(0xFFFF6F91), r * 0.06f, Offset(c.x, top - h * 0.45f))
}

/** 고깔 모자. */
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

// ---------------- 계열 진화 외형 ----------------
// 진화1(D30)부터 계열 특징이 몸에 자라난다. 진화2·3는 특징을 더 키워 표현한다.
// (완전한 비행형/수중형 실루엣은 정식 일러스트 영역)

private val WindWing = Color(0xCCBFE6FF)
private val WindEdge = Color(0xFF8FD0FF)
private val WaterFin = Color(0xCC79C4FF)
private val WaterEdge = Color(0xFF3B82E6)
private val LeafGreen2 = Color(0xFF7BD86A)
private val LeafDeep2 = Color(0xFF4CA85A)
private val TireDark = Color(0xFF2A2C38)
private val LightRim = Color(0xFFFFD54A)
private val EarthRim = Color(0xFFFF9D5C)

private fun tierSize(tier: Int) = 0.85f + (tier - 1) * 0.4f

/** 몸 뒤에 그리는 특징: 바람=날개, 물=지느러미, 나무=잎. */
private fun DrawScope.drawLineageBack(c: Offset, r: Float, lineage: Lineage, tier: Int) {
    val sz = tierSize(tier)
    when (lineage) {
        Lineage.WIND -> for (side in listOf(-1f, 1f)) {
            drawWing(c, r, side, sz, WindWing, WindEdge)
            if (tier >= 2) drawWing(c, r, side, sz * 0.66f, WindWing.copy(alpha = 0.45f), WindEdge)
        }
        Lineage.WATER -> {
            for (side in listOf(-1f, 1f)) drawFin(c, r, side, sz)
            drawTailFin(c, r, sz)
        }
        Lineage.TREE -> drawLeaves(c, r, sz, tier)
        else -> Unit
    }
}

/** 몸 앞/아래에 그리는 특징: 빛=바퀴+빛줄기, 흙=튼튼한 바퀴. */
private fun DrawScope.drawLineageFront(c: Offset, r: Float, lineage: Lineage, tier: Int) {
    val sz = tierSize(tier)
    when (lineage) {
        Lineage.LIGHT_RAY -> {
            for (side in listOf(-1f, 1f)) drawWheel(c + Offset(side * r * 0.56f, r * 0.98f), r * 0.3f * sz, LightRim)
            drawStar(c + Offset(r * 0.78f, -r * 0.2f), r * 0.16f * sz, LightRim)
        }
        Lineage.EARTH -> for (side in listOf(-1f, 1f)) drawWheel(c + Offset(side * r * 0.62f, r * 0.98f), r * 0.36f * sz, EarthRim)
        else -> Unit
    }
}

private fun DrawScope.drawWing(c: Offset, r: Float, side: Float, sz: Float, fill: Color, edge: Color) {
    val a = Offset(c.x + side * r * 0.55f, c.y - r * 0.15f)
    val p = Path().apply {
        moveTo(a.x, a.y)
        cubicTo(
            a.x + side * r * 0.2f * sz, a.y - r * 1.35f * sz,
            a.x + side * r * 1.5f * sz, a.y - r * 1.2f * sz,
            a.x + side * r * 1.8f * sz, a.y - r * 0.25f * sz,
        )
        cubicTo(
            a.x + side * r * 1.5f * sz, a.y + r * 0.15f * sz,
            a.x + side * r * 1.0f * sz, a.y + r * 0.35f * sz,
            a.x + side * r * 0.4f * sz, a.y + r * 0.2f * sz,
        )
        close()
    }
    drawPath(p, fill)
    drawPath(p, edge, style = Stroke(r * 0.04f))
    for (i in 1..2) {
        val tip = Offset(a.x + side * r * (0.7f + i * 0.45f) * sz, a.y - r * (0.75f - i * 0.2f) * sz)
        drawLine(edge.copy(alpha = 0.55f), a + Offset(side * r * 0.3f * sz, 0f), tip, r * 0.025f)
    }
}

private fun DrawScope.drawFin(c: Offset, r: Float, side: Float, sz: Float) {
    val a = Offset(c.x + side * r * 0.95f, c.y + r * 0.05f)
    val p = Path().apply {
        moveTo(a.x, a.y - r * 0.25f * sz)
        lineTo(a.x + side * r * 0.95f * sz, a.y + r * 0.1f * sz)
        lineTo(a.x + side * r * 0.25f * sz, a.y + r * 0.55f * sz)
        close()
    }
    drawPath(p, WaterFin)
    drawPath(p, WaterEdge, style = Stroke(r * 0.035f))
}

private fun DrawScope.drawTailFin(c: Offset, r: Float, sz: Float) {
    val a = Offset(c.x, c.y + r * 0.92f)
    val p = Path().apply {
        moveTo(a.x, a.y)
        lineTo(a.x - r * 0.45f * sz, a.y + r * 0.62f * sz)
        lineTo(a.x + r * 0.45f * sz, a.y + r * 0.62f * sz)
        close()
    }
    drawPath(p, WaterFin)
    drawPath(p, WaterEdge, style = Stroke(r * 0.035f))
}

private fun DrawScope.drawLeaves(c: Offset, r: Float, sz: Float, tier: Int) {
    val base = c + Offset(0f, -r * 0.92f)
    val n = 2 + tier
    val leaf = Brush.linearGradient(listOf(LeafGreen2, LeafDeep2), base - Offset(0f, r * 0.7f), base)
    for (i in 0 until n) {
        val ang = -42f + i * (84f / (n - 1))
        rotate(ang, pivot = base) {
            drawOval(leaf, base - Offset(r * 0.14f * sz, r * 0.8f * sz), Size(r * 0.28f * sz, r * 0.8f * sz))
        }
    }
}

private fun DrawScope.drawWheel(at: Offset, radius: Float, rim: Color) {
    drawCircle(TireDark, radius, at)
    drawCircle(rim, radius, at, style = Stroke(radius * 0.24f))
    drawCircle(Color(0xFF5A5E70), radius * 0.38f, at)
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
