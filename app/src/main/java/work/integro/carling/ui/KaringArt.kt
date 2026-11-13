package work.integro.carling.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.R
import work.integro.carling.game.CarColor
import work.integro.carling.game.CarPart
import work.integro.carling.game.CarSlot
import work.integro.carling.game.Personality
import work.integro.carling.game.Stage
import work.integro.carling.game.TimeBucket

/** 카링을 보여주는 모습: 서 있기, 차에 앉아 있기(주차), 달리기(D). */
enum class Pose { STAND, CAR, DRIVE }

/** 보이는 바퀴 하나: 가운데와 타원 반지름(그림 너비·높이 대비). 휠캡을 이 타원에 맞춰 눌러 붙인다. */
internal class Wheel(val cx: Float, val cy: Float, val rx: Float, val ry: Float)

/**
 * 차에 아이템을 다는 자리(그림 너비·높이 대비). 안테나·뒤쪽 소품은 [antenna]·[rear]에 아래 가운데를 맞추고
 * 차 그림 뒤에 그려 차체가 아래를 가린다. 휠캡은 바퀴 위에 그린다.
 */
internal class CarSlots(val wheels: List<Wheel>, val antenna: Offset, val antennaH: Float, val rear: Offset, val rearH: Float, val body: List<Offset> = emptyList())

private fun pts(vararg v: Float): List<Offset> = v.toList().chunked(2).map { Offset(it[0], it[1]) }

/** 그림 한 장과 그 위에 얹을 자리(그림 너비·높이 대비 비율). 차가 없는 그림은 [slots]가 null. */
internal class ArtSpec(@param:DrawableRes val res: Int, val aspect: Float, val heart: Offset, val slots: CarSlots?)

private const val SQ = 1312f / 1199f
private const val WIDE = 1448f / 1086f
private const val WIDER = 1536f / 1024f

private val Egg = ArtSpec(R.drawable.karing_egg, SQ, Offset(.50f, .54f), null)

/** 단계와 모습에 맞는 그림. 알은 차가 없어 어느 모습이든 알이다. */
/** 그림마다 머리(또는 머리 장식) 맨 윗선 높이(그림 높이 대비). 알파 채널에서 잰 값. */
internal fun headTopOf(@DrawableRes res: Int): Float = when (res) {
    R.drawable.karing_baby -> 0.204f
    R.drawable.karing_baby_car -> 0.148f
    R.drawable.karing_baby_drive -> 0.181f
    R.drawable.karing_flame -> 0.130f
    R.drawable.karing_flame_car -> 0.095f
    R.drawable.karing_flame_drive -> 0.094f
    R.drawable.karing_wing -> 0.143f
    R.drawable.karing_wing_car -> 0.097f
    R.drawable.karing_wing_drive -> 0.083f
    else -> 0.148f
}

/** 그림마다 두 눈 가운데(그림 너비·높이 대비). 알파·어두운 눈동자에서 잰 값(11/13). 안경·선글라스를 여기에 맞춘다. */
internal fun eyesOf(@DrawableRes res: Int): Pair<Offset, Offset>? = when (res) {
    R.drawable.karing_baby -> Offset(.411f, .441f) to Offset(.624f, .440f)
    R.drawable.karing_baby_car -> Offset(.414f, .313f) to Offset(.566f, .323f)
    R.drawable.karing_baby_drive -> Offset(.507f, .322f) to Offset(.614f, .317f)
    R.drawable.karing_wing -> Offset(.372f, .440f) to Offset(.572f, .443f)
    R.drawable.karing_wing_car -> Offset(.423f, .343f) to Offset(.563f, .346f)
    R.drawable.karing_wing_drive -> Offset(.529f, .304f) to Offset(.637f, .292f)
    R.drawable.karing_flame -> Offset(.338f, .426f) to Offset(.519f, .431f)
    R.drawable.karing_flame_car -> Offset(.392f, .330f) to Offset(.509f, .337f)
    R.drawable.karing_flame_drive -> Offset(.564f, .328f) to Offset(.640f, .320f)
    else -> null
}

