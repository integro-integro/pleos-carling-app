package work.integro.carling.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
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
    var showMap by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(false) }
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
            onMap = { showMap = true },
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

        // 도움말(튜토리얼) — 언제든 열어 다시 볼 수 있다
        TextButton(onClick = { showTutorial = true }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            Text("❓ 도움말", fontSize = 18.sp, color = Muted)
        }

        if (BuildConfig.DEBUG) DevPanel(store, vehicle, Modifier.align(Alignment.TopStart).padding(12.dp))

        data.pendingReport?.let { report ->
            ReportDialog(store, report, data, personality, onClose = store::dismissReport)
        }
        if (showMemories) MemoriesDialog(data.memories, onClose = { showMemories = false })
        if (showMap) MapDialog(data, onClose = { showMap = false })
        if (showTutorial) TutorialDialog(onClose = { showTutorial = false })
    }
}

@Composable
private fun StatusPanel(
    store: CarlingStore,
    data: CarlingData,
    onFeed: () -> Unit,
    onMemories: () -> Unit,
    onMap: () -> Unit,
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                GameSlot("📖", "추억", enabled = true, badge = data.memories.size, modifier = Modifier.weight(1f), onClick = onMemories)
                GameSlot("🗺", "지도", enabled = true, badge = data.memories.count { it.saved }, modifier = Modifier.weight(1f), onClick = onMap)
                GameSlot("🎩", if (data.wearingHat) "벗기" else "쓰기", enabled = data.hasHat, selected = data.wearingHat, modifier = Modifier.weight(1f), onClick = store::toggleHat)
                Column(Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally) {
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

/** 카링의 지도 — 어두운 지도에 '기억으로 저장'한 길이 하나씩 켜진다. */
@Composable
private fun MapDialog(data: CarlingData, onClose: () -> Unit) {
    val saved = data.memories.filter { it.saved }
    val roadColor = data.lineage?.let { Color(it.color) } ?: BaseGlow
    Scrim {
        Text("카링의 지도", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("함께 달린 길이 하나씩 켜져요", fontSize = 18.sp, color = Muted)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            MapStat("밝혀진 길", saved.size)
            MapStat("함께한 날", data.togetherDays.size)
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(Color(0xFF0A1024), RoundedCornerShape(20.dp)),
        ) {
            Canvas(Modifier.fillMaxSize().padding(16.dp)) {
                val w = size.width
                val h = size.height
                // 희미한 격자
                val grid = Color.White.copy(alpha = 0.05f)
                for (i in 1..7) {
                    drawLine(grid, Offset(w * i / 8f, 0f), Offset(w * i / 8f, h), 1f)
                    drawLine(grid, Offset(0f, h * i / 8f), Offset(0f + w, h * i / 8f), 1f)
                }
                val n = min(saved.size, 24)
                if (n == 0) return@Canvas
                val cols = if (n <= 1) 1 else 4
                val rows = (n + cols - 1) / cols
                val mx = w * 0.08f
                val my = h * 0.14f
                val stepX = if (cols > 1) (w - 2 * mx) / (cols - 1) else 0f
                val stepY = if (rows > 1) (h - 2 * my) / (rows - 1) else 0f
                fun jitter(i: Int, salt: Int): Float {
                    val v = (i * 73856093) xor (salt * 19349663)
                    return ((v ushr 8) and 0xFF) / 255f
                }
                val pts = ArrayList<Offset>(n)
                for (i in 0 until n) {
                    val row = i / cols
                    val colRaw = i % cols
                    val col = if (row % 2 == 0) colRaw else (cols - 1 - colRaw)
                    val jx = (jitter(i, 1) - 0.5f) * stepX * 0.35f
                    val jy = (jitter(i, 2) - 0.5f) * stepY * 0.3f
                    val x = if (cols > 1) mx + col * stepX + jx else w / 2f
                    val y = if (rows > 1) my + row * stepY + jy else h / 2f
                    pts += Offset(x, y)
                }
                // 길(선) — 발견 순서대로 이어진다
                for (i in 1 until pts.size) {
                    drawLine(roadColor.copy(alpha = 0.45f), pts[i - 1], pts[i], 4f, cap = StrokeCap.Round)
                }
                // 노드 — 나중에 켜진 길일수록 밝게
                pts.forEachIndexed { i, p ->
                    val bright = 0.5f + 0.5f * (i + 1f) / pts.size
                    drawCircle(Brush.radialGradient(listOf(roadColor.copy(alpha = 0.5f * bright), Color.Transparent), p, 26f), 26f, p)
                    drawCircle(roadColor.copy(alpha = bright), 7f, p)
                    drawCircle(Color.White.copy(alpha = 0.9f), 2.5f, p + Offset(-1.5f, -1.5f))
                }
            }
            if (saved.isEmpty()) {
                Text(
                    "아직 밝혀진 길이 없어요.\n운전을 마치면 '기억으로 저장'으로 길을 켜요.",
                    fontSize = 20.sp, color = Muted, textAlign = TextAlign.Center, lineHeight = 30.sp,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
        }
        if (saved.size > 24) {
            Spacer(Modifier.height(8.dp))
            Text("최근 24개의 길을 보여줘요 (전체 ${saved.size}개)", fontSize = 15.sp, color = Muted)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClose, modifier = Modifier.height(60.dp).width(200.dp)) { Text("닫기", fontSize = 24.sp) }
    }
}

@Composable
private fun MapStat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Gold)
        Text(label, fontSize = 16.sp, color = Muted)
    }
}

/**
 * 튜토리얼(도움말) 내용 — 한곳에서 관리한다.
 * ⚠ 기능을 추가·변경하면 여기 항목도 함께 업데이트할 것.
 */
private val tutorialSections: List<Triple<String, String, String>> = listOf(
    Triple("🥚", "첫 만남", "알은 모두에게 하나, 고르지 않아요. 첫 운전이 알을 깨우고, 이름을 지어주면 나만의 카링이 돼요."),
    Triple("🚗", "함께 달리기", "운전을 마칠 때마다 트립카드 한 장이 생겨요. 거리가 아니라 '함께한 횟수'로 자라요."),
    Triple("⚡", "성장 에너지 (XP)", "트립·안전운전·연료 보충·교감으로 하루 최대 50. 180일 동안 성장 6번 + 진화 3번을 거쳐요."),
    Triple("🗺", "지도 포인트 (MP)", "도착 후 '기억으로 저장'하면 지도에 길이 켜져요. 새 길 +3, 익숙한 길 +1 (하루 2장). 지도 버튼으로 볼 수 있어요."),
    Triple("🎨", "계열", "운전 습관에 따라 D14에 계열(바람·빛·나무·물·흙)이 정해지고, 몸 색과 외형이 바뀌어요."),
    Triple("✨", "진화", "D30·D90·D180에 진화카드를 받고 특별 퀘스트를 채우면 진화해요. 단계는 건너뛰지 않아요."),
    Triple("💗", "돌보기", "쓰다듬기·밥 주기·놀아주기·말 걸기로 교감해요 (하루 서로 다른 2가지 행동까지 XP)."),
    Triple("↩", "되돌아가기", "첫 만남 중에는 화면 왼쪽 위 '뒤로'로 이전 단계를 다시 볼 수 있어요."),
)

@Composable
private fun TutorialDialog(onClose: () -> Unit) {
    Scrim {
        Text("카링 도움말", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("카링과 함께 자라는 방법", fontSize = 18.sp, color = Muted)
        Spacer(Modifier.height(16.dp))
        tutorialSections.forEach { (icon, title, body) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(icon, fontSize = 30.sp, modifier = Modifier.width(52.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Gold)
                    Text(body, fontSize = 19.sp, lineHeight = 28.sp, color = Ink)
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
