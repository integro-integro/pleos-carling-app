package work.integro.carling.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import work.integro.carling.game.GameClock
import work.integro.carling.game.GrowthRules
import work.integro.carling.game.Light
import work.integro.carling.game.Lineage
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

private const val TAG = "CarlingStore"

/** 끝난 주행 한 번의 원재료. TripTracker가 만든다. */
data class TripResult(
    val startMillis: Long,
    val endMillis: Long,
    val distanceKm: Float?,
    val harshEvents: Int?,
    val placeCell: String?,
    val placeName: String?,
)

/**
 * 카링 상태를 기기 안에 저장하고 v1 성장 엔진을 적용한다.
 * XP(성장 에너지)와 MP(지도 포인트)는 누적이며 서로 환산하지 않는다.
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
        } ?: CarlingData()

    private fun update(block: (CarlingData) -> CarlingData) {
        data = block(data)
        prefs.edit().putString(KEY_DATA, json.encodeToString(CarlingData.serializer(), data)).apply()
    }

    // ---- 첫 만남(v1) ----

    /** 알을 깨운다. 성격은 고르지 않고 첫 운전을 함께한 시간대가 정한다. 탄생일을 기록한다. */
    fun hatch() = update {
        val personality = it.personality ?: personalityFor(it.lightTrips)
        it.copy(hatched = true, personality = personality, birthDate = clock.today().toString())
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
                intimacyActionsToday = it.intimacyActionsToday + "PET",
            )
        }
        refreshStage()
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

    /** 위치는 '처음 간 곳/다시 찾은 곳' 판정에만 쓴다. 언제든 바꿀 수 있다. */
    fun setLocationConsent(allowed: Boolean) = update { it.copy(locationConsent = allowed) }

    val usesLocation: Boolean get() = data.locationConsent == true

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

    // ---- 날짜 롤오버 / 단계 계산 ----

    /** 날이 바뀌면 오늘 XP·MP를 누적으로 접고 일일 집계를 리셋한다. */
    private fun rolloverIfNeeded() {
        val today = clock.today().toString()
        var d = data
        if (d.xpDate != today) {
            d = d.copy(
                totalXpPastDays = d.totalXpPastDays + d.xpToday,
                xpDate = today,
                tripsToday = 0,
                safeTripsToday = 0,
                fuelXpGiven = false,
                intimacyActionsToday = emptySet(),
            )
        }
        if (d.mpDate != today) {
            d = d.copy(
                totalMpPastDays = d.totalMpPastDays + d.mpTodayValue,
                mpDate = today,
                mpTodayValue = 0,
            )
        }
        if (d !== data) update { d }
    }

    private fun refreshStage() = update {
        val idx = it.stageIndexOn(clock.today())
        val lineage = if (it.lineage == null && idx >= 2) GrowthRules.dominantLineage(it.lineageScores) else it.lineage
        it.copy(reachedStage = idx, lineage = lineage)
    }

    // ---- 하루 루틴(케어) ----

    fun greetIfNewDay(): Boolean {
        rolloverIfNeeded()
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

    /** 밥·간식 주기 — 포만감(케어) + 친밀도 행동 XP(하루 2행동까지 +5). */
    fun feed() {
        if (!canFeedToday()) return
        rolloverIfNeeded()
        update {
            it.copy(
                lastFeedDate = clock.today().toString(),
                fullness = (it.fullness + GrowthRules.FEED_FULLNESS).coerceAtMost(100),
                intimacyActionsToday = it.intimacyActionsToday + "FEED",
            )
        }
        refreshStage()
    }

    /** 충전 세션 한 번 — 포만감 + 연료·충전 XP(하루 1회 +10). */
    fun onCharging() {
        rolloverIfNeeded()
        update {
            it.copy(
                fullness = (it.fullness + GrowthRules.CHARGE_FULLNESS).coerceAtMost(100),
                fuelXpGiven = true,
            )
        }
        refreshStage()
    }

    /** 쓰다듬기 — 친밀도(케어) 하루 상한 + 친밀도 행동 XP. */
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
                intimacyActionsToday = it.intimacyActionsToday + "PET",
            )
        }
        refreshStage()
    }

    /** 놀아주기 / 말 걸기 — 친밀도 행동 XP(서로 다른 2행동까지). */
    fun interact(action: String) {
        rolloverIfNeeded()
        update { it.copy(intimacyActionsToday = it.intimacyActionsToday + action) }
        refreshStage()
    }

    fun toggleHat() = update { if (it.hasHat) it.copy(wearingHat = !it.wearingHat) else it }

    fun toggleVoice() = update { it.copy(voiceOn = !it.voiceOn) }

    fun setScene(value: Scene) = update { it.copy(scene = value) }

    /** 개발용: 낮밤 미리보기. null이면 실제 시각을 따른다. 저장하지 않는다. */
    var devNightness by mutableStateOf<Float?>(null)

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
                fullness = (it.fullness - days * GrowthRules.DAILY_FULLNESS_DECAY)
                    .coerceAtLeast(GrowthRules.MIN_FULLNESS),
            )
        }
    }

    // ---- 트립카드 저장 (MP) ----

    /** 지금 저장하면 받을 MP. */
    fun saveMpFor(card: TripCard): Int = GrowthRules.saveMp(
        newPlace = card.newPlace,
        alreadyRewarded = card.mpGained > 0,
        rewardedOnDay = data.tripCards.count { it.date == card.date && it.mpGained > 0 },
    )

    /** 트립카드를 저장하거나 저장을 취소한다. 취소해도 받은 MP는 그대로이고, 다시 저장해도 더 주지 않는다. */
    fun toggleTripSaved(number: Int) {
        rolloverIfNeeded()
        val card = data.tripCards.firstOrNull { it.number == number } ?: return
        val mp = if (card.saved) 0 else saveMpFor(card)
        update {
            it.copy(
                mpTodayValue = it.mpTodayValue + mp,
                tripCards = it.tripCards.map { c ->
                    if (c.number == number) c.copy(saved = !c.saved, mpGained = c.mpGained + mp) else c
                },
            )
        }
        refreshStage()
    }

    // ---- 진화 퀘스트 ----

    fun savedTripCount(): Int = data.tripCards.count { it.saved }

    fun canCompleteQuest(evoIndex: Int): Boolean {
        val spec = GrowthRules.EVOLUTIONS[evoIndex] ?: return false
        return evoIndex !in data.doneQuests && savedTripCount() >= spec.memoriesNeeded
    }

    fun completeQuest(evoIndex: Int) {
        if (!canCompleteQuest(evoIndex)) return
        update { it.copy(doneQuests = it.doneQuests + evoIndex) }
        refreshStage()
    }

    // ---- 주행 기록 ----

    fun recordTrip(trip: TripResult) {
        val seconds = (trip.endMillis - trip.startMillis) / 1000
        if (seconds < GrowthRules.MIN_TRIP_SECONDS) return

        val day = clock.dateOf(trip.startMillis).toString()
        val minutes = ((seconds + 30) / 60).toInt().coerceAtLeast(1)
        val light = Light.ofHour(clock.hourOf(trip.startMillis))
        val safety = if (trip.harshEvents == null || trip.harshEvents == 0) 95
        else (92 - trip.harshEvents * 4).coerceAtLeast(72)

        // v1 첫 만남: 알이 깨어나기 전의 '첫 운전' → 트립카드 #001만 만들고 성장 엔진은 돌리지 않는다.
        if (!data.hatched) {
            if (data.firstTripDone) return
            update {
                it.copy(
                    firstTripDone = true,
                    firstTripMinutes = minutes,
                    firstTripDistanceKm = trip.distanceKm,
                    firstTripSafety = safety,
                    firstTripPlaceName = trip.placeName,
                    togetherDays = it.togetherDays + day,
                    tripCount = 1,
                    totalMinutes = it.totalMinutes + minutes,
                    lightMinutes = it.lightMinutes + (light to minutes),
                    lightTrips = it.lightTrips + (light to 1),
                    placeCells = trip.placeCell?.let { c -> it.placeCells + c } ?: it.placeCells,
                    newPlaces = it.newPlaces + if (trip.placeCell != null) 1 else 0,
                    lineageScores = mergeScores(it.lineageScores, GrowthRules.scoreTrip(trip.distanceKm, trip.harshEvents, trip.placeCell != null)),
                    hasHat = true,
                    wearingHat = true,
                    tripCards = it.tripCards + TripCard(
                        number = 1,
                        date = day,
                        startMillis = trip.startMillis,
                        minutes = minutes,
                        distanceKm = trip.distanceKm,
                        light = light,
                        safety = safety.takeIf { trip.harshEvents != null },
                        placeName = trip.placeName,
                        newPlace = true,
                        dayNumber = 1,
                        highlights = listOf("우리의 첫 드라이브"),
                    ),
                )
            }
            return
        }

        rolloverIfNeeded()
        val before = data
        val oldStage = before.reachedStage
        val safe = safety >= GrowthRules.SAFE_SCORE_MIN
        val newPlace = trip.placeCell?.let { it !in before.placeCells }

        // 진화카드 추첨 — 유효 운전일당 1회
        var ownedCards = before.ownedCards
        var cardFails = before.cardFails
        var cardDrawDate = before.cardDrawDate
        var cardGotLabel: String? = null
        val dayNum = before.dayNumber(clock.dateOf(trip.startMillis))
        if (cardDrawDate != day) {
            cardDrawDate = day
            val evo = GrowthRules.activeEvolution(dayNum, ownedCards)
            if (evo != null) {
                val fails = cardFails[evo.evoIndex] ?: 0
                val win = fails + 1 >= GrowthRules.CARD_PITY || Random.nextFloat() < GrowthRules.cardChance(before.tripsToday + 1)
                if (win) {
                    ownedCards = ownedCards + evo.evoIndex
                    cardGotLabel = "${GrowthRules.STAGES.first { it.evoIndex == evo.evoIndex }.label} 카드"
                } else {
                    cardFails = cardFails + (evo.evoIndex to fails + 1)
                }
            }
        }

        val highlights = mutableListOf<String>()
        if (newPlace == true) highlights += "처음 보는 풍경"
        cardGotLabel?.let { highlights += "$it 획득" }

        var next = before.copy(
            togetherDays = before.togetherDays + day,
            tripCount = before.tripCount + 1,
            totalMinutes = before.totalMinutes + minutes,
            tripsToday = before.tripsToday + 1,
            safeTripsToday = before.safeTripsToday + if (safe) 1 else 0,
            lightMinutes = before.lightMinutes + (light to (before.lightMinutes[light] ?: 0) + minutes),
            lightTrips = before.lightTrips + (light to (before.lightTrips[light] ?: 0) + 1),
            placeCells = trip.placeCell?.let { before.placeCells + it } ?: before.placeCells,
            newPlaces = before.newPlaces + if (newPlace == true) 1 else 0,
            revisits = before.revisits + if (newPlace == false) 1 else 0,
            intimacy = (before.intimacy + GrowthRules.TRIP_INTIMACY).coerceAtMost(100),
            lineageScores = mergeScores(before.lineageScores, GrowthRules.scoreTrip(trip.distanceKm, trip.harshEvents, newPlace)),
            ownedCards = ownedCards,
            cardFails = cardFails,
            cardDrawDate = cardDrawDate,
        )

        val newStage = next.stageIndexOn(clock.dateOf(trip.startMillis))
        val lineageWasNull = before.lineage == null
        val lineage = if (lineageWasNull && newStage >= 2) GrowthRules.dominantLineage(next.lineageScores) else before.lineage
        next = next.copy(reachedStage = newStage, lineage = lineage)

        if (newStage > oldStage) highlights += "${GrowthRules.STAGES[newStage].label} 도달"
        if (lineageWasNull && lineage != null) highlights += "${lineage.label} 계열이 되었어요"

        val number = before.tripCount + 1
        update {
            next.copy(
                pendingCard = number,
                tripCards = before.tripCards + TripCard(
                    number = number,
                    date = day,
                    startMillis = trip.startMillis,
                    minutes = minutes,
                    distanceKm = trip.distanceKm,
                    light = light,
                    safety = safety.takeIf { trip.harshEvents != null },
                    placeName = trip.placeName,
                    newPlace = newPlace,
                    dayNumber = dayNum,
                    driveXpToday = GrowthRules.driveXp(next.tripsToday, next.safeTripsToday),
                    highlights = highlights,
                ),
            )
        }
    }

    private fun mergeScores(a: Map<Lineage, Float>, b: Map<Lineage, Float>): Map<Lineage, Float> {
        if (b.isEmpty()) return a
        val m = a.toMutableMap()
        b.forEach { (k, v) -> m[k] = (m[k] ?: 0f) + v }
        return m
    }

    // ---- 개발용 ----

    fun devGrantCard(evoIndex: Int) {
        update { it.copy(ownedCards = it.ownedCards + evoIndex) }
        refreshStage()
    }

    fun reset() {
        prefs.edit().clear().apply()
        clock.reset()
        data = CarlingData()
    }

    private companion object {
        const val KEY_DATA = "data"
    }
}
