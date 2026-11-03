package work.integro.carling.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import work.integro.carling.data.Personality
import work.integro.carling.game.SpecialMove
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * 정차 화면에서 카링이 스스로 돌아다니고, 누르면 반응한다.
 * 주행 중 화면에서는 쓰지 않는다 — 안전 원칙: 주행 중 애니메이션 최소화.
 */

/** 성격별 움직임 성향: 쉬는 시간, 걷는 속도, 행동 가중치. */
internal data class Temper(
    val idleMs: LongRange,
    val speedDp: Float,
    val weights: Map<Move, Int>,
)

internal enum class Move { WALK, RUN, JUMP, ZIGZAG, SPIN, LOOK, WIGGLE, NAP }

internal fun temperOf(p: Personality?): Temper = when (p) {
    Personality.DODO -> Temper(
        idleMs = 600L..2_000L, speedDp = 190f,
        weights = mapOf(Move.WALK to 3, Move.RUN to 4, Move.JUMP to 4, Move.ZIGZAG to 3, Move.SPIN to 2, Move.LOOK to 1, Move.WIGGLE to 2, Move.NAP to 0),
    )
    Personality.NARUN -> Temper(
        idleMs = 2_500L..6_000L, speedDp = 90f,
        weights = mapOf(Move.WALK to 5, Move.RUN to 0, Move.JUMP to 1, Move.ZIGZAG to 1, Move.SPIN to 0, Move.LOOK to 2, Move.WIGGLE to 2, Move.NAP to 4),
    )
    else -> Temper( // 루미: 차분하게 걷고 자주 둘러본다
        idleMs = 1_500L..4_000L, speedDp = 120f,
        weights = mapOf(Move.WALK to 5, Move.RUN to 1, Move.JUMP to 1, Move.ZIGZAG to 2, Move.SPIN to 1, Move.LOOK to 4, Move.WIGGLE to 1, Move.NAP to 1),
    )
}

private fun <T> pick(weights: Map<T, Int>): T {
    val total = weights.values.sum()
    var r = Random.nextInt(total)
    for ((k, w) in weights) {
        if (r < w) return k
        r -= w
    }
    return weights.keys.first()
}

/** 지금 바로 보여줄 움직임. [move]가 null이면 성장할 때의 '짠'이다. [id]가 바뀔 때마다 한 번 재생한다. */
data class WanderCue(val id: Int, val move: SpecialMove?)

/** 돌아다니다가 배운 동작을 할 확률. */
private const val SPECIAL_CHANCE = 0.22f

/** 카링 몸의 현재 자세. 모든 값은 px/도 단위이고 프레임마다 바뀐다. */
class CarlingMotion internal constructor(private val scope: CoroutineScope) {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    var lift by mutableFloatStateOf(0f)
    var rotation by mutableFloatStateOf(0f)
    var squash by mutableFloatStateOf(1f)
    var facingLeft by mutableStateOf(false)
    var napping by mutableStateOf(false)

    /** 머리 위에 잠깐 떠오르는 감정 표시(♥, Zzz 등)와 말풍선. */
    var emote by mutableStateOf<String?>(null)
    var bubble by mutableStateOf<String?>(null)
    var emoteKey by mutableStateOf(0)

    internal var rangeX = 0f
    internal var rangeY = 0f
    internal var px = 1f // 1dp의 px
    internal var temper = temperOf(null)
    internal var specials: List<SpecialMove> = emptyList()
    internal var wanderEnabled = false
    private var job: Job? = null
    private var tapTimes = ArrayDeque<Long>()

