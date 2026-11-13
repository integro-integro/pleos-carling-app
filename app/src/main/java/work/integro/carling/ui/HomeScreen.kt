package work.integro.carling.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import work.integro.carling.BuildConfig
import work.integro.carling.data.CarlingData
import work.integro.carling.data.CarlingStore
import work.integro.carling.data.TripCard
import work.integro.carling.game.Badge
import work.integro.carling.game.CabinView
import work.integro.carling.game.CarColor
import work.integro.carling.game.CarPart
import work.integro.carling.game.CarSlot
import work.integro.carling.game.CarStyle
import work.integro.carling.game.CardState
import work.integro.carling.game.DevSimulator
import androidx.compose.animation.core.animateFloat
import work.integro.carling.game.PersonalityResult
import work.integro.carling.game.Rules
import work.integro.carling.game.Stage
import work.integro.carling.game.TimeBucket
import work.integro.carling.game.TripPurpose
import work.integro.carling.voice.Babble

/** 하단 탭(사용자 요청 11/12): 홈 · 내 카링 · 트립카드 · 리포트. */
private enum class Tab(val icon: String, val label: String) { KARING("🏠", "홈"), MY("💜", "내 카링"), CARDS("🎫", "트립카드"), REPORT("📊", "리포트") }

/** 열린 카드 시트. */
private class OpenSheet(val tripId: Long, val mode: SheetMode)

/**
 * 주차(P) 홈. 사용자가 조작하는 순간은 주차 상태의 세 번뿐이다: 카드 완성, 럭키박스 개봉, 리포트 열람.
 * 안내 순서: 동의 → 도착·재안내 카드 → 단계 변화 → 개성 변화.
 */
@Composable
fun HomeScreen(store: CarlingStore, onLocation: (Boolean) -> Unit = store::setLocationConsent) {
    val data = store.data
    // 시간대(배경 하늘)는 30초마다 다시 본다. 시뮬레이터로 시계를 돌려도 바로 바뀐다.
    var tick by remember { androidx.compose.runtime.mutableLongStateOf(store.clock.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick = store.clock.now()
        }
    }
    val bucket = TimeBucket.of(store.clock.hourOf(maxOf(tick, store.clock.now()).let { store.clock.now() }))
    var tab by remember { mutableStateOf(Tab.KARING) }
    // 홈 · 내 카링 · 트립카드 · 리포트
    var sheet by remember { mutableStateOf<OpenSheet?>(null) }
    var showBox by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showTurn by remember { mutableStateOf(false) }
    var showBadges by remember { mutableStateOf(false) }
    var showShop by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    var showDev by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val personality = remember(data.rewards, data.cards, data.trips) { store.personalityOf(data) }

    LaunchedEffect(data.shownStage, data.voiceOn) {
        Babble.stage = Babble.stageOf(data.shownStage)
        Babble.lineage = Babble.timbreOf(data.shownStage)
        Babble.enabled = data.voiceOn
    }
    // 확정·만료는 화면을 보는 동안에도 판정한다
    LaunchedEffect(Unit) {
        while (true) {
            store.settle()
            store.expireCards()
            store.publishMonthReport()
            delay(30_000)
        }
    }
    // 달이 바뀌는 순간(시계 건너뛰기 포함)에도 지난달 리포트를 만든다
    LaunchedEffect(store.clock.today(), data.trips.size) { store.publishMonthReport() }
    var reportFocus by remember { mutableStateOf<java.time.YearMonth?>(null) }
    // 재탑승 후 처음 보는 홈: 확정 뒤 임시로 남은 카드를 재안내하고, 이때부터 24시간 기한이 시작된다
    var reminding by remember { mutableStateOf(emptyList<Long>()) }
    LaunchedEffect(store.ignitionMillis) {
        val due = store.reminderCards().map { it.tripId }
        if (due.isNotEmpty()) {
            store.markReminded(due)
            reminding = due
        }
    }
    LaunchedEffect(tab) {
        when (tab) {
            Tab.REPORT -> store.logOpen("report_opened")
            Tab.KARING -> store.logOpen("karing_opened")
            else -> Unit
        }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(3_200)
            notice = null
        }
    }

    // 도착(또는 재안내) 카드: 가장 최근 것부터
    val prompt = (data.unseenCards + reminding).distinct().mapNotNull { id -> data.card(id)?.takeIf { it.state == CardState.DRAFT } }.maxByOrNull { it.tripId }

    fun complete(card: TripCard, purpose: TripPurpose, tags: List<String>, companion: work.integro.carling.game.Companion?, custom: String?) {
        store.complete(card.tripId, purpose, tags, companion, custom)
        val reward = store.data.rewards[card.tripId]
        notice = if (reward != null) "+${reward.gp} 성장 포인트" + (if (reward.boxId != null) " · 🎁 럭키박스가 도착했어요" else "")
        else "완성했어요 · 운행이 확정되면 약 ${store.expectedGp(card.tripId)}점이 반영돼요"
        reminding = reminding - card.tripId
        sheet = null
    }
    fun completeAll() {
        val n = store.data.draftCards.size
        store.completeAllDrafts()
        reminding = emptyList()
        sheet = null
        if (n > 0) notice = "카드 ${n}장을 완성했어요"
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop(bucket, still = false, modifier = Modifier.fillMaxSize())

        Box(Modifier.fillMaxSize().padding(bottom = if (tab == Tab.KARING) 0.dp else 96.dp)) {
            when (tab) {
                Tab.KARING -> KaringTab(data, personality, bucket, store, onBox = { showBox = true }, onCards = { tab = Tab.CARDS }, onTurn = { showTurn = true }, onBadges = { showBadges = true }, onMy = { tab = Tab.MY })
                Tab.CARDS -> TripCardsTab(
                    data,
                    store.clock.today(),
                    onOpen = { c -> sheet = OpenSheet(c.tripId, if (c.state == CardState.COMPLETED) SheetMode.EDIT else SheetMode.OPEN) },
                    onCompleteAll = ::completeAll,
                )
                Tab.REPORT -> ReportTab(data, store.clock.today(), personality, onLocation = onLocation, store = store, focus = reportFocus, onFocused = { reportFocus = null })
                Tab.MY -> MyKaringTab(data, store)
            }
        }

        // 상단 오른쪽
        Row(Modifier.align(Alignment.TopEnd).padding(16.dp).zIndex(5f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tab == Tab.KARING || tab == Tab.MY) {
                JellyChip(data.jelly) { showShop = true }
                SmallChip("👥", onClick = { showInvite = true })
            }
            SmallChip(if (data.voiceOn) "🔊" else "🔇", onClick = store::toggleVoice)
            SmallChip("❓ 도움말", onClick = { showHelp = true })
            if (BuildConfig.DEBUG && !work.integro.carling.game.DevUi.hidden) SmallChip("🛠", onClick = { showDev = !showDev })
        }

        TabBar(tab, drafts = data.draftCards.size, onTab = { tab = it }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).zIndex(5f))

        notice?.let {
            Text(
                it,
                fontSize = 23.sp,
                color = Ink,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp).zIndex(6f)
                    .background(Color.White, RoundedCornerShape(50)).border(2.dp, MintSoft, RoundedCornerShape(50))
                    .padding(horizontal = 28.dp, vertical = 12.dp),
            )
        }
        // 개성 변화: 근거 한 줄과 함께
        data.unseenPersonality?.let { id -> data.snapshots.firstOrNull { it.id == id } }?.let { snap ->
            if (prompt == null && data.unseenStage == null && data.consented) {
                LaunchedEffect(snap.id) {
                    delay(5_500)
                    store.ackPersonality()
                }
                Column(
                    Modifier.align(Alignment.TopCenter).padding(top = 80.dp).zIndex(6f)
                        .background(Color.White, RoundedCornerShape(26.dp)).border(2.dp, LilacSoft, RoundedCornerShape(26.dp))
                        .clickable { store.ackPersonality() }.padding(horizontal = 28.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("✨ 카링이 달라졌어요", fontSize = 22.sp, color = Lilac, fontWeight = FontWeight.Bold)
                    Text(snap.line.ifEmpty { "개성이 기본으로 돌아왔어요" }, fontSize = 20.sp, color = Ink, textAlign = TextAlign.Center)
                }
            }
        }

        if (showDev && BuildConfig.DEBUG) DevPanel(store, Modifier.align(Alignment.TopEnd).padding(top = 76.dp, end = 16.dp).zIndex(7f))

        // ---- 위에 뜨는 창 ----
        Box(Modifier.fillMaxSize().zIndex(8f)) {
            val open = sheet
            if (open != null) {
                val card = data.card(open.tripId)
                val trip = data.trip(open.tripId)
                if (card != null && trip != null && card.state != CardState.EXPIRED) {
                    TripCardSheet(
                        trip, card, data.rewards[card.tripId], open.mode, store.suggestPurpose(card.tripId), data.customTags,
                        otherDrafts = 0,
                        onComplete = { p, t, c, o -> if (open.mode == SheetMode.EDIT) { store.editPurpose(card.tripId, p, c, t, o); sheet = null; notice = "카드를 고쳤어요" } else complete(card, p, t, c, o) },
                        customPurposes = data.customPurposes,
                        onCompleteAll = ::completeAll,
                        onLater = { sheet = null },
                    )
                } else sheet = null
            } else if (prompt != null && data.consented) {
                val trip = data.trip(prompt.tripId)
                if (trip != null) TripCardSheet(
                    trip, prompt, null, if (prompt.tripId in reminding) SheetMode.REMINDER else SheetMode.ARRIVAL,
                    store.suggestPurpose(prompt.tripId), data.customTags,
                    otherDrafts = data.draftCards.size - 1,
                    onComplete = { p, t, c, o -> complete(prompt, p, t, c, o) },
                    customPurposes = data.customPurposes,
                    onCompleteAll = ::completeAll,
                    onLater = {
                        store.later()
                        reminding = emptyList()
                    },
                )
            }
            if (sheet == null && prompt == null) data.unseenStage?.let { stage ->
                StageReveal(stage, data, onName = store::giveName, onDone = store::ackStage)
            }
            if (showBox) {
                LuckyBoxDialog(
                    data,
                    onOpen = { store.openBox(it.boxId) },
                    onOpenAll = { store.openAll() },
                    onEquip = store::equip,
                    onPaint = store::paint,
                    onClose = { showBox = false },
                )
            }
            if (showHelp) HelpDialog(onClose = { showHelp = false })
            if (showTurn) TurntableDialog(data, onClose = { showTurn = false }, onHome = { store.setHomeAngle(it); notice = if (it == null) "홈에서 기본 모습으로 보여요" else "홈에서 이 방향으로 보여요" })
            if (showShop) ShopDialog(data, onBuy = store::buy, onUseTheme = store::useTheme, onClose = { showShop = false })
            if (showInvite) InviteDialog("CAR-" + (data.trips.firstOrNull()?.tripId ?: 20261112L).toString(36).takeLast(5).uppercase(), onClose = { showInvite = false })
            val levelNow = work.integro.carling.game.LevelRules.of(data.totalGp)
            if (levelNow > data.levelSeen && data.levelSeen > 0 && !showBox && sheet == null && prompt == null && data.unseenStage == null) LevelUpDialog(data.levelSeen, levelNow, onClose = store::seenLevel)
            if (showBadges) BadgeShelfDialog(data, onPaint = { store.paint(it); notice = "${it.label}으로 칠했어요" }, onClose = { showBadges = false })
            if (data.unseenSetReward && !showBox) SetRewardDialog(
                onPaint = { store.paint(work.integro.carling.game.BadgeRules.SET_REWARD); store.seenSetReward() },
                onClose = store::seenSetReward,
            )
            // 짜잔! 달이 꽉 차서 리포트가 만들어졌다
            val newReport = data.unseenMonthReport?.let { m -> data.monthReports.firstOrNull { it.month == m } }
            if (newReport != null && sheet == null && prompt == null && data.unseenStage == null && !showBox) MonthReportDialog(
                newReport,
                onOpen = { store.seenMonthReport(); reportFocus = java.time.YearMonth.parse(newReport.month); tab = Tab.REPORT },
                onLater = store::seenMonthReport,
            )
            if (!data.consented) ConsentDialog(onAgree = store::consent)
        }
    }
}

