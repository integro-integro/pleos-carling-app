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
import work.integro.carling.vehicle.VehicleState

/** 진화했으면 빛 색, 아니면 공통 빛. */
fun CarlingData.glowColor(): Color = evolution?.light?.let { Color(it.color) } ?: BaseGlow

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
    // 카링이 돌아다니는 영역 — 이름·말풍선 위의 빈 공간 전체
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
                    stage = data.stage,
                    glow = data.glowColor(),
                    path = data.evolution?.path,
                    hat = data.wearingHat,
                    mood = personality,
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
                Box(Modifier.width(480.dp)) { panel() }
            }
        }

        if (BuildConfig.DEBUG) DevPanel(store, vehicle, Modifier.align(Alignment.TopStart).padding(12.dp))

        data.pendingReport?.let { report ->
            ReportDialog(report, data, personality, onClose = store::dismissReport)
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
    GameFrame(title = "카링 상태") {
        Column {
            val days = data.togetherDays.size
            GameGauge(
                icon = "🌱",
                label = "성장",
                value = if (days >= GrowthRules.EVOLVE_DAYS) "MAX" else "$days / ${GrowthRules.EVOLVE_DAYS}일",
                progress = growthProgress(data),
                fill = listOf(Color(0xFFB6F28A), Color(0xFF4DBB5E)),
                markers = listOf(GrowthRules.GROWN_DAYS / GrowthRules.EVOLVE_DAYS.toFloat()),
            )
            Spacer(Modifier.height(6.dp))
            GameGauge(
                icon = "💗",
                label = "친밀도",
                value = "${data.intimacy}",
                progress = data.intimacy / 100f,
                fill = listOf(Color(0xFFFFA3C4), Color(0xFFE5507E)),
            )
            Spacer(Modifier.height(6.dp))
            GameGauge(
                icon = "🍙",
                label = "포만감",
                value = "${data.fullness}",
                progress = data.fullness / 100f,
                fill = listOf(Color(0xFFFFE08A), Color(0xFFF0A030)),
            )
            Spacer(Modifier.height(14.dp))
            LightSummary(data)
            Spacer(Modifier.height(16.dp))
            val canFeed = store.canFeedToday()
            GameButton(
                text = if (canFeed) "밥 주기" else "오늘은 배불러요",
                icon = "🍙",
                top = Color(0xFFFFB648),
                bottom = Color(0xFFE07A12),
                enabled = canFeed,
                modifier = Modifier.fillMaxWidth(),
                onClick = onFeed,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                GameSlot("📖", "추억", enabled = true, badge = data.memories.size, modifier = Modifier.weight(1f), onClick = onMemories)
                GameSlot(
                    "🎩",
                    if (data.wearingHat) "벗기" else "쓰기",
                    enabled = data.hasHat,
                    selected = data.wearingHat,
                    modifier = Modifier.weight(1f),
                    onClick = store::toggleHat,
                )
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
    val evolution = data.evolution
    return if (evolution != null) "${evolution.light.label} ${evolution.path.label} 카링" else data.stage.label
}

private fun growthProgress(data: CarlingData): Float =
    (data.togetherDays.size / GrowthRules.EVOLVE_DAYS.toFloat()).coerceAtMost(1f)

/** 지금까지 시간대별로 함께 달린 횟수 — 어떤 빛으로 자랄지 미리 보여준다. 가장 많이 달린 빛이 맥동한다. */
@Composable
private fun LightSummary(data: CarlingData) {
    val leading = data.lightMinutes.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Light.entries.forEach { light ->
            LightGem(
                label = light.label,
                color = Color(light.color),
                count = data.lightTrips[light] ?: 0,
                leading = light == leading,
            )
        }
    }
}

// ---------------- 도착 리포트 / 추억 카드 ----------------

@Composable
private fun ReportDialog(report: TripReport, data: CarlingData, personality: Personality, onClose: () -> Unit) {
    val evolution = data.evolution
    Scrim {
        Text(
            when {
                report.evolvedNow && evolution != null -> "첫 진화! ${evolution.light.label} ${evolution.path.label} 카링"
                report.firstDrive -> "우리의 첫 드라이브!"
                else -> "오늘의 기억"
            },
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            textAlign = TextAlign.Center,
        )
        if (report.evolvedNow || report.firstDrive) {
            CarlingDrawing(
                size = 220.dp,
                stage = data.stage,
                glow = data.glowColor(),
                path = evolution?.path,
                hat = data.wearingHat,
                mood = personality,
            )
        } else {
            Spacer(Modifier.height(16.dp))
        }
        val distance = report.distanceKm?.takeIf { it > 0.05f }?.let { " · %.1fkm".format(it) } ?: ""
        ReportLine("함께한 시간", "${report.minutes}분$distance")
        ReportLine("시간대", report.light.label)
        when (report.newPlace) {
            true -> ReportLine("처음 간 곳", report.placeName ?: "새로운 곳")
            false -> ReportLine("다시 찾은 곳", report.placeName ?: "익숙한 곳")
            null -> Unit
        }
        if (report.smoothPraise) ReportLine("부드러운 운전", "칭찬 1회")
        ReportLine("성장도", if (report.newDay) "+1일 (함께한 날 ${report.togetherDays}일)" else "오늘은 이미 함께했어요")
        if (report.intimacyGain > 0) ReportLine("친밀도", "+${report.intimacyGain}")
        if (report.firstDrive) ReportLine("선물", "첫 드라이브 기념 모자")
        if (report.grownNow) ReportLine("성장", "조금 더 자랐어요!")
        Spacer(Modifier.height(18.dp))
        val closing = when {
            report.evolvedNow && evolution != null -> evolution.reason
            report.firstDrive -> "내일 아침에도 여기서 기다릴게요."
            else -> personality.arrivalLine
        }
        Speech(closing)
        Spacer(Modifier.height(22.dp))
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
                        Modifier
                            .fillMaxWidth()
                            .background(PanelLight, RoundedCornerShape(20.dp))
                            .padding(20.dp),
                    ) {
                        Text(card.date, fontSize = 16.sp, color = Muted)
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

/** 개발·데모용 도구. debug 빌드에서만 보인다. */
@Composable
private fun DevPanel(store: CarlingStore, vehicle: VehicleState, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    var light by remember { mutableStateOf(Light.SUNSET) }
    var explorer by remember { mutableStateOf(false) }
    val simulator = remember(store) { DevSimulator(store) }

    Column(modifier) {
        TextButton(onClick = { open = !open }) { Text(if (open) "개발자 도구 ▲" else "🛠", fontSize = 18.sp) }
        if (!open) return@Column
        Column(
            Modifier
                .background(Panel, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DebugLine("Vehicle SDK", if (vehicle.connected) "연결됨" else "연결 안 됨")
            DebugLine("기어", vehicle.gear.name.removePrefix("GEAR_"))
            DebugLine("충전", "구 ${if (vehicle.chargePortOpen) "열림" else "닫힘"} / 충전중 ${vehicle.isCharging}")
            DebugLine("SOC", vehicle.stateOfCharge?.let { "$it%" } ?: "-")
            DebugLine("게임 날짜", store.clock.today().toString())
            DebugLine("장소", "새 ${store.data.newPlaces} / 재방문 ${store.data.revisits}")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Light.entries.forEach { l ->
                    FilterChip(selected = light == l, onClick = { light = l }, label = { Text(l.label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = !explorer, onClick = { explorer = false }, label = { Text("단골") })
                FilterChip(selected = explorer, onClick = { explorer = true }, label = { Text("탐험") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { simulator.simulateDays(1, light, explorer) }) { Text("가상 주행 +1일") }
                OutlinedButton(onClick = { simulator.simulateDays(6, light, explorer) }) { Text("+6일") }
                OutlinedButton(onClick = {
                    val left = GrowthRules.EVOLVE_DAYS - store.data.togetherDays.size
                    if (left > 0) simulator.simulateDays(left, light, explorer)
                }) { Text("30일까지") }
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
