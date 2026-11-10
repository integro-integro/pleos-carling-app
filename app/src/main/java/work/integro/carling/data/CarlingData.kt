package work.integro.carling.data

import kotlinx.serialization.Serializable
import work.integro.carling.game.Form
import work.integro.carling.game.GrowthRules
import work.integro.carling.game.Lineage
import work.integro.carling.game.Quest
import work.integro.carling.game.QuestKind
import work.integro.carling.game.RoomItem
import work.integro.carling.game.Space

/** 기기에 저장되는 카링월드의 모든 상태. 경로·위치는 저장하지 않는다. */
@Serializable
data class CarlingData(
    /** 카링 식별자. 나중에 계정 연동·이관을 하더라도 같은 카링임을 알 수 있게 처음 한 번 만든다. */
    val carlingId: String = "",
    val name: String? = null,
    val personality: Personality? = null,
    val introSeen: Boolean = false,

    /** 진행 중인 운행. 앱이 도중에 꺼져도 이어서 기록한다. */
    val activeTrip: ActiveTrip? = null,

    // ---- 성장 ----
    val totalXp: Int = 0,
    /** 실제 이동이 확인된 날짜. 하루에 여러 번 달려도 하루로 센다. */
    val drivingDays: Set<String> = emptySet(),
    /** 날짜별 유효 운행 수. 하루 첫 5회만 XP를 받는다. */
    val dayValidTrips: Map<String, Int> = emptyMap(),
    val form: Form = Form.EGG,
    val lineage: Lineage? = null,

    // ---- 공간 ----
    /** 카링이 지금 지내는 공간. 새 아이템은 이 공간부터 채워진다. */
    val currentSpace: Space = Space.BEDROOM,
    /** 놓인 아이템(지급 순서). 처음 놓일 때만 친밀도가 오른다. */
    val placed: List<RoomItem> = emptyList(),
    val duplicates: Map<RoomItem, Int> = emptyMap(),

    // ---- 퀘스트 ----
    val activeQuest: Quest? = null,
    val questQueue: List<QuestKind> = emptyList(),
    val questSequence: Int = 0,
    val questsDone: Int = 0,

    // ---- 기록과 보여주기(연출을 놓쳐도 보상은 이미 저장돼 있다) ----
    val tripCards: List<TripCard> = emptyList(),
    val unseenCards: List<Int> = emptyList(),
    val newItems: List<RoomItem> = emptyList(),
    val seenLevel: Int = 1,

    val voiceOn: Boolean = true,
) {
    val level: Int get() = GrowthRules.levelOf(totalXp)
    val intimacy: Int get() = placed.size
    val drivingDayCount: Int get() = drivingDays.size
    val hatched: Boolean get() = form != Form.EGG
    val canEvolve: Boolean get() = GrowthRules.canEvolve(form, level, intimacy, questsDone)
    val spacesOpen: Boolean get() = level >= Space.MENU_LEVEL
}

@Serializable
data class ActiveTrip(
    val startMillis: Long,
    val startOdometerKm: Float?,
    /** 앱이 다시 떠도 이어 세도록 주기적으로 저장하는 실제 이동시간. */
    val movingSeconds: Long = 0,
)

/** 조건을 채운 운행 한 번이 남기는 카드. 만들어지는 순간 보상까지 자동 저장된다. */
@Serializable
data class TripCard(
    val number: Int,
    /** 같은 운행을 다시 처리해도 보상이 두 번 나가지 않게 하는 세션 ID(시작 시각). */
    val sessionId: Long,
    /** 귀속 날짜(운행이 끝난 날). */
    val date: String,
    val startMillis: Long,
    val endMillis: Long,
    val distanceKm: Float?,
    val movingSeconds: Long?,
    /** 앱이 계산한 안전 점수. 차속을 못 받았으면 null. */
    val score: Int?,
    /** 그날 몇 번째 유효 운행인지. */
    val dayOrder: Int,
    val xpBase: Int,
    val xpBonus: Int,
    val item: RoomItem?,
    /** 이번에 처음 놓인 아이템인지. 중복이면 친밀도가 오르지 않는다. */
    val newPlacement: Boolean,
    val questLine: String? = null,
    /** 개발자 도구로 만든 시뮬레이션 데이터인지. */
    val simulated: Boolean = false,
) {
    val elapsedMinutes: Int get() = ((endMillis - startMillis) / 60_000L).toInt()
    val xpTotal: Int get() = xpBase + xpBonus
    val xpLimitReached: Boolean get() = dayOrder > GrowthRules.DAILY_PAID_TRIPS
}