    fun startWandering() {
        job?.cancel()
        job = scope.launch {
            while (true) {
                delay(Random.nextLong(temper.idleMs.first, temper.idleMs.last))
                if (specials.isNotEmpty() && Random.nextFloat() < SPECIAL_CHANCE) {
                    special(specials.random())
                    continue
                }
                when (pick(temper.weights)) {
                    Move.WALK -> walkTo(randomPoint(), temper.speedDp, hopDp = 12f)
                    Move.RUN -> walkTo(randomPoint(far = true), temper.speedDp * 2.4f, hopDp = 8f, hopLenDp = 45f)
                    Move.JUMP -> jumpTo(randomPoint(near = true))
                    Move.ZIGZAG -> repeat(Random.nextInt(3, 6)) { walkTo(randomPoint(near = true), temper.speedDp * 1.3f, hopDp = 10f) }
                    Move.SPIN -> spin(turns = 1)
                    Move.LOOK -> lookAround()
                    Move.WIGGLE -> wiggle()
                    Move.NAP -> nap()
                }
            }
        }
    }

    /** 가운데로 돌아와 멈춘다 (리포트·추억 카드를 볼 때). */
    fun rest() {
        job?.cancel()
        job = scope.launch {
            emote = null
            bubble = null
            walkTo(0f to 0f, 300f, hopDp = 6f)
            settle()
        }
    }

    /** 터치 반응. 1.2초 안에 세 번 누르면 어지러워한다. 일반 반응일 때만 [onPetted] (친밀도 보상). */
    fun onTap(lines: List<String>, onPetted: () -> Unit) {
        val now = System.currentTimeMillis()
        tapTimes.addLast(now)
        while (tapTimes.isNotEmpty() && now - tapTimes.first() > 1_200) tapTimes.removeFirst()
        val dizzy = tapTimes.size >= 3
        if (dizzy) tapTimes.clear()

        job?.cancel()
        job = scope.launch {
            settle()
            if (dizzy) {
                show("💫", "어지러워요~ 살살 해줘요!")
                spin(turns = 2, liftDp = 30f)
                wiggle(times = 4, angle = 14f)
            } else {
                onPetted()
                show("💕", lines.random())
                squashTo(0.82f, 90)
                arc(heightDp = 70f, millis = 520, fromX = x, fromY = y, toX = x, toY = y)
                squashTo(1.08f, 80)
                squashTo(1f, 120)
            }
            delay(1_500)
            startWandering()
        }
    }

    /** 진화 직후의 새 동작이나 성장의 '짠'을 바로 보여준다. */
    fun play(cue: WanderCue) {
        job?.cancel()
        job = scope.launch {
            settle()
            val move = cue.move
            if (move == null) zzan() else {
                show("✨", "${move.label}!")
                special(move)
            }
            delay(800)
            if (wanderEnabled) startWandering()
        }
    }

    fun stop() {
        job?.cancel()
    }

    // ---- 행동들 ----

    private suspend fun zzan() {
        show("✨", "짠!")
        squashTo(0.72f, 110)
        arc(heightDp = 80f, millis = 420, fromX = x, fromY = y, toX = x, toY = y)
        squashTo(1.18f, 90)
        squashTo(1f, 170)
    }

    private suspend fun special(move: SpecialMove) {
        when (move) {
            SpecialMove.FLIP -> flip()
            SpecialMove.GLIDE -> glide()
            SpecialMove.ZIP -> repeat(3) { walkTo(randomPoint(far = true), 1_200f, hopDp = 0f) }
            SpecialMove.LEAF_DANCE -> leafDance()
            SpecialMove.WAVE -> wave()
            SpecialMove.DRIFT -> drift()
            SpecialMove.AURA -> aura()
        }
        settle()
    }

    /** 움츠렸다가 뛰어올라 한 바퀴 돈다. */
    private suspend fun flip() {
        squashTo(0.75f, 130)
        animate(0f, 1f, animationSpec = tween(750, easing = LinearEasing)) { t, _ ->
            lift = sin(t * PI.toFloat()) * 170f * px
            rotation = 360f * t * if (facingLeft) -1f else 1f
            squash = 1f
        }
        rotation = 0f
        lift = 0f
        squashTo(1.15f, 80)
    }