internal fun artFor(stage: Stage, pose: Pose): ArtSpec = when (stage) {
    // 11/13 사용자 결정: 알을 뺀 다섯 모습 중 젤리링=아기 그림, 아기링=날개 그림, 꼬마링=불꽃 그림. 버디링·소울링은 추후 공개(그림이 오기 전까지 불꽃 실루엣)
    Stage.EGG -> Egg
    Stage.CRACKED -> when (pose) {
        Pose.STAND -> ArtSpec(R.drawable.karing_baby, SQ, Offset(.507f, .635f), null)
        Pose.CAR -> ArtSpec(R.drawable.karing_baby_car, WIDE, Offset(.47f, .54f), CarSlots(listOf(Wheel(.628f, .765f, .036f, .09f), Wheel(.745f, .75f, .03f, .075f)), Offset(.74f, .56f), .30f, Offset(.67f, .54f), .26f,
            pts(.215f, .66f, .235f, .58f, .30f, .565f, .66f, .565f, .70f, .525f, .745f, .55f, .775f, .63f, .775f, .74f, .70f, .79f, .53f, .80f, .35f, .80f, .24f, .78f)))
        Pose.DRIVE -> ArtSpec(R.drawable.karing_baby_drive, WIDE, Offset(.55f, .43f), CarSlots(listOf(Wheel(.315f, .72f, .042f, .085f), Wheel(.46f, .74f, .042f, .085f)), Offset(.29f, .56f), .30f, Offset(.355f, .53f), .26f,
            pts(.26f, .64f, .28f, .57f, .33f, .555f, .70f, .555f, .75f, .60f, .77f, .66f, .765f, .74f, .70f, .78f, .36f, .78f, .27f, .72f)))
    }
    Stage.BABY -> when (pose) {
        Pose.STAND -> ArtSpec(R.drawable.karing_wing, SQ, Offset(.477f, .664f), null)
        Pose.CAR -> ArtSpec(R.drawable.karing_wing_car, WIDE, Offset(.48f, .50f), CarSlots(listOf(Wheel(.66f, .79f, .04f, .10f), Wheel(.80f, .75f, .03f, .08f)), Offset(.73f, .60f), .28f, Offset(.655f, .56f), .25f,
            pts(.185f, .66f, .21f, .60f, .32f, .615f, .64f, .615f, .68f, .56f, .77f, .57f, .82f, .64f, .82f, .75f, .74f, .82f, .55f, .84f, .30f, .80f, .19f, .76f)))
        Pose.DRIVE -> ArtSpec(R.drawable.karing_wing_drive, WIDER, Offset(.57f, .47f), CarSlots(listOf(Wheel(.26f, .73f, .045f, .10f), Wheel(.505f, .78f, .042f, .10f)), Offset(.235f, .55f), .30f, Offset(.315f, .52f), .26f,
            pts(.19f, .62f, .22f, .52f, .30f, .53f, .45f, .575f, .72f, .575f, .80f, .60f, .86f, .66f, .86f, .74f, .72f, .80f, .30f, .82f, .19f, .72f)))
    }
    Stage.BREEZE, Stage.MYCARLING, Stage.SOUL -> when (pose) {
        Pose.STAND -> ArtSpec(R.drawable.karing_flame, SQ, Offset(.435f, .642f), null)
        Pose.CAR -> ArtSpec(R.drawable.karing_flame_car, WIDER, Offset(.44f, .53f), CarSlots(listOf(Wheel(.545f, .785f, .04f, .10f), Wheel(.795f, .745f, .03f, .085f)), Offset(.79f, .60f), .28f, Offset(.69f, .57f), .25f,
            pts(.14f, .70f, .18f, .63f, .28f, .635f, .62f, .635f, .66f, .58f, .80f, .54f, .83f, .60f, .83f, .78f, .74f, .84f, .50f, .87f, .20f, .83f, .14f, .77f)))
        Pose.DRIVE -> ArtSpec(R.drawable.karing_flame_drive, 1774f / 887f, Offset(.58f, .49f), CarSlots(listOf(Wheel(.29f, .73f, .036f, .10f), Wheel(.55f, .78f, .034f, .10f)), Offset(.245f, .55f), .32f, Offset(.335f, .52f), .28f,
            pts(.21f, .60f, .23f, .48f, .30f, .50f, .45f, .60f, .72f, .60f, .80f, .62f, .86f, .67f, .86f, .76f, .80f, .82f, .30f, .86f, .22f, .74f)))
    }
}

