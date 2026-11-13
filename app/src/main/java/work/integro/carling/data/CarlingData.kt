package work.integro.carling.data

import kotlinx.serialization.Serializable
import work.integro.carling.game.Badge
import work.integro.carling.game.CabinView
import work.integro.carling.game.CarColor
import work.integro.carling.game.CarPart
import work.integro.carling.game.CarSlot
import work.integro.carling.game.CarStyle
import work.integro.carling.game.CardClock
import work.integro.carling.game.CardState
import work.integro.carling.game.Personality
import work.integro.carling.game.Stage
import work.integro.carling.game.TimeBucket
import work.integro.carling.game.TripPurpose
import work.integro.carling.game.TripStatus

/**
 * 기기 안에 저장되는 마이카링의 모든 상태. 서버는 없다. 경로·위치는 저장하지 않는다.
 * 운행(TripRecord), 카드(TripCard), 보상 원장(rewards)을 분리한다(PRD 5장).
 * 리포트의 운행 숫자는 확정된 운행만, 성장·개성은 보상이 지급된 운행만 읽는다.
 */
@Serializable
data class CarlingData(
    val karingId: String = "",
    val name: String? = null,
    /** 수집 항목 안내와 동의. */
    val consented: Boolean = false,
    /** 위치(동네) 사용 동의. null이면 아직 묻지 않았다. */
    val locationConsent: Boolean? = null,
    val voiceOn: Boolean = true,

    /** 모든 운행(기록 중·주차됨·확정). 카드 만료와 관계없이 남는다. */
    val trips: List<TripRecord> = emptyList(),
    /** 최소 기준을 넘은 운행에만 1장. 키는 운행 ID. */
    val cards: List<TripCard> = emptyList(),
    /** 보상 원장. 운행 ID가 있으면 이미 지급했다(같은 운행에 두 번 지급하지 않는 근거). */
    val rewards: Map<Long, Reward> = emptyMap(),
    val boxes: List<LuckyBox> = emptyList(),
    /** 날짜별 지급한 럭키박스 수(하루 최대 5개). */
    val dailyBoxes: Map<String, Int> = emptyMap(),

    // ---- 카링 ----
    val totalGp: Int = 0,
    val stage: Stage = Stage.EGG,
    val personality: Personality = Personality(),
    val snapshots: List<PersonalitySnapshot> = emptyList(),
    val stageHistory: List<StageChange> = emptyList(),
    val badges: Map<Badge, Int> = emptyMap(),
    /** 모은 자동차 꾸미기(개수). 이전 이모지 스티커 8종(carItems)은 버린다. */
    val parts: Map<CarPart, Int> = emptyMap(),
    /** 자리마다 달린 아이템. */
    val equipped: Map<CarSlot, CarPart> = emptyMap(),
    /** 모은 차체 색(개수). 펄 화이트는 처음부터 가진다. */
    val colors: Map<CarColor, Int> = emptyMap(),
    /** 지금 칠한 차체 색. null이면 펄 화이트. */
    val carColor: CarColor? = null,
    /** 고른 모습(꼬마링 이상이 되면 지나온 모습 중에서 고를 수 있다). null이면 지금 단계. */
    val look: Stage? = null,
    /** 카링 탭 배경 구도. */
    val cabin: CabinView = CabinView.DASH,
    /** 한 번이라도 완성한 장착 스타일(배지처럼 남는다). */
    val styles: Set<CarStyle> = emptySet(),

    // ---- 보여주기 ----
    /** 주차 후 아직 안내하지 않은 카드(운행 ID). */
    val unseenCards: List<Long> = emptyList(),
    /** 아직 보여주지 않은 단계 변화. */
    val unseenStage: Stage? = null,
    /** 아직 보여주지 않은 개성 변화(스냅샷 ID). */
    val unseenPersonality: Int? = null,
    val customTags: List<String> = emptyList(),
    /** '기타'에 직접 적은 목적(최근 것 뒤로). */
    val customPurposes: List<String> = emptyList(),
    /** 절약 금액 계산 단가(원): 전기 kWh당, 연료 L당. */
    val kwhPrice: Int = work.integro.carling.game.ImprovementRules.DEFAULT_KWH_PRICE,
    val fuelPrice: Int = work.integro.carling.game.ImprovementRules.DEFAULT_FUEL_PRICE,
    /** 운행일지를 받을 메일 주소. 이 차 안에만 저장한다. */
    val logEmail: String? = null,
    /** 배지 6종을 다 모아 오로라 색을 받았다는 안내를 아직 안 봤다. */
    val unseenSetReward: Boolean = false,
    /** 내 카링에서 고른 대표 배지(홈 상태표에 보인다). */
    val displayBadge: work.integro.carling.game.Badge? = null,
    /** 주행 한마디를 한 횟수. */
    val talks: Map<work.integro.carling.game.Talk, Int> = emptyMap(),
    /** 상점 재화 젤리. [jellyStarted]가 false면 불러올 때 지난 기록으로 한 번 채운다. */
    val jelly: Int = 0,
    val jellyStarted: Boolean = false,
    /** 축하를 본 레벨. 0이면 아직 정하지 않았다(불러올 때 지금 레벨로). */
    val levelSeen: Int = 0,
    /** 차에 타 있는지(내 카링에서 '차 내리기'면 false). 홈에도 그대로 보인다. */
    val riding: Boolean = true,
    /** 차 안 배경 톤. 11/13 사용자 선택: A 차분한 뉴트럴. */
    val cabinTone: work.integro.carling.game.CabinTone = work.integro.carling.game.CabinTone.CALM,
    /** 달이 꽉 차면 한 번 만들어 저장하는 월간 리포트(11/13 사용자 결정). */
    val monthReports: List<MonthReport> = emptyList(),
    /** 새로 만들어져 아직 '짜잔' 알림을 보지 않은 리포트의 달("2026-11"). */
    val unseenMonthReport: String? = null,
    /** 360° 보기에서 고른 홈 방향(0~7, 45도마다). null이면 기본 그림. */
    val homeAngle: Int? = null,
    /** 상점에서 산 배경 테마(시간대 고정)와 지금 쓰는 테마. */
    val themes: Set<work.integro.carling.game.TimeBucket> = emptySet(),
    val theme: work.integro.carling.game.TimeBucket? = null,
    /** 생활반경 혜택 선택 동의(4.15). null이면 아직 묻지 않았다. 위치 동의와 따로. */
    val benefitConsent: Boolean? = null,
    /** 닫은 혜택과 닫은 시각(30일 숨김). */
    val benefitSnoozed: Map<String, Long> = emptyMap(),
    val events: List<EventLog> = emptyList(),
    /** 주유·충전 기록(출발할 때 남은 양이 직전 주차보다 크게 늘어난 경우). */
    val refuels: List<Refuel> = emptyList(),
) {
    val current: TripRecord? get() = trips.lastOrNull { it.status != TripStatus.CONFIRMED }
    fun trip(id: Long): TripRecord? = trips.firstOrNull { it.tripId == id }
    fun card(id: Long): TripCard? = cards.firstOrNull { it.tripId == id }
    val draftCards: List<TripCard> get() = cards.filter { it.state == CardState.DRAFT }
    val unopenedBoxes: List<LuckyBox> get() = boxes.filter { it.openedAt == null }
    val nextStageGp: Int? get() = stage.next?.minGp
    val style: CarStyle? get() = CarStyle.of(equipped)
    /** 화면에 보이는 모습: 고른 모습이 지금 단계 이하이고 아기링 이상이면 그 모습. */
    val shownStage: Stage get() = look?.takeIf { stage.ordinal >= Stage.BREEZE.ordinal && it.ordinal in Stage.BABY.ordinal..stage.ordinal } ?: stage
    /** 모습을 고를 수 있는지(꼬마링 이상). */
    val canChooseLook: Boolean get() = stage.ordinal >= Stage.BREEZE.ordinal
}

