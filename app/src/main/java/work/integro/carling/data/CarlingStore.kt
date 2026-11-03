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
import work.integro.carling.game.Stage
import java.time.LocalDate
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
)

/**
 * 카링 상태를 기기 안에 저장하고 게임 규칙을 적용한다.
 * (Pleos ID 계정 연동 API가 공개되지 않아 MVP는 로컬 저장)
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

    /** 알을 깨운다(톡 터치). 성격은 고르지 않고, 첫 운전을 함께한 시간대가 정한다. */
    fun hatch() = update {
        val personality = it.personality ?: personalityFor(it.lightTrips)
        it.copy(hatched = true, personality = personality)
    }

    /** 태어난 뒤 첫 쓰다듬기 — 친밀도 퀘스트를 처음 배우는 순간. +5. */
    fun completeFirstPet() = update {
        val today = clock.today().toString()
        it.copy(
            firstPetDone = true,
            petDate = today,
            petsToday = 1,
            intimacy = (it.intimacy + GrowthRules.TRIP_INTIMACY).coerceAtMost(100),
        )
    }

    fun giveName(value: String) = update { it.copy(name = value) }

    fun finishGuide() = update { it.copy(guideSeen = true) }

    /** 성격은 '함께한 경험'이 정한다(v1): 첫 운전을 가장 많이 함께한 시간대로 말투·표정을 고른다. */
    private fun personalityFor(lightTrips: Map<Light, Int>): Personality {
        val light = lightTrips.maxByOrNull { it.value }?.key ?: Light.ofHour(clock.hourOf(System.currentTimeMillis()))
        return when (light) {
            Light.DAWN, Light.DAY -> Personality.DODO
            Light.SUNSET -> Personality.NARUN
            Light.STAR -> Personality.LUMI
        }
    }

    /** 위치는 '처음 간 곳/다시 찾은 곳' 판정에만 쓴다. 언제든 바꿀 수 있다. */
    fun setLocationConsent(allowed: Boolean) = update { it.copy(locationConsent = allowed) }

    val usesLocation: Boolean get() = data.locationConsent == true

    // ---- 진행 중 주행 ----

    fun beginTrip(startMillis: Long, startOdometerKm: Float?) =
        update { it.copy(activeTrip = ActiveTrip(startMillis, startOdometerKm)) }

    fun clearActiveTrip() = update { it.copy(activeTrip = null) }

    // ---- 하루 루틴 ----

    /** 정차 화면이 열릴 때마다 호출. 날이 바뀌었으면 포만감 감소·첫 인사 처리. 첫 인사면 true. */
    fun greetIfNewDay(): Boolean {
        val today = clock.today().toString()
        applyDailyDecay()
        if (data.lastGreetDate == today) return false
        update {
            it.copy(
                lastGreetDate = today,
                intimacy = (it.intimacy + GrowthRules.GREET_INTIMACY).coerceAtMost(100),
            )
        }
        return true
    }

    /** 하루 첫 만남 먹이 — 하루 1회. */
    fun canFeedToday(): Boolean = data.lastFeedDate != clock.today().toString()

    fun feed() {
        if (!canFeedToday()) return
        update {
            it.copy(
                lastFeedDate = clock.today().toString(),
                fullness = (it.fullness + GrowthRules.FEED_FULLNESS).coerceAtMost(100),
            )
        }
    }

    /** 충전 세션 한 번에 한 번. 결제 금액과 무관. */
    fun onCharging() = update {
        it.copy(fullness = (it.fullness + GrowthRules.CHARGE_FULLNESS).coerceAtMost(100))
    }

    /** 쓰다듬기. 하루 [GrowthRules.DAILY_PETS]번까지만 친밀도가 오른다. */
    fun pet() {
        val today = clock.today().toString()
        val count = if (data.petDate == today) data.petsToday else 0
        if (count >= GrowthRules.DAILY_PETS) return
        update {
            it.copy(
                petDate = today,
                petsToday = count + 1,
                intimacy = (it.intimacy + GrowthRules.PET_INTIMACY).coerceAtMost(100),
            )
        }
    }

    fun toggleHat() = update { if (it.hasHat) it.copy(wearingHat = !it.wearingHat) else it }

    fun toggleVoice() = update { it.copy(voiceOn = !it.voiceOn) }

    fun dismissReport() = update { it.copy(pendingReport = null) }

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

    // ---- 주행 ----

    /** 주행 한 번을 반영하고 도착 리포트를 남긴다. 너무 짧은 주행은 무시. */
    fun recordTrip(trip: TripResult) {
        val seconds = (trip.endMillis - trip.startMillis) / 1000
        if (seconds < GrowthRules.MIN_TRIP_SECONDS) return

        val day = clock.dateOf(trip.startMillis).toString()
        val minutes = ((seconds + 30) / 60).toInt().coerceAtLeast(1)
        val light = Light.ofHour(clock.hourOf(trip.startMillis))

        // v1 첫 만남: 알이 깨어나기 전의 '첫 운전'. 성장 엔진을 돌리지 않고,
        // 트립카드 #001만 만들어 알에 금을 낸다(부화는 사용자가 알을 터치할 때).
        if (!data.hatched) {
            if (data.firstTripDone) return // 부화 전 추가 주행은 무시
            val safety = if (trip.harshEvents == null || trip.harshEvents == 0) 95 else (92 - trip.harshEvents * 4).coerceAtLeast(72)
            update {
                it.copy(
                    firstTripDone = true,
                    firstTripMinutes = minutes,
                    firstTripDistanceKm = trip.distanceKm,
                    firstTripSafety = safety,
                    firstTripPlaceName = trip.placeName,
                    // 첫 트립 = 트립카드 #001: 통계·지도에도 반영한다
                    togetherDays = it.togetherDays + day,
                    tripCount = 1,
                    totalMinutes = it.totalMinutes + minutes,
                    lightMinutes = it.lightMinutes + (light to minutes),
                    lightTrips = it.lightTrips + (light to 1),
                    placeCells = trip.placeCell?.let { c -> it.placeCells + c } ?: it.placeCells,
                    newPlaces = it.newPlaces + if (trip.placeCell != null) 1 else 0,
                    hasHat = true,
                    wearingHat = true,
                    memories = it.memories + MemoryCard(day, "우리의 첫 드라이브", "처음으로 같이 ${minutes}분을 달렸어요. 이 길이 첫 트립카드가 되었어요!"),
                )
            }
            return
        }

        val before = data

        val newDay = day !in before.togetherDays
        val sameRewardDay = before.rewardDate == day
        val rewardedSoFar = if (sameRewardDay) before.rewardedTripsToday else 0
        val praisedSoFar = if (sameRewardDay) before.smoothPraiseToday else 0
        val rewarded = rewardedSoFar < GrowthRules.DAILY_TRIP_REWARDS
        val intimacyGain = if (rewarded) GrowthRules.TRIP_INTIMACY else 0
        val smoothPraise = trip.harshEvents == 0 && praisedSoFar < GrowthRules.DAILY_SMOOTH_PRAISE

        val newPlace = trip.placeCell?.let { it !in before.placeCells }
        val firstDrive = before.tripCount == 0
        val days = before.togetherDays + day

        val memories = before.memories.toMutableList()
        if (firstDrive) {
            memories += MemoryCard(day, "우리의 첫 드라이브", "처음으로 같이 ${minutes}분을 달렸어요. 기념 모자를 받았어요!")
        }
        if (newPlace == true && before.newPlaces == 0) {
            val where = trip.placeName ?: "처음 보는 곳"
            memories += MemoryCard(day, "처음 보는 풍경", "${where}에 처음 같이 왔어요.")
        }

        var next = before.copy(
            togetherDays = days,
            tripCount = before.tripCount + 1,
            totalMinutes = before.totalMinutes + minutes,
            lightMinutes = before.lightMinutes + (light to (before.lightMinutes[light] ?: 0) + minutes),
            lightTrips = before.lightTrips + (light to (before.lightTrips[light] ?: 0) + 1),
            placeCells = trip.placeCell?.let { before.placeCells + it } ?: before.placeCells,
            newPlaces = before.newPlaces + if (newPlace == true) 1 else 0,
            revisits = before.revisits + if (newPlace == false) 1 else 0,
            intimacy = (before.intimacy + intimacyGain).coerceAtMost(100),
            rewardDate = day,
            rewardedTripsToday = rewardedSoFar + 1,
            smoothPraiseToday = praisedSoFar + if (smoothPraise) 1 else 0,
            hasHat = before.hasHat || firstDrive,
            wearingHat = before.wearingHat || firstDrive,
        )

        val grownNow = before.stage == Stage.BABY && next.stage == Stage.GROWN
        if (grownNow) {
            memories += MemoryCard(day, "함께한 지 ${GrowthRules.GROWN_DAYS}일", "조금 더 자랐어요. 매일 만나줘서 고마워요.")
        }
        val evolvedNow = next.stage == Stage.EVOLVED && before.evolution == null
        if (evolvedNow) {
            val evolution = GrowthRules.evolve(next.lightMinutes, next.lightTrips, next.newPlaces, next.revisits)
            next = next.copy(evolution = evolution)
            memories += MemoryCard(day, "${evolution.light.label} ${evolution.path.label} 카링", evolution.reason)
        }

        update {
            next.copy(
                memories = memories,
                pendingReport = TripReport(
                    minutes = minutes,
                    distanceKm = trip.distanceKm,
                    light = light,
                    placeName = trip.placeName,
                    newPlace = newPlace,
                    smoothPraise = smoothPraise,
                    newDay = newDay,
                    togetherDays = days.size,
                    intimacyGain = intimacyGain,
                    firstDrive = firstDrive,
                    grownNow = grownNow,
                    evolvedNow = evolvedNow,
                ),
            )
        }
    }

    /** 개발용: 처음부터 다시. */
    fun reset() {
        prefs.edit().clear().apply()
        clock.reset()
        data = CarlingData()
    }

    private companion object {
        const val KEY_DATA = "data"
    }
}