private val MintGlow = Color(0xFF8CFFD6)

/**
 * 카링 한 장면. 성장(단계)은 그림이, 개성은 그 위에 얹는 층이 맡는다.
 * - 몸 색(최빈 시간대): 카링 뒤의 빛무리 색. 밤이면 별이 반짝인다.
 * - 대표 장식(최빈 목적): 몸 옆에 다는 장식.
 * - 심장빛(평균 운전점수): 85점 이상이면 민트 심장이 선명하게 반짝인다.
 * 그림이 아직 층으로 나뉘지 않아 몸 자체의 색은 바꾸지 않는다(층 분리 그림이 오면 교체).
 * [still]이면 움직이지 않는다(주행 중 화면).
 */
@Composable
fun KaringView(
    stage: Stage,
    pose: Pose,
    personality: Personality,
    equipped: Map<CarSlot, CarPart>,
    height: Dp,
    carColor: CarColor? = null,
    modifier: Modifier = Modifier,
    still: Boolean = false,
    onTap: (() -> Unit)? = null,
    /** 머리 위 레벨 엠블럼(11/13 사용자 요청). null이면 안 단다. */
    emblemLevel: Int? = null,
    /** 바깥에서 좌우로 뒤집어 그릴 때 숫자를 바로 보이게 다시 뒤집는다. */
    mirrored: Boolean = false,
) {
    val art = artFor(stage, pose)
    val t = rememberInfiniteTransition(label = "karing")
    val breath by t.animateFloat(0.985f, 1.015f, infiniteRepeatable(tween(1700), RepeatMode.Reverse), label = "breath")
    val beat by t.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(if (personality.heartBright) 620 else 900), RepeatMode.Reverse), label = "beat")
    val twinkle by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "twinkle")
    // 반딧불이 머리 위를 한 바퀴 도는 시간
    val orbit by t.animateFloat(0f, 1f, infiniteRepeatable(tween(6_000, easing = androidx.compose.animation.core.LinearEasing)), label = "orbit")

    // 말랑 젤리: 서 있는 카링을 누르면 출렁인다(차는 출렁이지 않게 서 있을 때만)
    val jelly = remember { JellyBody() }
    val shader = remember { if (JellySupported && pose == Pose.STAND && !still) JellyShader() else null }
    var frame by remember { mutableIntStateOf(0) }
    if (shader != null) LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) jelly.step(((now - last) / 1e9f).coerceIn(1f / 240f, 1f / 20f), 0f, 0f)
                last = now
                frame++
            }
        }
    }

    BoxWithConstraints(modifier.height(height).aspectRatio(art.aspect)) {
        val w = maxWidth
        val h = maxHeight
        val vibe = personality.vibe?.takeIf { stage.ordinal >= Stage.CRACKED.ordinal }
        val bodyColor = Color(vibe?.color ?: personality.body.color)
        // 몸 색 빛무리
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width * art.heart.x, size.height * art.heart.y)
            val r = size.minDimension * 0.62f
            val strong = if (vibe != null) 0.7f else 0.55f
            drawCircle(Brush.radialGradient(listOf(bodyColor.copy(alpha = strong), bodyColor.copy(alpha = strong * 0.35f), Color.Transparent), c, r), r, c)
            drawOval(Color(0x22202450), Offset(size.width * 0.22f, size.height * 0.90f), androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.05f))
        }
        // 차 뒤에 다는 것: 안테나와 뒤쪽 소품(차체가 아래를 가린다)
        art.slots?.let { sl ->
            equipped[CarSlot.REAR]?.let { PartImage(it, w, h, sl.rear, sl.rearH, still) }
            equipped[CarSlot.ANTENNA]?.let { PartImage(it, w, h, sl.antenna, sl.antennaH, still, sway = true) }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (!still) scaleY = breath
                    transformOrigin = TransformOrigin(0.5f, 0.92f)
                    if (shader != null) {
                        @Suppress("UNUSED_EXPRESSION") frame
                        renderEffect = shader.effect(size.width, size.height, jelly.displacements(breathing = true))
                    }
                }
                .then(
                    if (onTap != null || shader != null) Modifier.pointerInput(Unit) {
                        detectTapGestures { at ->
                            jelly.poke((at.x / size.width).coerceIn(0f, 1f), (at.y / size.height).coerceIn(0f, 1f), 2.8f)
                            onTap?.invoke()
                        }
                    } else Modifier,
                ),
        ) {
            Image(painterResource(art.res), contentDescription = stage.label, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            // 차체 색: 같은 그림을 색 변환해 차체 영역(바퀴 제외)에만 덧그린다. 그림자·광택은 밝기로 남는다.
            val body = art.slots?.body.orEmpty()
            if (carColor != null && carColor != CarColor.PEARL && body.size >= 3) {
                val wheels = art.slots?.wheels.orEmpty()
                Image(
                    painterResource(art.res),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = remember(carColor) { paintFilter(carColor) },
                    modifier = Modifier.fillMaxSize().drawWithContent {
                        val poly = Path().apply {
                            body.forEachIndexed { i, p -> if (i == 0) moveTo(p.x * size.width, p.y * size.height) else lineTo(p.x * size.width, p.y * size.height) }
                            close()
                        }
                        val holes = Path().apply {
                            // 타이어까지 가리도록 바퀴 타원을 조금 크게 뺀다
                            wheels.forEach { wh ->
                                val cx = wh.cx * size.width
                                val cy = wh.cy * size.height
                                val rx = wh.rx * size.width * 1.6f
                                val ry = wh.ry * size.height * 1.3f
                                addOval(androidx.compose.ui.geometry.Rect(cx - rx, cy - ry, cx + rx, cy + ry))
                            }
                        }
                        val clip = Path().apply { op(poly, holes, androidx.compose.ui.graphics.PathOperation.Difference) }
                        clipPath(clip) { this@drawWithContent.drawContent() }
                    },
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width * art.heart.x, size.height * art.heart.y)
                val base = size.minDimension * (if (art === Egg) 0.22f else 0.11f)
                val glow = base * (if (still) 1f else beat) * (if (personality.heartBright) 1.35f else 1f)
                drawCircle(Brush.radialGradient(listOf(MintGlow.copy(alpha = if (personality.heartBright) 0.75f else 0.4f), Color.Transparent), c, glow), glow, c)
                if (false && stage == Stage.CRACKED) {
                    // 젤리링: 첫 금과 새어 나오는 민트빛
                    val crack = Path().apply {
                        moveTo(size.width * .33f, size.height * .40f)
                        lineTo(size.width * .40f, size.height * .36f)
                        lineTo(size.width * .45f, size.height * .42f)
                        lineTo(size.width * .52f, size.height * .35f)
                        lineTo(size.width * .58f, size.height * .41f)
                        lineTo(size.width * .65f, size.height * .37f)
                    }
                    drawPath(crack, MintGlow.copy(alpha = 0.55f), style = Stroke(width = size.minDimension * 0.03f, cap = StrokeCap.Round))
                    drawPath(crack, Color(0xFF6E5BB5), style = Stroke(width = size.minDimension * 0.008f, cap = StrokeCap.Round))
                }
                if (personality.body == TimeBucket.NIGHT) {
                    // 밤 몸 색: 몸 주위 별무늬
                    listOf(Offset(.22f, .30f), Offset(.78f, .26f), Offset(.84f, .55f), Offset(.16f, .62f)).forEachIndexed { i, p ->
                        val a = if (still) 0.8f else 0.35f + 0.65f * ((twinkle + i * 0.27f) % 1f)
                        drawStar(Offset(p.x * size.width, p.y * size.height), size.minDimension * 0.025f, Color(0xFFFFF4B8).copy(alpha = a))
                    }
                }
            }
        }
        // 낮형 드라이빙 고글 / 밤형 헤드램프 + 반딧불: 두 눈 자리를 기준으로 이마 위에 그린다(11/13 사용자 선택)
        val eyes = eyesOf(art.res)
        if (vibe != null && eyes != null) {
            Canvas(Modifier.fillMaxSize().graphicsLayer {
                if (!still) { scaleY = breath; transformOrigin = TransformOrigin(0.5f, 0.92f) }
            }) { drawGear(vibe, Offset(eyes.first.x * size.width, eyes.first.y * size.height), Offset(eyes.second.x * size.width, eyes.second.y * size.height), still, if (still) 0.15f else orbit) }
        }
        // 대표 장식: 심장 오른쪽 몸 위, 흰 동그라미 배지로 크게
        personality.decorationItem?.takeIf { stage.ordinal >= Stage.BABY.ordinal }?.let { deco ->
            val ds = h * 0.15f
            Box(
                Modifier.offset(x = w * (art.heart.x + 0.08f) - ds * 0.3f, y = h * (art.heart.y + 0.02f) - ds * 0.5f).size(ds)
                    .graphicsLayer { scaleX = if (mirrored) -1f else 1f }
                    .then(Modifier.drawWithContent {
                        drawCircle(Color.White.copy(alpha = 0.92f), size.minDimension / 2)
                        drawCircle(bodyColor, size.minDimension / 2, style = Stroke(size.minDimension * 0.07f))
                        drawContent()
                    }),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) { Text(deco.icon, fontSize = (ds.value * 0.55f).sp) }
        }
        // 휠캡: 보이는 바퀴마다 타원으로 눌러 붙인다
        val wheel = equipped[CarSlot.WHEEL]
        val sl = art.slots
        if (wheel != null && sl != null) {
            sl.wheels.forEach { wh ->
                Image(
                    painterResource(partRes(wheel)),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .offset(x = w * (wh.cx - wh.rx), y = h * (wh.cy - wh.ry))
                        .size(w * wh.rx * 2, h * wh.ry * 2),
                )
            }
        }
        // 머리 위 레벨 엠블럼: 몸 가운데 위, 머리 맨 윗선 바로 위
        emblemLevel?.let { lv ->
            val es = h * 0.2f
            Box(Modifier.offset(x = w * art.heart.x - es / 2, y = h * headTopOf(art.res) - es * 0.98f).graphicsLayer { scaleX = if (mirrored) -1f else 1f }) { LevelEmblem(lv, es) }
        }
    }
}