/** 카링 탭(홈): 차에 탄 카링과 말풍선, 오른쪽에 성장·개성·수집. */
@Composable
private fun KaringTab(data: CarlingData, personality: PersonalityResult, bucket: TimeBucket, store: CarlingStore, onBox: () -> Unit, onCards: () -> Unit, onTurn: () -> Unit, onBadges: () -> Unit, onMy: () -> Unit) {
    var line by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(line) {
        if (line != null) {
            delay(3_000)
            line = null
        }
    }
    val view = data.cabin
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 차 안 배경: 화면을 꽉 채우도록 잘라 그리고, 카링은 배경 그림 속 대시보드 자리에 놓는다
        CabinBackdrop(view, data.theme ?: bucket, Modifier.fillMaxSize())
        val w = maxWidth
        val h = maxHeight
        val g = cabinGeometry(w, h, view)
        val anchorX = g.anchorX
        val anchorY = g.anchorY
        val karH = g.karH
        // 대시보드 위를 좌우로 오가며 논다: 잠깐 멈췄다가 다른 자리로 통통 굴러간다
        val wander = remember { Animatable(0f) }
        var facingRight by remember { mutableStateOf(false) }
        LaunchedEffect(view, data.shownStage) {
            while (true) {
                delay(2_200L + (Math.random() * 3_000).toLong())
                var target = (Math.random() * 2 - 1).toFloat()
                if (kotlin.math.abs(target - wander.value) < 0.35f) target = if (wander.value > 0) target - 0.6f else target + 0.6f
                target = target.coerceIn(-1f, 1f)
                // 그림은 왼쪽을 보고 있어 오른쪽으로 갈 때 뒤집는다
                facingRight = target > wander.value
                wander.animateTo(target, tween((kotlin.math.abs(target - wander.value) * 2_600).toInt() + 700, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            }
        }
        val rolling = wander.isRunning
        val hop = if (rolling) kotlin.math.abs(kotlin.math.sin(wander.value * 18f)) else 0f
        // 상태표: 차 안이 보이도록 처음엔 접힌 카드. 펼치면 전체(말풍선이 상태표 밑으로 들어가지 않게 폭을 먼저 안다)
        var expanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
        val panelW = if (expanded) 560.dp else 380.dp
        val riding = data.riding || data.shownStage.ordinal < Stage.BABY.ordinal
        // 말풍선: 카링 머리 바로 위. 오른쪽 상태표와 겹치지 않게 왼쪽으로 밀어 둔다
        val bubbleW = 560.dp
        val headY = anchorY - karH * (if (riding) 0.86f else 0.78f)
        val centerX = anchorX + g.wander * wander.value
        val maxLeft = (w - 28.dp - panelW - 24.dp - bubbleW).coerceAtLeast(16.dp)
        Box(
            Modifier.offset(x = (centerX - bubbleW / 2).coerceIn(16.dp, maxLeft), y = 0.dp).width(bubbleW).height(headY.coerceAtLeast(150.dp)),
            contentAlignment = Alignment.BottomCenter,
        ) { Speech(line ?: homeLine(data, bucket), maxWidth = bubbleW) }
        // 카링 그림은 아래 10%가 여백이라 바퀴 바닥을 자리에 맞춘다
        Box(Modifier.offset(x = anchorX - 420.dp + g.wander * wander.value, y = 0.dp).width(840.dp).height(anchorY + karH * 0.1f)) {
            Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                val angle = data.homeAngle.takeIf { data.shownStage.ordinal >= Stage.BABY.ordinal && riding }
                if (angle != null) {
                    // 360° 보기에서 고른 방향. 반대로 굴러갈 땐 거울 방향 그림으로 바꿔 숫자가 뒤집히지 않게 한다
                    Box(Modifier.graphicsLayer {
                        translationY = -hop * karH.toPx() * 0.04f + karH.toPx() * 0.08f
                        rotationZ = if (rolling) (if (facingRight) 2f else -2f) else 0f
                    }.clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                        val l = tapLines.random(); line = l; Babble.say(l)
                    }) {
                        KaringTurntable(data, karH * 1.2f, fixedStep = if (facingRight) (8 - angle) % 8 else angle)
                    }
                } else Box(Modifier.graphicsLayer {
                    scaleX = if (facingRight) -1f else 1f
                    translationY = -hop * karH.toPx() * 0.04f
                    rotationZ = if (rolling) (if (facingRight) 2f else -2f) else 0f
                }) {
                KaringView(
                    data.shownStage,
                    if (riding && data.shownStage.ordinal >= Stage.BABY.ordinal) Pose.CAR else Pose.STAND,
                    data.personality,
                    if (riding) data.equipped else emptyMap(),
                    height = if (riding) karH else karH * 0.8f,
                    carColor = data.carColor,
                    onTap = {
                        val l = tapLines.random()
                        line = l
                        Babble.say(l)
                    },
                    mirrored = facingRight,
                )
                }
            }
        }
        Row(Modifier.padding(start = 28.dp, top = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "P · 함께 출발해요",
                fontSize = 20.sp,
                color = Ink,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(50)).padding(horizontal = 20.dp, vertical = 10.dp),
            )
            if (data.shownStage.ordinal >= Stage.BABY.ordinal) Text(
                "🔄 360° 보기",
                fontSize = 18.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Mint, RoundedCornerShape(50)).clickable(onClick = onTurn).padding(horizontal = 16.dp, vertical = 9.dp),
            )
            Text(
                "🏅 배지 ${Badge.entries.count { (data.badges[it] ?: 0) > 0 }}/${Badge.entries.size}",
                fontSize = 18.sp,
                color = Color(0xFF6B4A00),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Color(0xFFFFE3A3), RoundedCornerShape(50)).clickable(onClick = onBadges).padding(horizontal = 16.dp, vertical = 9.dp),
            )
            // 배경 구도 고르기
            Row(Modifier.background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(50)).padding(4.dp)) {
                CabinView.entries.forEach { v ->
                    Text(
                        v.label,
                        fontSize = 17.sp,
                        color = if (v == view) Color.White else Ink,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(if (v == view) Lilac else Color.Transparent, RoundedCornerShape(50)).clickable { store.setCabin(v) }.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Box(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, bottom = 110.dp)) {
            if (data.unopenedBoxes.isNotEmpty()) LuckyBoxButton(data.unopenedBoxes.size, data.unopenedBoxes.firstOrNull(), onBox, Modifier.align(Alignment.BottomStart))
            if (data.draftCards.isNotEmpty()) {
                Text(
                    "🎫 완성할 카드 ${data.draftCards.size}장",
                    fontSize = 20.sp,
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 10.dp)
                        .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(50)).clickable(onClick = onCards)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(end = 28.dp)) {
            if (expanded) {
                KaringPanel(data, personality, store, onFold = { expanded = false }, onMy = onMy, modifier = Modifier.width(560.dp).height(h - 110.dp).padding(top = 78.dp, bottom = 8.dp))
            } else {
                KaringMiniCard(data, onUnfold = { expanded = true }, Modifier.width(380.dp).padding(top = 78.dp))
            }
        }
    }
}

