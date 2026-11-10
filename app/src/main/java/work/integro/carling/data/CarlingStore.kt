package work.integro.carling.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import work.integro.carling.game.CarCatalog
import work.integro.carling.game.CareIssue
import work.integro.carling.game.CareRules
import work.integro.carling.game.Coupon
import work.integro.carling.game.CouponKind
import work.integro.carling.game.CarlingItem
import work.integro.carling.game.DayPart
import work.integro.carling.game.GameClock
import work.integro.carling.game.GrowthRules
import work.integro.carling.game.Light
import work.integro.carling.game.Lineage
import work.integro.carling.game.Region
import work.integro.carling.game.Vehicle
import work.integro.carling.game.UsableItem
import work.integro.carling.game.VEHICLE_GIFT_STAGE
import java.time.LocalDate
import kotlin.random.Random
import java.time.temporal.ChronoUnit

private const val TAG = "CarlingStore"

/** 끝난 주행 한 번의 원재료. TripTracker가 만든다. */
data class TripResult(
    val startMillis: Long,
    val endMillis: Long,
    val distanceKm: Float?,
    val harshEvents: Int?,
    val placeCell: String?,
    val placeName: String?,
    val region: Region? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val startLat: Double? = null,
    val startLng: Double? = null,
    /** 주행 중 기록한 실제 경로. */
    val track: List<GeoDot> = emptyList(),
)

/**
 * 카링 상태를 기기 안에 저장하고 성장 규칙 v0.5(서비스 시각화 V3)를 적용한다.
 * 트립카드를 저장해야 XP가 들어오고 그날이 운전일이 된다. XP는 차감하지 않고, 최소 운전일과 누적 XP로 자란다.
 * 모든 변경은 메인 스레드에서 호출한다.
 */
class CarlingStore(context: Context, val clock: GameClock) {

