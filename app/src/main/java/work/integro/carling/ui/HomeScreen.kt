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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import work.integro.carling.data.Personality
import work.integro.carling.data.recentDestinations
import work.integro.carling.game.DevSimulator
import work.integro.carling.game.GrowthRules
import work.integro.carling.game.Light
import work.integro.carling.game.Lineage
import work.integro.carling.game.Region
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
    var showTripCards by remember { mutableStateOf(false) }
    var openCard by remember { mutableStateOf<Int?>(null) }
    var showMap by remember { mutableStateOf(false) }
    var showScenes by remember { mutableStateOf(false) }
    var showItems by remember { mutableStateOf(false) }
    var showFoods by remember { mutableStateOf(false) }
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

    val carlingSize = if (compact) 360.dp else 480.dp
    // 변화 장면을 보기 전까지는 옛 모습을 유지해, 새 모습이 미리 보이지 않게 한다
    val celebrated = data.celebratedStage
    val growing = celebrated != null && data.reachedStage > celebrated
    LaunchedEffect(celebrated, data.reachedStage) {
        if (celebrated == null || celebrated > data.reachedStage) store.markStageCelebrated()
    }
    val evolving = growing && GrowthRules.STAGES[data.reachedStage].kind == GrowthRules.Kind.EVOLUTION
    // 트립카드 등 다른 창을 다 닫은 뒤에 재생한다. 주행 중에는 이 화면 자체가 없어 정차 후로 미뤄진다.
    val noDialog = data.pendingCard == null && openCard == null && !showTripCards && !showMap && !showItems && !showFoods && !showScenes && !showTutorial
    val growthNotice = growing && !evolving && noDialog
    val lookStage = store.devLook?.first ?: if (growing && !growthNotice) celebrated else data.reachedStage
    var cueId by remember { mutableIntStateOf(0) }
    var cue by remember { mutableStateOf<WanderCue?>(null) }
    LaunchedEffect(growthNotice) {
        if (growthNotice) cue = WanderCue(++cueId, null)
    }
    val lookLineage = store.devLook?.second ?: data.lineage
    val playground = @Composable { areaModifier: Modifier ->
        Wandering(
            size = carlingSize,
            enabled = data.pendingCard == null && !showTripCards && openCard == null && !showMap && !showTutorial && !showScenes && !showItems && !showFoods && !(evolving && noDialog),
            specials = GrowthRules.knownMoves(lookStage, lookLineage),
            cue = cue,
            personality = personality,
            tapLines = personality.tapLines,
            onPetted = store::pet,
            modifier = areaModifier,
        ) { napping ->
            Box(contentAlignment = Alignment.BottomEnd) {
                CarlingDrawing(
                    size = carlingSize,
                    stageIndex = lookStage,
                    glow = lookLineage?.let { Color(it.color) } ?: BaseGlow,
                    item = data.equippedItem,
                    mood = personality,
                    lineage = lookLineage,
                    asleep = napping,
                )
                if (vehicle.chargePortOpen) Text("🍙", fontSize = 72.sp)
            }
        }
    }
    val caption = @Composable {
        Column(Modifier.onScene(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(GrowthRules.stageName(lookStage, lookLineage), fontSize = 22.sp, color = Gold)
        }
        Spacer(Modifier.height(14.dp))
        Speech(line)
    }
    val panel = @Composable {
        StatusPanel(
            store = store,
            data = data,
            onFeed = { store.feed(); justFed = true },
            onTripCards = { showTripCards = true },
            onMap = { showMap = true },
            onScenes = { showScenes = true },
            onItems = { showItems = true },
            onFoods = { showFoods = true },
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
        Row(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            TextButton(onClick = store::toggleVoice) {
                Text(if (data.voiceOn) "🔊 음성 켜짐" else "🔇 음성 꺼짐", fontSize = 18.sp, color = Ink, modifier = Modifier.onScene())
            }
            TextButton(onClick = { showTutorial = true }) {
                Text("❓ 도움말", fontSize = 18.sp, color = Ink, modifier = Modifier.onScene())
            }
        }

        if (BuildConfig.DEBUG) DevPanel(store, vehicle, Modifier.align(Alignment.TopStart).padding(12.dp))

        if (showTripCards) {
            TripCardsDialog(data.tripCards, onOpen = { openCard = it }, onClose = { showTripCards = false })
        }
        data.pendingCard?.let { number ->
            data.tripCards.firstOrNull { it.number == number }?.let { card ->
                NewTripCardDialog(
                    card = card,
                    onOpen = { store.dismissTripCard(); openCard = number },
                    onClose = store::dismissTripCard,
                )
            }
        }
        openCard?.let { number ->
            data.tripCards.firstOrNull { it.number == number }?.let { card ->
                TripCardDetailDialog(
                    card = card,
                    mpIfSaved = store.saveMpFor(card),
                    onToggleSaved = { store.toggleTripSaved(number) },
                    onClose = { openCard = null },
                )
            }
        }
        if (showMap) CarlingMapScreen(data.mapRoutes, store.routeTracks, onClose = { showMap = false })
        if (showFoods) {
            FoodsDialog(data, onEat = { store.eatFood(it) }, onClose = { showFoods = false })
        }
        if (showItems) {
            ItemsDialog(data, personality, onSelect = store::equipItem, onClose = { showItems = false })
        }
        if (!growing && GrowthRules.itemsUnlocked(data.reachedStage) && !data.itemIntroSeen && data.pendingCard == null && openCard == null) {
            ItemUnlockDialog(
                data,
                personality,
                onOpenItems = { store.markItemIntroSeen(); showItems = true },
                onLater = store::markItemIntroSeen,
            )
        }
        if (showScenes) ScenePickerDialog(data.scene, onSelect = store::setScene, onClose = { showScenes = false })
        if (showTutorial) TutorialDialog(onClose = { showTutorial = false })
        if (data.pendingMapNotice && noDialog && !growing) {
            MapNotice(
                onOpen = { store.dismissMapNotice(); showMap = true },
                onDone = store::dismissMapNotice,
            )
        }
        if (growthNotice) {
            key(celebrated, data.reachedStage) {
                GrowthNotice(data.reachedStage, data.lineage, onDone = store::markStageCelebrated)
            }
        }
        if (evolving && noDialog) {
            key(celebrated, data.reachedStage) {
                EvolutionOverlay(celebrated, data.reachedStage, data.lineage, onDone = {
                    GrowthRules.learnedMove(data.reachedStage, data.lineage)?.let { cue = WanderCue(++cueId, it) }
                    store.markStageCelebrated()
                })
            }
        }
    }
}

@Composable
private fun StatusPanel(
    store: CarlingStore,
    data: CarlingData,
    onFeed: () -> Unit,
    onTripCards: () -> Unit,
    onMap: () -> Unit,
    onScenes: () -> Unit,
    onItems: () -> Unit,
    onFoods: () -> Unit,
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
                Text(GrowthRules.stageName(cur, data.lineage), style = gameText(20.sp))
                Spacer(Modifier.weight(1f))
                data.lineage?.let { Text("${it.label} 계열", style = gameText(19.sp), color = Color(it.color)) }
                    ?: Text("계열 미정 (D14)", style = gameText(17.sp), color = Muted)
            }
            Spacer(Modifier.height(10.dp))
            GameGauge(
                icon = "⚡",
                label = "XP",
                value = if (next != null) "${data.totalXp} / ${next.cumXp}" else "${data.totalXp}",
                progress = if (next != null) GrowthRules.stageProgress(data.totalXp, data.stageSpec.cumXp, next.cumXp) else 1f,
                fill = listOf(Color(0xFFB6F28A), Color(0xFF4DBB5E)),
            )
            Spacer(Modifier.height(6.dp))
            GameGauge(
                icon = "🗺",
                label = "MP",
                value = if (next != null) "${data.totalMp} / ${next.cumMp}" else "${data.totalMp}",
                progress = if (next != null) GrowthRules.stageProgress(data.totalMp, data.stageSpec.cumMp, next.cumMp) else 1f,
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
                Text("다음 ${GrowthRules.stageName(n.index, data.lineage)}: D${n.day} · XP ${n.cumXp} · MP ${n.cumMp}$extra",
                    fontSize = 16.sp, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                if (!GrowthRules.itemsUnlocked(data.reachedStage)) {
                    Text("🔒 성장 1이 되면 아이템이 열려요", fontSize = 16.sp, color = Gold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                // 진화 퀘스트 — 조건 충족 시 완료 버튼
                if (n.evoIndex != null && n.evoIndex in data.ownedCards && n.evoIndex !in data.doneQuests) {
                    Spacer(Modifier.height(8.dp))
                    val can = store.canCompleteQuest(n.evoIndex)
                    GameButton(
                        text = if (can) "진화 퀘스트 완료" else "트립 ${store.savedTripCount()}/${evo?.memoriesNeeded ?: 1}장 저장 필요",
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                GameButton(
                    text = if (canFeed) "밥 주기 (+5 XP)" else "오늘은 배불러요",
                    icon = "🍙",
                    top = Color(0xFFFFB648), bottom = Color(0xFFE07A12),
                    enabled = canFeed,
                    modifier = Modifier.weight(2.4f),
                    onClick = onFeed,
                )
                GameSlot("🍊", "특산물", enabled = true, badge = data.foods.values.sum(), modifier = Modifier.weight(1f), onClick = onFoods)
            }
            Spacer(Modifier.height(10.dp))
            // 교감 — 놀아주기 / 말 걸기 (친밀도 XP)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameSlot("🎈", "놀아주기", enabled = "PLAY" !in data.intimacyActionsToday, modifier = Modifier.weight(1f)) { store.interact("PLAY") }
                GameSlot("💬", "말 걸기", enabled = "TALK" !in data.intimacyActionsToday, modifier = Modifier.weight(1f)) { store.interact("TALK") }
                GameSlot("🖼", "배경", enabled = true, modifier = Modifier.weight(1f), onClick = onScenes)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                GameSlot("🃏", "트립카드", enabled = true, badge = data.tripCards.recentDestinations().size, modifier = Modifier.weight(1.2f), onClick = onTripCards)
                GameSlot("🗺", "지도", enabled = true, modifier = Modifier.weight(1f), onClick = onMap)
                val itemsOpen = GrowthRules.itemsUnlocked(data.reachedStage)
                GameSlot(if (itemsOpen) "🎁" else "🔒", "아이템", enabled = itemsOpen, selected = data.equippedItem != null, modifier = Modifier.weight(1f), onClick = onItems)
                Column(Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📍 위치", style = gameText(17.sp))
                    Spacer(Modifier.height(6.dp))
                    GameToggle(checked = data.locationConsent == true, onChange = onLocationToggle)
                }
            }
        }
    }
}

/**
 * 튜토리얼(도움말) 내용 — 한곳에서 관리한다.
 * ⚠ 기능을 추가·변경하면 여기 항목도 함께 업데이트할 것.
 */
private val tutorialSections: List<Triple<String, String, String>> = listOf(
    Triple("🥚", "첫 만남", "알은 모두에게 하나, 고르지 않아요. 첫 운전을 마치면 알을 네 번 눌러 깨우고, 이름을 지어주면 나만의 카링이 돼요."),
    Triple("🚗", "함께 달리기", "운전을 마칠 때마다 트립카드 한 장이 생겨요. 거리가 아니라 '함께한 횟수'로 자라요."),
    Triple("🃏", "우리의 트립카드", "트립은 자동으로 쌓여요. 목적지별 최근 20곳을 보여주고, 같은 곳을 다시 가면 맨 위로 올라와요. 모든 트립은 Drive Log에 남아요."),
    Triple("⚡", "성장 에너지 (XP)", "트립·안전운전·연료 보충·교감으로 하루 최대 50. 180일 동안 성장 6번 + 진화 3번을 거쳐요."),
    Triple("⭐", "지도 포인트 (MP)", "트립카드를 저장하면 받아요. 새 길 +3, 익숙한 길 +1 (운행일마다 2장까지). 저장은 그보다 많이 해도 돼요."),
    Triple("🗺", "지도", "처음엔 비어 있어요. 운전을 마칠 때마다 달린 길이 선으로 켜지고, 경로를 기록하지 못한 곳은 도착지에 불빛으로 보여요. 자주 달릴수록 밝아지고, 확대하면 경로를 자세히 볼 수 있어요. 위치 사용이 켜져 있어야 해요."),
    Triple("🌤", "날씨 인사", "날짜가 바뀐 뒤 첫 운전 때 말을 걸어요. 오전에는 오늘 예보를, 오후에는 지금 날씨를 알려줘요. 위치 사용이 켜져 있어야 해요."),
    Triple("🔊", "음성", "주행을 시작하거나 마칠 때 짧게 말을 걸어요. 화면 오른쪽 위에서 언제든 끌 수 있어요."),
    Triple("🖼", "배경", "알이 깨어나면 도시·바닷가 해안·맑은 하늘·고요한 숲 중에서 고를 수 있어요. 낮과 밤은 차량 시계에 맞춰 저절로 바뀌어요."),
    Triple("🎁", "아이템", "성장 1이 되면 열려요. 선글라스·목걸이·신발 등 10가지 중 하나를 골라 카링을 꾸며 보세요."),
    Triple("🍊", "지역 특산물", "제주도·강원도·부산·대구·전주를 방문하면 특산물을 받아요(지역마다 하루 1개, 위치 사용 필요). 배고플 때 먹이면 경험치가 더 많이 올라요."),
    Triple("🌱", "게이지", "XP와 MP 숫자는 누적으로 보여요. 성장·진화하면 게이지가 모두 비워지고, 다음 단계까지 새로 채워요. 넘친 XP와 MP는 이어지지 않아요."),
    Triple("🎨", "계열", "처음엔 진주색 아기 키링이에요. 성장 2(빠르면 D14)가 되면 운전 습관에 따라 계열(바람·빛·나무·물·흙)이 정해지고, 몸 색과 외형이 바뀌어요. 한 번 정해진 계열은 바뀌지 않아요."),
    Triple("🏷", "이름", "자랄 때마다 이름이 바뀌어요. 바람 계열이면 산들링, 나래링을 거쳐 하늘링 완전체가 돼요. 빛은 섬광링, 나무는 우듬지링, 물은 바다링, 흙은 질주링이 마지막 모습이에요."),
    Triple("✨", "진화", "D30·D90·D180부터, XP와 MP에 진화카드와 특별 퀘스트까지 갖추면 진화해요. 진화카드는 D20·D80·D170부터 운전한 날 추첨해 늦어도 6번째에 받고, 퀘스트는 트립카드를 1·3·5장 저장하면 완료할 수 있어요. 단계는 건너뛰지 않고, 성장할 때는 알림과 함께 '짠' 하고 바뀌고, 진화할 때는 변신 장면과 함께 새로운 동작을 배워요."),
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
internal fun Scrim(content: @Composable () -> Unit) {
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
        TextButton(onClick = { open = !open }) { Text(if (open) "개발자 도구 ▲" else "🛠", fontSize = 18.sp, color = Ink, modifier = Modifier.onScene()) }
        if (!open) return@Column
        Column(
            Modifier.background(Panel, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val d = store.data
            DebugLine("단계", "${GrowthRules.stageName(d.reachedStage, d.lineage)} · ${d.stageSpec.label} (idx ${d.reachedStage})")
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
                listOf<Pair<String, Float?>>("실제 시각" to null, "낮" to 0f, "노을" to 0.5f, "밤" to 1f).forEach { (label, value) ->
                    FilterChip(selected = store.devNightness == value, onClick = { store.devNightness = value }, label = { Text(label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val look = store.devLook
                OutlinedButton(onClick = {
                    val from = look?.first ?: (GrowthRules.MAX_STAGE + 1)
                    store.devLook = (from - 1).coerceAtLeast(0) to lineage
                }) { Text("◀ 외형") }
                OutlinedButton(onClick = { store.devLook = null }) { Text(look?.let { "${it.first}단계 · 실제로" } ?: "실제 외형") }
                OutlinedButton(onClick = {
                    val from = look?.first ?: -1
                    store.devLook = (from + 1).coerceAtMost(GrowthRules.MAX_STAGE) to lineage
                }) { Text("외형 ▶") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { store.clock.skipDays(1) }) { Text("다음 날로") }
                OutlinedButton(onClick = store::devRestartFromD1) { Text("D1부터") }
                OutlinedButton(onClick = store::reset) { Text("알부터") }
            }
            OutlinedButton(onClick = store::devReplayStageChange) { Text("변화 장면 다시 보기") }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Region.entries.forEach { r ->
                    OutlinedButton(onClick = { store.devGiveFood(r) }) { Text(r.foodIcon) }
                }
                OutlinedButton(onClick = store::devMakeHungry) { Text("배고픔") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { store.devResetGreeting(9) }) { Text("오전 인사 준비") }
                OutlinedButton(onClick = { store.devResetGreeting(15) }) { Text("오후 인사 준비") }
                OutlinedButton(onClick = simulator::driveToNextSample) { Text("새 길 주행") }
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
