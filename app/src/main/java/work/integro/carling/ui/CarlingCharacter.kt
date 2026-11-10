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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
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
import work.integro.carling.game.Vehicle
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
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
                if (item != null && body != null) {
                    val eyes = art.eyes ?: Offset(body.x, body.y - art.bodyRadius * 0.15f)
                    drawItem(
                        item,
                        ItemAnchor(
                            c = Offset(body.x * s, body.y * s),
                            r = art.bodyRadius * s,
                            eyes = Offset(eyes.x * s, eyes.y * s),
                            eyeGap = (if (art.eyeGap > 0f) art.eyeGap else art.bodyRadius * 0.3f) * s,
                            heart = Offset(art.heart.x * s, art.heart.y * s),
                            feetY = (art.shadowAt?.y ?: (body.y + art.bodyRadius)) * s,
                        ),
                    )
                }
                drawSparkles(center, s * 0.42f, Color.White, twinkle)
            }
        }
    }
}

/** 이동수단에 탄 모습: 차체 → 좌석의 카링 → 좌석 아래 차체(카링 앞) 순서로 겹쳐 그린다. */
@Composable
private fun RidingDrawing(vehicle: Vehicle, size: Dp, modifier: Modifier, rider: @Composable (Dp) -> Unit) {
    val art = vehicleArt(vehicle)
    val riderSize = size * art.rider
    Box(modifier.size(size)) {
        Image(painterResource(art.res), contentDescription = null, modifier = Modifier.fillMaxSize())
        // 걸터앉으면 안장 높이까지 내려 앉히고, 안장 아래로 내려가는 부분은 서서히 투명하게 한다
        val legs = if (art.straddle) STRADDLE_LEGS else 0f
        Box(
            Modifier
                .offset(x = size * art.seat.x - riderSize / 2, y = size * art.seat.y - riderSize * (0.95f - legs))
                .size(riderSize)
                .graphicsLayer { if (art.straddle) compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    if (art.straddle) {
                        val end = 0.95f - legs
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Black,
                                (end - STRADDLE_FADE) to Color.Black,
                                end to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                },
        ) { rider(riderSize) }
        Image(
            painterResource(art.res),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().drawWithContent {
                clipRect(top = this.size.height * art.cover) { this@drawWithContent.drawContent() }
            },
        )
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
    vehicle: Vehicle? = null,
) {
    if (vehicle != null) {
        RidingDrawing(vehicle, size, modifier) { riderSize ->
            CarlingDrawing(riderSize, stageIndex = stageIndex, glow = glow, item = item, mood = mood, lineage = lineage, asleep = asleep)
        }
        return
    }
    val art = carlingArt(stageIndex, lineage)
    val shownItem = item.takeIf { GrowthRules.itemsUnlocked(stageIndex) && canWearItem(stageIndex, lineage, it) }
    // 자는 모습 일러스트는 최종 시안에 아직 없어, 자는 동안에는 심장만 잔잔하게 둔다
    if (art.res != 0) {
        ArtDrawing(art, size, modifier, item = shownItem, beating = !asleep)
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
            if (shownItem != null) {
                drawItem(shownItem, ItemAnchor(c, r, c - Offset(0f, r * 0.2f), r * 0.46f, c + Offset(0f, r * 0.52f), c.y + r))
            }
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

/*
 * 꾸미기 아이템 — 캐릭터와 같은 '젤리' 질감(반투명 광택, 둥근 형태, 위쪽 하이라이트)으로 그린다.
 * 위치는 머리 꼭대기([ItemAnchor.top])와 두 눈을 기준으로 잡아 그림이 바뀌어도 어긋나지 않게 한다.
 */
private fun DrawScope.drawItem(item: CarlingItem, a: ItemAnchor) = when (item) {
    CarlingItem.PARTY_HAT -> drawPartyHat(a)
    CarlingItem.RIBBON -> drawRibbon(a)
    CarlingItem.CROWN -> drawCrown(a)
    CarlingItem.SUNGLASSES -> drawHeartGlasses(a)
    CarlingItem.ROUND_GLASSES -> drawRoundGlasses(a)
    CarlingItem.FLOWER_CROWN -> drawFlowerCrown(a)
    CarlingItem.HEADPHONES -> drawHeadphones(a)
    CarlingItem.STRAW_HAT -> drawStrawHat(a)
    CarlingItem.STAR_PIN -> drawStarPin(a)
    CarlingItem.SPROUT -> drawSprout(a)
    CarlingItem.NECKLACE, CarlingItem.SHOES -> Unit
}

private class ItemAnchor(val c: Offset, val r: Float, val eyes: Offset, val eyeGap: Float, val heart: Offset, val feetY: Float) {
    /** 머리 꼭대기. */
    val top: Offset get() = Offset(c.x, c.y - r)
}

/** 젤리 질감 채우기: 왼쪽 위가 밝고 아래가 진한 반투명 그라데이션. */
private fun jelly(base: Color, at: Offset, radius: Float) = Brush.radialGradient(
    listOf(lerp(Color.White, base, 0.35f), base, lerp(base, Color.Black, 0.18f)),
    center = at - Offset(radius * 0.35f, radius * 0.45f),
    radius = radius * 1.6f,
)

/** 젤리 광택 한 점과 테두리. */
private fun DrawScope.gloss(path: Path, base: Color, highlightAt: Offset, hw: Float, hh: Float) {
    drawPath(path, lerp(base, Color.White, 0.55f).copy(alpha = 0.7f), style = Stroke(hw * 0.18f))
    drawOval(Color.White.copy(alpha = 0.75f), highlightAt - Offset(hw / 2, hh / 2), Size(hw, hh))
}

private fun DrawScope.jellyBall(at: Offset, radius: Float, base: Color) {
    drawCircle(jelly(base, at, radius), radius, at)
    drawCircle(lerp(base, Color.White, 0.5f).copy(alpha = 0.6f), radius, at, style = Stroke(radius * 0.12f))
    drawOval(Color.White.copy(alpha = 0.8f), at - Offset(radius * 0.55f, radius * 0.6f), Size(radius * 0.6f, radius * 0.38f))
}

private val RibbonPink = Color(0xFFFF7FAE)
private val JellyGold = Color(0xFFFFC94A)
private val JellyMint = Color(0xFF5FE3C0)

/** 머리 오른쪽 위의 통통한 젤리 리본. */
private fun DrawScope.drawRibbon(a: ItemAnchor) {
    val at = a.top + Offset(a.r * 0.42f, a.r * 0.16f)
    val s = a.r * 0.36f
    for (side in listOf(-1f, 1f)) {
        val wing = Path().apply {
            moveTo(at.x, at.y)
            cubicTo(at.x + side * s * 0.4f, at.y - s * 1.0f, at.x + side * s * 1.5f, at.y - s * 0.9f, at.x + side * s * 1.4f, at.y)
            cubicTo(at.x + side * s * 1.5f, at.y + s * 0.9f, at.x + side * s * 0.4f, at.y + s * 1.0f, at.x, at.y)
            close()
        }
        val wc = at + Offset(side * s * 0.8f, 0f)
        drawPath(wing, jelly(RibbonPink, wc, s))
        gloss(wing, RibbonPink, wc + Offset(-s * 0.15f, -s * 0.35f), s * 0.5f, s * 0.25f)
    }
    jellyBall(at, s * 0.42f, Color(0xFFFFA9C8))
}

/** 왕관: 둥근 봉우리 세 개와 구슬, 가운데 하트 보석. */
private fun DrawScope.drawCrown(a: ItemAnchor) {
    val base = a.top.y + a.r * 0.12f
    val w = a.r * 0.5f
    val h = a.r * 0.42f
    val crown = Path().apply {
        moveTo(a.c.x - w, base)
        quadraticTo(a.c.x - w * 1.05f, base - h * 0.6f, a.c.x - w * 0.95f, base - h)
        quadraticTo(a.c.x - w * 0.6f, base - h * 0.45f, a.c.x - w * 0.42f, base - h * 0.55f)
        quadraticTo(a.c.x - w * 0.2f, base - h * 1.15f, a.c.x, base - h * 1.25f)
        quadraticTo(a.c.x + w * 0.2f, base - h * 1.15f, a.c.x + w * 0.42f, base - h * 0.55f)
        quadraticTo(a.c.x + w * 0.6f, base - h * 0.45f, a.c.x + w * 0.95f, base - h)
        quadraticTo(a.c.x + w * 1.05f, base - h * 0.6f, a.c.x + w, base)
        close()
    }
    val mid = Offset(a.c.x, base - h * 0.5f)
    drawPath(crown, jelly(JellyGold, mid, w))
    gloss(crown, JellyGold, mid + Offset(-w * 0.45f, -h * 0.1f), w * 0.35f, h * 0.22f)
    drawRoundRect(lerp(JellyGold, Color(0xFFE08A1E), 0.5f), Offset(a.c.x - w * 1.02f, base - h * 0.12f), Size(w * 2.04f, h * 0.26f), CornerRadius(h * 0.13f))
    for (x in listOf(-0.95f, 0f, 0.95f)) {
        val tip = Offset(a.c.x + x * w, base - h * (if (x == 0f) 1.3f else 1.05f))
        jellyBall(tip, a.r * 0.06f, Color(0xFFFFF1B8))
    }
    drawHeart(Offset(a.c.x, base - h * 0.42f), a.r * 0.12f, Color(0xFFFF6F9C))
}

private fun DrawScope.drawHeart(center: Offset, hw: Float, color: Color) {
    val p = Path().apply {
        moveTo(center.x, center.y + hw * 0.95f)
        cubicTo(center.x - hw * 1.5f, center.y - hw * 0.1f, center.x - hw * 0.65f, center.y - hw * 1.15f, center.x, center.y - hw * 0.3f)
        cubicTo(center.x + hw * 0.65f, center.y - hw * 1.15f, center.x + hw * 1.5f, center.y - hw * 0.1f, center.x, center.y + hw * 0.95f)
        close()
    }
    drawPath(p, jelly(color, center, hw))
    drawOval(Color.White.copy(alpha = 0.8f), center - Offset(hw * 0.6f, hw * 0.55f), Size(hw * 0.45f, hw * 0.28f))
}

/** 하트 모양 젤리 선글라스. */
private fun DrawScope.drawHeartGlasses(a: ItemAnchor) {
    val lens = a.eyeGap * 0.78f
    val frame = Color(0xFFFF5C8A)
    for (side in listOf(-1f, 1f)) {
        val at = Offset(a.eyes.x + side * a.eyeGap, a.eyes.y + lens * 0.05f)
        val p = Path().apply {
            moveTo(at.x, at.y + lens * 0.95f)
            cubicTo(at.x - lens * 1.5f, at.y - lens * 0.1f, at.x - lens * 0.65f, at.y - lens * 1.15f, at.x, at.y - lens * 0.3f)
            cubicTo(at.x + lens * 0.65f, at.y - lens * 1.15f, at.x + lens * 1.5f, at.y - lens * 0.1f, at.x, at.y + lens * 0.95f)
            close()
        }
        drawPath(p, Brush.verticalGradient(listOf(Color(0xE63A1F4A), Color(0xE6702A5C)), at.y - lens, at.y + lens))
        drawPath(p, frame, style = Stroke(a.r * 0.035f))
        drawOval(Color.White.copy(alpha = 0.55f), at - Offset(lens * 0.75f, lens * 0.55f), Size(lens * 0.55f, lens * 0.3f))
    }
    drawLine(frame, Offset(a.eyes.x - a.eyeGap * 0.35f, a.eyes.y - lens * 0.2f), Offset(a.eyes.x + a.eyeGap * 0.35f, a.eyes.y - lens * 0.2f), a.r * 0.035f, cap = StrokeCap.Round)
}

/** 동그란 안경: 얇은 금테에 맑은 렌즈. 눈이 비쳐 보인다. */
private fun DrawScope.drawRoundGlasses(a: ItemAnchor) {
    val radius = a.eyeGap * 0.78f
    val gold = Color(0xFFE8B04A)
    for (side in listOf(-1f, 1f)) {
        val at = Offset(a.eyes.x + side * a.eyeGap, a.eyes.y)
        drawCircle(Color(0x33BFE6FF), radius, at)
        drawCircle(gold, radius, at, style = Stroke(a.r * 0.03f))
        drawArc(Color.White.copy(alpha = 0.7f), 200f, 50f, false, at - Offset(radius * 0.75f, radius * 0.75f), Size(radius * 1.5f, radius * 1.5f), style = Stroke(a.r * 0.02f, cap = StrokeCap.Round))
    }
    drawArc(gold, 200f, 140f, false, Offset(a.eyes.x - a.eyeGap * 0.25f, a.eyes.y - radius * 0.25f), Size(a.eyeGap * 0.5f, radius * 0.5f), style = Stroke(a.r * 0.03f))
}

/** 꽃 화관: 머리 둘레를 따라 젤리 꽃과 잎을 엮는다. */
private fun DrawScope.drawFlowerCrown(a: ItemAnchor) {
    val colors = listOf(Color(0xFFFF9CC2), Color(0xFFFFE07A), Color(0xFFC9B5FF), Color(0xFFFFB8A0))
    val n = 5
    for (i in 0 until n) {
        val t = i / (n - 1f) * 2f - 1f
        val at = Offset(a.c.x + t * a.r * 0.72f, a.top.y + a.r * (0.1f + t * t * 0.32f))
        // 꽃 사이의 잎
        if (i < n - 1) {
            val t2 = (i + 0.5f) / (n - 1f) * 2f - 1f
            val leaf = Offset(a.c.x + t2 * a.r * 0.72f, a.top.y + a.r * (0.06f + t2 * t2 * 0.32f))
            drawOval(jelly(Color(0xFF7BD86A), leaf, a.r * 0.08f), leaf - Offset(a.r * 0.09f, a.r * 0.05f), Size(a.r * 0.18f, a.r * 0.1f))
        }
        val petal = a.r * 0.075f
        for (k in 0 until 5) {
            val angle = (k * 72f - 90f) * (kotlin.math.PI.toFloat() / 180f)
            jellyBall(at + Offset(kotlin.math.cos(angle) * petal, kotlin.math.sin(angle) * petal), petal * 0.85f, colors[i % colors.size])
        }
        jellyBall(at, petal * 0.6f, Color(0xFFFFC14D))
    }
}

/** 고깔 모자: 민트 줄무늬 젤리 고깔에 방울. */
private fun DrawScope.drawPartyHat(a: ItemAnchor) {
    val pivot = a.top + Offset(-a.r * 0.22f, a.r * 0.12f)
    val h = a.r * 0.7f
    val w = a.r * 0.32f
    rotate(-14f, pivot = pivot) {
        val hat = Path().apply {
            moveTo(pivot.x - w, pivot.y)
            quadraticTo(pivot.x - w * 0.2f, pivot.y - h * 0.6f, pivot.x, pivot.y - h)
            quadraticTo(pivot.x + w * 0.2f, pivot.y - h * 0.6f, pivot.x + w, pivot.y)
            close()
        }
        val mid = pivot - Offset(0f, h * 0.45f)
        drawPath(hat, jelly(JellyMint, mid, w))
        for (k in 1..2) {
            val y = pivot.y - h * k * 0.32f
            val half = w * (1f - k * 0.32f)
            drawLine(Color(0xFFFFF1B8), Offset(pivot.x - half, y), Offset(pivot.x + half, y), a.r * 0.045f, cap = StrokeCap.Round)
        }
        gloss(hat, JellyMint, mid + Offset(-w * 0.3f, -h * 0.05f), w * 0.25f, h * 0.18f)
        jellyBall(Offset(pivot.x, pivot.y - h), a.r * 0.08f, Color(0xFFFF9CC2))
    }
}

/** 헤드폰: 머리띠와 하트 무늬 젤리 귀덮개. */
private fun DrawScope.drawHeadphones(a: ItemAnchor) {
    val band = Color(0xFF9F8CFF)
    val cupY = a.eyes.y + a.r * 0.02f
    val x = a.r * 0.98f
    drawArc(
        band, 200f, 140f, false,
        topLeft = Offset(a.c.x - x, a.top.y - a.r * 0.05f),
        size = Size(x * 2, a.r * 1.7f),
        style = Stroke(a.r * 0.11f, cap = StrokeCap.Round),
    )
    for (side in listOf(-1f, 1f)) {
        val at = Offset(a.c.x + side * a.r * 0.96f, cupY)
        val w = a.r * 0.18f
        val h = a.r * 0.26f
        drawOval(jelly(band, at, h), at - Offset(w, h), Size(w * 2, h * 2))
        drawHeart(at, w * 0.5f, Color(0xFFFF9CC2))
        drawOval(Color.White.copy(alpha = 0.7f), at - Offset(w * 0.7f, h * 0.8f), Size(w * 0.5f, h * 0.35f))
    }
}

/** 밀짚모자: 넓은 챙과 분홍 띠, 작은 꽃. */
private fun DrawScope.drawStrawHat(a: ItemAnchor) {
    val brimY = a.top.y + a.r * 0.22f
    val straw = Color(0xFFF5D48A)
    drawOval(jelly(straw, Offset(a.c.x, brimY), a.r), Offset(a.c.x - a.r * 0.95f, brimY - a.r * 0.13f), Size(a.r * 1.9f, a.r * 0.3f))
    val crown = Path().apply {
        moveTo(a.c.x - a.r * 0.5f, brimY)
        cubicTo(a.c.x - a.r * 0.52f, brimY - a.r * 0.55f, a.c.x + a.r * 0.52f, brimY - a.r * 0.55f, a.c.x + a.r * 0.5f, brimY)
        close()
    }
    drawPath(crown, jelly(straw, Offset(a.c.x, brimY - a.r * 0.25f), a.r * 0.5f))
    drawRect(Color(0xFFFF8FB4), Offset(a.c.x - a.r * 0.5f, brimY - a.r * 0.13f), Size(a.r * 1.0f, a.r * 0.1f))
    gloss(crown, straw, Offset(a.c.x - a.r * 0.22f, brimY - a.r * 0.32f), a.r * 0.2f, a.r * 0.1f)
    jellyBall(Offset(a.c.x + a.r * 0.36f, brimY - a.r * 0.09f), a.r * 0.07f, Color(0xFFFFFFFF))
}

/** 별 머리핀: 머리 왼쪽 위에 꽂은 노란 젤리 별. */
private fun DrawScope.drawStarPin(a: ItemAnchor) {
    val at = a.top + Offset(-a.r * 0.45f, a.r * 0.2f)
    val outer = a.r * 0.24f
    val inner = outer * 0.5f
    val star = Path().apply {
        for (k in 0 until 10) {
            val rad = if (k % 2 == 0) outer else inner
            val ang = (-90f + k * 36f) * (kotlin.math.PI.toFloat() / 180f)
            val p = at + Offset(kotlin.math.cos(ang) * rad, kotlin.math.sin(ang) * rad)
            if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    rotate(-12f, pivot = at) {
        drawPath(star, jelly(JellyGold, at, outer))
        gloss(star, JellyGold, at + Offset(-outer * 0.25f, -outer * 0.3f), outer * 0.4f, outer * 0.22f)
    }
}

/** 새싹: 머리 꼭대기에서 자란 두 잎. */
private fun DrawScope.drawSprout(a: ItemAnchor) {
    val root = a.top + Offset(0f, a.r * 0.06f)
    val green = Color(0xFF7BD86A)
    drawLine(Color(0xFF5BB85A), root, root - Offset(0f, a.r * 0.22f), a.r * 0.05f, cap = StrokeCap.Round)
    for (side in listOf(-1f, 1f)) {
        val stem = root - Offset(0f, a.r * 0.22f)
        val tip = stem + Offset(side * a.r * 0.36f, -a.r * 0.16f)
        val leaf = Path().apply {
            moveTo(stem.x, stem.y)
            quadraticTo(stem.x + side * a.r * 0.12f, tip.y - a.r * 0.14f, tip.x, tip.y)
            quadraticTo(stem.x + side * a.r * 0.24f, stem.y + a.r * 0.06f, stem.x, stem.y)
            close()
        }
        val mid = Offset((stem.x + tip.x) / 2, (stem.y + tip.y) / 2)
        drawPath(leaf, jelly(green, mid, a.r * 0.18f))
        gloss(leaf, green, mid + Offset(-side * a.r * 0.02f, -a.r * 0.04f), a.r * 0.1f, a.r * 0.04f)
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
