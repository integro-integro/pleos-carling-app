package work.integro.carling.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import work.integro.carling.R
import work.integro.carling.data.CarlingData
import work.integro.carling.game.CarColor
import work.integro.carling.game.CarSlot
import work.integro.carling.game.Stage
import kotlin.math.roundToInt

/** 턴테이블 그림 한 장의 자리(그림 너비·높이 대비). [rearFront]면 뒤쪽 소품을 카링 앞에 그린다(뒤에서 볼 때). */
internal class TurnSpec(val wheels: List<Wheel>, val antenna: Offset, val rear: Offset, val rearFront: Boolean)

/**
 * 0~180도 다섯 장의 자리. 225~315도는 135~45도를 좌우로 뒤집어 쓴다.
 * 바퀴는 휠 가운데(아기카링 민트 원판, 산들·마이카링 빛나는 테)를 색으로 찾아 그 타원에 맞췄다(11/12 재측정).
 * 앞·뒤(0·180도)는 바퀴 면이 보이지 않아 휠캡을 그리지 않는다.
 */
private val TurnSpecs = mapOf(
    "baby_0" to TurnSpec(listOf(), Offset(0.645f, 0.56f), Offset(0.5f, 0.56f), false),
    "baby_1" to TurnSpec(listOf(Wheel(0.359f, 0.820f, 0.025f, 0.055f), Wheel(0.217f, 0.702f, 0.017f, 0.052f)), Offset(0.286f, 0.6f), Offset(0.374f, 0.58f), false),
    "baby_2" to TurnSpec(listOf(Wheel(0.286f, 0.821f, 0.043f, 0.056f), Wheel(0.618f, 0.822f, 0.043f, 0.055f)), Offset(0.209f, 0.63f), Offset(0.287f, 0.6f), false),
    "baby_3" to TurnSpec(listOf(Wheel(0.653f, 0.786f, 0.021f, 0.069f), Wheel(0.777f, 0.628f, 0.013f, 0.055f)), Offset(0.271f, 0.64f), Offset(0.367f, 0.66f), true),
    "baby_4" to TurnSpec(listOf(), Offset(0.332f, 0.63f), Offset(0.5f, 0.68f), true),
    "wing_0" to TurnSpec(listOf(), Offset(0.627f, 0.56f), Offset(0.5f, 0.56f), false),
    "wing_1" to TurnSpec(listOf(Wheel(0.356f, 0.806f, 0.023f, 0.056f), Wheel(0.208f, 0.710f, 0.016f, 0.047f)), Offset(0.279f, 0.6f), Offset(0.37f, 0.58f), false),
    "wing_2" to TurnSpec(listOf(Wheel(0.285f, 0.817f, 0.039f, 0.053f), Wheel(0.653f, 0.820f, 0.038f, 0.051f)), Offset(0.204f, 0.63f), Offset(0.283f, 0.6f), false),
    "wing_3" to TurnSpec(listOf(Wheel(0.633f, 0.801f, 0.022f, 0.070f), Wheel(0.760f, 0.672f, 0.012f, 0.043f)), Offset(0.285f, 0.64f), Offset(0.375f, 0.66f), true),
    "wing_4" to TurnSpec(listOf(), Offset(0.344f, 0.63f), Offset(0.5f, 0.68f), true),
    "flame_0" to TurnSpec(listOf(), Offset(0.643f, 0.56f), Offset(0.5f, 0.56f), false),
    "flame_1" to TurnSpec(listOf(Wheel(0.421f, 0.814f, 0.027f, 0.062f), Wheel(0.179f, 0.740f, 0.023f, 0.056f)), Offset(0.246f, 0.6f), Offset(0.351f, 0.58f), false),
    "flame_2" to TurnSpec(listOf(Wheel(0.197f, 0.809f, 0.046f, 0.064f), Wheel(0.592f, 0.831f, 0.042f, 0.056f)), Offset(0.154f, 0.63f), Offset(0.247f, 0.6f), false),
    "flame_3" to TurnSpec(listOf(Wheel(0.580f, 0.802f, 0.034f, 0.085f), Wheel(0.801f, 0.636f, 0.021f, 0.070f)), Offset(0.248f, 0.64f), Offset(0.354f, 0.66f), true),
    "flame_4" to TurnSpec(listOf(), Offset(0.327f, 0.63f), Offset(0.5f, 0.68f), true),
)

