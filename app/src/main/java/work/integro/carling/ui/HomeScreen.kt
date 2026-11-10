package work.integro.carling.ui

import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.horizontalScroll
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
import work.integro.carling.voice.Babble
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
import work.integro.carling.data.Scene
import work.integro.carling.data.Personality
import work.integro.carling.data.recentDestinations
import work.integro.carling.game.DevSimulator
import work.integro.carling.game.DayPart
import work.integro.carling.game.CareIssue
import work.integro.carling.game.GrowthRules
import work.integro.carling.game.Light
import work.integro.carling.game.Lineage
import work.integro.carling.game.Vehicle
import work.integro.carling.game.VEHICLE_GIFT_STAGE
import work.integro.carling.game.VEHICLE_PICK_STAGE
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
    var showDex by remember { mutableStateOf(false) }
    var showCare by remember { mutableStateOf(false) }
    var openCard by remember { mutableStateOf<Int?>(null) }
    var showMap by remember { mutableStateOf(false) }
    var showScenes by remember { mutableStateOf(false) }
    var showItems by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(false) }
    var justFed by remember { mutableStateOf(false) }
    var playHint by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { greetedToday = store.greetIfNewDay() }

    val careTop = data.careQuests.minByOrNull { it.priority }
    val line = when {
        careTop != null -> careTop.line
        vehicle.isCharging || vehicle.chargePortOpen -> personality.mealLine
        playHint -> "저를 누른 채 이리저리 끌어 주세요. 같이 놀아요!"
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
    // 성장·색 변화·진화 모두 같은 변화 장면을 쓴다(서비스 시각화 V3)
    val evolving = growing
    // 트립카드 등 다른 창을 다 닫은 뒤에 재생한다. 주행 중에는 이 화면 자체가 없어 정차 후로 미뤄진다.
    val noDialog = data.pendingCard == null && openCard == null && !showTripCards && !showDex && !showCare && !showMap && !showItems && !showScenes && !showTutorial
    val lookStage = store.devLook?.first ?: if (growing) celebrated else data.reachedStage
    var cueId by remember { mutableIntStateOf(0) }
    var cue by remember { mutableStateOf<WanderCue?>(null) }
    val lookLineage = store.devLook?.second ?: data.lineage
    // 카링 목소리(옹알이)는 보이는 모습의 단계·계열을 따르고, 음성 켜짐/꺼짐을 같이 쓴다
    LaunchedEffect(lookStage, lookLineage, data.voiceOn) {
        Babble.stage = lookStage
        Babble.lineage = lookLineage
        Babble.enabled = data.voiceOn
    }
    val playground = @Composable { areaModifier: Modifier ->
        Wandering(
            size = carlingSize,
            enabled = data.pendingCard == null && !showTripCards && !showDex && openCard == null && !showMap && !showTutorial && !showScenes && !showItems && !(evolving && noDialog),
            specials = GrowthRules.knownMoves(lookStage, lookLineage),
            cue = cue,
            personality = personality,
            tapLines = personality.tapLines,
            onPetted = store::pet,
            onPlayed = { store.play(); playHint = false },
            care = careTop,
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
                    vehicle = data.ridingVehicle,
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
    var dockOpen by rememberSaveable { mutableStateOf(true) }

    Box(Modifier.fillMaxSize()) {
        // 배경을 가리지 않도록 상태는 왼쪽 위, 기능은 아래 독에 둔다
        Column(
            Modifier.fillMaxSize().padding(top = if (compact) 150.dp else 120.dp, bottom = if (dockOpen) 118.dp else 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            playground(Modifier.fillMaxWidth().weight(1f))
            caption()
        }
        StatusHud(store, data, Modifier.align(Alignment.TopStart).padding(16.dp))
        CareChip(data.careQuests, onClick = { showCare = true }, modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
        FunctionDock(
            store = store,
            data = data,
            open = dockOpen,
            onToggle = { dockOpen = !dockOpen },
            onFeed = { store.feed(); justFed = true },
            onPlay = { playHint = true },
            onTripCards = { showTripCards = true },
            onDex = { showDex = true },
            onMap = { showMap = true },
            onScenes = { showScenes = true },
            onItems = { showItems = true },
            onLocationToggle = { allowed ->
                store.setLocationConsent(allowed)
                if (allowed) onLocationAllowed()
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp, start = 16.dp, end = 16.dp),
        )

        // 도움말(튜토리얼) — 언제든 열어 다시 볼 수 있다
        Row(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            TextButton(onClick = store::toggleVoice) {
                Text(if (data.voiceOn) "🔊 음성 켜짐" else "🔇 음성 꺼짐", fontSize = 18.sp, color = Ink, modifier = Modifier.onScene())
            }
            TextButton(onClick = { showTutorial = true }) {
                Text("❓ 도움말", fontSize = 18.sp, color = Ink, modifier = Modifier.onScene())
            }
        }

        if (BuildConfig.DEBUG) DevPanel(store, vehicle, Modifier.align(Alignment.TopEnd).padding(top = 60.dp, end = 12.dp))

        if (showTripCards) {
            TripCardsDialog(data.tripCards, onOpen = { openCard = it }, onClose = { showTripCards = false })
        }
        if (showCare) CareQuestDialog(data.careQuests, data.intimacy, data.heartPoints, onClose = { showCare = false })
        store.careNotice?.let { n -> key(n) { CareDoneNotice(n, onDone = { store.careNotice = null }) } }
        if (noDialog && !growing) data.pendingGift?.let { GiftDialog(it, onClaim = store::claimGift) }
        if (showDex) DexDialog(data.tripCards, data.lineage, data.reachedStage, onOpen = { openCard = it }, onClose = { showDex = false })
        data.pendingCard?.let { number ->
            data.tripCards.firstOrNull { it.number == number }?.let { card ->
                NewTripCardDialog(
                    card = card,
                    canSave = store.canSave(card),
                    xpIfSaved = store.saveXpFor(card),
                    onSave = { store.saveTripCard(number) },
                    onClose = store::dismissTripCard,
                )
            }
        }
        openCard?.let { number ->
            data.tripCards.firstOrNull { it.number == number }?.let { card ->
                TripCardDetailDialog(
                    card = card,
                    canSave = store.canSave(card),
                    xpIfSaved = store.saveXpFor(card),
                    onSave = { store.saveTripCard(number) },
                    onClose = { openCard = null },
                )
            }
        }
        if (showMap) CarlingMapScreen(data.mapRoutes, store.routeTracks, onClose = { showMap = false })
        if (showItems) {
            ItemsDialog(
                data, personality,
                onSelect = store::equipItem,
                onRide = store::ride,
                onPickVehicle = store::pickVehicle,
                onUse = { if (store.useItem(it)) justFed = true },
                onClose = { showItems = false },
            )
        }
        if (!growing && GrowthRules.itemsUnlocked(data.reachedStage) && !data.itemIntroSeen && data.pendingCard == null && openCard == null) {
            ItemUnlockDialog(
                data,
                personality,
                onOpenItems = { store.markItemIntroSeen(); showItems = true },
                onLater = store::markItemIntroSeen,
            )
        }
        if (showScenes) ScenePickerDialog(data.scene, data.ownedScenes(), onSelect = store::setScene, onClose = { showScenes = false })
        if (showTutorial) TutorialDialog(onClose = { showTutorial = false })
        if (data.pendingMapNotice && noDialog && !growing) {
            MapNotice(
                onOpen = { store.dismissMapNotice(); showMap = true },
                onDone = store::dismissMapNotice,
            )
        }
        if (evolving && noDialog) {
            // 한 번에 여러 단계를 넘었으면 한 단계씩 보여준다
            val to = (celebrated ?: data.reachedStage) + 1
            key(celebrated, to) {
                EvolutionOverlay(
                    from = celebrated ?: 0,
                    to = to,
                    lineage = data.lineage,
                    newScenes = Scene.unlockedAt(to).map { it.label },
                    gift = when (to) {
                        VEHICLE_GIFT_STAGE -> data.lineage?.let { "${Vehicle.of(it).label}를 선물로 받았어요! 아이템 › 타기에서 타 보세요." }
                        VEHICLE_PICK_STAGE -> "이동수단 하나를 골라 받을 수 있어요! 아이템 › 타기"
                        else -> null
                    },
                    onDone = {
                        GrowthRules.learnedMove(to, data.lineage)?.let { cue = WanderCue(++cueId, it) }
                        store.markStageCelebrated(to)
                    },
                )
            }
        }
    }
}

/** 왼쪽 위의 작은 상태 표시. 단계·계열, 다음 모습까지의 운전일·XP, 친밀도·포만감을 한눈에 보여준다. */
@Composable
private fun StatusHud(store: CarlingStore, data: CarlingData, modifier: Modifier = Modifier) {
    val cur = data.reachedStage
    val next = if (cur < GrowthRules.MAX_STAGE) GrowthRules.STAGES[cur + 1] else null
    val days = data.drivingDayCount
    Column(
        modifier
            .widthIn(max = 460.dp)
            .background(Color(0xB3060A24), RoundedCornerShape(22.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(GrowthRules.stageName(cur, data.lineage), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.width(10.dp))
            data.lineage?.let { Text("${it.label} 계열", fontSize = 17.sp, color = Color(it.color), fontWeight = FontWeight.Medium) }
                ?: Text("계열 미정", fontSize = 16.sp, color = Muted)
            Spacer(Modifier.weight(1f))
            next?.let { Text("다음: ${GrowthRules.stageName(it.index, data.lineage)}", fontSize = 15.sp, color = Muted) }
        }
        HudBar("🚗 운전일", if (next != null) "$days / ${next.drivingDays}" else "$days",
            if (next != null) GrowthRules.stageProgress(days, data.stageSpec.drivingDays, next.drivingDays) else 1f, Color(0xFF6FB4FF))
        HudBar("⚡ XP", if (next != null) "${data.totalXp} / ${next.cumXp}" else "${data.totalXp}",
            if (next != null) GrowthRules.stageProgress(data.totalXp, data.stageSpec.cumXp, next.cumXp) else 1f, Color(0xFF7BDB6A))
        HudBar("💗 친밀도", "${data.intimacy}", data.intimacy / 100f, Color(0xFFFF7FA8))
        HudBar("🍙 포만감", "${data.fullness}", data.fullness / 100f, Color(0xFFFFC04D))
        next?.let { n ->
            val daysLeft = n.drivingDays - days
            if (data.totalXp >= n.cumXp && daysLeft > 0) {
                Text("경험치는 충분해! ${daysLeft}번만 더 같이 달리면 자랄 수 있어.", fontSize = 15.sp, color = Gold)
            }
        }
    }
}

@Composable
private fun HudBar(label: String, value: String, progress: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 16.sp, color = Ink, modifier = Modifier.width(96.dp))
        Box(Modifier.weight(1f).height(12.dp).background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(12.dp).background(color, RoundedCornerShape(6.dp)))
        }
        Text(value, fontSize = 16.sp, color = Ink, textAlign = TextAlign.End, modifier = Modifier.width(110.dp))
    }
}

/** 화면 아래의 기능 독. ▼로 접으면 작은 '메뉴' 단추만 남아 배경을 거의 가리지 않는다. */
@Composable
private fun FunctionDock(
    store: CarlingStore,
    data: CarlingData,
    open: Boolean,
    onToggle: () -> Unit,
    onFeed: () -> Unit,
    onPlay: () -> Unit,
    onTripCards: () -> Unit,
    onDex: () -> Unit,
    onMap: () -> Unit,
    onScenes: () -> Unit,
    onItems: () -> Unit,
    onLocationToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(26.dp)
    if (!open) {
        Box(
            modifier
                .background(Color(0xB3060A24), shape)
                .border(1.dp, Color.White.copy(alpha = 0.15f), shape)
                .clickable(onClick = onToggle)
                .padding(horizontal = 28.dp, vertical = 12.dp),
        ) { Text("▲ 메뉴", fontSize = 19.sp, color = Ink, fontWeight = FontWeight.Medium) }
        return
    }
    val canFeed = store.canFeedToday()
    val itemsOpen = GrowthRules.itemsUnlocked(data.reachedStage)
    val location = data.locationConsent == true
    Row(
        modifier
            .background(Color(0xB3060A24), shape)
            .border(1.dp, Color.White.copy(alpha = 0.15f), shape)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockButton("🍙", if (canFeed) "밥 주기" else "배불러요", enabled = canFeed, onClick = onFeed)
        DockButton("🎈", "놀아주기", onClick = onPlay)
        DockButton("🃏", "트립카드", badge = data.tripCards.count { !it.saved && store.canSave(it) }.takeIf { it > 0 }, onClick = onTripCards)
        DockButton("📖", "도감", onClick = onDex)
        DockButton("🗺", "지도", onClick = onMap)
        DockButton("🎁", "아이템", badge = if (data.canPickVehicle) 1 else data.usables.values.sum().takeIf { it > 0 }, selected = data.equippedItem != null || data.ridingVehicle != null, onClick = onItems)
        DockButton("🖼", "배경", badge = data.ownedScenes().size, onClick = onScenes)
        DockButton(if (location) "📍" else "📍", if (location) "위치 켜짐" else "위치 꺼짐", selected = location) { onLocationToggle(!location) }
        Spacer(Modifier.width(6.dp))
        DockButton("▼", "접기", onClick = onToggle)
    }
}

@Composable
private fun DockButton(
    icon: String,
    label: String,
    enabled: Boolean = true,
    badge: Int? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .padding(horizontal = 3.dp)
            .width(92.dp)
            .background(if (selected) Gold.copy(alpha = 0.22f) else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(if (enabled) 1f else 0.45f)) {
            Text(icon, fontSize = 32.sp)
            Text(label, fontSize = 15.sp, color = Ink, maxLines = 1)
        }
        if (badge != null && badge > 0) {
            Text(
                "$badge",
                fontSize = 13.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 10.dp).background(Color(0xFFE5507E), RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * 튜토리얼(도움말) 내용 — 한곳에서 관리한다.
 * ⚠ 기능을 추가·변경하면 여기 항목도 함께 업데이트할 것.
 */
private val tutorialSections: List<Triple<String, String, String>> = listOf(
    Triple("🥚", "첫 만남", "알은 모두에게 하나, 고르지 않아요. 첫 운전을 마치고 트립카드를 저장한 뒤 알을 네 번 두드려 깨우고, 이름을 지어주면 나만의 카링이 돼요."),
    Triple("🚗", "함께 달리기", "3분 이상 달릴 때마다 트립카드 한 장이 생겨요. 달리는 동안 카링은 화면 구석에서 은은하게 빛날 뿐이에요."),
    Triple("🃏", "트립카드 저장", "운전을 마치면 도착지·날짜·달린 길이 별자리로 그려진 트립카드가 나와요. 저장하지 않은 카드는 24시간 뒤 사라져요. 도착 후 24시간 안에 저장하면 아래 보상 칸이 뒤집혀 자동차와 아이템이 나오고 '저장 완료' 도장이 찍혀요. 저장한 카드 1장에 +10 XP, 안전 점수 95점 이상이면 +3 XP (하루 5장까지). 저장한 카드가 있는 날이 '운전일'이 돼요."),
    Triple("📖", "도감", "트립카드를 저장하면 앞면의 자동차가 도감에 모여요. 1975년 포니부터 지금의 현대·기아·제네시스, 미래 콘셉트카까지 100대이고, 일반·레어·에픽·전설 희귀도가 있어요. 카링 탭에는 만난 카링이 모이고, 아직 못 만난 카링은 실루엣으로 보여요."),
    Triple("⚡", "성장 에너지 (XP)", "트립카드 저장과 교감(쓰다듬기·밥 주기·놀아주기 각 하루 1회 +2)으로 하루 최대 71. XP는 자라도 줄지 않아요."),
    Triple("🌱", "자라는 방법", "최소 운전일과 누적 XP를 둘 다 채우면 다음 모습이 열려요. 7운전일 175 XP · 15운전일 375 XP · 30운전일 750 XP · 60운전일 1,500 XP · 90운전일 2,250 XP. 많이 달려도 운전일보다 빨리 자라지는 않고, 쉬어도 퇴화하지 않아요."),
    Triple("🎨", "계열", "처음엔 진주색 아기 카링이에요. 15운전일에 저장한 트립의 운전 습관으로 계열(바람·빛·나무·물·흙)이 정해져 색이 입혀지고, 한 번 정해지면 바뀌지 않아요."),
    Triple("✨", "진화", "30·60·90운전일에 세 번 진화해요. 바람은 산들링→날개링→하늘링, 빛은 반짝링→햇살링→태양링, 나무는 새싹링→잎새링→숲결링, 물은 물결링→파도링→바다링, 흙은 모래링→소호링→구미링이 돼요. 자랄 때마다 변화 장면과 축하 카드가 나와요."),
    Triple("🖼", "배경", "Dr.Pab의 심장 연구소에서 시작해요. 알이 깨어날 때와 자랄 때마다 새 배경을 하나씩 얻어 모두 8곳을 모을 수 있어요. 아침·낮·노을·밤은 차량 시계에 맞춰 저절로 바뀌어요."),
    Triple("🗺", "지도", "대한민국 지도가 안개로 덮여 있고, 함께 달린 길 주변 1km씩 밝혀져요. 처음엔 전국만 보이지만 다닌 시·도, 시군구, 동마다 필터가 생겨 눌러서 들어가 볼 수 있어요. 지역마다 밝혀진 땅이 몇 %인지 알려줘요. 위치 사용이 켜져 있어야 해요."),
    Triple("🌤", "날씨 인사", "날짜가 바뀐 뒤 첫 운전 때 말을 걸어요. 오전에는 오늘 예보를, 오후에는 지금 날씨를 알려줘요. 위치 사용이 켜져 있어야 해요."),
    Triple("🔊", "음성", "카링이 말할 때 \"뾰로롱\" 옹알이 목소리가 나요. 어릴수록 높고, 계열마다 음색이 달라요. 주행을 시작하거나 마칠 때는 \"뿅\" 하고 또렷하게 말을 걸어요. 화면 오른쪽 위에서 언제든 끌 수 있어요."),
    Triple("🎁", "아이템 · 꾸미기", "7운전일에 자라면 열려요. 리본·왕관·꽃 화관·하트 선글라스 등 10가지 젤리 아이템 중 하나로 카링을 꾸며 보세요. 진화한 모습에도 머리에 맞춰 보여요."),
    Triple("🚗", "아이템 · 타기", "이동수단을 모아 탈 수 있어요. 진화 2에 내 계열의 이동수단을 선물로 받고, 진화 3에 하나를 더 골라 받아요. 다시 누르면 내려요."),
    Triple("🍙", "아이템 · 사용하기", "트립카드를 저장할 때마다 간식(주먹밥·쿠키·딸기우유·푸딩·무지개 젤리)을 하나씩 받아 사용하기에 모여요. 먹이면 배가 부르고, 그날 첫 밥이면 +2 XP예요."),
    Triple("💗", "돌보기", "주차 중에만 교감할 수 있어요. 쓰다듬기(톡 터치)·밥 주기·놀아주기(카링을 누른 채 끌기)를 각각 하루 한 번 하면 +2 XP씩 받아요. 끌어서 옮기는 건 언제든 할 수 있어요. 카링은 진짜 젤리처럼 말랑해서, 누르면 콕 들어갔다 튕기고 끌거나 뛰면 몸이 출렁여요."),
    Triple("🛠", "차량 돌봄", "차가 아프면 카링도 아파요. 타이어 공기압이 빠지면 절뚝거리고, 연료가 부족하면 배고파하고, 엔진오일에 문제가 생기면 아파해요. 화면 위 경고를 누르면 돌봄 퀘스트가 보이고, 차를 고치면 차량 신호로 자동 완료돼 친밀도가 올라요. 친밀도가 가득 찬 뒤 쌓인 마음 포인트로 카링이 정비 쿠폰을 선물해요(시연용 · 블루핸즈 제휴 예정)."),
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
            DebugLine("누적", "운전일 ${d.drivingDayCount} · XP ${d.totalXp}")
            DebugLine("계열", d.lineage?.label ?: "미정")
            DebugLine("게임 날짜", "${store.clock.today()} (D${d.dayNumber(store.clock.today())})")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Lineage.entries.forEach { l ->
                    FilterChip(selected = lineage == l, onClick = { lineage = l }, label = { Text(l.label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { simulator.simulateDays(1, lineage) }) { Text("+1일") }
                OutlinedButton(onClick = { simulator.simulateDays(7, lineage) }) { Text("+7일") }
                OutlinedButton(onClick = { simulator.advanceToStage(1, lineage) }) { Text("성장까지") }
                OutlinedButton(onClick = { simulator.advanceToStage(2, lineage) }) { Text("2단계까지") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { simulator.advanceToStage(3, lineage) }) { Text("진화1까지") }
                OutlinedButton(onClick = { simulator.advanceToStage(4, lineage) }) { Text("진화2까지") }
                OutlinedButton(onClick = { simulator.advanceToStage(5, lineage) }) { Text("완성형까지") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = store.devDayPart == null, onClick = { store.devDayPart = null }, label = { Text("실제 시각") })
                DayPart.entries.forEach { part ->
                    FilterChip(selected = store.devDayPart == part, onClick = { store.devDayPart = part }, label = { Text(part.label) })
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = store::devReplayStageChange) { Text("변화 장면 다시 보기") }
                OutlinedButton(onClick = store::devGrantVehicles) { Text("이동수단 모두") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CareIssue.entries.forEach { c ->
                    FilterChip(selected = c in store.devCare, onClick = { store.devToggleCare(c) }, label = { Text("${c.icon} ${c.chip}") })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = store::devGiveItems) { Text("간식 받기") }
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
