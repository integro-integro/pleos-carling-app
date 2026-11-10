package work.integro.carling.ui

import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.border
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.data.CarlingData
import work.integro.carling.data.Personality
import work.integro.carling.game.GrowthRules

/*
 * 서비스 시각화 v1 · 알과의 첫 만남 (FIRST MEETING)
 * 알은 모두에게 하나, 고르는 것은 없다. 첫 운전이 알을 깨우고, 이름을 지어주는 순간 나만의 카링이 된다.
 *   1 탑승·정차   : 당신에게 도착한 알 — 설정도 선택도 없이, 그냥 출발하면 된다
 *   (첫 운전 주행 중: 알이 은은하게 빛날 뿐)
 *   2 도착·주차 후 : 트립카드 #001 → 알을 톡 터치해 깨우기
 *   3 이름 지어주기
 *   4 첫 교감 — 쓰다듬기 (친밀도 퀘스트를 처음 배운다)
 *   5 지도의 첫 불빛, 내일의 약속
 */

@Composable
private fun Step(onBack: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { content() }
        if (onBack != null) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
                Text("‹ 뒤로", fontSize = 22.sp, color = Ink, modifier = Modifier.onScene())
            }
        }
    }
}

/** 1 · 당신에게 도착한 알 — 첫 운전이 알을 깨운다. 누른다고 깨지 않는다. */
@Composable
fun EggArrivalScreen(onDevFirstDrive: (() -> Unit)? = null) = Step {
    val compact = LocalCompact.current
    Spacer(Modifier.height(if (compact) 40.dp else 60.dp))
    EggDrawing(size = if (compact) 280.dp else 360.dp)
    Spacer(Modifier.height(28.dp))
    Text(GrowthRules.EGG_NAME, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.onScene())
    Spacer(Modifier.height(12.dp))
    Speech("미래에서 온 작은 친구가 당신을 기다리고 있어요.")
    Spacer(Modifier.height(14.dp))
    Text(
        "고르는 것도, 설정도 없어요.\n그냥 출발하면, 함께 달린 길이 이 알을 깨워요.",
        fontSize = if (compact) 18.sp else 22.sp,
        color = Ink,
        lineHeight = 32.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.onScene(),
    )
    if (onDevFirstDrive != null) {
        Spacer(Modifier.height(28.dp))
        TextButton(onClick = onDevFirstDrive) { Text("🛠 첫 운전 시뮬레이트", fontSize = 16.sp, color = Ink, modifier = Modifier.onScene()) }
    }
}

/** 2 · 트립카드 #001 — 저장하면 보상을 받고, 알을 눌러 금이 가게 해 네 번째에 깨어난다. */
@Composable
fun AwakenScreen(data: CarlingData, xpIfSaved: Int, onSave: () -> Unit, onWake: () -> Unit) = Step {
    val compact = LocalCompact.current
    var taps by rememberSaveable { mutableIntStateOf(0) }
    val card = data.tripCards.firstOrNull()
    // 첫 트립카드도 다른 카드와 같은 모양이다. 저장해야 알이 반응한다.
    val saved = card?.saved == true
    val speech = @Composable {
        // 글 길이가 달라도 줄 수가 같아야 알 위치가 움직이지 않아 연달아 누를 수 있다
        Speech(
            when {
                !saved -> "운전이 끝났어요. 첫 트립카드를 저장해 볼까요?"
                else -> when (taps) {
                    0 -> "첫 트립카드를 저장했어요. 알을 눌러 깨워줄래요?"
                    1 -> "어? 알에 금이 갔어요!"
                    2 -> "안에서 무언가 움직여요!"
                    3 -> "조금만 더! 곧 나올 것 같아요!"
                    else -> "톡!"
                }
            },
            maxWidth = 520.dp,
        )
    }
    val egg = @Composable {
        HatchingEgg(
            size = if (compact) 220.dp else 280.dp,
            taps = taps,
            onTap = { if (saved) taps += 1 },
            onHatched = onWake,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (saved) "${GrowthRules.EGG_NAME}을 눌러 주세요 · 남은 횟수 ${(EGG_TAPS_TO_HATCH - taps).coerceAtLeast(0)}번" else "트립카드를 저장하면 알이 반응해요",
            fontSize = 18.sp,
            color = Ink,
            modifier = Modifier.onScene(),
        )
    }
    val cardView = @Composable {
        if (card != null) TripCardReveal(card, canSave = !saved, xpIfSaved = xpIfSaved, width = if (compact) 260.dp else 300.dp, onSave = onSave)
    }
    if (compact) {
        cardView()
        Spacer(Modifier.height(16.dp))
        speech()
        Spacer(Modifier.height(12.dp))
        egg()
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            cardView()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                speech()
                Spacer(Modifier.height(16.dp))
                egg()
            }
        }
    }
}