private fun stageKey(stage: Stage): String? = when (stage) {
    Stage.CRACKED -> "baby"
    Stage.BABY -> "wing"
    Stage.BREEZE, Stage.MYCARLING, Stage.SOUL -> "flame"
    else -> null
}

@DrawableRes
private fun turnRes(key: String, i: Int, mask: Boolean): Int = when ("$key$i") {
    "baby0" -> if (mask) R.drawable.turnmask_baby_0 else R.drawable.turn_baby_0
    "baby1" -> if (mask) R.drawable.turnmask_baby_1 else R.drawable.turn_baby_1
    "baby2" -> if (mask) R.drawable.turnmask_baby_2 else R.drawable.turn_baby_2
    "baby3" -> if (mask) R.drawable.turnmask_baby_3 else R.drawable.turn_baby_3
    "baby4" -> if (mask) R.drawable.turnmask_baby_4 else R.drawable.turn_baby_4
    "wing0" -> if (mask) R.drawable.turnmask_wing_0 else R.drawable.turn_wing_0
    "wing1" -> if (mask) R.drawable.turnmask_wing_1 else R.drawable.turn_wing_1
    "wing2" -> if (mask) R.drawable.turnmask_wing_2 else R.drawable.turn_wing_2
    "wing3" -> if (mask) R.drawable.turnmask_wing_3 else R.drawable.turn_wing_3
    "wing4" -> if (mask) R.drawable.turnmask_wing_4 else R.drawable.turn_wing_4
    "flame0" -> if (mask) R.drawable.turnmask_flame_0 else R.drawable.turn_flame_0
    "flame1" -> if (mask) R.drawable.turnmask_flame_1 else R.drawable.turn_flame_1
    "flame2" -> if (mask) R.drawable.turnmask_flame_2 else R.drawable.turn_flame_2
    "flame3" -> if (mask) R.drawable.turnmask_flame_3 else R.drawable.turn_flame_3
    else -> if (mask) R.drawable.turnmask_flame_4 else R.drawable.turn_flame_4
}

/** 각도별 머리 위 엠블럼 자리(머리 맨 윗선 가운데, 알파 채널에서 잰 값). 225~315도는 좌우 반전으로 따라간다. */
private val HeadSpots = mapOf(
    "baby" to listOf(0.500f, 0.469f, 0.465f, 0.525f, 0.493f),
    "wing" to listOf(0.564f, 0.431f, 0.534f, 0.475f, 0.462f),
    "flame" to listOf(0.535f, 0.397f, 0.410f, 0.448f, 0.536f),
)
private const val HEAD_TOP = 0.072f

/** 각도별 두 눈 가운데(그림 너비·높이 대비, 11/13 측정). 180도(뒷모습)는 눈이 없다. */
private val TurnEyes: Map<String, List<Pair<Offset, Offset>?>> = mapOf(
    "baby" to listOf(Offset(.409f, .278f) to Offset(.590f, .276f), Offset(.432f, .291f) to Offset(.595f, .265f), Offset(.379f, .276f) to Offset(.559f, .273f), Offset(.517f, .278f) to Offset(.635f, .209f), null),
    "wing" to listOf(Offset(.428f, .341f) to Offset(.573f, .340f), Offset(.456f, .335f) to Offset(.591f, .327f), Offset(.403f, .343f) to Offset(.547f, .344f), Offset(.475f, .338f) to Offset(.579f, .291f), null),
    "flame" to listOf(Offset(.406f, .339f) to Offset(.553f, .339f), Offset(.489f, .338f) to Offset(.614f, .331f), Offset(.445f, .356f) to Offset(.581f, .352f), Offset(.516f, .313f) to Offset(.616f, .264f), null),
)

/** 8방향(45도마다) 중 [step]에 해당하는 그림 번호와 좌우 반전 여부. */
private fun frameOf(step: Int): Pair<Int, Boolean> {
    val s = ((step % 8) + 8) % 8
    return if (s <= 4) s to false else (8 - s) to true
}

/**
 * 카링과 차를 8방향으로 돌려 본다(사용자 요청 2026-11-12). 좌우로 끌면 45도씩 돌고, 손을 떼면 천천히 저절로 돈다.
 * 단 장착한 안테나·뒤쪽 소품·휠캡과 차체 색이 각도마다 제자리에 보인다.
 */
