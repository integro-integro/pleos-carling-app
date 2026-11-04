package work.integro.carling.data

import kotlinx.serialization.Serializable
import work.integro.carling.game.CarlingItem
import work.integro.carling.game.GrowthRules
import work.integro.carling.game.Light
import work.integro.carling.game.Lineage
import work.integro.carling.game.Region
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 기기에 저장되는 카링의 모든 상태. 위치는 어림해서 저장한다(장소는 격자 해시, 길 양 끝은 약 1km 단위). */
@Serializable
data class CarlingData(
    val hatched: Boolean = false,
    val personality: Personality? = null,
    val name: String? = null,
    val birthDate: String? = null,

    // 첫 만남(v1)
    val firstTripDone: Boolean = false,
    val firstPetDone: Boolean = false,
    val firstTripMinutes: Int = 0,
    val firstTripDistanceKm: Float? = null,
    val firstTripSafety: Int = 0,
    val firstTripPlaceName: String? = null,

    // (구) 첫 5분 질문 — v1에서는 쓰지 않지만 과거 저장 호환을 위해 남김
    val routine: Routine? = null,
    val usualLight: Light? = null,
    val guideSeen: Boolean = false,
    val locationConsent: Boolean? = null,
    /** 사용자가 읽고 동의한 위치 안내의 판. 2부터 날씨 서비스 전송이 안내에 들어 있다. */
    val locationConsentVersion: Int = 0,
    /** 날씨 인사를 마지막으로 한 날. */
    val weatherGreetDate: String? = null,

    /** 진행 중인 주행. 주행 도중 프로세스가 정리돼도 이어서 기록하기 위해 저장한다. */
    val activeTrip: ActiveTrip? = null,

    // ---- 성장 엔진(v1): XP(성장 에너지) + MP(지도 포인트), 서로 환산하지 않음 ----
    // 누적값 — 지난 날들에서 확정된 합계. 오늘치는 아래 집계로 더한다. 성장하면 새 단계 시작값을 넘는 만큼 버린다.
    val totalXpPastDays: Int = 0,
    val totalMpPastDays: Int = 0,
    // 오늘 XP 집계 (날이 바뀌면 past로 접고 리셋)
    val xpDate: String? = null,
    val tripsToday: Int = 0,
    val safeTripsToday: Int = 0,
    val fuelXpGiven: Boolean = false,
    val intimacyActionsToday: Set<String> = emptySet(), // PET/FEED/PLAY/TALK
    // 오늘 MP 집계
    val mpDate: String? = null,
    val mpTodayValue: Int = 0,

    // 계열 — 성장 2(빠르면 D14)에 도달할 때 누적 점수로 결정하고 이후 바뀌지 않는다.
    val lineageScores: Map<Lineage, Float> = emptyMap(),
    val lineage: Lineage? = null,

    // 진화카드 · 특별 퀘스트
    val ownedCards: Set<Int> = emptySet(),    // 획득한 진화 단계(1/2/3)
    val cardFails: Map<Int, Int> = emptyMap(), // 단계별 추첨 실패 누적
    val cardDrawDate: String? = null,          // 오늘 이미 추첨했는지
    val doneQuests: Set<Int> = emptySet(),     // 완료한 진화 퀘스트

    val reachedStage: Int = 0, // 마지막으로 계산된 단계 인덱스(0 = 아기)
    /** 변화 장면을 마지막으로 보여준 단계. null이면 아직 기준이 없어 장면 없이 현재 단계로 맞춘다. */
    val celebratedStage: Int? = null,

    // 통계
    val togetherDays: Set<String> = emptySet(),
    val tripCount: Int = 0,
    val totalMinutes: Int = 0,
    val lightMinutes: Map<Light, Int> = emptyMap(),
    val lightTrips: Map<Light, Int> = emptyMap(),
    val placeCells: Set<String> = emptySet(),
    val newPlaces: Int = 0,
    val revisits: Int = 0,

    // 돌보기(케어) 수치 — 성장과 별개
    val intimacy: Int = 0,
    val fullness: Int = 60,
    val lastDecayDate: String? = null,
    val lastGreetDate: String? = null,
    val lastFeedDate: String? = null,
    val petDate: String? = null,
    val petsToday: Int = 0,

    // 꾸미기·트립카드
    val equippedItem: CarlingItem? = null,
    /** 가지고 있는 지역 특산물 개수. */
    val foods: Map<Region, Int> = emptyMap(),
    /** 지역별 마지막으로 특산물을 받은 날. 지역마다 하루 1개만 받는다. */
    val foodGiftDates: Map<Region, String> = emptyMap(),
    /** 성장 1의 '아이템이 열렸어요' 안내를 봤는지. */
    val itemIntroSeen: Boolean = false,
    /** Drive Log — 모든 트립. 트립카드 화면은 여기서 목적지별 최근 20곳을 골라 보여준다. */
    val tripCards: List<TripCard> = emptyList(),
    /** 지도에 켜진 길. 양 끝은 약 1km 단위로 어림하고, 이 기기에만 저장한다. */
    val mapRoutes: List<MapRoute> = emptyList(),
    /** 새 길 알림을 아직 보여주지 않았는지. */
    val pendingMapNotice: Boolean = false,
    /** 주차 후 아직 확인하지 않은 새 트립카드의 번호. */
    val pendingCard: Int? = null,

    // 설정
    val voiceOn: Boolean = true,
    val scene: Scene = Scene.CLEAR_SKY,
) {
    /** 오늘 쌓은 XP(상한 50은 항목별 상한으로 자연 충족). */
    val xpToday: Int
        get() = GrowthRules.driveXp(tripsToday, safeTripsToday) +
            (if (fuelXpGiven) GrowthRules.FUEL_XP else 0) +
            GrowthRules.intimacyXp(intimacyActionsToday.size)

    val totalXp: Int get() = totalXpPastDays + xpToday
    val totalMp: Int get() = totalMpPastDays + mpTodayValue

    fun dayNumber(today: LocalDate): Int =
        birthDate?.let { ChronoUnit.DAYS.between(LocalDate.parse(it), today).toInt() + 1 } ?: 1

    fun stageIndexOn(today: LocalDate): Int =
        GrowthRules.stageIndex(dayNumber(today), totalXp, totalMp, ownedCards, doneQuests)

    /** 위치를 쓰도록 동의했지만 날씨 전송 안내를 아직 보지 못했는지. */
    val needsLocationReconsent: Boolean
        get() = locationConsent == true && locationConsentVersion < CURRENT_LOCATION_CONSENT

    val stageSpec get() = GrowthRules.STAGES[reachedStage.coerceIn(0, GrowthRules.MAX_STAGE)]
}

