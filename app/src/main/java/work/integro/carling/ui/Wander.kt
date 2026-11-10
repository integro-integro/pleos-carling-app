package work.integro.carling.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import work.integro.carling.game.Action
import work.integro.carling.game.Exit
import work.integro.carling.game.Interaction
import work.integro.carling.game.RoomItem
import work.integro.carling.game.fixturesOf
import work.integro.carling.game.interactionFor
import work.integro.carling.game.Space
import work.integro.carling.voice.Babble
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

/** 그림 안에서 카링의 발이 있는 높이. */
private const val FEET_AT = 0.95f

/** 가구를 관통하지 않게 이동할 때 지나는 앞쪽 통로의 높이(방 그림 대비). */
private const val CORRIDOR = 0.87f

/** 방 안의 위치(발 기준, 방 그림 대비 비율). */
data class RoomSpot(val x: Float, val y: Float)

/** 알이 놓여 있는 자리. */
val EGG_SPOT = RoomSpot(.50f, .79f)

/** 공간마다 가구와 겹치지 않는 빈 바닥. */
private fun freeSpots(space: Space): List<RoomSpot> = when (space) {
    Space.BEDROOM -> listOf(RoomSpot(.50f, .87f), RoomSpot(.38f, .87f), RoomSpot(.62f, .87f), RoomSpot(.30f, .86f), RoomSpot(.72f, .86f), RoomSpot(.80f, .80f), RoomSpot(.50f, .77f))
    Space.LIVING -> listOf(RoomSpot(.50f, .88f), RoomSpot(.40f, .87f), RoomSpot(.62f, .87f), RoomSpot(.70f, .86f), RoomSpot(.45f, .80f), RoomSpot(.66f, .70f))
    Space.KITCHEN -> listOf(RoomSpot(.50f, .88f), RoomSpot(.38f, .87f), RoomSpot(.65f, .89f), RoomSpot(.36f, .76f), RoomSpot(.45f, .70f))
    Space.BATHROOM -> listOf(RoomSpot(.50f, .88f), RoomSpot(.38f, .87f), RoomSpot(.62f, .86f), RoomSpot(.56f, .79f), RoomSpot(.68f, .76f))
    Space.STUDIO -> listOf(RoomSpot(.50f, .88f), RoomSpot(.38f, .87f), RoomSpot(.62f, .87f), RoomSpot(.55f, .80f), RoomSpot(.30f, .86f))
}


/** 혼자 지낼 때 가끔 스스로 하는 가구 교감의 확률과 같은 가구를 다시 찾기까지의 시간. */
private const val SELF_INTERACT_CHANCE = 0.4f
private const val SELF_INTERACT_COOLDOWN_MS = 25_000L

/** 스스로 하기 좋은 행동(바라보기만 하는 것은 빼고). */
private val SelfActions = setOf(Action.SLEEP, Action.SIT, Action.DANCE, Action.WATCH, Action.EAT, Action.READ)

class CarlingMotion internal constructor(private val scope: CoroutineScope) {
    /** 발 위치(방 가운데 기준 px). */
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    var lift by mutableFloatStateOf(0f)
    var rotation by mutableFloatStateOf(0f)
    var squash by mutableFloatStateOf(1f)
    var facingLeft by mutableStateOf(false)
    var napping by mutableStateOf(false)

    /** 스탠드 근처에 있어 게임 속 스탠드가 켜졌는지. */
    var lampOn by mutableStateOf(false)
        private set

    var emote by mutableStateOf<String?>(null)
    var bubble by mutableStateOf<String?>(null)
    var emoteKey by mutableIntStateOf(0)

    internal var roomW = 1f
    internal var roomH = 1f
    internal var px = 1f
    internal var placed: Set<RoomItem> = emptySet()
    internal var space: Space = Space.BEDROOM
    private var job: Job? = null
    private var positioned = false
    private var travelling = false
    private val lastUsed = HashMap<String, Long>()

