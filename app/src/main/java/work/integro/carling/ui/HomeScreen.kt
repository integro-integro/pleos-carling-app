package work.integro.carling.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import work.integro.carling.BuildConfig
import work.integro.carling.data.CarlingData
import work.integro.carling.data.CarlingStore
import work.integro.carling.data.Personality
import work.integro.carling.game.DevSimulator
import work.integro.carling.game.Form
import work.integro.carling.game.GrowthRules
import work.integro.carling.game.Lineage
import work.integro.carling.game.Exit
import work.integro.carling.game.Interaction
import work.integro.carling.game.Space
import work.integro.carling.game.arrivalExit
import work.integro.carling.game.exitsOf
import work.integro.carling.game.interactionFor
import work.integro.carling.voice.Babble

/** 주차 후 지난 운행 결과를 보여주는 순서: 카드 → 레벨 → 알의 금 → 새 아이템. */
private enum class Reveal { NONE, CARD, LEVEL, ITEM }

private class EvolutionShow(val from: CarlingArt, val to: CarlingArt, val title: String, val line: String)

/** 카링월드 홈: 큰 방 화면, 카링, 레벨·XP, 함께 달린 날, 새 변화. */
@Composable
fun HomeScreen(store: CarlingStore) {
    val data = store.data
    val personality = data.personality ?: Personality.LUMI
    val level = data.level

    var showCards by remember { mutableStateOf(false) }
    var showGrowth by remember { mutableStateOf(false) }
    var showSpaces by remember { mutableStateOf(false) }
    var showDex by remember { mutableStateOf(false) }
    var zoomedOut by remember { mutableStateOf(false) }
    var travel by remember { mutableStateOf<Space?>(null) }
    var doorHint by remember { mutableStateOf<String?>(null) }
    var showWorld by remember { mutableStateOf(false) }
    var exitChoice by remember { mutableStateOf<Exit?>(null) }
    var showHelp by remember { mutableStateOf(false) }
    var showDev by remember { mutableStateOf(false) }
    var pickLineage by remember { mutableStateOf(false) }
    var evolution by remember { mutableStateOf<EvolutionShow?>(null) }

    val reveal = when {
        data.unseenCards.isNotEmpty() -> Reveal.CARD
        data.seenLevel < level -> Reveal.LEVEL
        data.newItems.isNotEmpty() -> Reveal.ITEM
        else -> Reveal.NONE
    }
    val overlayOpen = showCards || showGrowth || showSpaces || showDex || showWorld || exitChoice != null || travel != null || showHelp || pickLineage || evolution != null ||
        !data.introSeen || reveal == Reveal.CARD || (data.hatched && data.name == null)

    LaunchedEffect(data.form, data.lineage, data.voiceOn) {
        Babble.stage = Babble.stageOf(data.form)
        Babble.lineage = data.lineage
        Babble.enabled = data.voiceOn
    }
    LaunchedEffect(reveal, level) {
        when (reveal) {
            Reveal.LEVEL -> {
                delay(2_800)
                store.ackLevel()
            }
            Reveal.ITEM -> {
                delay(4_200)
                store.ackItems()
            }
            else -> Unit
        }
    }

    // 금이 늘어나는 순간은 레벨 알림과 함께 보여준다. 그 전까지는 지난 모습을 유지한다.
    val shownLevel = if (reveal == Reveal.CARD) data.seenLevel else level
    val art = carlingArt(data.form, data.lineage, shownLevel)
    val motion = rememberCarlingMotion()
    val eggCracking = reveal == Reveal.LEVEL && !data.hatched && GrowthRules.eggStage(level) != GrowthRules.eggStage(data.seenLevel)

    fun startEvolution(lineage: Lineage?) {
        val fromArt = carlingArt(data.form, data.lineage, level)
        val next = data.form.next ?: return
        store.evolve(lineage)
        val toArt = carlingArt(next, lineage ?: data.lineage, level)
        evolution = when (next) {
            Form.BABY -> EvolutionShow(fromArt, toArt, "아기 카링이 깨어났어요!", "알 속의 빛이 민트색 심장이 되었어요.")
            else -> EvolutionShow(fromArt, toArt, "${lineage?.carlingName ?: next.label} 모습이 되었어요!", "다음 공간 '정원'은 곧 열릴 거예요.")
        }
    }

    // 공간 이동: 깨어난 카링은 출입구까지 걸어간 뒤 옮기고, 알은 바로 옮긴다
    fun goTo(space: Space, via: Exit? = null) {
        showSpaces = false
        exitChoice = null
        if (space == data.currentSpace) return
        if (!space.isOpen(level)) {
            doorHint = "${space.label}은(는) Lv.${space.unlockLevel}에 열려요"
            return
        }
        val exit = via ?: exitsOf(data.currentSpace).let { exits -> exits.firstOrNull { space in it.targets } ?: exits.first() }
        if (data.hatched) motion.walkToExit(exit) { travel = space } else travel = space
    }
    fun onExit(exit: Exit) {
        when {
            !data.spacesOpen -> doorHint = "Lv.${Space.MENU_LEVEL}이 되면 문 너머 공간이 열려요"
            exit.targets.size == 1 -> goTo(exit.targets.first(), exit)
            else -> exitChoice = exit
        }
    }
    fun onTouch(interaction: Interaction) {
        if (data.hatched) motion.interact(interaction) else doorHint = "알이 깨어나면 같이 놀 수 있어요"
    }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(travel) {
        val target = travel ?: return@LaunchedEffect
        val from = data.currentSpace
        fade.animateTo(1f, tween(280))
        store.moveTo(target)
        motion.enter(data.hatched, arrivalExit(target, from))
        fade.animateTo(0f, tween(360))
        travel = null
        if (data.hatched) motion.startLife()
    }
    LaunchedEffect(doorHint) {
        if (doorHint != null) {
            delay(2_600)
            doorHint = null
        }
    }
    val zoom by animateFloatAsState(if (zoomedOut) ZOOM_OUT else ZOOM_IN, tween(450), label = "zoom")

    Box(Modifier.fillMaxSize()) {
        RoomWorld(
            space = data.currentSpace,
            zoom = zoom,
            onExit = ::onExit,
            onItem = { onTouch(interactionFor(it)) },
            onFixture = { onTouch(it.interaction) },
            modifier = Modifier.pointerInput(Unit) {
                // 두 손가락으로 벌리면 확대, 오므리면 축소
                detectTransformGestures { _, _, scale, _ ->
                    if (scale < 0.97f) zoomedOut = true else if (scale > 1.03f) zoomedOut = false
                }
            },
            // 새 아이템은 카드와 레벨을 본 뒤에 놓인다(데이터는 이미 저장돼 있다)
            placed = if (reveal == Reveal.CARD || reveal == Reveal.LEVEL) data.placed - data.newItems.toSet() else data.placed,
            newItems = if (reveal == Reveal.ITEM) data.newItems.toSet() else emptySet(),
            motion = motion,
        ) { roomW, roomH ->
            val size = roomW * if (data.hatched) 0.165f else 0.115f
            CarlingActor(
                motion = motion,
                size = size,
                roomW = roomW,
                roomH = roomH,
                alive = data.hatched,
                enabled = !overlayOpen,
                placed = if (data.hatched) data.placed.toSet() else emptySet(),
                space = data.currentSpace,
                tapLines = personality.tapLines,
                modifier = Modifier.fillMaxSize(),
            ) { asleep ->
                // 진화 연출 중에는 뒤에 새 모습이 미리 보이지 않게 가린다
                if (evolution == null && !(data.hatched && data.name == null)) CarlingPicture(art, size, asleep = asleep, rockDegrees = if (data.hatched) 0f else if (eggCracking) 9f else 2f)
            }
        }

        StatusHud(data, shownLevel, Modifier.align(Alignment.TopStart).padding(16.dp).zIndex(5f))

        Row(Modifier.align(Alignment.TopEnd).padding(14.dp).zIndex(5f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallChip(if (data.voiceOn) "🔊 음성" else "🔇 음성", onClick = store::toggleVoice)
            SmallChip("❓ 도움말", onClick = { showHelp = true })
            if (BuildConfig.DEBUG) SmallChip("🛠", onClick = { showDev = !showDev })
        }

        when (reveal) {
            Reveal.LEVEL -> Notice("Lv.$level 달성!", if (eggCracking) "알에 변화가 생겼어요" else "함께 달린 만큼 자랐어요", Modifier.align(Alignment.TopCenter).zIndex(6f))
            Reveal.ITEM -> Notice("새 변화!", data.newItems.joinToString { if (it.space == data.currentSpace) it.label else "${it.space.label}에 ${it.label}" } + " 놓였어요 · 친밀도 +${data.newItems.size}", Modifier.align(Alignment.TopCenter).zIndex(6f))
            else -> Unit
        }

        if (!data.hatched) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp).zIndex(5f)) {
                Speech(eggHint(shownLevel))
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
                .zIndex(5f)
                .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(30.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DockButton("🃏", "트립카드", onClick = { showCards = true })
            DockButton("📖", "도감", onClick = { showDex = true })
            if (data.spacesOpen) DockButton("🚪", "공간", onClick = { showSpaces = true })
            DockButton("🌱", "성장과 진화", dot = data.canEvolve, onClick = { showGrowth = true })
            DockButton("🗺️", "카링월드", onClick = { showWorld = true })
        }

        // 확대·축소: 축소하면 왼쪽 벽의 문이 보인다
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).zIndex(5f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallChip(if (zoomedOut) "🔍 확대" else "🔍 축소", onClick = { zoomedOut = !zoomedOut })
        }
        Text(
            "🏠 카링의 집 · ${data.currentSpace.label} · ${data.currentSpace.floorLabel}",
            fontSize = 20.sp,
            color = Ink,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = if (data.canEvolve) 150.dp else 124.dp)
                .zIndex(5f)
                .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
        doorHint?.let { Notice("🚪", it, Modifier.align(Alignment.TopCenter).zIndex(6f)) }

        if (showDev && BuildConfig.DEBUG) DevPanel(store, Modifier.align(Alignment.TopEnd).padding(top = 76.dp, end = 14.dp).zIndex(7f))

        // ---- 위에 뜨는 창 ----
        Box(Modifier.fillMaxSize().zIndex(8f)) {
            if (showCards) TripCardsScreen(data, store.clock.today(), onClose = { showCards = false })
            if (showGrowth) {
                GrowthScreen(data, onEvolve = {
                    showGrowth = false
                    if (data.form.next == Form.EVO1) pickLineage = true else startEvolution(null)
                }, onClose = { showGrowth = false })
            }
            if (pickLineage) {
                LineagePicker(onPick = {
                    pickLineage = false
                    startEvolution(it)
                }, onCancel = { pickLineage = false })
            }
            if (showHelp) HelpDialog(onClose = { showHelp = false })
            if (showSpaces) SpacePicker(data, onPick = ::goTo, onClose = { showSpaces = false })
            if (showDex) ItemDex(data, onClose = { showDex = false })
            exitChoice?.let { exit -> ExitChooser(exit, level, onPick = { goTo(it, exit) }, onClose = { exitChoice = null }) }
            if (showWorld) WorldMap(data, onClose = { showWorld = false })
            evolution?.let { e ->
                EvolutionOverlay(e.from, e.to, e.title, e.line, onDone = { evolution = null })
            }
            if (evolution == null && data.hatched && data.name == null) NameScreen(art, onName = store::giveName)
            if (evolution == null && reveal == Reveal.CARD) {
                TripRewardDialog(data.unseenCards.mapNotNull { n -> data.tripCards.firstOrNull { it.number == n } }, onClose = store::ackCards)
            }
            if (!data.introSeen) IntroDialog(onStart = store::finishIntro)
        }
        if (fade.value > 0f) Box(Modifier.fillMaxSize().zIndex(9f).background(Color.Black.copy(alpha = fade.value)))
    }
}