/** 3 · 이름 지어주기 — 이름을 부르면 반응하기 시작하고, 이때부터 나만의 카링이 된다. */
@Composable
fun NameScreen(personality: Personality, onName: (String) -> Unit, onBack: (() -> Unit)? = null) = Step(onBack) {
    var text by remember { mutableStateOf("") }
    val compact = LocalCompact.current
    CarlingDrawing(if (compact) 240.dp else 300.dp, mood = personality)
    Spacer(Modifier.height(16.dp))
    Speech("안녕… 나랑 같이 가줄래? 내 이름을 지어줄래?")
    Spacer(Modifier.height(28.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(10) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, color = Ink),
            placeholder = { Text("이름 (최대 10자)", fontSize = 24.sp) },
            modifier = Modifier.background(Panel.copy(alpha = 0.92f)).width(if (compact) 360.dp else 480.dp),
        )
        Spacer(Modifier.width(20.dp))
        Button(onClick = { onName(text.trim()) }, enabled = text.isNotBlank(), modifier = Modifier.height(72.dp)) {
            Text("정했어요", fontSize = 26.sp)
        }
    }
}

/** 첫 교감 튜토리얼의 세 단계. 하나를 마치면 다음 행동이 열린다(서비스 시각화 V3 '첫 교감 — 튜토리얼 가이드'). */
private enum class Bond(val title: String, val icon: String, val hint: String) {
    PET("쓰다듬기", "💗", "카링을 톡톡 눌러 주세요"),
    FEED("밥 주기", "🍙", "밥 아이콘을 눌러 카링에게 주세요"),
    PLAY("놀아주기", "🎈", "카링을 누른 채 위·아래·좌·우로 움직여 주세요"),
}

private val PetLines = listOf("헤헤, 간지러워!", "거기 좋아요~ 조금만 더!", "와아, 기분 최고예요!")
private const val PET_TAPS = 3
/** 놀아주기를 마치는 드래그 거리(dp). */
private const val PLAY_DISTANCE_DP = 900f

/**
 * 4 · 첫 교감 — 쓰다듬기 → 밥 주기 → 놀아주기를 하나씩 해 보며 각각 +2 XP를 받는 모습을 보여준다.
 * 행동마다 [onPet]·[onFeed]·[onPlay]로 XP를 기록하고, 셋을 마치면 [onDone]으로 다음 장면으로 간다.
 */