    /** 소파·침대처럼 가구 위에 올라가 있는지. 그동안은 가구보다 앞에 그린다. */
    var onFurniture by mutableStateOf(false)
        private set

    /** 발의 세로 위치(방 그림 대비). 가구와의 앞뒤를 정한다. */
    val footFraction: Float get() = y / roomH + 0.5f

    private fun xOf(spot: RoomSpot) = (spot.x - 0.5f) * roomW
    private fun yOf(spot: RoomSpot) = (spot.y - 0.5f) * roomH

    internal fun placeAt(spot: RoomSpot) {
        if (positioned) return
        positioned = true
        x = xOf(spot)
        y = yOf(spot)
    }

    /** 방 안을 짧은 경로로 돌아다니고, 놓인 가구가 있으면 가끔 찾아가 반응한다. */
    fun startLife() {
        if (travelling) return
        job?.cancel()
        job = scope.launch {
            while (true) {
                delay(Random.nextLong(1_800, 4_200))
                val now = System.currentTimeMillis()
                val choices = selfInteractions().filter { (key, i) -> i.action in SelfActions && now - (lastUsed[key] ?: 0L) > SELF_INTERACT_COOLDOWN_MS }
                if (choices.isNotEmpty() && Random.nextFloat() < SELF_INTERACT_CHANCE) {
                    val (key, i) = choices.random()
                    lastUsed[key] = now
                    perform(i)
                } else {
                    goTo(freeSpots(space).random(), via = null)
                    if (Random.nextFloat() < 0.4f) wiggle()
                }
            }
        }
    }

    /** 이 공간에서 교감할 수 있는 것들(놓인 아이템 + 배경 가구). */
    private fun selfInteractions(): List<Pair<String, Interaction>> =
        placed.filter { it.space == space }.map { it.name to interactionFor(it) } +
            fixturesOf(space).map { "${space.name}-${it.label}" to it.interaction }

    /** 누른 가구 곁으로 가서 교감한다. 반복해도 보상은 없다. */
    fun interact(interaction: Interaction) {
        if (travelling) return
        job?.cancel()
        napping = false
        lampOn = false
        job = scope.launch {
            perform(interaction)
            delay(1_200)
            startLife()
        }
    }

    /** 멈춘다(창을 볼 때, 주행 제한일 때). 다음 허용 시점에 기본 상태부터 다시 시작한다. */
    fun rest() {
        if (travelling) return
        job?.cancel()
        emote = null
        bubble = null
        napping = false
        lampOn = false
        lift = 0f
        rotation = 0f
    }

    fun stop() {
        job?.cancel()
    }

    /** 출입구(문·계단·아치)로 걸어간 뒤 [onArrive]를 부른다(공간 이동). */
    fun walkToExit(exit: Exit, onArrive: () -> Unit) {
        rest()
        job?.cancel()
        // 창이 닫히며 생활 루프가 다시 시작돼도 걸어가는 중에는 끊기지 않게 한다
        travelling = true
        job = scope.launch {
            onFurniture = false
            goTo(RoomSpot(exit.walkX, exit.walkY), via = if (space == Space.BEDROOM) RoomSpot(.30f, .72f) else null)
            onArrive()
        }
    }

    /** 새 공간에 들어선다. 깨어난 카링은 들어온 출입구 앞에서 나오고, 알은 가운데에 놓인다. */
    fun enter(alive: Boolean, at: Exit) {
        travelling = false
        onFurniture = false
        job?.cancel()
        val spot = if (alive) RoomSpot(at.walkX, at.walkY) else EGG_SPOT
        x = xOf(spot)
        y = yOf(spot)
        facingLeft = false
    }

    /** 누르면 말을 걸어 준다. 보상은 없다. */
    fun onTap(lines: List<String>, canMove: Boolean) {
        if (!canMove) {
            Babble.chirp()
            return
        }
        job?.cancel()
        napping = false
        lampOn = false
        job = scope.launch {
            lift = 0f
            show("💕", lines.random())
            squashTo(0.86f, 90)
            arc(heightDp = 46f, millis = 460)
            squashTo(1.06f, 80)
            squashTo(1f, 120)
            delay(1_500)
            startLife()
        }
    }