@Composable
fun KaringTurntable(
    data: CarlingData, height: Dp, modifier: Modifier = Modifier, autoSpin: Boolean = true, onStep: (Int) -> Unit = {},
    /** 정해 둔 방향(홈 화면). 주면 끌어도 돌지 않는다. */
    fixedStep: Int? = null,
    startStep: Int = 1,
    /** 차 앞 레벨 엠블럼. null이면 안 단다. */
    emblemLevel: Int? = null,
) {
    val key = stageKey(data.shownStage) ?: return
    var step by remember { mutableStateOf(startStep) }
    if (fixedStep != null) step = fixedStep
    var dragging by remember { mutableStateOf(false) }
    var acc by remember { mutableStateOf(0f) }
    LaunchedEffect(autoSpin, dragging) {
        if (!autoSpin || dragging || fixedStep != null) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1_300)
            step = (step + 1) % 8
            onStep(step)
        }
    }
    val (frame, mirror) = frameOf(step)
    val spec = TurnSpecs.getValue("${key}_$frame")
    val base = ImageBitmapCache.get(turnRes(key, frame, false))
    val mask = ImageBitmapCache.get(turnRes(key, frame, true))
    val color = data.carColor
    BoxWithConstraints(
        modifier
            .height(height)
            .aspectRatio(4f / 3f)
            .pointerInput(fixedStep) {
                if (fixedStep != null) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true; acc = 0f },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { _, dx ->
                    acc += dx
                    val unit = size.width / 8f
                    while (acc > unit) { acc -= unit; step = (step + 7) % 8; onStep(step) }
                    while (acc < -unit) { acc += unit; step = (step + 1) % 8; onStep(step) }
                }
            },
    ) {
        val w = maxWidth
        val h = maxHeight
        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = if (mirror) -1f else 1f }) {
            // 바닥 그림자
            Canvas(Modifier.fillMaxSize()) {
                drawOval(Color(0x33202450), Offset(size.width * 0.18f, size.height * 0.9f), androidx.compose.ui.geometry.Size(size.width * 0.64f, size.height * 0.08f))
            }
            if (!spec.rearFront) data.equipped[CarSlot.REAR]?.let { TurnPart(partRes(it), w, h, spec.rear, 0.24f) }
            data.equipped[CarSlot.ANTENNA]?.let { TurnPart(partRes(it), w, h, spec.antenna, 0.30f) }
            Image(base, contentDescription = "${data.shownStage.label} 360도", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            // 차체 색: 몸통 마스크 안에만 색 변환한 그림을 덧그린다
            if (color != null && color != CarColor.PEARL) {
                val filter = remember(color) { paintFilter(color) }
                Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                    val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    drawImage(mask, dstOffset = IntOffset.Zero, dstSize = dst)
                    drawImage(base, dstOffset = IntOffset.Zero, dstSize = dst, colorFilter = filter, blendMode = BlendMode.SrcIn)
                }
            }
            // 개성 장비: 낮형 드라이빙 고글 / 밤형 헤드램프 + 반딧불
            val vibe = data.personality.vibe
            if (vibe != null) {
                val orbit by androidx.compose.animation.core.rememberInfiniteTransition(label = "gear").animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(6_000, easing = androidx.compose.animation.core.LinearEasing)), label = "orbit")
                TurnEyes[key]?.getOrNull(frame)?.let { (l, r) ->
                    Canvas(Modifier.fillMaxSize()) { drawGear(vibe, Offset(l.x * size.width, l.y * size.height), Offset(r.x * size.width, r.y * size.height), still = false, phase = orbit) }
                }
            }
            // 대표 장식 배지: 눈 아래 가슴 옆
            data.personality.decorationItem?.let { deco ->
                TurnEyes[key]?.getOrNull(frame)?.let { (l, r) ->
                    val ds = h * 0.13f
                    Box(
                        Modifier.offset(x = w * (maxOf(l.x, r.x) + 0.05f) - ds / 2, y = h * ((l.y + r.y) / 2 + 0.17f) - ds / 2).size(ds)
                            .graphicsLayer { scaleX = if (mirror) -1f else 1f }
                            .background(Color.White.copy(alpha = 0.92f), androidx.compose.foundation.shape.CircleShape)
                            .border(ds * 0.07f, Color(vibe?.color ?: 0xFFC7B8FF), androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) { androidx.compose.material3.Text(deco.icon, fontSize = (ds.value * 0.55f).sp) }
                }
            }
            if (spec.rearFront) data.equipped[CarSlot.REAR]?.let { TurnPart(partRes(it), w, h, spec.rear, 0.22f) }
            data.equipped[CarSlot.WHEEL]?.let { wheel ->
                spec.wheels.forEach { wh ->
                    Image(
                        painterResource(partRes(wheel)),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.offset(x = w * (wh.cx - wh.rx), y = h * (wh.cy - wh.ry)).size(w * wh.rx * 2, h * wh.ry * 2),
                    )
                }
            }
            // 머리 위 레벨 엠블럼. 뒤집힌 그림 안에서도 숫자가 바로 읽히게 다시 뒤집는다
            emblemLevel?.let { lv ->
                val es = h * 0.17f
                val cx = HeadSpots.getValue(key)[frame]
                Box(Modifier.offset(x = w * cx - es / 2, y = h * HEAD_TOP - es * 0.95f).graphicsLayer { scaleX = if (mirror) -1f else 1f }) { LevelEmblem(lv, es) }
            }
        }
    }
}

