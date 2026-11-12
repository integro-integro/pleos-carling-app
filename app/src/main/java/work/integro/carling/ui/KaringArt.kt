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
import work.integro.carling.game.CarItem
import work.integro.carling.game.Personality
import work.integro.carling.game.Stage
import work.integro.carling.game.TimeBucket

/** 카링을 보여주는 모습: 서 있기, 차에 앉아 있기(주차), 달리기(D). */
enum class Pose { STAND, CAR, DRIVE }

/**
 * 그림 한 장과 그 위에 얹을 자리(그림 너비·높이 대비 비율).
 * @param decal 자동차 꾸미기 아이템을 붙일 차체 위치. 차가 없는 그림은 null.
 */
internal class ArtSpec(@param:DrawableRes val res: Int, val aspect: Float, val heart: Offset, val decal: Offset?)

private const val SQ = 1312f / 1199f
private const val WIDE = 1448f / 1086f
private const val WIDER = 1536f / 1024f

private val Egg = ArtSpec(R.drawable.karing_egg, SQ, Offset(.50f, .54f), null)

/** 단계와 모습에 맞는 그림. 알은 차가 없어 어느 모습이든 알이다. */
internal fun artFor(stage: Stage, pose: Pose): ArtSpec = when (stage) {
    Stage.EGG, Stage.CRACKED -> Egg
    Stage.BABY -> when (pose) {
        Pose.STAND -> ArtSpec(R.drawable.karing_baby, SQ, Offset(.507f, .635f), null)
        Pose.CAR -> ArtSpec(R.drawable.karing_baby_car, WIDE, Offset(.47f, .54f), Offset(.70f, .66f))
        Pose.DRIVE -> ArtSpec(R.drawable.karing_baby_drive, WIDE, Offset(.55f, .43f), Offset(.40f, .64f))
    }
    Stage.BREEZE -> when (pose) {
        Pose.STAND -> ArtSpec(R.drawable.karing_wing, SQ, Offset(.477f, .664f), null)
        Pose.CAR -> ArtSpec(R.drawable.karing_wing_car, WIDE, Offset(.48f, .50f), Offset(.74f, .62f))
        Pose.DRIVE -> ArtSpec(R.drawable.karing_wing_drive, WIDER, Offset(.57f, .47f), Offset(.38f, .60f))
    }
    Stage.MYCARLING -> when (pose) {
        Pose.STAND -> ArtSpec(R.drawable.karing_flame, SQ, Offset(.435f, .642f), null)
        Pose.CAR -> ArtSpec(R.drawable.karing_flame_car, WIDER, Offset(.44f, .53f), Offset(.70f, .62f))
        Pose.DRIVE -> ArtSpec(R.drawable.karing_flame_drive, 1774f / 887f, Offset(.58f, .49f), Offset(.44f, .66f))
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
    carItem: CarItem?,
    height: Dp,
    modifier: Modifier = Modifier,
    still: Boolean = false,
    onTap: (() -> Unit)? = null,
) {
    val art = artFor(stage, pose)
    val t = rememberInfiniteTransition(label = "karing")
    val breath by t.animateFloat(0.985f, 1.015f, infiniteRepeatable(tween(1700), RepeatMode.Reverse), label = "breath")
    val beat by t.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(if (personality.heartBright) 620 else 900), RepeatMode.Reverse), label = "beat")
    val twinkle by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "twinkle")

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
        val bodyColor = Color(personality.body.color)
        // 몸 색 빛무리
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width * art.heart.x, size.height * art.heart.y)
            val r = size.minDimension * 0.62f
            drawCircle(Brush.radialGradient(listOf(bodyColor.copy(alpha = 0.55f), bodyColor.copy(alpha = 0.18f), Color.Transparent), c, r), r, c)
            drawOval(Color(0x22202450), Offset(size.width * 0.22f, size.height * 0.90f), androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.05f))
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
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width * art.heart.x, size.height * art.heart.y)
                val base = size.minDimension * (if (art === Egg) 0.22f else 0.11f)
                val glow = base * (if (still) 1f else beat) * (if (personality.heartBright) 1.35f else 1f)
                drawCircle(Brush.radialGradient(listOf(MintGlow.copy(alpha = if (personality.heartBright) 0.75f else 0.4f), Color.Transparent), c, glow), glow, c)
                if (stage == Stage.CRACKED) {
                    // 깨어난 알: 첫 금과 새어 나오는 민트빛
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
        // 대표 장식: 심장 오른쪽 몸 위
        personality.decorationItem?.takeIf { stage.ordinal >= Stage.BABY.ordinal }?.let { deco ->
            Text(deco.icon, fontSize = (h.value * 0.11f).sp, modifier = Modifier.offset(x = w * (art.heart.x + 0.07f) - (h * 0.06f), y = h * (art.heart.y + 0.03f) - (h * 0.07f)))
        }
        // 자동차 꾸미기 아이템
        val decal = art.decal
        if (carItem != null && decal != null) {
            Text(carItem.icon, fontSize = (h.value * 0.12f).sp, modifier = Modifier.offset(x = w * decal.x - (h * 0.06f), y = h * decal.y - (h * 0.08f)))
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
        modifier = modifier.height(size).aspectRatio(art.aspect).graphicsLayer { alpha = if (dim) 0.35f else 1f },
    )
}

/** 화면에 보이는 이름. */
fun karingName(name: String?, stage: Stage): String = name ?: stage.label

internal val ThumbHeight = 120.dp