private val tapLines = listOf("헤헤, 간지러워요!", "오늘은 어디 가요?", "같이 달리면 좋아요!", "말랑말랑~", "카드가 쌓이면 제가 달라져요!")

private fun homeLine(data: CarlingData, bucket: TimeBucket): String = when {
    data.unopenedBoxes.isNotEmpty() -> "럭키박스가 도착했어요! 열어 볼래요?"
    data.draftCards.isNotEmpty() -> "아직 완성하지 않은 여정이 있어요."
    data.stage == Stage.EGG -> "함께 달리면 알이 깨어나요."
    bucket == TimeBucket.MORNING -> "좋은 아침! 오늘도 함께 출발할까요?"
    bucket == TimeBucket.NIGHT -> "오늘도 수고했어요. 조심히 가요."
    else -> "오늘도 함께 출발할까요?"
}

/** 오른쪽 정보: 단계·성장 바, 개성 3축과 근거, 자동차 꾸미기, 수집 배지. */
@Composable
private fun KaringPanel(data: CarlingData, personality: PersonalityResult, store: CarlingStore, onFold: () -> Unit, modifier: Modifier, onMy: () -> Unit = {}) {
    Panel(modifier, padding = 24.dp) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(karingName(data.name, data.stage), fontSize = 30.sp, color = Ink, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                LevelEmblem(work.integro.carling.game.LevelRules.of(data.totalGp), 60.dp)
                Spacer(Modifier.width(8.dp))
                if (data.name != null) Text(data.stage.label, fontSize = 19.sp, color = Lilac, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${"%,d".format(data.totalGp)}점", fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                FoldButton("접기 ▴", onFold)
            }
            Text(data.stage.line, fontSize = 17.sp, color = Muted)
            Spacer(Modifier.height(12.dp))
            GrowthBar(data)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                Stage.entries.filter { it != Stage.CRACKED }.forEach { s ->
                    val pickable = data.canChooseLook && s.ordinal in Stage.BABY.ordinal..data.stage.ordinal
                    val shown = s == data.shownStage
                    Column(
                        Modifier
                            .background(if (shown && data.canChooseLook) MintSoft else Color.Transparent, RoundedCornerShape(12.dp))
                            .border(2.dp, if (shown && data.canChooseLook) Mint else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable(enabled = pickable) { store.chooseLook(s) }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        StageThumb(s, 54.dp, dim = s.ordinal > data.stage.ordinal)
                        Text(s.label, fontSize = 13.sp, color = if (s == data.stage || (s == Stage.EGG && data.stage == Stage.CRACKED) || shown) Ink else Faint)
                        if (!s.open) Text("추후 공개", fontSize = 11.sp, color = Coral, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (data.canChooseLook) Text("지나온 모습을 눌러 카링의 모습을 바꿀 수 있어요", fontSize = 14.sp, color = Faint)

            Divider()
            Text("개성 · 최근 저장 카드 ${personality.sampleSize}장", fontSize = 18.sp, color = Muted, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val p = data.personality
            Axis(Color(p.vibe?.color ?: p.body.color), "낮길·밤길", p.vibe?.let { "${it.icon} ${it.label} · ${it.eyewear}" } ?: "기본", personality.bodyReason())
            Axis(null, "대표 장식", p.decorationItem?.let { "${it.icon} ${it.label}" } ?: "없음", personality.decorationReason())
            Axis(if (p.heartBright) Mint else MintSoft, "심장빛", if (p.heartBright) "선명하게 반짝" else "기본", personality.heartReason())
            Spacer(Modifier.height(8.dp))
            PersonalityCatalog(p)

            Divider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("꾸미기 ${data.parts.size}/${CarPart.entries.size} · 색 ${data.colors.size + 1}/${CarColor.entries.size} · 배지 ${data.badges.size}/${Badge.entries.size}", fontSize = 18.sp, color = Muted, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                data.style?.let { Text("${it.icon} ${it.label}", fontSize = 17.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.background(Mint, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp)) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CarSlot.entries.forEach { slot ->
                    val part = data.equipped[slot]
                    Box(Modifier.size(70.dp).background(if (part != null) LilacSoft else Paper, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        if (part != null) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(partRes(part)), part.label, Modifier.size(56.dp))
                        else Text(slot.label.take(2), fontSize = 14.sp, color = Faint)
                    }
                }
                Box(Modifier.size(46.dp).background(Color((data.carColor ?: CarColor.PEARL).light), CircleShape).border(2.dp, Line, CircleShape))
                data.displayBadge?.let { Medal(it, work.integro.carling.game.BadgeTier.of(data.badges[it] ?: 0), 70.dp) }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "💜 내 카링에서 꾸미기 →",
                fontSize = 19.sp, color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Lilac, RoundedCornerShape(50)).clickable(onClick = onMy).padding(horizontal = 18.dp, vertical = 9.dp),
            )
        }
    }
}

/** 개성 도감: 나올 수 있는 몸 분위기 2가지와 대표 장식 전부. 지금 것에 테두리. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PersonalityCatalog(p: work.integro.carling.game.Personality) {
    Text("개성 종류", fontSize = 16.sp, color = Muted, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        work.integro.carling.game.Vibe.entries.forEach { v ->
            CatalogChip("${v.icon} ${v.label} · ${v.eyewear}", v.buckets.joinToString("·") { it.label } + " 출발이 많으면", p.vibe == v, Color(v.color))
        }
    }
    Spacer(Modifier.height(8.dp))
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        work.integro.carling.game.Decoration.entries.forEach { d ->
            CatalogChip("${d.icon} ${d.label}", d.purposes(), p.decorationItem == d, Lilac)
        }
    }
}

@Composable
private fun CatalogChip(title: String, sub: String, on: Boolean, accent: Color) {
    Column(
        Modifier.background(if (on) accent.copy(alpha = 0.18f) else Paper, RoundedCornerShape(14.dp))
            .border(2.dp, if (on) accent else Color.Transparent, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(title, fontSize = 16.sp, color = Ink, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
        Text(sub, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun Divider() = Box(Modifier.padding(vertical = 16.dp).fillMaxWidth().height(1.dp).background(Line))

@Composable
private fun Axis(dot: Color?, title: String, value: String, reason: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(18.dp).background(dot ?: Color.Transparent, CircleShape).border(1.dp, Line, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 17.sp, color = Muted)
                Spacer(Modifier.width(10.dp))
                Text(value, fontSize = 21.sp, color = Ink, fontWeight = FontWeight.Bold)
            }
            Text(reason, fontSize = 16.sp, color = Muted)
        }
    }
}

@Composable
private fun <T> Grid(items: List<T>, columns: Int, cell: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { Box(Modifier.weight(1f)) { cell(it) } }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Slot(icon: String, label: String, owned: Boolean, selected: Boolean, count: Int = 0, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MintSoft else if (owned) Paper else Color(0xFFF3F3F8), RoundedCornerShape(16.dp))
            .border(2.dp, if (selected) Mint else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(enabled = owned, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (owned) icon else "？", fontSize = 28.sp, color = if (owned) Color.Unspecified else Faint)
        Text(if (owned) label + if (count > 1) " ×$count" else "" else "???", fontSize = 14.sp, color = if (owned) Ink else Faint, maxLines = 1)
    }
}

@Composable
private fun TabBar(tab: Tab, drafts: Int, onTab: (Tab) -> Unit, modifier: Modifier) {
    Row(
        modifier
            .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(36.dp))
            .border(1.dp, Line, RoundedCornerShape(36.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Tab.entries.forEach { t ->
            val on = t == tab
            Box {
                Row(
                    Modifier
                        .background(if (on) Lilac else Color.Transparent, RoundedCornerShape(28.dp))
                        .clickable(remember { MutableInteractionSource() }, indication = null) { onTab(t) }
                        .padding(horizontal = 34.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t.icon, fontSize = 24.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(t.label, fontSize = 22.sp, color = if (on) Color.White else Ink, fontWeight = FontWeight.Bold)
                }
                if (t == Tab.CARDS && drafts > 0) {
                    Text(
                        "$drafts",
                        fontSize = 15.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).background(Coral, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
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

/** 단계 변화 연출. 알에서 젤리링으로 깨어나면 이름을 지어준다(11/13 사용자 결정). */
@Composable
private fun StageReveal(stage: Stage, data: CarlingData, onName: (String) -> Unit, onDone: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val askName = stage == Stage.CRACKED && data.name == null
    // 시연 영상 녹화용(디버그): adb 명령으로 받은 이름을 한 글자씩 입력한다
    val typing = work.integro.carling.game.DevUi.typeName
    LaunchedEffect(typing) {
        if (BuildConfig.DEBUG && typing != null) {
            text = ""
            typing.forEach { c -> delay(260); text += c }
            work.integro.carling.game.DevUi.typeName = null
        }
    }
    FullScrim {
        Panel(Modifier.widthIn(max = 900.dp), padding = 32.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (stage == Stage.CRACKED) "알에서 젤리링이 깨어났어요!" else "카링이 ${stage.label}(으)로 자랐어요!", fontSize = 32.sp, color = Ink, fontWeight = FontWeight.Bold)
                Text(stage.line, fontSize = 20.sp, color = Muted)
                Spacer(Modifier.height(10.dp))
                KaringView(stage, if (stage.ordinal >= Stage.BABY.ordinal) Pose.CAR else Pose.STAND, data.personality, data.equipped, height = 380.dp, carColor = data.carColor)
                Spacer(Modifier.height(10.dp))
                if (askName) {
                    Text("안녕! 깨워줘서 고마워요. 제 이름을 지어줄래요?", fontSize = 22.sp, color = Ink)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it.take(10) },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 26.sp, color = Ink),
                            placeholder = { Text("이름 (최대 10자)", fontSize = 22.sp) },
                            modifier = Modifier.background(Color.White).width(400.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Button(onClick = { onName(text); onDone() }, enabled = text.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Lilac), modifier = Modifier.height(68.dp)) { Text("정했어요", fontSize = 24.sp) }
                    }
                    OutlinedButton(onClick = onDone, modifier = Modifier.padding(top = 10.dp)) { Text("나중에", fontSize = 18.sp) }
                } else {
                    Button(onClick = onDone, colors = ButtonDefaults.buttonColors(containerColor = Lilac), modifier = Modifier.height(68.dp)) { Text("좋아요!", fontSize = 24.sp) }
                }
            }
        }
    }
}