    /** 높이 떠올라 날개를 편 듯 기울며 천천히 미끄러진다. */
    private suspend fun glide() {
        val (tx, ty) = randomPoint(far = true)
        val sx = x
        val sy = y
        if (abs(tx - sx) > 4f) facingLeft = tx < sx
        animate(0f, 1f, animationSpec = tween(2_200, easing = FastOutSlowInEasing)) { t, _ ->
            x = sx + (tx - sx) * t
            y = sy + (ty - sy) * t
            lift = sin(t * PI.toFloat()) * 190f * px
            rotation = sin(t * 4f * PI.toFloat()) * 9f
        }
    }

    /** 제자리에서 좌우로 살랑이며 통통 뛴다. */
    private suspend fun leafDance() {
        animate(0f, 1f, animationSpec = tween(2_000, easing = LinearEasing)) { t, _ ->
            rotation = sin(t * 6f * PI.toFloat()) * 16f
            lift = abs(sin(t * 6f * PI.toFloat())) * 34f * px
            if (t > 0.5f) facingLeft = sin(t * 3f * PI.toFloat()) > 0f
        }
    }

    /** 물결처럼 위아래로 넘실대며 지나간다. */
    private suspend fun wave() {
        val (tx, ty) = randomPoint(far = true)
        val sx = x
        val sy = y
        if (abs(tx - sx) > 4f) facingLeft = tx < sx
        animate(0f, 1f, animationSpec = tween(2_400, easing = LinearEasing)) { t, _ ->
            x = sx + (tx - sx) * t
            y = sy + (ty - sy) * t
            val w = sin(t * 6f * PI.toFloat())
            lift = (w + 1f) * 55f * px
            rotation = cos(t * 6f * PI.toFloat()) * 14f * if (facingLeft) 1f else -1f
        }
    }

    /** 빠르게 달리다 몸을 기울여 미끄러지며 멈춘다. */
    private suspend fun drift() {
        repeat(2) {
            val target = randomPoint(far = true)
            if (abs(target.first - x) > 4f) facingLeft = target.first < x
            val tilt = if (facingLeft) 16f else -16f
            animate(rotation, tilt, animationSpec = tween(120)) { v, _ -> rotation = v }
            walkTo(target, 800f, hopDp = 0f)
            animate(rotation, -tilt * 1.4f, animationSpec = tween(160)) { v, _ -> rotation = v }
            animate(rotation, 0f, animationSpec = tween(200)) { v, _ -> rotation = v }
        }
    }

    /** 떠올라 빠르게 회전하며 몸을 키웠다가 내려앉는다. */
    private suspend fun aura() {
        show("✨", null)
        animate(0f, 1f, animationSpec = tween(1_600, easing = FastOutSlowInEasing)) { t, _ ->
            lift = sin(t * PI.toFloat()) * 130f * px
            rotation = 720f * t
            squash = 1f + sin(t * PI.toFloat()) * 0.12f
        }
        rotation = 0f
        lift = 0f
        squash = 1f
    }

    private fun randomPoint(near: Boolean = false, far: Boolean = false): Pair<Float, Float> {
        repeat(8) {
            val tx = (Random.nextFloat() * 2 - 1) * rangeX
            val ty = (Random.nextFloat() * 2 - 1) * rangeY
            val d = hypot(tx - x, ty - y)
            val diag = hypot(rangeX, rangeY).coerceAtLeast(1f)
            val ok = when {
                near -> d in diag * 0.1f..diag * 0.45f
                far -> d > diag * 0.6f
                else -> d > diag * 0.2f
            }
            if (ok) return tx to ty
        }
        return (Random.nextFloat() * 2 - 1) * rangeX to (Random.nextFloat() * 2 - 1) * rangeY
    }

