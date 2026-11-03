package work.integro.carling.data

import kotlinx.serialization.Serializable
import work.integro.carling.game.Evolution
import work.integro.carling.game.GrowthRules
import work.integro.carling.game.Light
import work.integro.carling.game.Stage

/** 기기에 저장되는 카링의 모든 상태. 정밀 위치는 저장하지 않는다(격자 해시만). */
@Serializable
data class CarlingData(
    val hatched: Boolean = false,
    val personality: Personality? = null,
    val name: String? = null,

    // 첫 만남(v1): 첫 운전이 알을 깨운다 → 트립카드 #001 → 이름 → 첫 교감 → 지도 첫 불빛 → 위치 동의
    val firstTripDone: Boolean = false,   // 첫 운전이 끝나 알에 금이 갔는가
    val firstPetDone: Boolean = false,    // 태어난 뒤 첫 쓰다듬기(친밀도 퀘스트 소개)를 했는가
    val firstTripMinutes: Int = 0,        // 트립카드 #001 — 함께한 시간
    val firstTripDistanceKm: Float? = null,
    val firstTripSafety: Int = 0,         // 트립카드 #001 — 안전운전 점수
    val firstTripPlaceName: String? = null,

    // (구) 첫 5분 질문 — v1에서는 쓰지 않지만 과거 저장 호환을 위해 남김
    val routine: Routine? = null,
    val usualLight: Light? = null,
    val guideSeen: Boolean = false,
    val locationConsent: Boolean? = null,

    /** 진행 중인 주행. 주행 도중 프로세스가 정리돼도 이어서 기록하기 위해 저장한다. */
    val activeTrip: ActiveTrip? = null,

    // 성장
    val togetherDays: Set<String> = emptySet(),
    val tripCount: Int = 0,
    val totalMinutes: Int = 0,
    val lightMinutes: Map<Light, Int> = emptyMap(),
    val lightTrips: Map<Light, Int> = emptyMap(),
    val placeCells: Set<String> = emptySet(),
    val newPlaces: Int = 0,
    val revisits: Int = 0,
    val evolution: Evolution? = null,

    // 수치
    val intimacy: Int = 0,
    val fullness: Int = 60,
    val lastDecayDate: String? = null,
    val lastGreetDate: String? = null,
    val lastFeedDate: String? = null,
    val rewardDate: String? = null,
    val rewardedTripsToday: Int = 0,
    val smoothPraiseToday: Int = 0,
    val petDate: String? = null,
    val petsToday: Int = 0,

    // 꾸미기·추억
    val hasHat: Boolean = false,
    val wearingHat: Boolean = false,
    val memories: List<MemoryCard> = emptyList(),
    val pendingReport: TripReport? = null,

    // 설정
    val voiceOn: Boolean = true,
) {
    val stage: Stage get() = GrowthRules.stage(hatched, togetherDays.size)
}

/** 첫 만남 질문 1: 주로 어떤 길을 달려요? */
@Serializable
enum class Routine(val label: String, val reply: String) {
    COMMUTE("출퇴근길", "매일 같은 길이라도, 같이 달리면 매일 달라 보일 거예요."),
    FAMILY("등하원·장보기", "가족이랑 같이 다니는 길이네요. 저도 끼워줘요!"),
    TRAVEL("여행길", "어디로 가든 처음 보는 풍경을 같이 보고 싶어요!"),
    WORK("업무·외근", "바쁜 날에도 잠깐씩 같이 있을게요."),
}

@Serializable
data class ActiveTrip(val startMillis: Long, val startOdometerKm: Float?)

@Serializable
data class MemoryCard(
    val date: String,
    val title: String,
    val text: String,
)

/** 주차 후 보여주는 '오늘의 기억' 한 장. */
@Serializable
data class TripReport(
    val minutes: Int,
    val distanceKm: Float?,
    val light: Light,
    val placeName: String?,
    val newPlace: Boolean?,
    val smoothPraise: Boolean,
    val newDay: Boolean,
    val togetherDays: Int,
    val intimacyGain: Int,
    val firstDrive: Boolean,
    val grownNow: Boolean,
    val evolvedNow: Boolean,
)