/**
 * 개성 장비(11/13 사용자 선택). 두 눈 사이 거리 d(너무 옆모습이면 그림 너비의 13%)를 기준으로 크기를 정한다.
 * - 낮형: 이마 위로 올려 쓴 레트로 드라이빙 고글(투명한 하늘빛 렌즈, 금빛 테, 갈색 가죽 끈). 눈은 가리지 않는다.
 * - 밤형: 이마의 헤드램프와 앞을 비추는 빛, 머리 위를 도는 반딧불 셋. [phase]가 반딧불 자리(0~1).
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGear(vibe: work.integro.carling.game.Vibe, l: Offset, r: Offset, still: Boolean, phase: Float) {
    val d = maxOf(kotlin.math.hypot(r.x - l.x, r.y - l.y), size.width * 0.13f)
    val mx = (l.x + r.x) / 2
    val my = (l.y + r.y) / 2
    val tiltDeg = Math.toDegrees(kotlin.math.atan2((r.y - l.y).toDouble(), (r.x - l.x).toDouble())).toFloat()
    if (vibe == work.integro.carling.game.Vibe.SUNNY) {
        val y = my - d * 0.62f
        val rr = d * 0.29f
        val gold = Color(0xFFC88A2E)
        rotate(tiltDeg, Offset(mx, y)) {
            val strap = Path().apply { moveTo(mx - d * 0.98f, y + d * 0.1f); quadraticBezierTo(mx, y - d * 0.12f, mx + d * 0.98f, y + d * 0.1f) }
            drawPath(strap, Color(0xFF8B5A3C), style = Stroke(d * 0.11f, cap = StrokeCap.Round))
            listOf(mx - d * 0.5f, mx + d * 0.5f).forEach { cx ->
                val c = Offset(cx, y)
                drawCircle(Brush.linearGradient(listOf(Color(0xFFE8FBFF).copy(alpha = 0.88f), Color(0xFF9ED8F0).copy(alpha = 0.8f)), Offset(cx - rr, y - rr), Offset(cx + rr, y + rr)), rr, c)
                drawCircle(gold, rr, c, style = Stroke(d * 0.06f))
                drawArc(Color.White.copy(alpha = 0.85f), 200f, 70f, false, Offset(cx - rr * 0.62f, y - rr * 0.62f), androidx.compose.ui.geometry.Size(rr * 1.24f, rr * 1.24f), style = Stroke(d * 0.035f, cap = StrokeCap.Round))
            }
            drawRoundRect(gold, Offset(mx - d * 0.22f, y - d * 0.03f), androidx.compose.ui.geometry.Size(d * 0.44f, d * 0.07f), androidx.compose.ui.geometry.CornerRadius(d * 0.03f))
        }
    } else {
        val y = my - d * 0.55f
        rotate(tiltDeg, Offset(mx, y)) {
            // 앞을 비추는 빛
            val beam = Path().apply { moveTo(mx - d * 0.12f, y); lineTo(mx - d * 2.3f, y + d * 1.5f); lineTo(mx + d * 0.8f, y + d * 1.75f); close() }
            drawPath(beam, Brush.verticalGradient(listOf(Color(0xFFFFF4B8).copy(alpha = 0.42f), Color(0x00FFF4B8)), startY = y, endY = y + d * 1.75f))
            val band = Path().apply { moveTo(mx - d * 0.95f, y + d * 0.11f); quadraticBezierTo(mx, y - d * 0.1f, mx + d * 0.95f, y + d * 0.11f) }
            drawPath(band, Color(0xFF2E3266), style = Stroke(d * 0.18f, cap = StrokeCap.Round))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF4B8).copy(alpha = 0.75f), Color.Transparent), Offset(mx, y), d * 0.45f), d * 0.45f, Offset(mx, y))
            drawRoundRect(Color(0xFF3A3F7A), Offset(mx - d * 0.28f, y - d * 0.22f), androidx.compose.ui.geometry.Size(d * 0.56f, d * 0.44f), androidx.compose.ui.geometry.CornerRadius(d * 0.14f))
            drawCircle(Color(0xFFFFF8D6), d * 0.16f, Offset(mx, y))
            drawCircle(Color(0xFFFFD86B), d * 0.16f, Offset(mx, y), style = Stroke(d * 0.035f))
        }
        // 반딧불 셋: 머리 위 타원을 돌며 깜빡인다
        listOf(0f, 0.37f, 0.71f).forEachIndexed { i, off ->
            val a = ((phase + off) % 1f) * 2f * Math.PI.toFloat()
            val c = Offset(mx + kotlin.math.cos(a) * d * (1.15f + 0.1f * i), y - d * 0.35f + kotlin.math.sin(a) * d * 0.45f)
            val blink = if (still) 0.8f else 0.55f + 0.45f * ((kotlin.math.sin(a * 3f + i) + 1f) / 2f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFE6FF4D).copy(alpha = 0.8f * blink), Color(0x00E6FF4D)), c, d * 0.3f), d * 0.3f, c)
            drawCircle(Color(0xFFFFFFC8).copy(alpha = blink), d * 0.095f, c)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(c: Offset, r: Float, color: Color) {
    val p = Path()
    for (i in 0 until 8) {
        val rr = if (i % 2 == 0) r else r * 0.35f
        val a = Math.toRadians(i * 45.0 - 90).toFloat()
        val x = c.x + rr * kotlin.math.cos(a)
        val y = c.y + rr * kotlin.math.sin(a)
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, color)
}

/** 단계 그림(서 있는 모습) 썸네일. */
@Composable
fun StageThumb(stage: Stage, size: Dp, modifier: Modifier = Modifier, dim: Boolean = false) {
    val art = artFor(stage, if (stage.ordinal >= Stage.BABY.ordinal) Pose.CAR else Pose.STAND)
    Image(
        painterResource(art.res),
        contentDescription = stage.label,
        // 추후 공개 단계는 실루엣만
        colorFilter = if (!stage.open) ColorFilter.tint(Color(0xFF9C97C9), androidx.compose.ui.graphics.BlendMode.SrcIn) else null,
        modifier = modifier.height(size).aspectRatio(art.aspect).graphicsLayer { alpha = if (dim) 0.35f else 1f },
    )
}

