package work.integro.carling.data

import kotlinx.serialization.Serializable
import work.integro.carling.game.Badge
import work.integro.carling.game.CarItem
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
    val carItems: Map<CarItem, Int> = emptyMap(),
    val equippedCarItem: CarItem? = null,

    // ---- 보여주기 ----
    /** 주차 후 아직 안내하지 않은 카드(운행 ID). */
    val unseenCards: List<Long> = emptyList(),
    /** 아직 보여주지 않은 단계 변화. */
    val unseenStage: Stage? = null,
    /** 아직 보여주지 않은 개성 변화(스냅샷 ID). */
    val unseenPersonality: Int? = null,
    val customTags: List<String> = emptyList(),
    val events: List<EventLog> = emptyList(),
) {
    val current: TripRecord? get() = trips.lastOrNull { it.status != TripStatus.CONFIRMED }
    fun trip(id: Long): TripRecord? = trips.firstOrNull { it.tripId == id }
    fun card(id: Long): TripCard? = cards.firstOrNull { it.tripId == id }
    val draftCards: List<TripCard> get() = cards.filter { it.state == CardState.DRAFT }
    val unopenedBoxes: List<LuckyBox> get() = boxes.filter { it.openedAt == null }
    val nextStageGp: Int? get() = stage.next?.minGp
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
    val tags: List<String> = emptyList(),
    val remindedAt: Long? = null,
    val expiredAt: Long? = null,
) {
    fun clock(trip: TripRecord?): CardClock = CardClock(state, trip?.confirmedAt, remindedAt)
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
    val carItem: CarItem? = null,
    val createdAt: Long,
    val openedAt: Long? = null,
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