@Composable
private fun TurnPart(@DrawableRes res: Int, w: Dp, h: Dp, anchor: Offset, heightFrac: Float) {
    val ph = h * heightFrac
    Image(
        painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.offset(x = w * anchor.x - ph / 2, y = h * anchor.y - ph).size(ph).graphicsLayer { transformOrigin = TransformOrigin(0.5f, 1f) },
    )
}

/** 같은 그림을 매번 다시 읽지 않도록. */
private object ImageBitmapCache {
    @Composable
    fun get(@DrawableRes res: Int) = androidx.compose.ui.graphics.ImageBitmap.imageResource(res)
}

private val AngleNames = listOf("앞", "앞·오른쪽", "오른쪽", "뒤·오른쪽", "뒤", "뒤·왼쪽", "왼쪽", "앞·왼쪽")

/** 360도 보기 창: 큰 턴테이블, 방향 표시, 장착한 것 목록. */
@Composable
fun TurntableDialog(data: CarlingData, onClose: () -> Unit, onHome: ((Int?) -> Unit)? = null) {
    var step by remember { mutableStateOf(data.homeAngle ?: 1) }
    var auto by remember { mutableStateOf(data.homeAngle == null) }
    FullScrim {
        Panel(Modifier.width(1080.dp), padding = 28.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${karingName(data.name, data.shownStage)}의 차 360°", fontSize = 30.sp, color = Ink, fontWeight = FontWeight.Bold)
                Text("좌우로 끌어서 돌려 보세요 · 지금 ${AngleNames[step]}에서 보는 중", fontSize = 18.sp, color = Muted)
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().height(520.dp).background(Brush.radialGradient(listOf(Color.White, Color(0xFFE3DEFF), Color(0xFFC9C3F5))), RoundedCornerShape(30.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (stageKey(data.shownStage) == null) {
                        Text("아기링이 되면 차를 360도로 돌려 볼 수 있어요", fontSize = 22.sp, color = Muted)
                    } else {
                        KaringTurntable(data, 420.dp, autoSpin = auto, onStep = { step = it }, startStep = data.homeAngle ?: 1, modifier = Modifier.padding(top = 70.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    // 8방향 점
                    (0 until 8).forEach { i -> Box(Modifier.size(if (i == step) 14.dp else 9.dp).background(if (i == step) Lilac else Faint, CircleShape)) }
                    Spacer(Modifier.width(16.dp))
                    val parts = CarSlot.entries.mapNotNull { data.equipped[it]?.label } + listOfNotNull(data.carColor?.takeIf { it != CarColor.PEARL }?.label?.let { "$it 차체" })
                    Text(if (parts.isEmpty()) "아직 단 꾸미기가 없어요" else parts.joinToString(" · "), fontSize = 18.sp, color = Ink)
                    data.style?.let { Text("  ${it.icon} ${it.label}", fontSize = 18.sp, color = Mint, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(if (auto) "⏸ 멈추기" else "▶ 저절로 돌기", on = false) { auto = !auto }
                    if (onHome != null && stageKey(data.shownStage) != null) {
                        Pill(if (data.homeAngle == step) "🏠 홈에 이 방향으로 있어요" else "🏠 이 방향으로 홈에 두기", on = data.homeAngle == step, onColor = Mint) { auto = false; onHome(step) }
                        if (data.homeAngle != null) Pill("기본 모습으로", on = false) { onHome(null) }
                    }
                    Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Lilac), modifier = Modifier.height(60.dp)) { Text("닫기", fontSize = 22.sp) }
                }
            }
        }
    }
}