    /** 휘어진 경로(2차 베지어)를 따라 통통 뛰며 걷는다. */
    private suspend fun walkTo(target: Pair<Float, Float>, speedDp: Float, hopDp: Float, hopLenDp: Float = 60f) {
        val (tx, ty) = target
        val sx = x
        val sy = y
        val dist = hypot(tx - sx, ty - sy)
        if (dist < 1f) return
        if (abs(tx - sx) > 4f) facingLeft = tx < sx
        // 진행 방향에 수직으로 휘는 제어점
        val bend = (Random.nextFloat() * 2 - 1) * dist * 0.45f
        val cx = (sx + tx) / 2 - (ty - sy) / dist * bend
        val cy = (sy + ty) / 2 + (tx - sx) / dist * bend
        val hops = (dist / (hopLenDp * px)).coerceAtLeast(1f)
        val millis = (dist / (speedDp * px) * 1000).toInt().coerceIn(350, 6_000)
        animate(0f, 1f, animationSpec = tween(millis, easing = FastOutSlowInEasing)) { t, _ ->
            val u = 1 - t
            x = u * u * sx + 2 * u * t * cx + t * t * tx
            y = u * u * sy + 2 * u * t * cy + t * t * ty
            lift = abs(sin(t * hops * PI.toFloat())) * hopDp * px
        }
        lift = 0f
    }

    /** 크게 한 번 폴짝. */
    private suspend fun jumpTo(target: Pair<Float, Float>) {
        if (abs(target.first - x) > 4f) facingLeft = target.first < x
        squashTo(0.8f, 140)
        arc(heightDp = 110f, millis = 650, fromX = x, fromY = y, toX = target.first, toY = target.second)
        squashTo(1.12f, 80)
        squashTo(1f, 160)
    }

    private suspend fun arc(heightDp: Float, millis: Int, fromX: Float, fromY: Float, toX: Float, toY: Float) {
        animate(0f, 1f, animationSpec = tween(millis, easing = LinearEasing)) { t, _ ->
            x = fromX + (toX - fromX) * t
            y = fromY + (toY - fromY) * t
            lift = sin(t * PI.toFloat()) * heightDp * px
        }
        lift = 0f
    }

    private suspend fun spin(turns: Int, liftDp: Float = 20f) {
        animate(0f, 1f, animationSpec = tween(700 * turns, easing = FastOutSlowInEasing)) { t, _ ->
            rotation = 360f * turns * t
            lift = sin(t * PI.toFloat()) * liftDp * px
        }
        rotation = 0f
        lift = 0f
    }

    private suspend fun lookAround() {
        repeat(Random.nextInt(2, 5)) {
            facingLeft = !facingLeft
            delay(Random.nextLong(350, 900))
        }
    }

    private suspend fun wiggle(times: Int = 3, angle: Float = 9f) {
        repeat(times) {
            animate(rotation, angle, animationSpec = tween(110)) { v, _ -> rotation = v }
            animate(rotation, -angle, animationSpec = tween(110)) { v, _ -> rotation = v }
        }
        animate(rotation, 0f, animationSpec = tween(110)) { v, _ -> rotation = v }
    }

    private suspend fun nap() {
        show("💤", null)
        napping = true
        try {
            squashTo(0.9f, 600)
            delay(Random.nextLong(3_000, 6_000))
            squashTo(1f, 400)
        } finally {
            napping = false
        }
        emote = null
    }

    private suspend fun squashTo(target: Float, millis: Int) {
        animate(squash, target, animationSpec = tween(millis)) { v, _ -> squash = v }
    }

    private suspend fun settle() {
        lift = 0f
        rotation = 0f
        if (squash != 1f) squashTo(1f, 100)
    }

    private fun show(emoteText: String?, line: String?) {
        emote = emoteText
        emoteKey++
        bubble = line
        if (line != null) {
            val key = emoteKey
            scope.launch {
                delay(2_600)
                if (emoteKey == key) bubble = null
            }
        }
    }
}

