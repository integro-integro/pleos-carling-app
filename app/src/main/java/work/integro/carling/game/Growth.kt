package work.integro.carling.game

import kotlinx.serialization.Serializable

/**
 * 계열 다섯 가지. 젤리 알과 아기 카링은 공통이고, 계열 진화 1에서 사용자가 하나를 고른다.
 * 계열은 모습만 다르고 성능 차이는 없다.
 */
@Serializable
enum class Lineage(val label: String, val color: Long, val carlingName: String, val tagline: String) {
    WIND("바람", 0xFF7FC4FF, "산들링", "바람처럼 어디든 같이 달릴래요."),
    LIGHT_RAY("빛", 0xFFFFD54A, "햇살링", "햇살처럼 곁을 환하게 밝혀 줄게요."),
    TREE("나무", 0xFF7BD86A, "새싹링", "천천히, 단단하게 같이 자랄래요."),
    WATER("물", 0xFF4FA0F5, "물결링", "물결처럼 편안하게 함께할게요."),
    EARTH("흙", 0xFFD9A066, "모래링", "언제나 든든하게 옆에 있을게요."),
}

/**
 * 진화 단계. 진화하려면 최소 레벨, 친밀도, 퀘스트를 모두 채워야 한다.
 * [intimacyGoal]·[questGoal]은 누적 목표라서 진화해도 값을 차감하지 않는다. (초기 제안값, 시험 후 조정)
 */
@Serializable
enum class Form(val label: String, val minLevel: Int, val intimacyGoal: Int, val questGoal: Int) {
    EGG("젤리 알", 1, 0, 0),
    BABY("아기 카링", 10, 12, 8),
    EVO1("계열 진화 1", 20, 24, 24);

    val next: Form? get() = entries.getOrNull(ordinal + 1)
}

/** 아직 외형이 없는 다음 진화. 화면에 '추후 공개'로만 보여준다. */
data class FutureEvolution(val label: String, val minLevel: Int)

val FUTURE_EVOLUTIONS = listOf(
    FutureEvolution("계열 진화 2", 30),
    FutureEvolution("계열 진화 3", 50),
    FutureEvolution("계열 진화 4", 70),
    FutureEvolution("계열 진화 5", 99),
)

/** 차량 데이터로 판정하는 퀘스트 종류. 사용자가 완료를 누르는 일은 없다. */
@Serializable
enum class QuestKind(val title: String, val target: Int, val unit: String, val detail: String) {
    ONE_TRIP("함께 떠난 여정", 1, "회", "유효 운행 1회 함께 달리기"),
    TEN_MINUTES("함께한 시간", 10, "분", "실제로 달린 시간 10분 쌓기"),
    TWO_DAYS("다시 만난 날", 2, "일", "서로 다른 날 2번 함께 달리기"),
}

/** 진행 중인 퀘스트. 활성화된 뒤의 운행만 센다. */
@Serializable
data class Quest(
    val kind: QuestKind,
    /** 달린 시간 퀘스트는 초로 모은다. */
    val seconds: Long = 0,
    val trips: Int = 0,
    val days: Set<String> = emptySet(),
) {
    val progress: Int
        get() = when (kind) {
            QuestKind.ONE_TRIP -> trips
            QuestKind.TEN_MINUTES -> (seconds / 60).toInt()
            QuestKind.TWO_DAYS -> days.size
        }.coerceAtMost(kind.target)

    val done: Boolean
        get() = when (kind) {
            QuestKind.ONE_TRIP -> trips >= kind.target
            QuestKind.TEN_MINUTES -> seconds >= kind.target * 60L
            QuestKind.TWO_DAYS -> days.size >= kind.target
        }
}

/** 카링월드의 성장 규칙(기획안 v2.2). 화면·저장과 분리해 시험할 수 있게 둔다. */
object GrowthRules {
    const val EGG_NAME = "젤리 알"

    // ---- 유효 운행 ----
    /** 기획 기준: 같은 운행에서 거리 2km 이상 그리고 실제 이동시간 5분 이상. */
    const val REAL_MIN_KM = 2f
    const val REAL_MIN_MOVING_SECONDS = 300L

    /** 개발 빌드는 에뮬레이터에서 시험할 수 있게 기어만 바꿔도 통과시킨다. */
    val MIN_KM: Float = if (work.integro.carling.BuildConfig.DEBUG) 0f else REAL_MIN_KM
    val MIN_MOVING_SECONDS: Long = if (work.integro.carling.BuildConfig.DEBUG) 5L else REAL_MIN_MOVING_SECONDS