    private val prefs = context.getSharedPreferences("carling", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    var data by mutableStateOf(load())
        private set

    private fun load(): CarlingData =
        prefs.getString(KEY_DATA, null)?.let {
            runCatching { json.decodeFromString<CarlingData>(it) }
                .onFailure { e -> Log.w(TAG, "저장 데이터 읽기 실패, 초기화", e) }
                .getOrNull()
        }?.let(::migrate)?.let { d -> if (d.equippedItem?.hidden == true) d.copy(equippedItem = null) else d }
            ?.let(::giveCarsToOldCards)
            ?: CarlingData(growthVersion = GROWTH_VERSION)

    /** 자동차 카드가 생기기 전에 저장한 트립카드에도 차를 정해 준다(카드 번호로 정해 늘 같은 차가 나온다). */
    private fun giveCarsToOldCards(d: CarlingData): CarlingData {
        if (d.tripCards.none { it.saved && it.carModel == null }) return d
        val owned = d.tripCards.mapNotNull { it.carModel }.toMutableSet()
        return d.copy(tripCards = d.tripCards.map { c ->
            if (!c.saved || c.carModel != null) c
            else CarCatalog.draw(owned, Random(c.number)).let { car -> owned += car.id; c.copy(carModel = car.id) }
        })
    }

    /**
     * 이전 성장 규칙(D일·MP·진화카드)으로 쌓인 저장을 v0.5로 다시 계산한다.
     * 저장한 트립카드로 운전일과 XP를 다시 세고, 바뀐 단계는 변화 장면 없이 맞춘다.
     */
    private fun migrate(d: CarlingData): CarlingData {
        if (d.growthVersion >= GROWTH_VERSION) return d
        val rewarded = mutableMapOf<String, Int>()
        val cards = d.tripCards.map { c ->
            if (!c.saved) return@map c.copy(xpGained = 0)
            val n = rewarded[c.date] ?: 0
            val xp = GrowthRules.cardXp(c.safety, n)
            if (xp > 0) rewarded[c.date] = n + 1
            c.copy(xpGained = xp)
        }
        var next = d.copy(
            tripCards = cards,
            growthXp = cards.sumOf { it.xpGained },
            drivingDays = cards.filter { it.saved }.map { it.date }.toSet(),
            growthVersion = GROWTH_VERSION,
        )
        val stage = if (next.hatched) next.computedStage() else 0
        val lineage = if (stage >= GrowthRules.LINEAGE_STAGE) next.lineage ?: GrowthRules.dominantLineage(next.lineageScores) else null
        next = next.copy(reachedStage = stage, celebratedStage = stage, lineage = lineage)
        if (next.scene !in next.ownedScenes()) next = next.copy(scene = Scene.LAB)
        Log.i(TAG, "성장 규칙 v0.5로 변환: 운전일 ${next.drivingDayCount}, XP ${next.growthXp}, 단계 $stage")
        return next
    }

    /** 길별 실제 경로. 크기가 커서 자주 저장되는 상태와 나눠, 새 경로가 생길 때만 저장한다. */
    var routeTracks by mutableStateOf(loadTracks())
        private set

    private fun loadTracks(): Map<String, List<GeoDot>> =
        prefs.getString(KEY_TRACKS, null)?.let {
            runCatching { json.decodeFromString<Map<String, List<GeoDot>>>(it) }.getOrNull()
        } ?: emptyMap()

    private fun saveTrack(key: String?, track: List<GeoDot>) {
        if (key == null) return
        routeTracks = routeTracks + (key to track)
        prefs.edit().putString(KEY_TRACKS, json.encodeToString(routeTracks)).apply()
    }

    private fun update(block: (CarlingData) -> CarlingData) {
        data = block(data)
        prefs.edit().putString(KEY_DATA, json.encodeToString(CarlingData.serializer(), data)).apply()
    }

    // ---- 첫 만남(v1) ----

    /**
     * 알을 깨운다. 성격은 고르지 않고 첫 운전을 함께한 시간대가 정한다. 탄생일을 기록한다.
     * 깨우기 전에 저장한 첫 트립카드(#001)의 보상도 여기서 함께 기록한다.
     */
    fun hatch() {
        update {
            val personality = it.personality ?: personalityFor(it.lightTrips)
            it.copy(hatched = true, personality = personality, birthDate = clock.today().toString())
        }
        data.tripCards.firstOrNull()?.let { if (!it.saved) saveTripCard(it.number, force = true) }
        update { it.copy(celebratedStage = it.reachedStage) }
    }

    /** 태어난 뒤 첫 쓰다듬기 — 친밀도 퀘스트를 처음 배우는 순간. */
    fun completeFirstPet() {
        rolloverIfNeeded()
        update {
            it.copy(
                firstPetDone = true,
                petDate = clock.today().toString(),
                petsToday = 1,
                intimacy = (it.intimacy + GrowthRules.TRIP_INTIMACY).coerceAtMost(100),
            )
        }
        gainIntimacyXp("PET")
    }

    fun giveName(value: String) = update { it.copy(name = value) }

    fun finishGuide() = update { it.copy(guideSeen = true) }

    /** 첫 만남 온보딩에서 한 단계 뒤로 — 실수로 눌렀거나 다시 보고 싶을 때. */
    fun onboardingBack() = update {
        when {
            // 위치 동의 → 지도 첫 불빛
            it.name != null && it.firstPetDone && it.guideSeen && it.locationConsent == null ->
                it.copy(guideSeen = false)
            // 지도 첫 불빛 → 첫 교감
            it.name != null && it.firstPetDone && !it.guideSeen ->
                it.copy(firstPetDone = false)
            // 첫 교감 → 이름 짓기
            it.name != null && !it.firstPetDone ->
                it.copy(name = null)
            // 이름 짓기 → 알 깨우기(다시 깨우기 전 상태)
            it.hatched && it.name == null ->
                it.copy(hatched = false, personality = null, birthDate = null)
            else -> it
        }
    }

    /** 위치는 지도의 길, 처음 간 곳 판정, 날씨 인사에 쓴다. 언제든 바꿀 수 있다. */
    fun setLocationConsent(allowed: Boolean) = update {
        it.copy(locationConsent = allowed, locationConsentVersion = CURRENT_LOCATION_CONSENT)
    }

    val usesLocation: Boolean get() = data.locationConsent == true

    /** 날씨 서비스로 어림한 위치를 보내도 되는지. 그 안내를 읽고 동의한 경우에만 된다. */
    val usesWeather: Boolean get() = usesLocation && !data.needsLocationReconsent

    // ---- 날씨 인사 ----

    /** 주행 위젯에 잠깐 보여줄 인사. 저장하지 않는다. */
    var driveGreeting by mutableStateOf<String?>(null)

    /** 개발용: 인사에 쓸 시각. null이면 실제 시각을 따른다. */
    var devGreetingHour by mutableStateOf<Int?>(null)

    /** 날짜가 바뀐 뒤 첫 운전이면 true를 돌려주고 오늘 인사한 것으로 표시한다. 깨어나기 전에는 인사하지 않는다. */
    fun takeDailyGreeting(): Boolean {
        val today = clock.today().toString()
        if (!data.hatched || data.weatherGreetDate == today) return false
        update { it.copy(weatherGreetDate = today) }
        return true
    }

    /** 개발용: 다음 주행에서 인사를 다시 하게 한다. */
    fun devResetGreeting(hour: Int) {
        devGreetingHour = hour
        update { it.copy(weatherGreetDate = null) }
    }

    private fun personalityFor(lightTrips: Map<Light, Int>): Personality {
        val light = lightTrips.maxByOrNull { it.value }?.key ?: Light.ofHour(clock.hourOf(System.currentTimeMillis()))
        return when (light) {
            Light.DAWN, Light.DAY -> Personality.DODO
            Light.SUNSET -> Personality.NARUN
            Light.STAR -> Personality.LUMI
        }
    }

    // ---- 진행 중 주행 ----

    fun beginTrip(startMillis: Long, startOdometerKm: Float?) =
        update { it.copy(activeTrip = ActiveTrip(startMillis, startOdometerKm)) }

    fun clearActiveTrip() = update { it.copy(activeTrip = null) }

    fun setTripStart(lat: Double, lng: Double) = update {
        it.copy(activeTrip = it.activeTrip?.copy(startLat = lat, startLng = lng))
    }

    fun dismissMapNotice() = update { it.copy(pendingMapNotice = false) }

    /**
     * 첫 운전은 위치 동의 전에 끝난다. 동의하면 차가 서 있는 곳(첫 운전의 도착지)에 첫 길을 켠다.
     */
    fun lightFirstTrip(lat: Double, lng: Double) = update {
        if (!it.firstTripDone || it.tripCount > 1 || it.mapRoutes.isNotEmpty()) return@update it
        val change = it.mapRoutes.afterTrip(null, null, lat, lng, it.firstTripPlaceName, it.firstTripDistanceKm, clock.today().toString())
        it.copy(mapRoutes = change.routes)
    }

    // ---- 날짜 롤오버 / 단계 계산 ----

    /** 날이 바뀌면 교감 행동 집계를 비운다. XP는 누적값이라 접을 것이 없다. */
    private fun rolloverIfNeeded() {
        val today = clock.today().toString()
        if (data.xpDate != today) update { it.copy(xpDate = today, intimacyActionsToday = emptySet()) }
    }

    /** 운전일과 누적 XP로 단계를 다시 계산한다. 아기 카링 2단계에 처음 닿으면 계열을 정한다. 퇴화는 없다. */
    private fun refreshStage() = update {
        if (!it.hatched) return@update it
        val idx = maxOf(it.computedStage(), it.reachedStage)
        val lineage = if (it.lineage == null && idx >= GrowthRules.LINEAGE_STAGE) GrowthRules.dominantLineage(it.lineageScores) else it.lineage
        // 진화 2 기념: 내 계열의 이동수단
        val vehicles = if (idx >= VEHICLE_GIFT_STAGE && lineage != null) it.ownedVehicles + Vehicle.of(lineage) else it.ownedVehicles
        it.copy(reachedStage = idx, lineage = lineage, ownedVehicles = vehicles)
    }

    // ---- 차량 돌봄 ----

    /** 개발용: 차량 신호 대신 켜 둔 돌봄 문제. 저장하지 않는다. */
    var devCare by mutableStateOf<Set<CareIssue>>(emptySet())
    private val devTouched = HashSet<CareIssue>()

    fun devToggleCare(issue: CareIssue) {
        devTouched += issue
        devCare = if (issue in devCare) devCare - issue else devCare + issue
    }

    /** 방금 마친 돌봄 퀘스트(잠깐 보여주는 알림). 저장하지 않는다. */
    var careNotice by mutableStateOf<CareIssue?>(null)

    /**
     * 차량 신호로 돌봄 퀘스트를 맞춘다. 새로 생긴 문제는 퀘스트가 되고, 신호가 정상으로 돌아온 퀘스트는 완료된다.
     * 신호를 받지 못한 항목은 완료로 치지 않는다(연결이 끊겼다고 낫지 않는다).
     */
    fun syncCare(vehicleIssues: Set<CareIssue>, known: Set<CareIssue>) {
        if (!data.hatched) return
        val issues = vehicleIssues + devCare
        val judged = known + devTouched
        val started = issues - data.careQuests
        val resolved = data.careQuests.filter { it !in issues && it in judged }.toSet()
        if (started.isEmpty() && resolved.isEmpty()) return
        update {
            var intimacy = it.intimacy
            var heart = it.heartPoints
            repeat(resolved.size) {
                val sum = intimacy + CareRules.CARE_INTIMACY
                heart += (sum - 100).coerceAtLeast(0)
                intimacy = sum.coerceAtMost(100)
            }
            var gift = it.pendingGift
            if (gift == null && heart >= CareRules.GIFT_HEART) {
                heart -= CareRules.GIFT_HEART
                gift = Coupon(clock.now(), CouponKind.entries.random(), clock.today().toString())
            }
            it.copy(
                careQuests = it.careQuests - resolved + started,
                careDone = it.careDone + resolved.size,
                intimacy = intimacy,
                heartPoints = heart,
                pendingGift = gift,
            )
        }
        resolved.minByOrNull { it.priority }?.let { careNotice = it }
    }

    /** 카링이 내민 선물을 받아 쿠폰함에 넣는다. */
    fun claimGift() = update { d -> d.pendingGift?.let { d.copy(coupons = d.coupons + it, pendingGift = null) } ?: d }

    // ---- 타기 ----

    /** 이동수단에 타거나(가진 것만) 내린다(null). */
    fun ride(vehicle: Vehicle?) = update {
        if (vehicle == null || vehicle in it.ownedVehicles) it.copy(ridingVehicle = vehicle) else it
    }

    /** 진화 3 선물: 아직 없는 이동수단 하나를 골라 받는다. 받자마자 타 본다. */
    fun pickVehicle(vehicle: Vehicle) = update {
        if (!it.canPickVehicle || vehicle in it.ownedVehicles) it
        else it.copy(ownedVehicles = it.ownedVehicles + vehicle, vehiclePicked = true, ridingVehicle = vehicle)
    }

    /** 개발용: 이동수단을 모두 받는다. */
    fun devGrantVehicles() = update { it.copy(ownedVehicles = Vehicle.entries.toSet()) }

    /** 교감 3종(쓰다듬기·밥 주기·놀아주기)은 각각 하루 한 번 +2 XP. */
    private fun gainIntimacyXp(action: String) {
        rolloverIfNeeded()
        val xp = GrowthRules.intimacyXp(action, data.intimacyActionsToday)
        update { it.copy(intimacyActionsToday = it.intimacyActionsToday + action, growthXp = it.growthXp + xp) }
        refreshStage()
    }

    // ---- 하루 루틴(케어) ----

    fun greetIfNewDay(): Boolean {
        rolloverIfNeeded()
        dropExpiredCards()
        applyDailyDecay()
        refreshStage()
        val today = clock.today().toString()
        if (data.lastGreetDate == today) return false
        update {
            it.copy(
                lastGreetDate = today,
                intimacy = (it.intimacy + GrowthRules.GREET_INTIMACY).coerceAtMost(100),
            )
        }
        return true
    }

    fun canFeedToday(): Boolean = data.lastFeedDate != clock.today().toString()

    /** 밥·간식 주기 — 포만감(케어) + 교감 XP(하루 1회 +2). */
    fun feed() {
        if (!canFeedToday()) return
        update {
            it.copy(
                lastFeedDate = clock.today().toString(),
                fullness = (it.fullness + GrowthRules.FEED_FULLNESS).coerceAtMost(100),
            )
        }
        gainIntimacyXp("FEED")
    }

    /** 충전 세션 한 번 — 포만감만 채운다. 연료·충전 XP는 규칙 v0.5에서 보류했다. */
    fun onCharging() = update {
        it.copy(fullness = (it.fullness + GrowthRules.CHARGE_FULLNESS).coerceAtMost(100))
    }

    /** 사용하기: 모아 둔 간식을 하나 먹인다. 포만감이 차고, 오늘 첫 밥이면 밥 주기 교감(+2 XP)으로 친다. */
    fun useItem(item: UsableItem): Boolean {
        val owned = data.usables[item] ?: 0
        if (owned <= 0) return false
        update {
            it.copy(
                usables = it.usables + (item to owned - 1),
                fullness = (it.fullness + item.fullness).coerceAtMost(100),
                lastFeedDate = clock.today().toString(),
            )
        }
        gainIntimacyXp("FEED")
        return true
    }

    /** 쓰다듬기 — 친밀도(케어) 하루 상한 + 교감 XP(하루 1회 +2). */
    fun pet() {
        rolloverIfNeeded()
        val today = clock.today().toString()
        val count = if (data.petDate == today) data.petsToday else 0
        update {
            it.copy(
                petDate = today,
                petsToday = (count + 1).coerceAtMost(GrowthRules.DAILY_PETS),
                intimacy = if (count < GrowthRules.DAILY_PETS)
                    (it.intimacy + GrowthRules.PET_INTIMACY).coerceAtMost(100) else it.intimacy,
            )
        }
        gainIntimacyXp("PET")
    }

    /** 놀아주기 — 교감 XP(하루 1회 +2). */
    fun play() = gainIntimacyXp("PLAY")

    /** 아이템을 착용한다. null이면 벗는다. 아기 카링 성장(7운전일) 전에는 착용할 수 없다. */
    fun equipItem(item: CarlingItem?) = update {
        if (GrowthRules.itemsUnlocked(it.reachedStage)) it.copy(equippedItem = item) else it
    }

    fun markItemIntroSeen() = update { it.copy(itemIntroSeen = true) }

    /** [stage]까지(기본은 지금 단계) 변화 장면을 본 것으로 표시한다. 새로 얻은 배경이 있으면 바로 입혀 준다. */
    fun markStageCelebrated(stage: Int = data.reachedStage) = update {
        val to = stage.coerceAtMost(it.reachedStage)
        val from = it.celebratedStage ?: to
        val fresh = (from + 1..to).flatMap { s -> Scene.unlockedAt(s) }
        it.copy(celebratedStage = to, scene = fresh.lastOrNull() ?: it.scene)
    }

    /** 개발용: 지금 단계의 변화 장면을 다시 보여준다. */
    fun devReplayStageChange() = update {
        if (it.reachedStage > 0) it.copy(celebratedStage = it.reachedStage - 1) else it
    }

    fun toggleVoice() = update { it.copy(voiceOn = !it.voiceOn) }

    /** 얻은 배경만 고를 수 있다. */
    fun setScene(value: Scene) = update { if (value in it.ownedScenes()) it.copy(scene = value) else it }

    /** 개발용: 시간대 미리보기. null이면 실제 시각을 따른다. 저장하지 않는다. */
    var devDayPart by mutableStateOf<DayPart?>(null)

    /** 개발용: 외형 미리보기(단계, 계열). null이면 실제 단계를 따른다. 저장하지 않는다. */
    var devLook by mutableStateOf<Pair<Int, Lineage>?>(null)

    fun dismissTripCard() = update { it.copy(pendingCard = null) }

    private fun applyDailyDecay() {
        val today = clock.today()
        val last = data.lastDecayDate?.let(LocalDate::parse)
        if (last == null) {
            update { it.copy(lastDecayDate = today.toString()) }
            return
        }
        val days = ChronoUnit.DAYS.between(last, today).toInt()
        if (days <= 0) return
        update {
            it.copy(
                lastDecayDate = today.toString(),
                // 이미 하한보다 낮으면(성장 직후) 올리지 않는다
                fullness = (it.fullness - days * GrowthRules.DAILY_FULLNESS_DECAY)
                    .coerceAtLeast(minOf(it.fullness, GrowthRules.MIN_FULLNESS)),
            )
        }
    }

    // ---- 트립카드 저장 (XP · 운전일) ----

    /** 이 카드를 아직 저장할 수 있는지. 도착 후 24시간이 지나면 저장할 수 없고, 곧 지워진다. */
    fun canSave(card: TripCard): Boolean =
        !card.saved && clock.now() - card.endMillis <= GrowthRules.SAVE_WINDOW_MILLIS

    /** 지금 저장하면 받을 XP. 그 운전일에 이미 5장을 보상받았으면 0. */
    fun saveXpFor(card: TripCard): Int = GrowthRules.cardXp(
        card.safety,
        rewardedOnDay = data.tripCards.count { it.date == card.date && it.xpGained > 0 },
    )

    /**
     * 트립카드를 저장한다. 저장하면 XP를 받고 그 트립의 날이 운전일이 되며, 운전 습관이 계열 점수에 더해진다.
     * 저장은 되돌리지 않는다. [force]는 첫 트립카드처럼 저장 기간과 상관없이 저장할 때 쓴다.
     */
    fun saveTripCard(number: Int, force: Boolean = false) {
        val card = data.tripCards.firstOrNull { it.number == number } ?: return
        if (card.saved || (!force && !canSave(card))) return
        val xp = saveXpFor(card)
        val score = GrowthRules.scoreTrip(card.distanceKm, card.harshEvents, card.newPlace == true || card.newRoute)
        val car = CarCatalog.draw(data.tripCards.mapNotNull { it.carModel }.toSet())
        val item = UsableItem.draw()
        update {
            it.copy(
                usables = it.usables + (item to (it.usables[item] ?: 0) + 1),
                growthXp = it.growthXp + xp,
                drivingDays = it.drivingDays + card.date,
                lineageScores = mergeScores(it.lineageScores, score),
                tripCards = it.tripCards.map { c -> if (c.number == number) c.copy(saved = true, xpGained = xp, carModel = car.id, itemReward = item) else c },
            )
        }
        refreshStage()
    }

    fun savedTripCount(): Int = data.tripCards.count { it.saved }

    // ---- 주행 기록 ----

    fun recordTrip(trip: TripResult) {
        val seconds = (trip.endMillis - trip.startMillis) / 1000
        if (seconds < GrowthRules.MIN_TRIP_SECONDS) return

        val day = clock.dateOf(trip.startMillis).toString()
        val minutes = ((seconds + 30) / 60).toInt().coerceAtLeast(1)
        val light = Light.ofHour(clock.hourOf(trip.startMillis))
        val safety = GrowthRules.safetyScore(trip.harshEvents)

        // v1 첫 만남: 알이 깨어나기 전의 '첫 운전' → 트립카드 #001만 만들고 성장 엔진은 돌리지 않는다.
        if (!data.hatched) {
            if (data.firstTripDone) return
            update {
                it.copy(
                    firstTripDone = true,
                    firstTripMinutes = minutes,
                    firstTripDistanceKm = trip.distanceKm,
                    firstTripSafety = safety ?: 0,
                    firstTripPlaceName = trip.placeName,
                    togetherDays = it.togetherDays + day,
                    tripCount = 1,
                    totalMinutes = it.totalMinutes + minutes,
                    lightMinutes = it.lightMinutes + (light to minutes),
                    lightTrips = it.lightTrips + (light to 1),
                    placeCells = trip.placeCell?.let { c -> it.placeCells + c } ?: it.placeCells,
                    newPlaces = it.newPlaces + if (trip.placeCell != null) 1 else 0,
                    mapRoutes = it.mapRoutes.afterTrip(trip.startLat, trip.startLng, trip.lat, trip.lng, trip.placeName, trip.distanceKm, day).routes,
                    tripCards = it.tripCards + TripCard(
                        number = 1,
                        date = day,
                        startMillis = trip.startMillis,
                        minutes = minutes,
                        distanceKm = trip.distanceKm,
                        light = light,
                        safety = safety,
                        placeName = trip.placeName,
                        newPlace = true,
                        dayNumber = 1,
                        placeCell = trip.placeCell,
                        highlights = listOf("우리의 첫 드라이브"),
                        harshEvents = trip.harshEvents,
                    ),
                )
            }
            return
        }

        val before = data
        val newPlace = trip.placeCell?.let { it !in before.placeCells }
        val dayNum = before.dayNumber(clock.dateOf(trip.startMillis))

        val highlights = mutableListOf<String>()
        val track = simplifyTrack(trip.track)
        val mapChange = before.mapRoutes.afterTrip(trip.startLat, trip.startLng, trip.lat, trip.lng, trip.placeName, trip.distanceKm, day, track) { routeTracks[it.trackKey] }
        if (mapChange.newRoute) highlights += "새로운 길이 지도에 켜졌어요"
        else if (mapChange.lit) highlights += "달렸던 길이 지도에서 더 밝아졌어요"
        if (newPlace == true) highlights += "처음 보는 풍경"

        val next = before.copy(
            togetherDays = before.togetherDays + day,
            tripCount = before.tripCount + 1,
            totalMinutes = before.totalMinutes + minutes,
            lightMinutes = before.lightMinutes + (light to (before.lightMinutes[light] ?: 0) + minutes),
            lightTrips = before.lightTrips + (light to (before.lightTrips[light] ?: 0) + 1),
            placeCells = trip.placeCell?.let { before.placeCells + it } ?: before.placeCells,
            newPlaces = before.newPlaces + if (newPlace == true) 1 else 0,
            revisits = before.revisits + if (newPlace == false) 1 else 0,
            intimacy = (before.intimacy + GrowthRules.TRIP_INTIMACY).coerceAtMost(100),
        )

        val number = before.tripCount + 1
        saveTrack(mapChange.trackKey, track)
        update {
            next.copy(
                pendingCard = number,
                mapRoutes = mapChange.routes,
                pendingMapNotice = next.pendingMapNotice || mapChange.newRoute,
                tripCards = before.tripCards + TripCard(
                    number = number,
                    date = day,
                    startMillis = trip.startMillis,
                    minutes = minutes,
                    distanceKm = trip.distanceKm,
                    light = light,
                    safety = safety,
                    placeName = trip.placeName,
                    newPlace = newPlace,
                    dayNumber = dayNum,
                    placeCell = trip.placeCell,
                    newRoute = mapChange.newRoute,
                    highlights = highlights,
                    harshEvents = trip.harshEvents,
                    startName = before.tripCards.lastOrNull()?.placeName,
                    routeDots = cardDots(track, trip),
                ),
            )
        }
    }

    /** 카드에 그릴 경로를 24점 안쪽으로 줄인다. 경로가 없으면 출발·도착 두 점만 남긴다. */
    private fun cardDots(track: List<GeoDot>, trip: TripResult): List<GeoDot> {
        val pts = track.ifEmpty {
            listOfNotNull(
                trip.startLat?.let { la -> trip.startLng?.let { GeoDot(la, it) } },
                trip.lat?.let { la -> trip.lng?.let { GeoDot(la, it) } },
            )
        }
        if (pts.size <= 24) return pts
        return List(24) { i -> pts[i * (pts.size - 1) / 23] }
    }

    /** 도착 후 24시간 안에 저장하지 않은 카드를 지운다. 자동차·아이템은 저장할 때만 주므로 함께 사라진다. */
    fun dropExpiredCards() {
        val now = clock.now()
        val expired = data.tripCards.filter { !it.saved && now - it.endMillis > GrowthRules.SAVE_WINDOW_MILLIS }
        if (expired.isEmpty()) return
        val gone = expired.map { it.number }.toSet()
        update { it.copy(tripCards = it.tripCards.filterNot { c -> c.number in gone }, pendingCard = it.pendingCard?.takeIf { n -> n !in gone }) }
    }

    private fun mergeScores(a: Map<Lineage, Float>, b: Map<Lineage, Float>): Map<Lineage, Float> {
        if (b.isEmpty()) return a
        val m = a.toMutableMap()
        b.forEach { (k, v) -> m[k] = (m[k] ?: 0f) + v }
        return m
    }

    // ---- 개발용 ----

    fun devGiveItems() = update { it.copy(usables = UsableItem.entries.associateWith { u -> (it.usables[u] ?: 0) + 1 }) }

    fun devMakeHungry() = update { it.copy(fullness = GrowthRules.MIN_FULLNESS) }


    /**
     * 개발용: 이름·첫 만남·설정은 그대로 두고 D1 아기 카링으로 되돌린다.
     * 게임 시계와 성장, 계열, 트립카드, 아이템을 모두 초기화한다.
     */
    fun devRestartFromD1() {
        clock.reset()
        routeTracks = emptyMap()
        prefs.edit().remove(KEY_TRACKS).apply()
        devLook = null
        val old = data
        update {
            CarlingData(
                hatched = true,
                personality = old.personality,
                name = old.name,
                birthDate = clock.today().toString(),
                firstTripDone = true,
                firstPetDone = true,
                guideSeen = true,
                locationConsent = old.locationConsent,
                locationConsentVersion = old.locationConsentVersion,
                voiceOn = old.voiceOn,
                scene = Scene.LAB,
                growthVersion = GROWTH_VERSION,
            )
        }
    }

    fun reset() {
        prefs.edit().clear().apply()
        clock.reset()
        routeTracks = emptyMap()
        data = CarlingData(growthVersion = GROWTH_VERSION)
    }

    private companion object {
        const val KEY_DATA = "data"
        const val KEY_TRACKS = "tracks"
        const val GROWTH_VERSION = 5
    }
}