@Composable
fun FirstPetScreen(
    name: String,
    personality: Personality,
    onPet: () -> Unit,
    onFeed: () -> Unit,
    onPlay: () -> Unit,
    onDone: () -> Unit,
    onBack: (() -> Unit)? = null,
) = Step(onBack) {
    val compact = LocalCompact.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }       // 0..2 진행 중, 3 = 모두 마침
    var finished by rememberSaveable { mutableIntStateOf(-1) }  // 방금 마친 단계(보상 표시 중)
    var taps by remember { mutableIntStateOf(0) }
    var line by remember { mutableStateOf("'$name'! 정말 마음에 들어. 나랑 같이 놀아줄래요?") }
    var xpPop by remember { mutableIntStateOf(0) }              // +2 XP가 떠오를 때마다 바뀐다
    var dragged by remember { mutableFloatStateOf(0f) }
    var fed by remember { mutableStateOf(false) }
    val bounce = remember { Animatable(1f) }
    val tilt = remember { Animatable(0f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val food = remember { Animatable(0f) }                       // 0 = 버튼, 1 = 카링 입
    val pop = remember { Animatable(0f) }

    fun reward(bond: Bond, thanks: String) {
        line = thanks
        finished = bond.ordinal
        xpPop++
        scope.launch {
            delay(1_600)
            finished = -1
            step = bond.ordinal + 1
            line = when (step) {
                1 -> "배가 조금 고파요… 밥을 줄래요?"
                2 -> "이제 같이 놀아요! 저를 움직여 볼래요?"
                else -> "오늘 정말 즐거웠어요! 매일 이렇게 같이 지내요."
            }
        }
    }
    LaunchedEffect(xpPop) {
        if (xpPop == 0) return@LaunchedEffect
        pop.snapTo(0f)
        pop.animateTo(1f, tween(1_400))
    }

    val size = if (compact) 240.dp else 300.dp
    val current = Bond.entries.getOrNull(step)
    // 진행 표시: 1 쓰다듬기 2XP · 2 밥 주기 2XP · 3 놀아주기 2XP
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(max = 760.dp)) {
        Bond.entries.forEach { b ->
            val done = b.ordinal < step || b.ordinal == finished
            val active = b == current && !done
            val shape = RoundedCornerShape(20.dp)
            Row(
                Modifier
                    .weight(1f)
                    .background(if (active) Accent.copy(alpha = 0.35f) else Panel.copy(alpha = 0.9f), shape)
                    .border(if (active) 2.dp else 1.dp, if (active) Gold else Accent.copy(alpha = 0.3f), shape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (done) "✓" else "${b.ordinal + 1}", fontSize = 20.sp, color = if (done) Gold else Ink, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("${b.icon} ${b.title}", fontSize = 19.sp, color = Ink, modifier = Modifier.weight(1f))
                Text("+${GrowthRules.INTIMACY_XP_EACH} XP", fontSize = 16.sp, color = if (done) Gold else Muted)
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    Box(contentAlignment = Alignment.Center) {
        val playing = current == Bond.PLAY && finished < 0
        CarlingDrawing(
            size = size,
            mood = personality,
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .graphicsLayer {
                    scaleX = bounce.value
                    scaleY = bounce.value
                    rotationZ = tilt.value
                }
                .pointerInput(step, finished) {
                    if (current == Bond.PET && finished < 0) {
                        detectTapGestures {
                            taps++
                            line = PetLines[(taps - 1).coerceAtMost(PetLines.size - 1)]
                            scope.launch {
                                // 세 가지 반응: 통통 튀기 → 좌우로 흔들기 → 한 바퀴 돌기
                                when (taps) {
                                    1 -> { bounce.animateTo(1.15f, tween(140)); bounce.animateTo(1f, spring(0.35f)) }
                                    2 -> { repeat(2) { tilt.animateTo(12f, tween(90)); tilt.animateTo(-12f, tween(90)) }; tilt.animateTo(0f, tween(90)) }
                                    else -> { tilt.animateTo(360f, tween(520)); tilt.snapTo(0f) }
                                }
                            }
                            if (taps == PET_TAPS) { onPet(); reward(Bond.PET, "와아, 간지러워! 쓰다듬어 줘서 좋아요.") }
                        }
                    } else if (playing) {
                        detectDragGestures(
                            onDragEnd = {
                                scope.launch { offsetX.animateTo(0f, spring(0.4f)) }
                                scope.launch { offsetY.animateTo(0f, spring(0.4f)) }
                            },
                        ) { change, amount ->
                            change.consume()
                            scope.launch { offsetX.snapTo(offsetX.value + amount.x) }
                            scope.launch { offsetY.snapTo(offsetY.value + amount.y) }
                            scope.launch { tilt.snapTo((offsetX.value / 12f).coerceIn(-20f, 20f)) }
                            dragged += amount.getDistance() / density.density
                            if (dragged < PLAY_DISTANCE_DP) {
                                line = "야호! 더 빨리, 더 멀리!"
                            } else if (finished < 0) {
                                onPlay()
                                reward(Bond.PLAY, "하하, 신나요! 같이 놀아줘서 고마워요.")
                                scope.launch { offsetX.animateTo(0f, spring(0.4f)) }
                                scope.launch { offsetY.animateTo(0f, spring(0.4f)) }
                                scope.launch { tilt.animateTo(0f) }
                            }
                        }
                    }
                },
        )
        // 밥이 카링 입으로 날아간다
        if (current == Bond.FEED && fed && food.value < 1f) {
            Text("🍙", fontSize = 56.sp, modifier = Modifier.offset(y = (size / 2) * (1f - food.value) + 40.dp).alpha(1f - food.value * 0.3f))
        }
        // +2 XP가 카링 위로 떠오른다
        if (xpPop > 0 && pop.value < 1f) {
            Text(
                "+${GrowthRules.INTIMACY_XP_EACH} XP",
                fontSize = 34.sp,
                color = Gold,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .offset(y = -(size / 2) - (60.dp * pop.value))
                    .alpha(1f - pop.value * pop.value)
                    .onScene(),
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    Speech(line)
    Spacer(Modifier.height(16.dp))
    when {
        current == null -> {
            Button(onClick = onDone, modifier = Modifier.height(64.dp).width(240.dp)) { Text("좋아요", fontSize = 24.sp) }
        }
        finished >= 0 -> Text("${Bond.entries[finished].title} 완료 · +${GrowthRules.INTIMACY_XP_EACH} XP", fontSize = 22.sp, color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.onScene())
        current == Bond.FEED -> {
            Button(
                onClick = {
                    if (fed) return@Button
                    fed = true
                    scope.launch {
                        food.animateTo(1f, tween(700))
                        line = "냠냠냠…"
                        repeat(3) { bounce.animateTo(1.08f, tween(120)); bounce.animateTo(0.96f, tween(120)) }
                        bounce.animateTo(1.1f, tween(200)); bounce.animateTo(1f, spring(0.4f))
                        onFeed()
                        reward(Bond.FEED, "아, 배불러요! 맛있는 밥 고마워요.")
                    }
                },
                enabled = !fed,
                modifier = Modifier.height(72.dp),
            ) { Text("🍙 밥 주기", fontSize = 26.sp) }
            Spacer(Modifier.height(8.dp))
            Text("👆 ${current.hint}", fontSize = 19.sp, color = Ink, modifier = Modifier.onScene())
        }
        current == Bond.PET -> Text("👆 ${current.hint} (${taps}/$PET_TAPS)", fontSize = 19.sp, color = Ink, modifier = Modifier.onScene())
        else -> Text("👆 ${current.hint}", fontSize = 19.sp, color = Ink, modifier = Modifier.onScene())
    }
}

/** 5 · 지도의 첫 불빛, 내일의 약속 — 어두웠던 지도에 오늘 달린 길 하나가 켜진다. */
@Composable
fun FirstMapScreen(name: String, personality: Personality, onDone: () -> Unit, onBack: (() -> Unit)? = null) = Step(onBack) {
    val compact = LocalCompact.current
    CarlingDrawing(if (compact) 200.dp else 240.dp, mood = personality)
    Spacer(Modifier.height(8.dp))
    Speech("위치를 허용하면 어두운 지도에 오늘 달린 길이 켜져요. 제가 자라는 방법을 알려줄게요.")
    Spacer(Modifier.height(24.dp))
    val tips = listOf(
        "트립카드 저장" to "저장해야 XP를 받고 그날이 운전일이 돼요",
        "지도" to "운전을 마칠 때마다 달린 길이 지도에 켜짐",
        "매일 교감" to "쓰다듬기·밥 주기·놀아주기 각 +2 XP",
        "천천히, 함께" to "운전일 90일 동안 세 번 진화해요",
    )
    val rows = if (compact) tips.chunked(2) else listOf(tips)
    rows.forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.widthIn(max = 1200.dp).padding(bottom = 16.dp)) {
            row.forEach { (title, sub) ->
                PanelBox(Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Gold, textAlign = TextAlign.Center)
                        Text(sub, fontSize = 18.sp, color = Muted, textAlign = TextAlign.Center, lineHeight = 26.sp)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Text("내일은 어떤 길을 같이 달릴까요?", fontSize = 22.sp, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.onScene())
    Spacer(Modifier.height(24.dp))
    Button(onClick = onDone, modifier = Modifier.height(64.dp).width(240.dp)) { Text("알겠어요", fontSize = 24.sp) }
}

/** 위치 사용 동의 — 기획안 데이터 원칙: 위치 활용은 이용자 동의 기반. */
@Composable
fun LocationConsentScreen(personality: Personality, onAnswer: (Boolean) -> Unit, onBack: (() -> Unit)? = null) = Step(onBack) {
    val compact = LocalCompact.current
    CarlingDrawing(if (compact) 200.dp else 240.dp, mood = personality)
    Speech("처음 가는 곳을 알아보고, 오늘 날씨도 알려주고 싶어요. 위치를 써도 될까요?")
    Spacer(Modifier.height(24.dp))
    PanelBox(Modifier.widthIn(max = 820.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ConsentLine("어디에 쓰나요", "주행 중 10초마다 위치를 기록해 달린 길을 지도에 켜요. 하루 첫 날씨 확인에도 써요.")
            ConsentLine("무엇을 남기나요", "달린 경로를 약 20m 단위로 이 차에 남겨요. 집이나 직장처럼 자주 가는 곳이 지도에 드러날 수 있어요.")
            ConsentLine("어디로 보내나요", "기록은 이 차 안에만 있어요. 날씨를 볼 때만 약 10km 단위로 어림한 위치를 날씨 서비스(Open-Meteo)로 보내요.")
            ConsentLine("언제든 바꿀 수 있어요", "홈 화면에서 끄면 그때부터 위치를 쓰지 않아요.")
            ConsentLine("허용하지 않으면", "카링은 똑같이 잘 자라요. 다만 지도에 길이 켜지지 않고, 날씨 인사도 받을 수 없어요.")
            ConsentLine("시스템 창이 뜨면", "'허용'을 눌러 주세요. 차량 위치와, 안전 점수에 쓰는 차량 속도를 차례로 물어요. 창에는 '알 수 없는 작업'이나 '위치'로 보일 수 있어요.")
        }
    }
    Spacer(Modifier.height(28.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(onClick = { onAnswer(false) }, modifier = Modifier.height(64.dp).width(240.dp)) {
            Text("허용하지 않을게요", fontSize = 22.sp)
        }
        Button(onClick = { onAnswer(true) }, modifier = Modifier.height(64.dp).width(240.dp)) {
            Text("허용할게요", fontSize = 22.sp)
        }
    }
}

@Composable
private fun ConsentLine(title: String, body: String) {
    Row {
        Text(title, fontSize = 20.sp, color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.width(200.dp))
        Text(body, fontSize = 20.sp, lineHeight = 28.sp, color = Ink, modifier = Modifier.weight(1f))
    }
}