    private suspend fun perform(i: Interaction) {
        goTo(RoomSpot(i.approachX, i.approachY), via = null)
        val sx = i.seatX
        val sy = i.seatY
        if (sx != null && sy != null) {
            onFurniture = true
            hopTo(xOf(RoomSpot(sx, sy)), yOf(RoomSpot(sx, sy)))
        }
        when (i.action) {
            Action.SLEEP -> {
                napping = true
                show(i.emote, null)
                squashTo(0.92f, 400)
                delay(Random.nextLong(4_500, 6_500))
                napping = false
                squashTo(1f, 250)
                show(null, "하암, 잘 잤다!")
                delay(900)
            }
            Action.SIT -> {
                show(i.emote, i.line)
                squashTo(0.9f, 300)
                delay(3_200)
                squashTo(1f, 200)
            }
            Action.WATCH -> {
                show(i.emote, i.line)
                repeat(3) {
                    arc(heightDp = 26f, millis = 340)
                    delay(180)
                }
                delay(800)
            }
            Action.EAT -> {
                show(i.emote, i.line)
                repeat(4) {
                    squashTo(0.88f, 110)
                    squashTo(1.04f, 110)
                }
                squashTo(1f, 100)
                delay(900)
            }
            Action.DANCE -> {
                show(i.emote, i.line)
                repeat(4) {
                    facingLeft = !facingLeft
                    arc(heightDp = 34f, millis = 380)
                    wiggle(times = 1, angle = 11f)
                }
                show("🎶", null)
            }
            Action.LIGHT -> {
                lampOn = true
                show(i.emote, i.line)
                delay(3_400)
                lampOn = false
            }
            Action.READ -> {
                show(i.emote, i.line)
                animate(rotation, -8f, animationSpec = tween(300)) { v, _ -> rotation = v }
                delay(2_600)
                animate(rotation, 0f, animationSpec = tween(200)) { v, _ -> rotation = v }
            }
            Action.SMELL, Action.ADMIRE -> {
                show(i.emote, i.line)
                wiggle(times = 2, angle = 6f)
                delay(1_600)
            }
            Action.PLAY -> {
                show(i.emote, i.line)
                spinOnce()
                delay(1_200)
            }
        }
        if (sx != null && sy != null) {
            hopTo(xOf(RoomSpot(i.approachX, i.approachY)), yOf(RoomSpot(i.approachX, i.approachY)))
            onFurniture = false
        }
    }

    private suspend fun hopTo(toX: Float, toY: Float) {
        val fromX = x
        val fromY = y
        if (kotlin.math.abs(toX - fromX) > 4f * px) facingLeft = toX < fromX
        animate(0f, 1f, animationSpec = tween(420, easing = LinearEasing)) { p, _ ->
            x = fromX + (toX - fromX) * p
            y = fromY + (toY - fromY) * p
            lift = 4f * p * (1f - p) * 40f * px
        }
        lift = 0f
    }

    private suspend fun spinOnce() {
        animate(0f, 360f, animationSpec = tween(520)) { v, _ -> rotation = v }
        rotation = 0f
    }

    /** 가구를 뚫지 않게 앞쪽 통로로 돌아서 간다. */
    private suspend fun goTo(spot: RoomSpot, via: RoomSpot?) {
        val corridor = yOf(RoomSpot(0f, CORRIDOR))
        if (y < corridor - 2f * px) walkTo(x, corridor)
        if (via != null) {
            walkTo(xOf(via), corridor)
            walkTo(xOf(via), yOf(via))
        } else {
            walkTo(xOf(spot), corridor)
        }
        walkTo(xOf(spot), yOf(spot))
    }