/** 화면에 보이는 이름. */
fun karingName(name: String?, stage: Stage): String = name ?: stage.label

internal val ThumbHeight = 120.dp

/** 자동차 꾸미기 그림. */
@DrawableRes
fun partRes(part: CarPart): Int = when (part) {
    CarPart.WHEEL_DAISY -> R.drawable.item_wheel_daisy
    CarPart.WHEEL_CLOVER -> R.drawable.item_wheel_clover
    CarPart.WHEEL_CANDY -> R.drawable.item_wheel_candy
    CarPart.WHEEL_PINWHEEL -> R.drawable.item_wheel_pinwheel
    CarPart.ANTENNA_SPROUT -> R.drawable.item_antenna_sprout
    CarPart.ANTENNA_HEART -> R.drawable.item_antenna_heart
    CarPart.ANTENNA_STAR -> R.drawable.item_antenna_star
    CarPart.ANTENNA_PLANET -> R.drawable.item_antenna_planet
    CarPart.REAR_SUITCASE -> R.drawable.item_rear_suitcase
    CarPart.REAR_BACKPACK -> R.drawable.item_rear_backpack
    CarPart.REAR_PICNIC -> R.drawable.item_rear_picnic
    CarPart.REAR_SURFBOARD -> R.drawable.item_rear_surfboard
}