/** 운행 하나. 병합돼도 ID(첫 출발 시각)는 유지된다. */
@Serializable
data class TripRecord(
    val tripId: Long,
    val status: TripStatus,
    val startedAt: Long,
    /** 마지막 P 전환 시각. 5분 판정의 기준. */
    val lastParkAt: Long? = null,
    val confirmedAt: Long? = null,
    val lastSnapshotAt: Long = startedAt,
    val startOdometerKm: Float? = null,
    val distanceKm: Float? = null,
    val movingSec: Long? = null,
    val harsh: Int? = null,
    val simulated: Boolean = false,
    /** 전기차인지(에너지 값을 받았을 때). null이면 모름. */
    val ev: Boolean? = null,
    /** 출발·주차 때 남은 양(%)과 EV 배터리(Wh). */
    val startPercent: Int? = null,
    val endPercent: Int? = null,
    val startWh: Float? = null,
    val endWh: Float? = null,
    /** 이 운행의 효율: 전기차 km/kWh, 내연기관차 km/L. 차량 값이 없으면 배터리 사용량으로 계산. 모르면 null. */
    val efficiency: Float? = null,
    /** 출발·도착 동네(행정동 코드 7자리). 위치 동의했을 때만 남기고, 좌표·경로는 저장하지 않는다. */
    val startDong: String? = null,
    val endDong: String? = null,
) {
    /** 경과시간: 출발부터 마지막 주차까지(병합 구간의 정차 포함). */
    val elapsedSec: Long get() = ((lastParkAt ?: lastSnapshotAt) - startedAt).coerceAtLeast(0) / 1000
    val endAt: Long get() = lastParkAt ?: lastSnapshotAt
    val score: Int? get() = work.integro.carling.game.Rules.drivingScore(harsh, distanceKm)
}