    private suspend fun walkTo(toX: Float, toY: Float) {
        val fromX = x
        val fromY = y
        val distance = hypot(toX - fromX, toY - fromY)
        if (distance < 2f * px) return
        if (kotlin.math.abs(toX - fromX) > 4f * px) facingLeft = toX < fromX
        // 다른 공간으로 갈 때는 조금 서둘러 걷는다
        val speed = WALK_SPEED_DP * if (travelling) 1.8f else 1f
        val millis = ((distance / px) / speed * 1000f).roundToInt().coerceAtLeast(220)
        val hops = ((distance / px) / HOP_LENGTH_DP).roundToInt().coerceAtLeast(1)
        animate(0f, 1f, animationSpec = tween(millis, easing = LinearEasing)) { p, _ ->
            x = fromX + (toX - fromX) * p
            y = fromY + (toY - fromY) * p
            val phase = (p * hops) % 1f
            lift = (4f * phase * (1f - phase)) * HOP_DP * px
        }
        lift = 0f
    }

    private suspend fun arc(heightDp: Float, millis: Int) {
        animate(0f, 1f, animationSpec = tween(millis, easing = LinearEasing)) { p, _ ->
            lift = 4f * p * (1f - p) * heightDp * px
        }
        lift = 0f
    }

    private suspend fun wiggle(times: Int = 3, angle: Float = 8f) {
        repeat(times) {
            animate(rotation, angle, animationSpec = tween(110, easing = FastOutSlowInEasing)) { v, _ -> rotation = v }
            animate(rotation, -angle, animationSpec = tween(110, easing = FastOutSlowInEasing)) { v, _ -> rotation = v }
        }
        animate(rotation, 0f, animationSpec = tween(90)) { v, _ -> rotation = v }
    }

    private suspend fun squashTo(target: Float, millis: Int) {
        animate(squash, target, animationSpec = tween(millis)) { v, _ -> squash = v }
    }

    private fun show(emoteText: String?, line: String?) {
        emote = emoteText
        emoteKey++
        bubble = line
        when {
            line != null -> Babble.say(line)
            emoteText != null && emoteText != "💤" -> Babble.chirp()
        }
        if (line != null) {
            val key = emoteKey
            scope.launch {
                delay(2_600)
                if (emoteKey == key) bubble = null
            }
        }
    }

    private companion object {
        const val WALK_SPEED_DP = 150f
        const val HOP_LENGTH_DP = 60f
        const val HOP_DP = 11f
    }
}

@Composable
fun rememberCarlingMotion(): CarlingMotion {
    val scope = rememberCoroutineScope()
    return remember { CarlingMotion(scope) }
}

/**
 * 방 안의 카링. 몸은 말랑한 젤리처럼 관성으로 출렁이고, 누르면 그 자리가 콕 들어갔다 튕긴다.
 * 젤리 알은 걷지 않고 가볍게 흔들리기만 한다.
 */
