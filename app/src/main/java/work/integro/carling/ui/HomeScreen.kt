package work.integro.carling.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.BuildConfig
import work.integro.carling.data.CarlingData
import work.integro.carling.data.CarlingStore
import work.integro.carling.data.MemoryCard
import work.integro.carling.data.Personality
import work.integro.carling.data.TripReport
import work.integro.carling.game.DevSimulator
import work.integro.carling.game.GrowthRules
import work.integro.carling.game.Light
import work.integro.carling.game.Lineage
import work.integro.carling.vehicle.VehicleState

/** 계열이 정해졌으면 계열 색, 아니면 공통 하늘빛. */
fun CarlingData.glowColor(): Color = lineage?.let { Color(it.color) } ?: BaseGlow

@Composable
fun HomeScreen(
    store: CarlingStore,
    data: CarlingData,
    personality: Personality,
    name: String,
    vehicle: VehicleState,
    onLocationAllowed: () -> Unit,
) {
    val compact = LocalCompact.current
    var greetedToday by remember { mutableStateOf(false) }
    var showMemories by remember { mutableStateOf(false) }
    var justFed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { greetedToday = store.greetIfNewDay() }

    val line = when {
        vehicle.isCharging || vehicle.chargePortOpen -> personality.mealLine
        justFed -> "냠냠, 고마워요! 배불러요."
        greetedToday -> "오늘도 만났네요! ${personality.greeting}"
        data.tripCount == 0 -> "멀리 가지 않아도 돼요. 매일 만나면 충분해요."
        else -> personality.greeting
    }

    val carlingSize = if (compact) 300.dp else 400.dp
    val playground = @Composable { areaModifier: Modifier ->
        Wandering(
            size = carlingSize,
            enabled = data.pendingReport == null && !showMemories,
            personality = personality,
            tapLines = personality.tapLines,
            onPetted = store::pet,
            modifier = areaModifier,
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                CarlingDrawing(
                    size = carlingSize,
                    stage = data.sizeStage,
                    glow = data.glowColor(),
                    hat = data.wearingHat,
                    mood = personality,
                    lineage = data.lineage,
                    evoTier = data.evoTier,
                )
                if (vehicle.chargePortOpen) Text("🍙", fontSize = 72.sp)
            }
        }
    }
    val caption = @Composable {
        Text(name, fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(stageTitle(data), fontSize = 22.sp, color = Gold)
        Spacer(Modifier.height(14.dp))
        Speech(line)
    }
    val panel = @Composable {
        StatusPanel(
            store = store,
            data = data,
            onFeed = { store.feed(); justFed = true },
            onMemories = { showMemories = true },
            onLocationToggle = { allowed ->
                store.setLocationConsent(allowed)
                if (allowed) onLocationAllowed()
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (compact) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(32.dp))
                playground(Modifier.fillMaxWidth().height(carlingSize + 200.dp))
                caption()
                Spacer(Modifier.height(24.dp))
                Box(Modifier.widthIn(max = 640.dp)) { panel() }
            }
        } else {
            Row(Modifier.fillMaxSize().padding(32.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    playground(Modifier.fillMaxWidth().weight(1f))
                    caption()
                }
                Spacer(Modifier.width(24.dp))
                Box(Modifier.width(500.dp)) { panel() }
            }
        }

        if (BuildConfig.DEBUG) DevPanel(store, vehicle, Modifier.align(Alignment.TopStart).padding(12.dp))

        data.pendingReport?.let { report ->
            ReportDialog(store, report, data, personality, onClose = store::dismissReport)
        }
        if (showMemories) MemoriesDialog(data.memories, onClose = { showMemories = false })
    }
}