/** 4부터 달린 경로를 기기에 저장한다는 안내가 들어 있다. */
const val CURRENT_LOCATION_CONSENT = 4

/** 트립카드 화면에 보여주는 목적지 수. 내비의 최근 목적지와 같다. */
const val RECENT_DESTINATIONS = 20

/** 고를 수 있는 배경. 낮과 밤은 시계를 따른다. */
@Serializable
enum class Scene(val label: String) {
    CITY("도시"),
    COAST("바닷가 해안"),
    CLEAR_SKY("맑은 하늘"),
    FOREST("고요한 숲"),
}

/** 첫 만남 질문(구). */
@Serializable
enum class Routine(val label: String, val reply: String) {
    COMMUTE("출퇴근길", "매일 같은 길이라도, 같이 달리면 매일 달라 보일 거예요."),
    FAMILY("등하원·장보기", "가족이랑 같이 다니는 길이네요. 저도 끼워줘요!"),
    TRAVEL("여행길", "어디로 가든 처음 보는 풍경을 같이 보고 싶어요!"),
    WORK("업무·외근", "바쁜 날에도 잠깐씩 같이 있을게요."),
}

@Serializable
data class ActiveTrip(
    val startMillis: Long,
    val startOdometerKm: Float?,
    val startLat: Double? = null,
    val startLng: Double? = null,
)

/** 지도에 켜진 길 하나. 실제 경로가 있으면 선으로, 없으면 도착지 불빛으로 그린다. [name]은 내비 장소 이름이다. */
@Serializable
data class MapRoute(
    val toLat: Double,
    val toLng: Double,
    val fromLat: Double? = null,
    val fromLng: Double? = null,
    val date: String = "",
    val name: String? = null,
    val distanceKm: Float? = null,
    /** 이 길을 달린 횟수. 많을수록 지도에서 더 밝다. */
    val visits: Int = 1,
    /** 실제 경로가 저장된 열쇠. 경로를 기록하지 못했으면 null이고 도착지 불빛으로만 그린다. */
    val trackKey: String? = null,
) {
    fun connects(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Boolean =
        (fromLat == aLat && fromLng == aLng && toLat == bLat && toLng == bLng) ||
            (fromLat == bLat && fromLng == bLng && toLat == aLat && toLng == aLng)
}

/** 목적지별 최근 카드. 같은 목적지를 다시 가면 맨 위로 올라가고, [limit]곳까지만 남긴다. */
fun List<TripCard>.recentDestinations(limit: Int = RECENT_DESTINATIONS): List<RecentDestination> {
    val visits = groupingBy { it.destinationKey }.eachCount()
    return asReversed()
        .distinctBy { it.destinationKey }
        .take(limit)
        .map { RecentDestination(it, visits.getValue(it.destinationKey)) }
}

data class RecentDestination(val card: TripCard, val visits: Int)

/** 트립 한 번이 남기는 카드. 저장 여부와 무관하게 자동으로 쌓이고, 원하는 카드만 저장한다. */
@Serializable
data class TripCard(
    val number: Int,
    val date: String,
    val startMillis: Long,
    val minutes: Int,
    val distanceKm: Float?,
    val light: Light,
    /** 안전운전 점수. 차량 속도를 읽지 못해 측정하지 못했으면 null. */
    val safety: Int?,
    val placeName: String?,
    /** 처음 간 길인지. 위치를 쓰지 않아 판정하지 못했으면 null. */
    val newPlace: Boolean?,
    val dayNumber: Int,
    /** 목적지 구역 표시(약 200m 구역 해시). 위치를 쓰지 않았으면 null. */
    val placeCell: String? = null,
    /** 출발지·도착지가 처음 이어진 길인지. */
    val newRoute: Boolean = false,
    val driveXpToday: Int = 0,
    val highlights: List<String> = emptyList(),
    val saved: Boolean = false,
    val mpGained: Int = 0,
) {
    val title: String get() = placeName ?: if (newPlace == true) "처음 달린 길" else "함께 달린 길"

    /** 같은 목적지를 묶는 기준. 위치를 쓰지 않은 트립은 목적지를 몰라 묶지 않는다. */
    val destinationKey: String get() = placeCell ?: "trip-$number"
}