/** 최초 실행: 수집 항목과 목적을 안내하고 동의를 받는다(FR-15 최소). */
@Composable
private fun ConsentDialog(onAgree: () -> Unit) {
    FullScrim {
        Panel(Modifier.widthIn(max = 940.dp), padding = 34.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KaringView(Stage.EGG, Pose.STAND, work.integro.carling.game.Personality(), emptyMap(), height = 300.dp)
                Spacer(Modifier.width(26.dp))
                Column {
                    Text("마이카링에 오신 걸 환영해요", fontSize = 32.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text("매일의 운행이 트립카드로 남고, 완성한 카드가 카링의 성장과 운행 리포트로 돌아와요.", fontSize = 21.sp, color = Ink, lineHeight = 30.sp)
                    Spacer(Modifier.height(14.dp))
                    Text("기록하는 것", fontSize = 18.sp, color = Muted, fontWeight = FontWeight.Bold)
                    Text("· 기어 상태와 차량 속도: 운행 시작·종료, 이동시간, 운전점수\n· 누적 주행거리: 운행 거리\n· 카드에 고른 운행 목적과 태그", fontSize = 19.sp, color = Ink, lineHeight = 28.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("기록하지 않는 것: 위치와 경로(GPS). 모든 기록은 이 차 안에만 저장돼요.", fontSize = 18.sp, color = Muted)
                    Text("권한을 거부한 항목은 '미확인'으로 두고 나머지 기능은 그대로 쓸 수 있어요.", fontSize = 18.sp, color = Muted)
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = onAgree, colors = ButtonDefaults.buttonColors(containerColor = Lilac), modifier = Modifier.height(72.dp)) { Text("동의하고 시작하기", fontSize = 25.sp) }
                }
            }
        }
    }
}