@Composable
private fun StatusPanel(
    store: CarlingStore,
    data: CarlingData,
    onFeed: () -> Unit,
    onMemories: () -> Unit,
    onLocationToggle: (Boolean) -> Unit,
) {
    val today = store.clock.today()
    val dayNum = data.dayNumber(today)
    val cur = data.reachedStage
    val next = if (cur < GrowthRules.MAX_STAGE) GrowthRules.STAGES[cur + 1] else null

    GameFrame(title = "카링 성장") {
        Column {
            // D일 · 단계 · 계열
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("D$dayNum", style = gameText(20.sp), color = Gold)
                Spacer(Modifier.width(10.dp))
                Text(data.stageSpec.label, style = gameText(20.sp))
                Spacer(Modifier.weight(1f))
                data.lineage?.let { Text("${it.label} 계열", style = gameText(19.sp), color = Color(it.color)) }
                    ?: Text("계열 미정 (D14)", style = gameText(17.sp), color = Muted)
            }
            Spacer(Modifier.height(10.dp))
            GameGauge(
                icon = "⚡",
                label = "XP",
                value = if (next != null) "${data.totalXp} / ${next.cumXp}" else "${data.totalXp}",
                progress = if (next != null) data.totalXp / next.cumXp.toFloat() else 1f,
                fill = listOf(Color(0xFFB6F28A), Color(0xFF4DBB5E)),
            )
            Spacer(Modifier.height(6.dp))
            GameGauge(
                icon = "🗺",
                label = "MP",
                value = if (next != null) "${data.totalMp} / ${next.cumMp}" else "${data.totalMp}",
                progress = if (next != null) data.totalMp / next.cumMp.toFloat() else 1f,
                fill = listOf(Color(0xFF9BD0FF), Color(0xFF3B82E6)),
            )
            Spacer(Modifier.height(8.dp))
            next?.let { n ->
                val evo = n.evoIndex?.let { GrowthRules.EVOLUTIONS[it] }
                val extra = if (n.evoIndex != null) {
                    val card = if (n.evoIndex in data.ownedCards) "진화카드✓" else "진화카드?"
                    val quest = if (n.evoIndex in data.doneQuests) "퀘스트✓" else "퀘스트"
                    " · $card · $quest"
                } else ""
                Text("다음 ${n.label}: D${n.day} · XP ${n.cumXp} · MP ${n.cumMp}$extra",
                    fontSize = 16.sp, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                // 진화 퀘스트 — 조건 충족 시 완료 버튼
                if (n.evoIndex != null && n.evoIndex in data.ownedCards && n.evoIndex !in data.doneQuests) {
                    Spacer(Modifier.height(8.dp))
                    val can = store.canCompleteQuest(n.evoIndex)
                    GameButton(
                        text = if (can) "진화 퀘스트 완료" else "기억 ${store.savedMemoryCount()}/${evo?.memoriesNeeded ?: 1} 저장 필요",
                        icon = "✨",
                        top = Color(0xFFB08CFF), bottom = Color(0xFF7B52D6),
                        enabled = can,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { store.completeQuest(n.evoIndex) },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            GameGauge("💗", "친밀도", "${data.intimacy}", data.intimacy / 100f, listOf(Color(0xFFFFA3C4), Color(0xFFE5507E)))
            Spacer(Modifier.height(6.dp))
            GameGauge("🍙", "포만감", "${data.fullness}", data.fullness / 100f, listOf(Color(0xFFFFE08A), Color(0xFFF0A030)))

            Spacer(Modifier.height(14.dp))
            val canFeed = store.canFeedToday()
            GameButton(
                text = if (canFeed) "밥 주기 (+5 XP)" else "오늘은 배불러요",
                icon = "🍙",
                top = Color(0xFFFFB648), bottom = Color(0xFFE07A12),
                enabled = canFeed,
                modifier = Modifier.fillMaxWidth(),
                onClick = onFeed,
            )
            Spacer(Modifier.height(10.dp))
            // 교감 — 놀아주기 / 말 걸기 (친밀도 XP)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameSlot("🎈", "놀아주기", enabled = "PLAY" !in data.intimacyActionsToday, modifier = Modifier.weight(1f)) { store.interact("PLAY") }
                GameSlot("💬", "말 걸기", enabled = "TALK" !in data.intimacyActionsToday, modifier = Modifier.weight(1f)) { store.interact("TALK") }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                GameSlot("📖", "추억", enabled = true, badge = data.memories.size, modifier = Modifier.weight(1f), onClick = onMemories)
                GameSlot("🎩", if (data.wearingHat) "벗기" else "쓰기", enabled = data.hasHat, selected = data.wearingHat, modifier = Modifier.weight(1f), onClick = store::toggleHat)
                Column(Modifier.weight(1.3f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📍 위치", style = gameText(17.sp))
                    Spacer(Modifier.height(6.dp))
                    GameToggle(checked = data.locationConsent == true, onChange = onLocationToggle)
                }
            }
        }
    }
}

private fun stageTitle(data: CarlingData): String {
    val lineage = data.lineage
    return if (lineage != null) "${lineage.label} ${data.stageSpec.label}" else data.stageSpec.label
}

// ---------------- 도착 리포트 / 추억 카드 ----------------

@Composable
private fun ReportDialog(
    store: CarlingStore,
    report: TripReport,
    data: CarlingData,
    personality: Personality,
    onClose: () -> Unit,
) {
    var savedMp by remember { mutableStateOf<Int?>(null) }
    Scrim {
        Text(
            when {
                report.cardGotLabel != null -> report.cardGotLabel
                report.stageUpLabel != null -> report.stageUpLabel
                report.lineageDecidedLabel != null -> "${report.lineageDecidedLabel} 계열이 되었어요"
                report.firstDrive -> "우리의 첫 드라이브!"
                else -> "오늘의 기억"
            },
            fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center,
        )
        if (report.firstDrive || report.stageUpLabel != null || report.lineageDecidedLabel != null) {
            CarlingDrawing(size = 200.dp, stage = data.sizeStage, glow = data.glowColor(), hat = data.wearingHat, mood = personality, lineage = data.lineage, evoTier = data.evoTier)
        } else {
            Spacer(Modifier.height(16.dp))
        }
        Text("TRIP CARD · D${report.dayNumber}", fontSize = 16.sp, color = Gold, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        val distance = report.distanceKm?.takeIf { it > 0.05f }?.let { " · %.1fkm".format(it) } ?: ""
        ReportLine("함께한 시간", "${report.minutes}분$distance")
        ReportLine("안전운전", if (report.smoothPraise) "칭찬! (90점 이상)" else "다음엔 더 부드럽게")
        when (report.newPlace) {
            true -> ReportLine("처음 간 곳", report.placeName ?: "새로운 곳")
            false -> ReportLine("다시 찾은 곳", report.placeName ?: "익숙한 곳")
            null -> Unit
        }
        ReportLine("오늘 운전 XP", "${report.driveXpToday}")
        ReportLine("누적", "XP ${report.totalXp} · MP ${report.totalMp}")
        report.lineageDecidedLabel?.let { ReportLine("계열 결정", "$it 계열") }
        report.cardGotLabel?.let { ReportLine("진화카드", it) }
        report.stageUpLabel?.let { ReportLine("성장", it) }

        Spacer(Modifier.height(16.dp))
        // 기억으로 저장 → MP
        when {
            savedMp != null -> {
                val mp = savedMp!!
                Speech(if (mp > 0) "이 길을 기억에 남겼어요. +$mp MP, 지도가 밝아졌어요!" else "오늘은 기억을 다 저장했어요(하루 2장).")
            }
            store.canSaveMemoryToday -> {
                val mp = if (report.newPlace == true) GrowthRules.MP_NEW_PATH else GrowthRules.MP_FAMILIAR
                Speech("오늘의 길을 기억으로 남길까요? (+$mp MP)")
                Spacer(Modifier.height(10.dp))
                GameButton(
                    text = "기억으로 저장 (+$mp MP)", icon = "🗺",
                    top = Color(0xFF9BD0FF), bottom = Color(0xFF3B82E6), enabled = true,
                    modifier = Modifier.widthIn(min = 300.dp),
                    onClick = { savedMp = store.saveTripMemory(report) },
                )
            }
            else -> Speech("오늘은 기억을 다 저장했어요(하루 2장).")
        }

        Spacer(Modifier.height(18.dp))
        Button(onClick = onClose, modifier = Modifier.height(64.dp).width(240.dp)) { Text("확인", fontSize = 26.sp) }
    }
}

@Composable
private fun ReportLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, fontSize = 22.sp, color = Muted, modifier = Modifier.width(180.dp))
        Text(value, fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MemoriesDialog(memories: List<MemoryCard>, onClose: () -> Unit) {
    Scrim {
        Text("우리의 추억 카드", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(16.dp))
        if (memories.isEmpty()) {
            Text("아직 추억이 없어요. 첫 드라이브를 떠나봐요!", fontSize = 22.sp, color = Muted)
        } else {
            LazyColumn(Modifier.fillMaxWidth().height(520.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(memories.reversed()) { card ->
                    Column(
                        Modifier.fillMaxWidth().background(PanelLight, RoundedCornerShape(20.dp)).padding(20.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(card.date, fontSize = 16.sp, color = Muted)
                            if (card.saved) { Spacer(Modifier.width(8.dp)); Text("🗺 저장됨", fontSize = 15.sp, color = Gold) }
                        }
                        Text(card.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Gold)
                        Text(card.text, fontSize = 20.sp, lineHeight = 30.sp, color = Ink)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClose, modifier = Modifier.height(60.dp).width(200.dp)) { Text("닫기", fontSize = 24.sp) }
    }
}

@Composable
private fun Scrim(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xB3000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 760.dp)
                .background(Night, RoundedCornerShape(36.dp))
                .verticalScroll(rememberScrollState())
                .padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

// ---------------- 개발용 ----------------

@Composable
private fun DevPanel(store: CarlingStore, vehicle: VehicleState, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    var lineage by remember { mutableStateOf(Lineage.WIND) }
    val simulator = remember(store) { DevSimulator(store) }

    Column(modifier) {
        TextButton(onClick = { open = !open }) { Text(if (open) "개발자 도구 ▲" else "🛠", fontSize = 18.sp) }
        if (!open) return@Column
        Column(
            Modifier.background(Panel, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val d = store.data
            DebugLine("단계", "${d.stageSpec.label} (idx ${d.reachedStage})")
            DebugLine("누적", "XP ${d.totalXp} · MP ${d.totalMp}")
            DebugLine("계열", d.lineage?.label ?: "미정")
            DebugLine("카드/퀘스트", "${d.ownedCards} / ${d.doneQuests}")
            DebugLine("게임 날짜", "${store.clock.today()} (D${d.dayNumber(store.clock.today())})")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Lineage.entries.forEach { l ->
                    FilterChip(selected = lineage == l, onClick = { lineage = l }, label = { Text(l.label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { simulator.simulateDays(1, lineage) }) { Text("+1일") }
                OutlinedButton(onClick = { simulator.simulateDays(7, lineage) }) { Text("+7일") }
                OutlinedButton(onClick = { simulator.advanceToStage(3, lineage) }) { Text("진화1까지") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { simulator.advanceToStage(6, lineage) }) { Text("진화2까지") }
                OutlinedButton(onClick = { simulator.advanceToStage(9, lineage) }) { Text("완성형까지") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { store.clock.skipDays(1) }) { Text("다음 날로") }
                OutlinedButton(onClick = store::reset) { Text("처음부터") }
            }
        }
    }
}

@Composable
private fun DebugLine(label: String, value: String) {
    Row {
        Text(label, fontSize = 18.sp, color = Muted, modifier = Modifier.width(120.dp))
        Text(value, fontSize = 18.sp, color = Ink)
    }
}