/** 돌아다니는 영역에서 배경의 땅이 시작되는 높이(위에서부터 비율). */
private const val GROUND_TOP = 0.72f

/** 그림 안에서 카링의 발이 있는 높이(위에서부터 비율). */
private const val FEET_AT = 0.85f

/**
 * 땅 위를 돌아다니는 카링.
 * [enabled]가 false면 가운데로 돌아와 멈춘다.
 */
@Composable
fun Wandering(
    size: Dp,
    enabled: Boolean,
    personality: Personality?,
    tapLines: List<String>,
    onPetted: () -> Unit,
    modifier: Modifier = Modifier,
    specials: List<SpecialMove> = emptyList(),
    cue: WanderCue? = null,
    content: @Composable (napping: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val motion = remember { CarlingMotion(scope) }
    val density = LocalDensity.current

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        motion.px = with(density) { 1.dp.toPx() }
        // 영역 밖(옆 패널·아래 이름)으로 나가지 않게: 그림 크기만큼 뺀다
        motion.rangeX = with(density) { ((maxWidth - size) / 2).coerceAtLeast(0.dp).toPx() }
        // 발이 배경의 땅 위에 있도록 세로로는 땅 높이 안에서만 돌아다닌다
        val lowestTop = (maxHeight - size).coerceAtLeast(0.dp)
        val highestTop = (maxHeight * GROUND_TOP - size * FEET_AT).coerceIn(0.dp, lowestTop)
        motion.rangeY = with(density) { ((lowestTop - highestTop) / 2).toPx() }
        val groundCenter = (highestTop + lowestTop) / 2 + size / 2 - maxHeight / 2
        motion.temper = temperOf(personality)
        motion.specials = specials
        motion.wanderEnabled = enabled

        LaunchedEffect(enabled, personality) {
            if (enabled) motion.startWandering() else motion.rest()
        }
        // 위의 효과 다음에 두어, 같은 순간에 시작하면 이 움직임이 이긴다
        LaunchedEffect(cue?.id) {
            if (cue != null && enabled) motion.play(cue)
        }
        DisposableEffect(Unit) { onDispose { motion.stop() } }

        Box(
            Modifier
                .offset { IntOffset(motion.x.roundToInt(), (groundCenter.toPx() + motion.y - motion.lift).roundToInt()) }
                .size(size),
            contentAlignment = Alignment.Center,
        ) {
            // 그림만 좌우 반전·회전·찌그러짐 (말풍선 글자는 뒤집히지 않게)
            Box(
                Modifier
                    .graphicsLayer {
                        scaleX = (if (motion.facingLeft) -1f else 1f) * (2f - motion.squash)
                        scaleY = motion.squash
                        rotationZ = motion.rotation
                        transformOrigin = TransformOrigin(0.5f, 0.85f)
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = enabled,
                    ) { motion.onTap(tapLines, onPetted) },
            ) { content(motion.napping) }

            FloatingEmote(motion.emote, motion.emoteKey, Modifier.align(Alignment.TopCenter))
            motion.bubble?.let { line ->
                Box(Modifier.align(Alignment.TopCenter).offset(y = (-36).dp)) { Speech(line, maxWidth = 420.dp) }
            }
        }
    }
}

/** 머리 위로 떠오르며 사라지는 감정 표시. */
@Composable
private fun FloatingEmote(text: String?, key: Int, modifier: Modifier) {
    if (text == null) return
    var rise by remember(key) { mutableFloatStateOf(0f) }
    LaunchedEffect(key) {
        animate(0f, 1f, animationSpec = tween(1_400)) { v, _ -> rise = v }
    }
    Text(
        text,
        fontSize = 48.sp,
        modifier = modifier
            .offset(x = 70.dp, y = (20 - 60 * rise).dp)
            .graphicsLayer { alpha = 1f - rise * 0.8f },
    )
}
