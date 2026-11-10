package work.integro.carling.data

import kotlinx.serialization.Serializable
import work.integro.carling.game.CarlingItem
import work.integro.carling.game.GrowthRules
import work.integro.carling.game.CareIssue
import work.integro.carling.game.Coupon
import work.integro.carling.game.Light
import work.integro.carling.game.Lineage
import work.integro.carling.game.Vehicle
import work.integro.carling.game.UsableItem
import work.integro.carling.game.VEHICLE_PICK_STAGE
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
    /** 첫 운전의 안전 점수. 0이면 점수가 없다. */
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

    // ---- 성장 엔진(규칙 v0.5): 트립카드를 저장해야 XP가 들어오고, 최소 운전일 + 누적 XP로 자란다 ----
    /** 누적 XP. 단계에 도달해도 차감하지 않는다. */
    val growthXp: Int = 0,
    /** 운전일 — 저장한 트립카드가 1장 이상인 날(트립 날짜). 달력 날짜가 아니다. */
    val drivingDays: Set<String> = emptySet(),
    /** 교감 XP를 센 날. 날이 바뀌면 [intimacyActionsToday]를 비운다. */
    val xpDate: String? = null,
    val intimacyActionsToday: Set<String> = emptySet(), // PET/FEED/PLAY
    /** 저장 데이터의 성장 규칙 판. 5 미만이면 불러올 때 v0.5로 다시 계산한다. */
    val growthVersion: Int = 0,

    // 계열 — 아기 카링 2단계(15운전일)에 도달할 때 저장한 트립의 누적 점수로 결정하고 이후 바뀌지 않는다.
    val lineageScores: Map<Lineage, Float> = emptyMap(),
    val lineage: Lineage? = null,

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
    /** 모은 이동수단. 진화 2에 내 계열의 것을 받고, 진화 3에 하나를 더 골라 받는다. */
    val ownedVehicles: Set<Vehicle> = emptySet(),
    /** 지금 타고 있는 이동수단. null이면 내려 있다. */
    val ridingVehicle: Vehicle? = null,
    /** 진화 3의 '하나 골라 받기' 선물을 이미 썼는지. */
    val vehiclePicked: Boolean = false,
    /** 사용하기 — 모아 둔 간식 개수. 트립카드를 저장할 때 하나씩 받는다. */
    val usables: Map<UsableItem, Int> = emptyMap(),
    /** (구) 지역 특산물. 더 이상 쓰지 않는다. */
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

    // 차량 돌봄
    /** 지금 진행 중인 돌봄 퀘스트. */
    val careQuests: Set<CareIssue> = emptySet(),
    /** 마친 돌봄 퀘스트 수. */
    val careDone: Int = 0,
    /** 친밀도가 100을 넘어 쌓인 마음 포인트. [CareRules.GIFT_HEART]마다 정비 쿠폰을 선물한다. */
    val heartPoints: Int = 0,
    /** 받은 정비 쿠폰. */
    val coupons: List<Coupon> = emptyList(),
    /** 카링이 내밀고 있는 선물(아직 열어 보지 않음). */
    val pendingGift: Coupon? = null,

    // 설정
    val voiceOn: Boolean = true,
    val scene: Scene = Scene.LAB,
) {
    val totalXp: Int get() = growthXp

    /** 운전일 수. */
    val drivingDayCount: Int get() = drivingDays.size

    /** 태어난 지 며칠째(달력 기준). 트립카드의 '함께한 지 N일째'에만 쓴다. */
    fun dayNumber(today: LocalDate): Int =
        birthDate?.let { ChronoUnit.DAYS.between(LocalDate.parse(it), today).toInt() + 1 } ?: 1

    /** 지금 운전일과 누적 XP로 도달한 단계. */
    fun computedStage(): Int = GrowthRules.stageIndex(drivingDayCount, growthXp)

    /** 진화 3 선물로 이동수단 하나를 고를 수 있는지. */
    val canPickVehicle: Boolean get() = reachedStage >= VEHICLE_PICK_STAGE && !vehiclePicked && ownedVehicles.size < Vehicle.entries.size

    /** 지금까지 얻은 배경. 깨어나기 전에는 Dr.Pab의 심장 연구소뿐이다. */
    fun ownedScenes(): List<Scene> = Scene.entries.filter { hatched && it.unlockStage <= reachedStage || it.unlockStage < 0 }

    /** 위치를 쓰도록 동의했지만 날씨 전송 안내를 아직 보지 못했는지. */
    val needsLocationReconsent: Boolean
        get() = locationConsent == true && locationConsentVersion < CURRENT_LOCATION_CONSENT

    val stageSpec get() = GrowthRules.STAGES[reachedStage.coerceIn(0, GrowthRules.MAX_STAGE)]
}