private val HelpSections = listOf(
    Triple("🚗", "운행 기록", "P가 아닌 기어로 바꾸면 기록이 시작돼요. 경로와 좌표는 기록하지 않아요. 운행 기록은 카드와 상관없이 모두 남아 리포트에 쓰여요."),
    Triple("🎫", "트립카드", "1km 이상, 3분 넘게 달리고 P로 바꾸면 새 카드가 뒤집히며 도착해요. 목적(출근·퇴근·업무·식사·장보기·여행·데이트·운동·취미·기타) 하나를 고르면 그 풍경이 카드에 담기고 '완성하기'로 끝나요. 누구랑(혼자·가족과·친구와·연인과·기타)과 태그는 선택이에요. '기타'를 고르면 '＋ 목적 적기'로 병원·아이 픽업처럼 목적을 직접 적을 수 있고, 한 번 적은 목적은 다음부터 칩으로 나와요. 완성한 카드는 눌러서(✏️) 목적·누구랑·태그를 고칠 수 있어요."),
    Triple("🗂️", "트립카드 앨범", "트립카드 탭은 기간(주·월·분기·년)과 ‹ › 로 앞 기간을 골라 보고, 주는 날짜별·월은 주별·분기와 년은 월별로 묶어 5장씩 보여 줘요. 위쪽에서 임시·완성·만료로 거르고, 임시 카드를 한 번에 완성할 수 있어요."),
    Triple("🧭", "트립카드 요약", "앨범 위에는 그 기간의 한 줄 요약(평일·주말 대표 루틴과 생활반경)과 왜(목적 비중)·언제(요일×시간대)·어디(자주 간 동네 3곳, 위치를 켰을 때)·누구랑, 생활반경(지난 기간 대비·새 동네), 루틴(같은 목적·주중/주말·시간대 3번 이상)과 특별한 여정(가장 먼 여정·처음 간 곳·반짝·특별 카드)이 나와요. 목적이나 루틴을 누르면 그 카드만 보여요."),
    Triple("🔎", "카드 상세보기", "완성한 카드를 누르면 상세보기가 열려요: 큰 카드, 출발 → 도착 동네, 거리·시간, 운전점수·급조작, 전비(연비), 같은 목적 평균과 비교, 이 여정이 몇 번째인지, 보상. '✏️ 고치기'로 목적·누구랑·태그를 고치고, '같은 목적 카드 보기'로 앨범을 그 목적만 걸러요. 임시 카드는 바로 완성 시트가 열려요."),
    Triple("✨", "카드 등급", "운전점수 85점 이상이면 민트빛으로 반짝이는 카드, 첫 카드나 100km 넘는 장거리는 금빛 '특별' 카드가 나와요. 보상은 같고 모습만 달라요."),
    Triple("⏱️", "5분 규칙", "P로 바꾸고 5분 안에 다시 출발하면 같은 여정으로 이어져요. P가 5분 이어지면 운행이 확정돼요. 앱이 꺼져 있었어도 다음에 켤 때 같은 규칙으로 판정해요."),
    Triple("🎁", "보상", "운행이 확정되고 카드가 완성되면 성장 포인트(10 + km + 분, 최대 150)와 럭키박스 1개가 와요. 같은 운행에는 한 번만. 럭키박스는 하루 5개까지예요."),
    Triple("🏅", "배지 진열장", "배지는 럭키박스에서 그 여정에 어울리는 것으로 나와요: 아침 출발은 아침 햇살, 밤은 밤하늘 별, 출퇴근은 출근길 커피, 주말은 주말 나들이, 같은 목적 3번 넘게는 단골길, 운전점수 85+는 민트 하트. 같은 배지를 더 받으면 동 → 은(3개) → 금(5개)으로 빛나고, 6종을 모두 모으면 오로라 차체 색을 받아요. 카링 탭 위쪽 '🏅 배지'에서 진열장을 볼 수 있어요."),
    Triple("🧩", "럭키박스", "금빛 모서리의 남보라 상자예요. 리본 색이 내용물 힌트예요. 모두 받기는 상자가 많아도 1초 안에 열려요. 보너스 성장 포인트 5~30점이 들어 있고, 수집 배지(6종)나 자동차 꾸미기(12종)가 함께 나오기도 해요. 상자 빛 색이 힌트예요: 라벤더는 포인트, 금빛은 배지, 민트는 자동차 꾸미기. 상자가 2개 이상이면 '모두 받기'로 한 번에 열 수 있어요."),
    Triple("🚗", "자동차 꾸미기", "휠캡·안테나·뒤쪽 소품 세 자리에 하나씩 달 수 있어요. 꾸미기를 받으면 '바로 장착'이나 '나중에'를 고르고, 달면 확대한 모습으로 보여줘요. 모두 받기로 받은 새 꾸미기는 비어 있는 자리에 바로 달려요. 카링 탭에서 눌러 바꾸거나 뗄 수 있어요."),
    Triple("🚘", "차 안 배경", "카링 탭과 주행 화면은 차 안 풍경이에요. 위쪽에서 '대시보드'와 '운전석 시점' 중 고를 수 있고, 아침·낮·저녁·밤에 따라 창밖 하늘과 실내 빛이 바뀌어요. 주차 중엔 카링이 대시보드 위를 좌우로 오가고, 달릴 땐 달리는 모습으로 동동 떠다녀요."),
    Triple("🪄", "모습 바꾸기", "꼬마링이 되면 상태표의 단계 그림을 눌러 지나온 모습(아기링·꼬마링) 중 하나로 바꿀 수 있어요. 목소리도 그 모습에 맞춰 바뀌어요."),
    Triple("🎨", "차체 색", "럭키박스에서 차체 색 7가지(민트·벚꽃 핑크·하늘·레몬·라벤더·코랄·밤하늘 네이비)를 모을 수 있어요. 받으면 '바로 칠하기'를 고르거나, 카링 탭에서 언제든 골라 칠해요. 기본은 펄 화이트예요."),
    Triple("🎀", "장착 스타일", "같은 테마 세 개를 맞추면 스타일이 완성돼요: 새싹 드라이브(새싹·클로버·피크닉 바구니), 주말 여행자(별·데이지·여행가방), 별빛 탐험가(행성·별사탕·캠핑 배낭), 구름 산책(하트·바람개비·서핑보드)."),
    Triple("🔄", "360° 보기", "카링 탭 위쪽 '360° 보기'를 누르면 카링의 차를 8방향으로 돌려 볼 수 있어요. 좌우로 끌거나 저절로 돌게 두면, 단 안테나·뒤쪽 소품·휠캡과 차체 색을 앞·옆·뒤에서 모두 볼 수 있어요."),
    Triple("⛽", "연비와 주유", "주차할 때마다 차량의 전비(연비)를 함께 남겨요. 출발할 때 남은 양이 직전 주차보다 5%p 이상 늘었으면 그 사이 충전(주유)한 것으로 세요. 운행 리포트에서 목적·시간대·거리별 비교와 충전 횟수를 볼 수 있어요."),
    Triple("🔊", "카링 목소리", "카링이 자라면 목소리도 자라요. 젤리링은 말랑한 옹알이, 아기링은 바람 섞인 속삭임, 꼬마링은 맑은 종소리 같은 목소리예요. 운행을 시작하고 마칠 때는 차량 음성으로 한마디 해요."),
    Triple("📍", "내 활동 반경", "운행 리포트 ⑤에서 1회 거리를 5·10·30·50km 고리로 나눠 보여 주고, 운행의 80%가 들어오는 '보통 거리'를 알려 줘요. 리포트의 '위치 켜기'로 동의하면 출발·도착한 동네 이름(행정동)만 이 차에 남겨, 집 동네(가장 많이 출발한 곳)를 가운데 둔 동네 지도와 활동 반경·다녀온 동네·처음 가 본 동네·자주 간 곳 5곳을 보여 줘요. 좌표와 경로는 저장하지 않고, '위치 끄기'를 누르면 동네 기록도 지워요."),
    Triple("💡", "꼭 알아야 할 3가지", "리포트는 기간이 꽉 차야 만들어져요. 이번 달은 '준비 중'으로 보이고, 한 달이 꽉 차면 카링이 '짜잔! 축하해요' 하고 알려 줘요. '리포트 보기'를 누르면 그달 리포트가 열리고 저장돼요. 맨 위 세 장은 출발 시각·습관→돈·비용·안전·생활 중 내 기록에서 찾은 것으로, 돈이나 시간 숫자와 할 일 하나를 담아요. 주 단위는 운행할 때마다 틈틈이 업데이트돼요."),
    Triple("🏷️", "생활반경 혜택", "운행 리포트 '꼭 알아야 할 3가지' 아래에서 선택해 켜면, 내 동네와 운행 습관에 맞는 혜택을 기간마다 1장 보여 줘요: 차량 상태에 살펴볼 곳이 있으면 정비 점검, 충전(주유)이 잦으면 충전·주유 할인, 같은 동네로 식사·장보기가 잦으면 주차, 장거리 뒤엔 세차, 운동·식사가 잦으면 생활 혜택. 위치와 기록은 차 안에서만 쓰고, 주행 중엔 보이지 않아요. 닫으면 30일 동안 숨고, '혜택 끄기'로 끌 수 있어요. 지금은 모두 예시 제휴(가상)예요."),
    Triple("📅", "리포트 기간", "리포트는 주·월·분기·년 단위로 보고 ‹ › 로 앞 기간으로 넘어가요. 분기는 주별, 년은 달별 막대로 묶어요."),
    Triple("📈", "운전 개선·절약", "운행 리포트 ⑧은 같은 단위의 최근 6개 기간을 나란히 놓고 운전점수·급조작(100km당)·효율·부드러운 운행 비율이 좋아졌는지 보여 줘요. ⑥ 효율 아래에는 같은 거리를 앞 기간 효율로 달렸을 때와 비교해 아낀(더 쓴) 금액이 나와요. 단가는 전기 kWh당 324원·휘발유 L당 1,650원으로 시작하고 －/＋로 바꿀 수 있어요."),
    Triple("🚘", "차량 상태", "운행 리포트 ⑨는 리포트를 열 때 차에서 타이어 공기압(4바퀴), 엔진오일·브레이크액, 워셔액, 남은 주행 가능 거리, 켜진 경고등을 읽어 보여 줘요. 차량이 주지 않는 값은 —로 나와요."),
    Triple("📝", "운행일지", "운행 리포트 위쪽 '운행일지 만들기'를 누르면 기간 요약·개선·절약·차량 상태·운행 목록이 담긴 일지가 나와요. 받을 메일 주소를 넣고 'QR 만들기'를 누른 뒤 폰 카메라로 찍으면 내 주소·제목·본문이 채워진 메일 쓰기 화면이 열려요. 보내기만 누르면 돼요. 주소는 이 차 안에만 저장해요."),
    Triple("🏅", "레벨과 엠블럼", "성장 포인트로 LV.1~50까지 올라요(처음엔 빨리, 갈수록 천천히). 레벨 엠블럼은 오른쪽 위 상태표의 이름 옆에 있고, 10레벨마다 새싹 → 동 → 은 → 금 → 오로라로 화려해져요. 레벨이 오르면 젤리 50개를 받아요."),
    Triple("🔄", "홈 방향", "360° 보기에서 원하는 방향으로 돌린 뒤 '🏠 이 방향으로 홈에 두기'를 누르면 홈에서 카링이 그 방향으로 있어요. '기본 모습으로'를 누르면 처음 그림으로 돌아가요."),
    Triple("🍬", "젤리와 상점", "젤리는 카드 보상(15 + 5km마다 1, 최대 40), 럭키박스 보너스, 이미 가진 배지·꾸미기·색이 또 나올 때(20), 레벨업(50)으로 모여요. 홈 위쪽 '🍬 상점'에서 자동차 꾸미기(300)·차체 색(400)·배경 테마(500, 차 안 배경을 아침·낮·저녁·밤 중 하나로 고정)를 살 수 있어요. 써도 성장 포인트는 줄지 않아요."),
    Triple("👥", "친구 초대", "친구를 초대하면, 둘이 같은 시간에 운전하고 있을 때 서로 어디쯤 달리고 있는지 볼 수 있어요. 둘 다 운전 중일 때만 위치를 주고받고 주차하면 멈춰요. 지금은 홈 위쪽 👥에서 초대 코드만 볼 수 있고, 초대하기는 곧 열려요."),
    Triple("💜", "내 카링", "'차 내리기'를 고르면 홈에서도 차에서 내린 모습으로 보여요. 하단 '내 카링'은 카링을 꾸미는 곳이에요. 왼쪽 슬롯 6칸(안테나·휠캡·뒤쪽 소품·차체 색·대표 배지·모습)을 누르면 오른쪽 보관함이 그 칸으로 바뀌고, 고르면 가운데에서 미리 본 뒤 '장착'으로 바꿔요. '해제'는 그 칸만, '모두 해제'는 꾸미기와 색을 모두 떼요. '차 내리기'로 서 있는 카링, '장착 전 보기'로 아무것도 달지 않은 모습을 볼 수 있고, 미리보기는 끌어서 360°로 돌아가요."),
    Triple("🗣️", "주행 한마디", "달리는 동안 카링이 내비게이션처럼 짧게 말해요: 급조작엔 '천천히 가 줘', 배터리 20% 아래면 '배고파 밥 줘', 시속 110km 넘으면 '너무 빨라', 2시간 넘게 달리면 '멀미 나 쉬어 가자', 밤 0~5시엔 '졸리면 말해 줘', 30분 부드럽게 달리면 칭찬, 버디링(추후 공개)은 50km마다 축하. 한마디 사이는 최소 5분, 같은 말은 한 운행에 한 번이고, 단계가 자랄수록 할 줄 아는 말이 늘어요. 화면엔 말하는 동안만 말풍선이 떠요."),
    Triple("📊", "리포트 두 가지", "운행 리포트는 핵심 발견, 운행 현황 8개 숫자와 증감, 날짜·요일별 거리, 가장 긴 여정, 목적별 운행, 출발 시간대, 요일×시간대 표, 거리 분포, 연속 운전일·카드 완성률·운전점수를 보여줘요. 성장 리포트는 ① 지난 기간과 비교해 카링이 어떻게 바뀌었나(단계·포인트·몸 색·장식·카드·수집·한마디) ② 얼마나 똑똑해졌나(내가 고른 목적을 미리 맞힌 비율, 알게 된 습관과 NEW) ③ 어떤 기능이 새로 열렸나(다음에 열릴 것과 여는 방법)를 먼저 보여 주고, 모은 포인트, 자라온 길 그래프, 단계 기록과 다음 단계 예상일, 개성과 그 근거가 된 최근 10장, 개성 변화 기록, 수집과 럭키박스 통계를 보여줘요."),
    Triple("⏰", "미완성 카드", "확정 후에도 완성하지 않은 카드는 7일 안에 다시 시동을 걸면 한 번 더 알려줘요. 그때부터 24시간이 지나면 만료돼요. 만료돼도 운행 기록은 리포트에 남아요."),
    Triple("🌱", "성장 단계", "알 → 젤리링(첫 보상, 말랑한 아기 모습, 이때 이름을 지어 줘요) → 아기링(1,500점, 날개 모습) → 꼬마링(3,500점, 불꽃 머리칼 모습). 그다음 버디링·소울링은 추후 공개돼요(포인트는 계속 쌓여요). 아기링부터 차에 타고 꾸밀 수 있어요."),
    Triple("🎨", "개성", "최근 완성한 카드 10장으로 정해져요. 아침·낮 출발이 많으면 낮길형: 이마에 드라이빙 고글을 올려 쓰고 햇살 테두리가 생겨요. 저녁·밤 출발이 많으면 밤길형: 이마에 헤드램프를 차고 반딧불 셋이 머리 위를 돌아요. 가장 많이 고른 목적은 대표 장식(출근·퇴근 넥타이, 업무 업무 배지, 데이트 하트 핀, 여행 작은 배낭, 취미 음표 핀, 식사 숟가락 핀, 장보기 장바구니, 운동 스포츠 밴드)이 되고, 평균 운전점수 85점 이상이면 심장이 선명하게 반짝여요. 상태표를 펼치면 개성 종류를 모두 볼 수 있어요."),
    Triple("🛡️", "주행 중", "운행 중에는 달리는 카링과 거리·시간만 보여주고, 카드·럭키박스·리포트는 주차 후에 열려요."),
)