private fun eggHint(level: Int): String = when {
    level >= 9 -> "안에서 누군가 웃고 있는 것 같아요."
    level >= 5 -> "금 사이로 빛이 새어 나와요."
    level >= 2 -> "알에 작은 금이 갔어요. 두근, 두근."
    else -> "함께 달리면 알이 조금씩 깨어나요."
}

@Composable
private fun StatusHud(data: CarlingData, level: Int, modifier: Modifier) {
    Column(
        modifier
            .width(330.dp)
            .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(data.name ?: formLabel(data.form, data.lineage), fontSize = 24.sp, color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("Lv.$level", fontSize = 24.sp, color = RoseDeep, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        SoftBar(GrowthRules.levelProgress(data.totalXp), Honey, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text("함께 달린 날 ${data.drivingDayCount}일", fontSize = 19.sp, color = Muted)
        if (data.canEvolve) Text("✨ 진화할 수 있어요!", fontSize = 19.sp, color = RoseDeep, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Notice(title: String, body: String, modifier: Modifier) {
    Column(
        modifier
            .padding(top = 18.dp)
            .background(Color.White.copy(alpha = 0.96f), RoundedCornerShape(26.dp))
            .padding(horizontal = 30.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontSize = 28.sp, color = RoseDeep, fontWeight = FontWeight.Bold)
        Text(body, fontSize = 22.sp, color = Ink, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SmallChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 20.sp,
        color = Ink,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@Composable
private fun DockButton(icon: String, label: String, dot: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 4.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 34.sp)
            Text(label, fontSize = 18.sp, color = Ink)
        }
        if (dot) Box(Modifier.align(Alignment.TopEnd).offset(x = 8.dp).size(16.dp).background(RoseDeep, CircleShape))
    }
}

@Composable
private fun IntroDialog(onStart: () -> Unit) {
    FullScrim {
        PanelBox(Modifier.widthIn(max = 720.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CarlingPicture(carlingArt(Form.EGG, null, 1), 240.dp)
                Text("작은 빛이 당신의 차에 도착했어요", fontSize = 30.sp, color = Ink, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(
                    "운전하면 젤리 알이 자라고, 이 방이 조금씩 채워져요.\n따로 저장하거나 꾸밀 필요 없이, 평소처럼 달리기만 하면 돼요.",
                    fontSize = 22.sp,
                    color = Ink,
                    lineHeight = 32.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Button(onClick = onStart, modifier = Modifier.height(72.dp)) { Text("함께 시작해요", fontSize = 26.sp) }
            }
        }
    }
}

private val HelpSections = listOf(
    Triple("🚗", "운전하면 끝", "거리 2km 이상, 실제로 달린 시간 5분 이상이면 트립카드 한 장이 자동으로 저장돼요. 저장 버튼은 없어요."),
    Triple("⚡", "XP와 레벨", "하루 첫 5번의 유효 운행마다 20 XP를 받아요. 안전 점수 90점 이상이면 5 XP를 더 받아요. 6번째부터는 카드와 아이템만 받아요."),
    Triple("🛋️", "자동으로 꾸며지는 방", "운행할 때마다 새 아이템이 정해진 자리에 놓여요. 방은 24종, 집의 공간은 12종씩이에요. 새 아이템이 처음 놓일 때 친밀도가 올라요."),
    Triple("🎯", "퀘스트", "레벨이 오를 때마다 운행 퀘스트가 생기고, 달리다 보면 자동으로 완료돼요."),
    Triple("🌱", "진화", "최소 레벨, 친밀도, 퀘스트를 모두 채우면 '성장과 진화'에서 진화할 수 있어요. Lv.10 이상이면 아기 카링이 깨어나고, Lv.20 이상이면 계열을 골라 진화해요."),
    Triple("🚪", "공간", "Lv.10이 되면 '공간' 메뉴가 생기고 거실이 열려요. 주방(Lv.13)·화장실(Lv.16)·작업실(Lv.18)은 잠겨 있다가 레벨이 오르면 열려요. 계열 진화 1부터는 정원 같은 더 넓은 공간이 이어질 예정이에요."),
    Triple("🏠", "2층 집", "거실이 집의 중심이에요. 거실의 계단으로 2층(방·작업실), 아치로 주방, 문으로 화장실에 가요. 다른 공간에서는 왼쪽 문으로 나가요."),
    Triple("🔍", "확대·축소와 문", "오른쪽의 축소 버튼(또는 두 손가락 오므리기)을 누르면 방 전체와 문·계단이 보여요. 문을 누르면 카링이 그쪽으로 걸어가요."),
    Triple("👆", "가구 누르기", "가구를 누르면 카링이 다가가요. 소파에 앉고, 침대에서 자고, TV를 보며 즐거워하고, 커피를 마시면 맛있다고 해요. 보상은 없는 놀이예요."),
    Triple("🗺️", "카링월드", "아래 '카링월드'에서 지도를 볼 수 있어요. 지금 있는 집은 밝게 빛나고, 정원·해변·숲·하늘 전망대·하늘 섬은 진화할 때마다 안개가 걷혀요."),
    Triple("📖", "아이템 도감", "공간마다 얻을 수 있는 아이템이 몇 개이고 몇 개를 모았는지 보여줘요. 새 아이템은 카링이 있는 공간부터 채워져요."),
    Triple("🃏", "트립카드", "지난 카드는 모두 남아요. 오늘, 지난 7일·14일·30일, 전체로 골라 볼 수 있어요."),
    Triple("🛏️", "카링의 생활", "깨어난 카링은 혼자서도 가끔 침대에서 자고, 소파에 앉고, 음악에 맞춰 춤춰요. 카링을 누르면 몸이 말랑하게 출렁이고 옹알이로 대답해요."),
    Triple("🔊", "음성", "카링이 말할 때 옹알이 목소리가 나요. 운행을 시작하거나 마칠 때는 \"뿅\" 하고 또렷하게 말을 걸어요. 화면 오른쪽 위에서 언제든 끌 수 있어요."),
    Triple("🛡️", "안전과 정보", "운행 중에는 게임 화면을 보여주지 않아요. 달린 경로와 위치는 저장하지 않아요."),
)

@Composable
private fun HelpDialog(onClose: () -> Unit) {
    FullScrim {
        PanelBox(Modifier.widthIn(max = 900.dp)) {
            Column {
                Text("카링월드 도움말", fontSize = 30.sp, color = Ink, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Column(Modifier.height(520.dp).verticalScroll(rememberScrollState())) {
                    HelpSections.forEach { (icon, title, body) ->
                        Row(Modifier.padding(vertical = 8.dp)) {
                            Text(icon, fontSize = 30.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(title, fontSize = 24.sp, color = Ink, fontWeight = FontWeight.Bold)
                                Text(body, fontSize = 21.sp, color = Muted, lineHeight = 30.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = onClose, modifier = Modifier.align(Alignment.End).height(64.dp)) { Text("닫기", fontSize = 24.sp) }
            }
        }
    }
}

@Composable
private fun DevPanel(store: CarlingStore, modifier: Modifier) {
    Column(
        modifier
            .width(300.dp)
            .background(Color.White, RoundedCornerShape(20.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("시뮬레이션 데이터", fontSize = 18.sp, color = RoseDeep, fontWeight = FontWeight.Bold)
        DevButton("유효 운행 1회 (95점)") { DevSimulator.validTrip(store) }
        DevButton("유효 운행 1회 (84점)") { DevSimulator.validTrip(store, harshEvents = 2) }
        DevButton("조건 미달 운행") { DevSimulator.shortTrip(store) }
        DevButton("하루 넘기고 2회 운행") { DevSimulator.nextDay(store) }
        DevButton("다음 진화 조건 채우기") { DevSimulator.fillNextEvolution(store) }
        DevButton("처음부터 다시") { store.devReset() }
    }
}

@Composable
private fun DevButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(label, fontSize = 18.sp) }
}