/** 4부터 달린 경로를 기기에 저장한다는 안내가 들어 있다. */
const val CURRENT_LOCATION_CONSENT = 4

/** 트립카드 화면에 보여주는 목적지 수. 내비의 최근 목적지와 같다. */
const val RECENT_DESTINATIONS = 20

/**
 * 배경 스킨 8가지(서비스 시각화 V3 '카링의 하루를 담은 배경 스킨'). 아침·낮·노을·밤은 시계를 따른다.
 * 처음에는 Dr.Pab의 심장 연구소에서 시작하고, 나머지는 깨어날 때와 성장·진화할 때 기념으로 하나씩 얻는다.
 * @param unlockStage 이 단계에 도달하면 얻는다. 음수면 처음부터 있다.
 */
@Serializable
enum class Scene(val label: String, val unlockStage: Int) {
    LAB("Dr.Pab의 심장 연구소", -1),
    ATTIC("트립카드 다락방", 0),
    CITY("강변 도시 쉼터", 1),
    COAST("등대 아래 작은 해변", 2),
    FOREST("반딧불이 쉬는 숲", 3),
    REST_STOP("길모퉁이 휴게소", 4),
    CLEAR_SKY("구름 위 맑은 하늘", 5),
    OBSERVATORY("별마중 천문대", 5);

    companion object {
        /** 이 단계에 도달하면서 새로 얻는 배경. */
        fun unlockedAt(stage: Int): List<Scene> = entries.filter { it.unlockStage == stage }
    }
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

/** 트립 한 번이 남기는 카드. 도착 후 24시간 안에 저장해야 XP·자동차·아이템을 받고, 저장하지 않으면 사라진다. */
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
    val highlights: List<String> = emptyList(),
    val saved: Boolean = false,
    /** 저장해서 받은 XP. 그 운전일에 5장을 넘겨 저장했으면 0. */
    val xpGained: Int = 0,
    /** 급조작 수. 저장할 때 계열 점수에 쓴다. 모르면 null. */
    val harshEvents: Int? = null,
    /** 저장하는 순간 공개되는 앞면 자동차([work.integro.carling.game.CarCatalog]). 저장 전에는 null. */
    val carModel: String? = null,
    /** 출발지 이름(지난 트립의 도착지). 모르면 null. */
    val startName: String? = null,
    /** 저장하며 받은 간식. 사용하기에 담긴다. */
    val itemReward: UsableItem? = null,
    /** 카드 앞면에 별자리로 그릴 경로(약 24점으로 줄인 것). 위치를 쓰지 않았으면 비어 있다. */
    val routeDots: List<GeoDot> = emptyList(),
) {
    /** 도착 시각(어림). 저장 가능 시간(도착 후 24시간)을 잰다. */
    val endMillis: Long get() = startMillis + minutes * 60_000L

    val title: String get() = placeName ?: if (newPlace == true) "처음 달린 길" else "함께 달린 길"

    /** 같은 목적지를 묶는 기준. 위치를 쓰지 않은 트립은 목적지를 몰라 묶지 않는다. */
    val destinationKey: String get() = placeCell ?: "trip-$number"
}