@Serializable
data class TripCard(
    val tripId: Long,
    /** 화면용 일련번호. */
    val number: Int,
    val state: CardState = CardState.DRAFT,
    val createdAt: Long,
    val completedAt: Long? = null,
    val purpose: TripPurpose? = null,
    /** 누구랑(선택). */
    val companion: work.integro.carling.game.Companion? = null,
    val tags: List<String> = emptyList(),
    /** '기타'를 고르고 직접 적은 목적(예: 병원). 없으면 null. */
    val customPurpose: String? = null,
    val remindedAt: Long? = null,
    val expiredAt: Long? = null,
) {
    fun clock(trip: TripRecord?): CardClock = CardClock(state, trip?.confirmedAt, remindedAt)

    /** 화면에 보이는 목적 이름: 직접 적은 목적이 있으면 그것. */
    val purposeLabel: String? get() = customPurpose ?: purpose?.label
}

@Serializable
data class Reward(val grantedAt: Long, val gp: Int, val boxId: Int?)

@Serializable
data class LuckyBox(
    val boxId: Int,
    val tripId: Long,
    /** 상자 생성 시 확정. 다시 뽑을 수 없다. */
    val bonusGp: Int,
    val badge: Badge? = null,
    /** 자동차 꾸미기. 이전 버전의 carItem 값은 읽지 않는다. */
    val part: CarPart? = null,
    val createdAt: Long,
    val openedAt: Long? = null,
    /** 차체 색. */
    val color: CarColor? = null,
)

/** 개성이 바뀔 때마다 남기는 근거(FR-10). */
@Serializable
data class PersonalitySnapshot(
    val id: Int,
    val createdAt: Long,
    val tripIds: List<Long>,
    val bucketCounts: Map<TimeBucket, Int>,
    val purposeCounts: Map<TripPurpose, Int>,
    val avgScore: Int?,
    val before: Personality,
    val after: Personality,
    /** 바뀐 축을 한 줄로. */
    val line: String,
)

@Serializable
data class StageChange(val at: Long, val from: Stage, val to: Stage)

/** 베타 지표용 로컬 로그. 자동 실행·자동 기록은 활성 행동으로 세지 않는다. */
@Serializable
data class EventLog(val at: Long, val type: String, val ref: Long? = null)

/** 주유(내연기관)·충전(전기차) 한 번. 남은 양이 [fromPercent]에서 [toPercent]로 늘었다. */
@Serializable
data class Refuel(val at: Long, val ev: Boolean, val fromPercent: Int, val toPercent: Int, val simulated: Boolean = false)

/** 저장된 월간 리포트: 그달 '꼭 알아야 할 3가지'와 요약 숫자. 만든 뒤에는 바뀌지 않는다. */
@kotlinx.serialization.Serializable
data class MonthReport(
    val month: String,
    val createdAt: Long,
    val insights: List<work.integro.carling.game.Insight>,
    val trips: Int,
    val km: Float,
    val minutes: Int,
)