/** 안테나·뒤쪽 소품: [anchor]에 아래 가운데를 맞춰 [heightFrac] 높이로 그린다. 안테나는 살랑 흔들린다. */
@Composable
private fun PartImage(part: CarPart, w: Dp, h: Dp, anchor: Offset, heightFrac: Float, still: Boolean, sway: Boolean = false) {
    val angle = if (sway && !still) {
        val t = rememberInfiniteTransition(label = "sway")
        t.animateFloat(-6f, 6f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "a").value
    } else 0f
    val ph = h * heightFrac
    Image(
        painterResource(partRes(part)),
        contentDescription = part.label,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .offset(x = w * anchor.x - ph / 2, y = h * anchor.y - ph)
            .size(ph)
            .graphicsLayer { rotationZ = angle; transformOrigin = TransformOrigin(0.5f, 1f) },
    )
}

/** 차체 색 변환: 밝기(L)에 따라 [CarColor.dark]~[CarColor.light] 사이 색으로. 투명도는 그대로. */
fun paintFilter(c: CarColor): ColorFilter {
    val light = Color(c.light)
    val dark = Color(c.dark)
    fun row(l: Float, d: Float): FloatArray {
        val k = l - d
        return floatArrayOf(0.299f * k, 0.587f * k, 0.114f * k, 0f, d * 255f)
    }
    return ColorFilter.colorMatrix(ColorMatrix(row(light.red, dark.red) + row(light.green, dark.green) + row(light.blue, dark.blue) + floatArrayOf(0f, 0f, 0f, 1f, 0f)))
}
