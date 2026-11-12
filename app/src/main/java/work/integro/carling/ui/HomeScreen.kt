package work.integro.carling.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import work.integro.carling.game.CarItem
import work.integro.carling.game.CardState
import work.integro.carling.game.DevSimulator
import work.integro.carling.game.PersonalityResult
import work.integro.carling.game.Rules
import work.integro.carling.game.Stage
import work.integro.carling.game.TimeBucket
import work.integro.carling.game.TripPurpose
import work.integro.carling.voice.Babble

/** 하단 탭: 카링 · 트립카드 · 리포트. */
private enum class Tab(val icon: String, val label: String) { KARING("💜", "카링"), CARDS("🎫", "트립카드"), REPORT("📊", "리포트") }

/** 열린 카드 시트. */
private class OpenSheet(val tripId: Long, val mode: SheetMode)

/**
 * 주차(P) 홈. 사용자가 조작하는 순간은 주차 상태의 세 번뿐이다: 카드 완성, 럭키박스 개봉, 리포트 열람.
 * 안내 순서: 동의 → 도착·재안내 카드 → 단계 변화 → 개성 변화.
 */
@Composable
fun HomeScreen(store: CarlingStore) {
    val data = store.data
    val bucket = TimeBucket.of(store.clock.hourOf(store.clock.now()))
    var tab by remember { mutableStateOf(Tab.KARING) }
    var sheet by remember { mutableStateOf<OpenSheet?>(null) }
    var boxId by remember { mutableStateOf<Int?>(null) }
    var showHelp by remember { mutableStateOf(false) }
    var showDev by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val personality = remember(data.rewards, data.cards, data.trips) { store.personalityOf(data) }

    LaunchedEffect(data.stage, data.voiceOn) {
        Babble.stage = Babble.stageOf(data.stage)
        Babble.lineage = Babble.timbreOf(data.stage)
        Babble.enabled = data.voiceOn
    }
    // 확정·만료는 화면을 보는 동안에도 판정한다
    LaunchedEffect(Unit) {
        while (true) {
            store.settle()
            store.expireCards()
            delay(30_000)
        }
    }
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

    fun complete(card: TripCard, purpose: TripPurpose, tags: List<String>) {
        store.complete(card.tripId, purpose, tags)
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

        Box(Modifier.fillMaxSize().padding(bottom = 96.dp)) {
            when (tab) {
                Tab.KARING -> KaringTab(data, personality, bucket, store, onBox = { boxId = data.unopenedBoxes.firstOrNull()?.boxId }, onCards = { tab = Tab.CARDS })
                Tab.CARDS -> TripCardsTab(
                    data,
                    store.clock.today(),
                    onOpen = { c -> sheet = OpenSheet(c.tripId, if (c.state == CardState.COMPLETED) SheetMode.EDIT else SheetMode.OPEN) },
                    onCompleteAll = ::completeAll,
                )
                Tab.REPORT -> ReportTab(data, store.clock.today(), personality)
            }
        }

        // 상단 오른쪽
        Row(Modifier.align(Alignment.TopEnd).padding(16.dp).zIndex(5f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallChip(if (data.voiceOn) "🔊" else "🔇", onClick = store::toggleVoice)
            SmallChip("❓ 도움말", onClick = { showHelp = true })
            if (BuildConfig.DEBUG) SmallChip("🛠", onClick = { showDev = !showDev })
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
                        onComplete = { p, t -> if (open.mode == SheetMode.EDIT) { store.editPurpose(card.tripId, p); sheet = null; notice = "목적을 ${p.label}(으)로 바꿨어요" } else complete(card, p, t) },
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
                    onComplete = { p, t -> complete(prompt, p, t) },
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
            boxId?.let { id -> data.boxes.firstOrNull { it.boxId == id } }?.let { box ->
                LuckyBoxDialog(
                    box,
                    remaining = data.unopenedBoxes.count { it.boxId != box.boxId },
                    onOpen = { store.openBox(it.boxId) },
                    onNext = { boxId = store.data.unopenedBoxes.firstOrNull()?.boxId },
                    onClose = { boxId = null },
                )
            }
            if (showHelp) HelpDialog(onClose = { showHelp = false })
            if (!data.consented) ConsentDialog(onAgree = store::consent)
        }
    }
}

/** 카링 탭(홈): 차에 탄 카링과 말풍선, 오른쪽에 성장·개성·수집. */
@Composable
private fun KaringTab(data: CarlingData, personality: PersonalityResult, bucket: TimeBucket, store: CarlingStore, onBox: () -> Unit, onCards: () -> Unit) {
    var line by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(line) {
        if (line != null) {
            delay(3_000)
            line = null
        }
    }
    Row(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 22.dp)) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Text(
                "P · 함께 출발해요",
                fontSize = 20.sp,
                color = Ink,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(50)).padding(horizontal = 20.dp, vertical = 10.dp),
            )
            Column(Modifier.align(Alignment.Center).offset(y = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Speech(line ?: homeLine(data, bucket))
                Spacer(Modifier.height(10.dp))
                KaringView(
                    data.stage,
                    if (data.stage.ordinal >= Stage.BABY.ordinal) Pose.CAR else Pose.STAND,
                    data.personality,
                    data.equippedCarItem,
                    height = 470.dp,
                    onTap = {
                        val l = tapLines.random()
                        line = l
                        Babble.say(l)
                    },
                )
            }
            if (data.unopenedBoxes.isNotEmpty()) LuckyBoxButton(data.unopenedBoxes.size, onBox, Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp))
            if (data.draftCards.isNotEmpty()) {
                Text(
                    "🎫 완성할 카드 ${data.draftCards.size}장",
                    fontSize = 20.sp,
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 18.dp)
                        .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(50)).clickable(onClick = onCards)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
        Spacer(Modifier.width(24.dp))
        KaringPanel(data, personality, store, Modifier.width(560.dp).fillMaxHeight().padding(top = 56.dp, bottom = 8.dp))
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
private fun KaringPanel(data: CarlingData, personality: PersonalityResult, store: CarlingStore, modifier: Modifier) {
    Panel(modifier, padding = 24.dp) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(karingName(data.name, data.stage), fontSize = 30.sp, color = Ink, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                if (data.name != null) Text(data.stage.label, fontSize = 19.sp, color = Lilac, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${"%,d".format(data.totalGp)}점", fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Bold)
            }
            Text(data.stage.line, fontSize = 17.sp, color = Muted)
            Spacer(Modifier.height(12.dp))
            val next = data.stage.next
            if (next != null) {
                val from = if (data.stage.ordinal <= Stage.CRACKED.ordinal) 0 else data.stage.minGp
                val target = if (data.stage == Stage.EGG) 1 else next.minGp
                val progress = if (data.stage == Stage.EGG) 0f else ((data.totalGp - from).toFloat() / (target - from).coerceAtLeast(1)).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(16.dp).background(LilacSoft, RoundedCornerShape(8.dp))) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Lilac, Mint)), RoundedCornerShape(8.dp)))
                }
                Text(
                    if (data.stage == Stage.EGG) "첫 카드를 완성하고 운행이 확정되면 알에 금이 가요" else "${next.label}까지 ${"%,d".format((next.minGp - data.totalGp).coerceAtLeast(0))}점",
                    fontSize = 17.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else Text("지금 공개된 마지막 단계예요. 다음 차체는 추후 공개!", fontSize = 17.sp, color = Muted)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                Stage.entries.filter { it != Stage.CRACKED }.forEach { s ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StageThumb(s, 54.dp, dim = s.ordinal > data.stage.ordinal)
                        Text(s.label, fontSize = 13.sp, color = if (s == data.stage || (s == Stage.EGG && data.stage == Stage.CRACKED)) Ink else Faint)
                    }
                }
            }

            Divider()
            Text("개성 · 최근 저장 카드 ${personality.sampleSize}장", fontSize = 18.sp, color = Muted, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val p = data.personality
            Axis(Color(p.body.color), "몸 색", p.body.colorName, personality.bodyReason())
            Axis(null, "대표 장식", p.decorationItem?.let { "${it.icon} ${it.label}" } ?: "없음", personality.decorationReason())
            Axis(if (p.heartBright) Mint else MintSoft, "심장빛", if (p.heartBright) "선명하게 반짝" else "기본", personality.heartReason())

            Divider()
            Text("자동차 꾸미기 · 럭키박스에서 모아요", fontSize = 18.sp, color = Muted, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Grid(CarItem.entries, 4) { item ->
                val owned = (data.carItems[item] ?: 0) > 0
                Slot(item.icon, item.label, owned, selected = data.equippedCarItem == item) {
                    if (owned) store.equip(if (data.equippedCarItem == item) null else item)
                }
            }
            if (data.stage.ordinal < Stage.BABY.ordinal && data.carItems.isNotEmpty()) Text("아기카링이 되면 차에 붙어요", fontSize = 15.sp, color = Faint)

            Divider()
            Text("수집 배지 ${data.badges.size}/${Badge.entries.size}", fontSize = 18.sp, color = Muted, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Grid(Badge.entries, 3) { b -> Slot(b.icon, b.label, (data.badges[b] ?: 0) > 0, selected = false, count = data.badges[b] ?: 0) {} }
        }
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

/** 단계 변화 연출. 아기카링이 되면 이름을 지어준다. */
@Composable
private fun StageReveal(stage: Stage, data: CarlingData, onName: (String) -> Unit, onDone: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val askName = stage == Stage.BABY && data.name == null
    FullScrim {
        Panel(Modifier.widthIn(max = 900.dp), padding = 32.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (stage == Stage.CRACKED) "알에 첫 금이 갔어요!" else "카링이 ${stage.label}(으)로 자랐어요!", fontSize = 32.sp, color = Ink, fontWeight = FontWeight.Bold)
                Text(stage.line, fontSize = 20.sp, color = Muted)
                Spacer(Modifier.height(10.dp))
                KaringView(stage, if (stage.ordinal >= Stage.BABY.ordinal) Pose.CAR else Pose.STAND, data.personality, data.equippedCarItem, height = 380.dp)
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
                KaringView(Stage.EGG, Pose.STAND, work.integro.carling.game.Personality(), null, height = 300.dp)
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
    Triple("🚗", "운행 기록", "P가 아닌 기어로 바꾸면 기록이 시작돼요. 경로와 위치는 기록하지 않아요. 운행 기록은 카드와 상관없이 모두 남아 리포트에 쓰여요."),
    Triple("🎫", "트립카드", "1km 이상, 3분 넘게 달리고 P로 바꾸면 새 카드가 뒤집히며 도착해요. 운행 목적(출퇴근·외근·가족과 함께·여행·취미·기타) 하나를 고르면 그 목적의 풍경이 카드에 담기고 '완성하기'로 끝나요. 하늘색은 출발 시간대(아침·낮·저녁·밤)를 따라요. 태그는 선택이에요."),
    Triple("✨", "카드 등급", "운전점수 85점 이상이면 민트빛으로 반짝이는 카드, 첫 카드나 100km 넘는 장거리는 금빛 '특별' 카드가 나와요. 보상은 같고 모습만 달라요."),
    Triple("⏱️", "5분 규칙", "P로 바꾸고 5분 안에 다시 출발하면 같은 여정으로 이어져요. P가 5분 이어지면 운행이 확정돼요. 앱이 꺼져 있었어도 다음에 켤 때 같은 규칙으로 판정해요."),
    Triple("🎁", "보상", "운행이 확정되고 카드가 완성되면 성장 포인트(10 + km + 분, 최대 150)와 럭키박스 1개가 와요. 같은 운행에는 한 번만. 럭키박스는 하루 5개까지예요."),
    Triple("🧩", "럭키박스", "보너스 성장 포인트 5~30점이 들어 있고, 수집 배지(6종)나 카링의 차를 꾸미는 아이템(8종)이 함께 나오기도 해요. 카링 탭에서 아이템을 눌러 차에 붙이거나 뗄 수 있어요."),
    Triple("⏰", "미완성 카드", "확정 후에도 완성하지 않은 카드는 7일 안에 다시 시동을 걸면 한 번 더 알려줘요. 그때부터 24시간이 지나면 만료돼요. 만료돼도 운행 기록은 리포트에 남아요."),
    Triple("🌱", "성장 단계", "젤리알 → 깨어난 알(첫 보상) → 아기카링(1,500점) → 산들카링(3,500점) → 마이카링(6,000점). 단계가 오를수록 카링과 차가 함께 자라요."),
    Triple("🎨", "개성", "최근 완성한 카드 10장으로 정해져요. 가장 많이 출발한 시간대가 몸 색(아침 살구빛·낮 하늘빛·저녁 라벤더·밤 남색과 별), 가장 많이 고른 목적이 장식, 평균 운전점수 85점 이상이면 심장이 선명하게 반짝여요."),
    Triple("📊", "리포트", "이번 달·지난 달·최근 7일의 핵심 발견, 운행 현황, 날마다 달린 거리, 목적별 운행, 생활 패턴, 카링 성장 내역을 보여줘요."),
    Triple("🛡️", "주행 중", "운행 중에는 달리는 카링과 거리·시간만 보여주고, 카드·럭키박스·리포트는 주차 후에 열려요."),
)

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
        Text("시뮬레이터 (디버그 전용)", fontSize = 17.sp, color = Lilac, fontWeight = FontWeight.Bold)
        DevButton("지금 주차한 운행 18km") { DevSimulator.parkNow(store) }
        DevButton("짧은 운행 0.6km") { DevSimulator.shortTrip(store) }
        DevButton("5분 지나기 (확정)") { DevSimulator.skip(store, Rules.STOP_MERGE_MILLIS) }
        DevButton("24시간 지나기") { DevSimulator.skip(store, Rules.DAY_MILLIS) }
        DevButton("7일 지나기") { DevSimulator.skip(store, 7 * Rules.DAY_MILLIS) }
        DevButton("다시 시동 (재안내)") { store.markIgnition() }
        DevButton("아침 출퇴근 9장") { DevSimulator.morningCommutes(store) }
        DevButton("밤 외근 6장") { DevSimulator.nightFieldTrips(store) }
        DevButton("주말 가족 3장") { DevSimulator.weekendFamily(store) }
        DevButton("포인트 +1,000") { store.devAddGp(1_000) }
        DevButton("처음부터 다시") { store.devReset() }
    }
}

@Composable
private fun DevButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(label, fontSize = 17.sp) }
}