    /**
     * 트립카드를 만들 수 있는 운행인지. 거리나 이동시간을 모르면 추측하지 않고 만들지 않는다.
     * 원본 값으로 판정하고 화면에 반올림한 값은 쓰지 않는다.
     */
    fun isValidTrip(distanceKm: Float?, movingSeconds: Long?, minKm: Float = MIN_KM, minSeconds: Long = MIN_MOVING_SECONDS): Boolean {
        if (minKm > 0f && distanceKm == null) return false
        if (minSeconds >= REAL_MIN_MOVING_SECONDS && movingSeconds == null) return false
        return (distanceKm ?: 0f) >= minKm && (movingSeconds ?: minSeconds) >= minSeconds
    }

    // ---- XP: 하루 첫 5번의 유효 운행만 지급 ----
    const val TRIP_XP = 20
    const val SCORE_BONUS_XP = 5
    const val SCORE_BONUS_MIN = 90
    const val DAILY_PAID_TRIPS = 5

    /** (기본 XP, 보너스 XP). [dayOrder]는 그날 몇 번째 유효 운행인지(1부터). */
    fun tripXp(dayOrder: Int, score: Int?): Pair<Int, Int> {
        if (dayOrder > DAILY_PAID_TRIPS) return 0 to 0
        return TRIP_XP to if (score != null && score >= SCORE_BONUS_MIN) SCORE_BONUS_XP else 0
    }

    // ---- 레벨 ----
    const val MAX_LEVEL = 99

    /** [level]에서 다음 레벨로 가는 데 필요한 XP. */
    fun xpToNext(level: Int): Int = when (level) {
        1 -> 20
        in 2..9 -> 40
        in 10..19 -> 80
        in 20..29 -> 120
        in 30..49 -> 200
        in 50..69 -> 300
        in 70..98 -> 400
        else -> 0
    }

    private val CUMULATIVE: IntArray = IntArray(MAX_LEVEL + 1).also { table ->
        for (level in 2..MAX_LEVEL) table[level] = table[level - 1] + xpToNext(level - 1)
    }

    /** [level]에 도달하는 데 필요한 누적 XP. */
    fun cumXp(level: Int): Int = CUMULATIVE[level.coerceIn(1, MAX_LEVEL)]

    fun levelOf(totalXp: Int): Int {
        var level = 1
        while (level < MAX_LEVEL && totalXp >= cumXp(level + 1)) level++
        return level
    }

    /** 다음 레벨까지 진행도(0~1). Lv99면 1. */
    fun levelProgress(totalXp: Int): Float {
        val level = levelOf(totalXp)
        if (level >= MAX_LEVEL) return 1f
        return ((totalXp - cumXp(level)).toFloat() / xpToNext(level)).coerceIn(0f, 1f)
    }

    /** 알의 균열 단계(1~4). Lv1 온전, Lv2 첫 금, Lv5 균열 확대, Lv9 안쪽 실루엣. */
    fun eggStage(level: Int): Int = when {
        level >= 9 -> 4
        level >= 5 -> 3
        level >= 2 -> 2
        else -> 1
    }

    // ---- 안전 점수 ----
    /** 급조작 수로 계산한 앱 자체 점수. 차속을 못 받으면 점수가 없다. */
    fun safetyScore(harshEvents: Int?): Int? = when (harshEvents) {
        null -> null
        0 -> 95
        else -> (92 - harshEvents * 4).coerceAtLeast(72)
    }

    /** 급가속·급감속 판정: 1초 안에 속도가 이만큼(m/s) 바뀌면 급조작. 실차에서 재보정 필요. */
    const val HARSH_DELTA_MPS = 3.0f

    /** 이 속도(m/s)를 넘으면 실제로 움직이는 중으로 본다. */
    const val MOVING_MPS = 0.5f

    // ---- 진화 ----
    /** 이번 진화의 세 조건을 모두 채웠는지. 이미 구현된 마지막 단계면 false. */
    fun canEvolve(form: Form, level: Int, intimacy: Int, questsDone: Int): Boolean {
        val next = form.next ?: return false
        return level >= next.minLevel && intimacy >= next.intimacyGoal && questsDone >= next.questGoal
    }

    /** 레벨업마다 생기는 퀘스트는 세 종류를 차례로 돌린다. */
    fun questKind(sequence: Int): QuestKind = QuestKind.entries[sequence % QuestKind.entries.size]
}