@Composable
fun CarlingActor(
    motion: CarlingMotion,
    size: Dp,
    roomW: Dp,
    roomH: Dp,
    alive: Boolean,
    enabled: Boolean,
    placed: Set<RoomItem>,
    space: Space,
    tapLines: List<String>,
    modifier: Modifier = Modifier,
    picture: @Composable (asleep: Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }

    BoxWithConstraints(modifier) {
        val centerX = with(density) { maxWidth.toPx() / 2 }
        val centerY = with(density) { maxHeight.toPx() / 2 }
        motion.px = with(density) { 1.dp.toPx() }
        motion.roomW = with(density) { roomW.toPx() }
        motion.roomH = with(density) { roomH.toPx() }
        motion.placed = placed
        motion.space = space
        motion.placeAt(EGG_SPOT)

        LaunchedEffect(enabled, alive) {
            if (enabled && alive) motion.startLife() else motion.rest()
        }
        DisposableEffect(Unit) { onDispose { motion.stop() } }

        // ---- 말랑 젤리: 몸의 움직임(가속도)을 받아 그림을 출렁이게 한다 ----
        val jelly = remember { JellyBody() }
        val jellyShader = remember { if (JellySupported) JellyShader() else null }
        var jellyFrame by remember { mutableIntStateOf(0) }
        if (jellyShader != null) LaunchedEffect(Unit) {
            var last = 0L
            var px0 = 0f; var py0 = 0f; var vx0 = 0f; var vy0 = 0f
            var sq0 = 1f; var sqv0 = 0f
            var idleTick = 0
            while (true) {
                withFrameNanos { t ->
                    if (last != 0L) {
                        val dt = ((t - last) / 1e9f).coerceIn(1f / 240f, 1f / 20f)
                        val bx = motion.x / sizePx
                        val by = (motion.y - motion.lift) / sizePx
                        val vx = (bx - px0) / dt
                        val vy = (by - py0) / dt
                        val sqv = (motion.squash - sq0) / dt
                        var ax = ((vx - vx0) / dt).coerceIn(-60f, 60f)
                        // 찌그러졌다 펴지는 순간의 반동도 위아래 출렁임으로 이어진다
                        val ay = ((vy - vy0) / dt).coerceIn(-60f, 60f) - ((sqv - sqv0) / dt * 0.02f).coerceIn(-30f, 30f)
                        // 그림은 왼쪽을 볼 때 좌우가 뒤집혀 그려지므로 힘의 방향도 뒤집는다
                        if (motion.facingLeft) ax = -ax
                        jelly.step(dt, ax, ay)
                        px0 = bx; py0 = by; vx0 = vx; vy0 = vy; sq0 = motion.squash; sqv0 = sqv
                    } else {
                        px0 = motion.x / sizePx; py0 = (motion.y - motion.lift) / sizePx
                    }
                    last = t
                    // 출렁이는 동안은 매 프레임, 가만히 있을 때는 숨쉬기만 초당 20번 정도 다시 그린다
                    idleTick++
                    if (!jelly.settled || idleTick % 3 == 0) jellyFrame++
                }
            }
        }

        Box(
            Modifier
                .offset {
                    IntOffset(
                        (centerX + motion.x - sizePx / 2).roundToInt(),
                        (centerY + motion.y - motion.lift - sizePx * FEET_AT).roundToInt(),
                    )
                }
                .size(size)
                .pointerInput(enabled) {
                    if (enabled) detectTapGestures { at ->
                        val u = (at.x / sizePx).coerceIn(0f, 1f)
                        jelly.poke(if (motion.facingLeft) 1f - u else u, (at.y / sizePx).coerceIn(0f, 1f), 2.8f)
                        motion.onTap(tapLines, alive)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // 그림만 좌우 반전·회전·찌그러짐 (말풍선 글자는 뒤집히지 않게)
            Box(
                Modifier.graphicsLayer {
                    scaleX = (if (motion.facingLeft) -1f else 1f) * (2f - motion.squash)
                    scaleY = motion.squash
                    rotationZ = motion.rotation
                    transformOrigin = TransformOrigin(0.5f, FEET_AT)
                    if (jellyShader != null) {
                        @Suppress("UNUSED_EXPRESSION") jellyFrame // 매 프레임 이 층만 다시 그린다
                        renderEffect = jellyShader.effect(this.size.width, this.size.height, jelly.displacements(breathing = !motion.napping))
                    }
                },
            ) { picture(motion.napping) }

            FloatingEmote(motion.emote, motion.emoteKey, Modifier.align(Alignment.TopCenter))
            motion.bubble?.let { line ->
                Box(Modifier.align(Alignment.TopCenter).offset(y = (-40).dp)) { Speech(line, maxWidth = 420.dp) }
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
        fontSize = 44.sp,
        modifier = modifier
            .offset(x = 60.dp, y = (20 - 60 * rise).dp)
            .graphicsLayer { alpha = 1f - rise * 0.8f },
    )
}