/** 달이 꽉 차면 뜨는 '짜잔' 알림: 리포트가 만들어지고 저장됐다는 것과 첫 카드 한 줄. */
@Composable
private fun MonthReportDialog(r: work.integro.carling.data.MonthReport, onOpen: () -> Unit, onLater: () -> Unit) {
    val ym = java.time.YearMonth.parse(r.month)
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "tada")
    val bounce by t.animateFloat(0.92f, 1.08f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "bounce")
    FullScrim {
        Box(contentAlignment = Alignment.Center) {
            SparkleBurst(r.createdAt, listOf(Color(0xFFFFD27A), Color(0xFF8CFFD6), Color(0xFFC7B8FF), Color.White), Modifier.size(900.dp, 600.dp), count = 80)
            Panel(Modifier.widthIn(max = 860.dp), padding = 34.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉", fontSize = 70.sp, modifier = Modifier.graphicsLayer { scaleX = bounce; scaleY = bounce })
                    Text("짜잔! 축하해요", fontSize = 36.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Text("${ym.monthValue}월이 꽉 찼어요. ${ym.monthValue}월 리포트가 만들어졌어요!", fontSize = 23.sp, color = Ink)
                    Text("운행 ${r.trips}번 · ${"%,.0f".format(r.km)}km · ${r.minutes / 60}시간 ${r.minutes % 60}분 · 리포트에 저장됐어요", fontSize = 18.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
                    r.insights.firstOrNull { it.kind != work.integro.carling.game.InsightKind.UPCOMING }?.let { i ->
                        Column(Modifier.padding(top = 18.dp).fillMaxWidth().background(LilacSoft, RoundedCornerShape(20.dp)).padding(18.dp)) {
                            Text("${i.kind.icon} ${i.title}", fontSize = 19.sp, color = Ink, fontWeight = FontWeight.Bold)
                            Text(i.big, fontSize = 26.sp, color = Lilac, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        OutlinedButton(onClick = onLater, modifier = Modifier.height(64.dp)) { Text("나중에", fontSize = 21.sp) }
                        Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = Lilac), modifier = Modifier.height(64.dp).widthIn(min = 240.dp)) { Text("리포트 보기", fontSize = 23.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpDialog(onClose: () -> Unit) {
    FullScrim {
        Panel(Modifier.widthIn(max = 980.dp), padding = 30.dp) {
            Column {
                Text("마이카링 도움말", fontSize = 30.sp, color = Ink, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Column(Modifier.height(560.dp).verticalScroll(rememberScrollState())) {
                    HelpSections.forEach { (icon, title, body) ->
                        Row(Modifier.padding(vertical = 8.dp)) {
                            Text(icon, fontSize = 30.sp)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(title, fontSize = 23.sp, color = Ink, fontWeight = FontWeight.Bold)
                                Text(body, fontSize = 20.sp, color = Muted, lineHeight = 29.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Lilac), modifier = Modifier.align(Alignment.End).height(64.dp)) { Text("닫기", fontSize = 23.sp) }
            }
        }
    }
}

@Composable
private fun DevPanel(store: CarlingStore, modifier: Modifier) {
    Column(
        modifier
            .width(320.dp)
            .background(Color.White, RoundedCornerShape(20.dp))
            .border(1.dp, Line, RoundedCornerShape(20.dp))
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("🎬 60초 시연", fontSize = 17.sp, color = Lilac, fontWeight = FontWeight.Bold)
        var demoReady by remember { mutableStateOf(work.integro.carling.game.DemoDrive.armed) }
        DevButton("시연 준비 (처음부터)") { DevSimulator.prepareDemo(store); demoReady = true }
        Text(
            (if (demoReady && work.integro.carling.game.DemoDrive.armed) "준비됨 · 이번 달 마지막 평일 아침 7:48\n" else "") +
                "① 기어 D → 6초 뒤 \"천천히 가 줘\"\n② 기어 P → 카드 도착(출근 38.6km·41분)\n③ '출근' 완성 → 🎁 럭키박스\n④ 아래 '한 달 마감' → 짜잔! → 리포트 보기\n⑤ 성장 리포트 → 똑똑함·출근 시간",
            fontSize = 14.sp, color = Muted, lineHeight = 20.sp,
        )
        DevButton("한 달 마감 (다음 달 1일로) → 짜잔") { DevSimulator.closeMonth(store) }
        DevButton("배경 톤: ${store.data.cabinTone.label} → 다음") { store.setCabinTone(work.integro.carling.game.CabinTone.entries[(store.data.cabinTone.ordinal + 1) % work.integro.carling.game.CabinTone.entries.size]) }
        Spacer(Modifier.height(4.dp))
        Text("시뮬레이터 (디버그 전용)", fontSize = 17.sp, color = Lilac, fontWeight = FontWeight.Bold)
        DevButton("지금 주차한 운행 18km") { DevSimulator.parkNow(store) }
        DevButton("짧은 운행 0.6km") { DevSimulator.shortTrip(store) }
        DevButton("5분 지나기 (확정)") { DevSimulator.skip(store, Rules.STOP_MERGE_MILLIS) }
        DevButton("24시간 지나기") { DevSimulator.skip(store, Rules.DAY_MILLIS) }
        DevButton("6시간 지나기 (시간대 바꾸기)") { DevSimulator.skip(store, 6 * Rules.HOUR_MILLIS) }
        DevButton("7일 지나기") { DevSimulator.skip(store, 7 * Rules.DAY_MILLIS) }
        DevButton("다시 시동 (재안내)") { store.markIgnition() }
        DevButton("아침 출퇴근 9장") { DevSimulator.morningCommutes(store) }
        DevButton("밤 외근 6장") { DevSimulator.nightFieldTrips(store) }
        DevButton("주말 식사·장보기 3장") { DevSimulator.weekendFamily(store) }
        DevButton("동네 나들이 20장 + 위치 켜기") { DevSimulator.neighborhoodTrips(store) }
        DevButton("출근 시간 실험 16장 + 장보기 4장") { DevSimulator.commuteExperiment(store) }
        DevButton("배지 상자 6개") { store.devBadgeBoxes() }
        DevButton("젤리 +1,000") { store.devAddJelly(1_000) }
        val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as? work.integro.carling.CarlingApplication
        var talkIdx by remember { mutableStateOf(0) }
        DevButton("주행 한마디 미리 듣기 (${work.integro.carling.game.Talk.entries[talkIdx].label})") {
            app?.tracker?.talk(work.integro.carling.game.Talk.entries[talkIdx])
            talkIdx = (talkIdx + 1) % work.integro.carling.game.Talk.entries.size
        }
        DevButton("충전 기록 30일") { store.devRefuels() }
        DevButton("포인트 +1,000") { store.devAddGp(1_000) }
        DevButton("꾸미기 상자 6개") { store.devPartBoxes() }
        DevButton("처음부터 다시") { store.devReset(); work.integro.carling.game.DemoDrive.armed = false }
    }
}

@Composable
private fun DevButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(label, fontSize = 17.sp) }
}

/** 성장(에너지) 바와 다음 단계까지 남은 포인트. */
@Composable
internal fun GrowthBar(data: CarlingData, compact: Boolean = false) {
    val next = data.stage.next
    if (next != null && !next.open) {
        Text("다음 단계 ${next.label}·${Stage.SOUL.label}은 추후 공개돼요 · 포인트는 계속 쌓여요", fontSize = if (compact) 15.sp else 17.sp, color = Muted)
    } else if (next != null) {
        val from = if (data.stage.ordinal <= Stage.CRACKED.ordinal) 0 else data.stage.minGp
        val target = if (data.stage == Stage.EGG) 1 else next.minGp
        val progress = if (data.stage == Stage.EGG) 0f else ((data.totalGp - from).toFloat() / (target - from).coerceAtLeast(1)).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(if (compact) 14.dp else 16.dp).background(LilacSoft, RoundedCornerShape(8.dp))) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Lilac, Mint)), RoundedCornerShape(8.dp)))
        }
        Text(
            if (data.stage == Stage.EGG) "첫 카드를 완성하고 운행이 확정되면 알에 금이 가요" else "${next.label}까지 ${"%,d".format((next.minGp - data.totalGp).coerceAtLeast(0))}점 · ${(progress * 100).toInt()}%",
            fontSize = if (compact) 15.sp else 17.sp,
            color = Muted,
            modifier = Modifier.padding(top = 6.dp),
        )
    } else Text("지금 공개된 마지막 단계예요. 다음 차체는 추후 공개!", fontSize = if (compact) 15.sp else 17.sp, color = Muted)
}

@Composable
private fun FoldButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 16.sp,
        color = Lilac,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.background(LilacSoft, RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** 접힌 상태표: 이름·단계·포인트, 에너지 바, 개성 3축 아이콘만. 누르면 펼친다. */
@Composable
private fun KaringMiniCard(data: CarlingData, onUnfold: () -> Unit, modifier: Modifier) {
    Panel(modifier.clickable(onClick = onUnfold), padding = 20.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StageThumb(data.shownStage, 44.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(karingName(data.name, data.stage), fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1)
                        Spacer(Modifier.width(8.dp))
                        LevelEmblem(work.integro.carling.game.LevelRules.of(data.totalGp), 40.dp)
                    }
                    Text("${data.stage.label} · ${"%,d".format(data.totalGp)}점", fontSize = 15.sp, color = Muted)
                }
                FoldButton("펼치기 ▾", onUnfold)
            }
            Spacer(Modifier.height(12.dp))
            GrowthBar(data, compact = true)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val p = data.personality
                Box(Modifier.size(16.dp).background(Color(p.vibe?.color ?: p.body.color), CircleShape))
                Text(p.vibe?.let { "${it.icon} ${it.label}" } ?: "기본", fontSize = 15.sp, color = Ink)
                p.decorationItem?.let { Text("· ${it.icon} ${it.label}", fontSize = 15.sp, color = Ink) }
                if (p.heartBright) Text("· 💚", fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                if (data.unopenedBoxes.isNotEmpty()) Text("🎁 ${data.unopenedBoxes.size}", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** 자동차 꾸미기 한 칸: 그림, 이름, 개수. 달려 있으면 민트 테두리. */
@Composable
private fun PartSlot(part: CarPart, count: Int, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MintSoft else if (count > 0) Paper else Color(0xFFF3F3F8), RoundedCornerShape(16.dp))
            .border(2.dp, if (selected) Mint else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(enabled = count > 0, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(partRes(part)),
            contentDescription = part.label,
            modifier = Modifier.size(48.dp).graphicsLayer { alpha = if (count > 0) 1f else 0.18f },
        )
        Text(if (count > 0) part.label.removeSuffix(" 휠캡").removeSuffix(" 안테나") + if (count > 1) " ×$count" else "" else "???", fontSize = 13.sp, color = if (count > 0) Ink else Faint, maxLines = 1)
    }
}

/** 차체 색 한 칸: 색 동그라미와 이름. 칠한 색은 민트 테두리. */
@Composable
private fun ColorSlot(c: CarColor, owned: Boolean, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MintSoft else if (owned) Paper else Color(0xFFF3F3F8), RoundedCornerShape(16.dp))
            .border(2.dp, if (selected) Mint else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(enabled = owned, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(34.dp)
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(c.light), Color(c.dark))), CircleShape)
                .border(1.dp, Line, CircleShape)
                .graphicsLayer { alpha = if (owned) 1f else 0.2f },
        )
        Text(if (owned) c.label else "???", fontSize = 13.sp, color = if (owned) Ink else Faint, maxLines = 1)
    }
}
