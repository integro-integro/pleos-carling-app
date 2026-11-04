package work.integro.carling.game

import kotlinx.serialization.Serializable

/** 빛 — 언제 함께 달렸나. 시동 시각으로 성격과 트립카드 표시에만 쓴다(성장 판정 아님). */
@Serializable
enum class Light(val label: String, val color: Long) {
    DAWN("새벽빛", 0xFF9DB8FF),
    DAY("햇살빛", 0xFFFFE08A),
    SUNSET("노을빛", 0xFFFFA07A),
    STAR("별빛", 0xFFB39DFF);

    val hours: String
        get() = when (this) {
            DAWN -> "04~09시"
            DAY -> "09~16시"
            SUNSET -> "16~20시"
            STAR -> "20~04시"
        }

    companion object {
        fun ofHour(hour: Int): Light = when (hour) {
            in 4..8 -> DAWN
            in 9..15 -> DAY
            in 16..19 -> SUNSET
            else -> STAR
        }
    }
}

/**
 * 계열(5계열) — 성장 2(빠르면 D14)에 도달할 때 누적 점수로 정해지고 이후 바뀌지 않는다.
 * 데이터가 부족하면 기본 계열(바람). 실제 판정은 [GrowthRules.scoreTrip]이고,
 * [condition]은 기획 표현으로 화면에 쓰지 않는다.
 */
@Serializable
enum class Lineage(val label: String, val color: Long, val names: List<String>, val condition: String) {
    WIND("바람", 0xFF7FC4FF, listOf("산들링", "나래링", "하늘링"), "긴 거리 · 고속 주행이 많을 때"),
    LIGHT_RAY("빛", 0xFFFFD54A, listOf("반짝링", "불빛링", "섬광링"), "도심 가다서다 주행이 많을 때"),
    TREE("나무", 0xFF7BD86A, listOf("새싹링", "잎새링", "우듬지링"), "산길 · 숲길 주행이 많을 때"),
    WATER("물", 0xFF4FA0F5, listOf("물결링", "파도링", "바다링"), "해안 · 강 · 호수길이 많을 때"),
    EARTH("흙", 0xFFFF9D5C, listOf("총총링", "부릉링", "질주링"), "10km 미만 짧은 트립이 많을 때"),
}

/** 진화할 때 배우는 특별한 동작. 배운 뒤에는 돌아다니다가도 가끔 한다. */
enum class SpecialMove(val label: String) {
    FLIP("공중제비"),
    GLIDE("바람 활공"),
    ZIP("번개 질주"),
    LEAF_DANCE("살랑 춤"),
    WAVE("물결 타기"),
    DRIFT("드리프트"),
    AURA("완전체 오라"),
}

/** 카링이 착용하는 아이템. 성장 1부터 쓸 수 있다. */
@Serializable
enum class CarlingItem(val label: String, val icon: String) {
    PARTY_HAT("고깔 모자", "🎉"),
    RIBBON("리본", "🎀"),
    CROWN("왕관", "👑"),
    SUNGLASSES("선글라스", "🕶"),
    ROUND_GLASSES("동그란 안경", "👓"),
    NECKLACE("목걸이", "📿"),
    SHOES("신발", "👟"),
    FLOWER_CROWN("꽃 화관", "🌸"),
    HEADPHONES("헤드폰", "🎧"),
    STRAW_HAT("밀짚모자", "👒"),
}

/**
 * 방문하면 특산물을 받는 지역. 좌표 범위는 시·도 경계를 사각형으로 어림한 값이다.
 * 위치는 판정에만 쓰고 저장하지 않는다.
 */
@Serializable
enum class Region(
    val label: String,
    val foodName: String,
    val foodIcon: String,
    val bonusXp: Int,
    private val minLat: Double,
    private val maxLat: Double,
    private val minLng: Double,
    private val maxLng: Double,
) {
    JEJU("제주도", "제주 감귤", "🍊", 20, 33.10, 33.60, 126.10, 127.00),
    GANGWON("강원도", "강원도 감자", "🥔", 15, 37.05, 38.65, 127.70, 129.45),
    BUSAN("부산", "부산 어묵", "🍢", 15, 34.95, 35.40, 128.75, 129.32),
    DAEGU("대구", "대구 사과", "🍎", 15, 35.60, 36.02, 128.35, 128.80),
    JEONJU("전주", "전주 비빔밥", "🍚", 20, 35.75, 35.90, 127.03, 127.22);

    fun contains(lat: Double, lng: Double) = lat in minLat..maxLat && lng in minLng..maxLng

    companion object {
        fun of(lat: Double, lng: Double): Region? = entries.firstOrNull { it.contains(lat, lng) }
    }
}

/** 캐릭터 크기 단계(그림용). 세부 성장/진화 단계는 [GrowthRules.StageSpec] 인덱스로 다룬다. */
enum class Stage(val label: String) {
    EGG("알"),
    BABY("아기 카링"),
    GROWN("성장한 카링"),
    EVOLVED("나만의 카링"),
}

/** 이전 버전 호환용(캐릭터 그림 파라미터). v1 성장 판정에는 쓰지 않는다. */
@Serializable
enum class Path(val label: String) { REGULAR("단골"), EXPLORER("탐험") }

object GrowthRules {

    enum class Kind { BIRTH, GROWTH, EVOLUTION }

    /** 한 단계의 기준: 최소 D값 + 누적 XP + 누적 MP. 진화 단계는 진화카드·특별 퀘스트가 더 필요. */
    data class StageSpec(
        val index: Int,
        val day: Int,
        val cumXp: Int,
        val cumMp: Int,
        val kind: Kind,
        val label: String,
        val evoIndex: Int? = null,
    )

    /** 180일 · 성장 6 + 진화 3 (서비스 시각화 v1, 1차 제안값). */
    val STAGES: List<StageSpec> = listOf(
        StageSpec(0, 1, 0, 0, Kind.BIRTH, "아기 카링"),
        StageSpec(1, 7, 70, 5, Kind.GROWTH, "성장 1"),
        StageSpec(2, 14, 180, 10, Kind.GROWTH, "성장 2"),
        StageSpec(3, 30, 450, 20, Kind.EVOLUTION, "진화 1", evoIndex = 1),
        StageSpec(4, 45, 700, 30, Kind.GROWTH, "성장 3"),
        StageSpec(5, 60, 1000, 40, Kind.GROWTH, "성장 4"),
        StageSpec(6, 90, 1550, 60, Kind.EVOLUTION, "진화 2", evoIndex = 2),
        StageSpec(7, 120, 2100, 80, Kind.GROWTH, "성장 5"),
        StageSpec(8, 150, 2650, 100, Kind.GROWTH, "성장 6"),
        StageSpec(9, 180, 3200, 120, Kind.EVOLUTION, "진화 3", evoIndex = 3),
    )

    val MAX_STAGE = STAGES.last().index

    const val EGG_NAME = "젤리 알"

    /** 화면에 보이는 단계 이름. 진화 1 전까지는 모든 계열이 아기 키링이고, 진화 1·2·3에서 계열 이름이 바뀐다. */
    fun stageName(stageIndex: Int, lineage: Lineage?): String {
        val names = (lineage ?: Lineage.WIND).names
        return when {
            stageIndex <= 2 -> "아기 키링 ${stageIndex + 1}단계"
            stageIndex <= 5 -> "${names[0]} ${stageIndex - 2}단계"
            stageIndex <= 8 -> "${names[1]} ${stageIndex - 5}단계"
            else -> "${names[2]} 완전체"
        }
    }

    // ---- XP (성장 에너지) — 하루 최대 50 ----
    const val DAILY_XP_CAP = 50
    const val DRIVE_XP_CAP = 30
    const val FUEL_XP = 10               // ② 연료·충전: 하루 1회
    const val INTIMACY_XP_EACH = 5       // ③ 친밀도: 행동당
    const val INTIMACY_MAX_ACTIONS = 2   // 서로 다른 2개 행동까지
    const val SAFE_SCORE_MIN = 90
    const val SAFE_BONUS_EACH = 2        // 90점 이상 트립당 +2
    const val SAFE_BONUS_MAX_TRIPS = 5   // 하루 5건까지

    /** ① 운전 경험치 — 하루 트립 횟수별 기본 XP(누적값, 행을 더하지 않음). */
    fun driveBaseXp(trips: Int): Int = when {
        trips <= 1 -> 0
        trips == 2 -> 10
        trips == 3 -> 13
        trips == 4 -> 16
        else -> 20
    }

    /** 하루 운전 XP = 기본 + 안전 보너스(90점 이상 트립당 +2, 5건까지). 상한 30. */
    fun driveXp(trips: Int, safeTrips: Int): Int =
        (driveBaseXp(trips) + safeTrips.coerceAtMost(SAFE_BONUS_MAX_TRIPS) * SAFE_BONUS_EACH).coerceAtMost(DRIVE_XP_CAP)

    fun intimacyXp(actions: Int): Int = actions.coerceAtMost(INTIMACY_MAX_ACTIONS) * INTIMACY_XP_EACH

    // ---- MP (지도 포인트) — 기억 저장으로 하루 2장까지 ----
    const val MP_NEW_PATH = 3
    const val MP_FAMILIAR = 1
    const val MP_DAILY_SAVES = 2

    /** 이 단계의 진화에서 새로 배우는 동작. 진화 단계가 아니면 null. */
    fun learnedMove(stageIndex: Int, lineage: Lineage?): SpecialMove? = when (STAGES.getOrNull(stageIndex)?.evoIndex) {
        1 -> SpecialMove.FLIP
        2 -> when (lineage ?: Lineage.WIND) {
            Lineage.WIND -> SpecialMove.GLIDE
            Lineage.LIGHT_RAY -> SpecialMove.ZIP
            Lineage.TREE -> SpecialMove.LEAF_DANCE
            Lineage.WATER -> SpecialMove.WAVE
            Lineage.EARTH -> SpecialMove.DRIFT
        }
        3 -> SpecialMove.AURA
        else -> null
    }

    /** 이 단계까지 배운 동작 전부. */
    fun knownMoves(stageIndex: Int, lineage: Lineage?): List<SpecialMove> =
        (1..stageIndex).mapNotNull { learnedMove(it, lineage) }

    /** 아이템이 열리는 단계(성장 1). 아기 카링과 확실히 다르게 하는 첫 보상이다. */
    const val ITEM_UNLOCK_STAGE = 1

    /** 이번 단계 게이지. 성장하면 초과분을 버리므로 단계마다 빈 상태에서 다시 찬다. */
    fun stageProgress(total: Int, from: Int, to: Int): Float =
        if (to <= from) 1f else ((total - from).toFloat() / (to - from)).coerceIn(0f, 1f)

    /** 성장 때 버릴 초과분. 누적값이 새 단계의 시작값이 되도록 한다. */
    fun overflow(total: Int, stageStart: Int): Int = (total - stageStart).coerceAtLeast(0)

    fun itemsUnlocked(stageIndex: Int): Boolean = stageIndex >= ITEM_UNLOCK_STAGE

    /**
     * 트립카드를 저장할 때 얻는 MP. 저장은 언제나 할 수 있지만, 보상은 운행일별 2장까지이고
     * 같은 카드를 다시 저장하면 0이다. 위치를 쓰지 않아 판정할 수 없으면 익숙한 길로 본다.
     */
    fun saveMp(newPlace: Boolean?, alreadyRewarded: Boolean, rewardedOnDay: Int): Int = when {
        alreadyRewarded || rewardedOnDay >= MP_DAILY_SAVES -> 0
        newPlace == true -> MP_NEW_PATH
        else -> MP_FAMILIAR
    }

    // ---- 진화카드 · 특별 퀘스트 ----
    /** [quest]는 기획 문구로 화면에 쓰지 않는다. 실제 조건은 저장한 트립카드 수([memoriesNeeded])다. */
    data class EvoSpec(val evoIndex: Int, val lotteryStartDay: Int, val quest: String, val memoriesNeeded: Int)

    val EVOLUTIONS: Map<Int, EvoSpec> = mapOf(
        1 to EvoSpec(1, 20, "저장한 기억 1개에 이름이나 메모 남기기", 1),
        2 to EvoSpec(2, 80, "저장한 기억 3개를 골라 추억 묶음 완성", 3),
        3 to EvoSpec(3, 170, "기억 5개를 선택하고 카링과의 여정 회고 완료", 5),
    )

    /**
     * 진화카드 추첨 확률 — 트립 횟수별. 5회 실패하면 6번째 추첨에서 확정된다.
     * 추첨은 그날 첫 트립에서만 하므로 지금은 항상 1회 구간(18%)이 적용된다.
     */
    const val CARD_PITY = 6
    fun cardChance(trips: Int): Float = when {
        trips <= 1 -> 0.18f
        trips == 2 -> 0.22f
        else -> 0.25f
    }

    /** 다음에 노릴 진화 단계(아직 카드를 못 받았고 추첨이 시작된 가장 가까운 진화). 없으면 null. */
    fun activeEvolution(dayNumber: Int, ownedCards: Set<Int>): EvoSpec? =
        EVOLUTIONS.values.sortedBy { it.evoIndex }
            .firstOrNull { it.evoIndex !in ownedCards && dayNumber >= it.lotteryStartDay }

    /** 지금 도달한 단계 인덱스. 건너뛰지 않고, 진화는 카드·퀘스트까지 있어야 한다. */
    fun stageIndex(
        dayNumber: Int,
        totalXp: Int,
        totalMp: Int,
        ownedCards: Set<Int>,
        doneQuests: Set<Int>,
    ): Int {
        var reached = 0
        for (i in 1..MAX_STAGE) {
            val s = STAGES[i]
            val base = dayNumber >= s.day && totalXp >= s.cumXp && totalMp >= s.cumMp
            val evoOk = s.evoIndex == null || (s.evoIndex in ownedCards && s.evoIndex in doneQuests)
            if (base && evoOk) reached = i else break
        }
        return reached
    }

    /** 진화 외형 단계: 0=아기/성장, 1=진화1(D30~), 2=진화2(D90~), 3=진화3(완성형). */
    fun evoTier(stageIndex: Int): Int = when {
        stageIndex >= 9 -> 3
        stageIndex >= 6 -> 2
        stageIndex >= 3 -> 1
        else -> 0
    }

    /** 캐릭터 그림용 크기 단계. */
    fun sizeStage(hatched: Boolean, stageIndex: Int): Stage = when {
        !hatched -> Stage.EGG
        stageIndex == 0 -> Stage.BABY
        stageIndex <= 2 -> Stage.GROWN
        else -> Stage.EVOLVED
    }

    // ---- 계열 판정 ----
    /** 한 번의 주행이 각 계열에 더하는 점수. 센서 한계로 1차 제안 휴리스틱. */
    fun scoreTrip(distanceKm: Float?, harshEvents: Int?, newPlace: Boolean?): Map<Lineage, Float> {
        val d = distanceKm ?: 0f
        val h = harshEvents ?: 0
        val m = mutableMapOf<Lineage, Float>()
        fun add(l: Lineage, v: Float) { m[l] = (m[l] ?: 0f) + v }
        when {
            d >= 20f -> add(Lineage.WIND, 2f)            // 긴 거리 · 고속
            d in 0.1f..9.99f -> add(Lineage.EARTH, 2f)   // 10km 미만 짧은 트립
            d in 10f..20f -> add(Lineage.WATER, 1f)      // 중간 거리 (해안·강 proxy)
        }
        if (h >= 3) add(Lineage.LIGHT_RAY, 1.6f)         // 가다서다(급조작 많음)
        else if (h >= 1) add(Lineage.LIGHT_RAY, 0.6f)
        if (newPlace == true) add(Lineage.TREE, 1.5f)    // 탐험 ~ 산길·숲길 proxy
        return m
    }

    /** 누적 점수가 가장 큰 계열. 비었거나 모두 0이면 기본 계열(바람). */
    fun dominantLineage(scores: Map<Lineage, Float>): Lineage =
        scores.maxByOrNull { it.value }?.takeIf { it.value > 0f }?.key ?: Lineage.WIND

    /** 이보다 짧은 P→D→P 전환은 주행으로 치지 않는다. */
    const val MIN_TRIP_SECONDS = 5L

    /** 급가속·급감속 판정: 1초 안에 속도가 이만큼(m/s) 바뀌면 급조작. 실차에서 재보정 필요. */
    const val HARSH_DELTA_MPS = 3.0f

    // ---- 돌보기(케어) 수치 — 성장과 별개의 상호작용 ----
    const val TRIP_INTIMACY = 5
    const val GREET_INTIMACY = 3
    const val PET_INTIMACY = 1
    const val DAILY_PETS = 5
    const val FEED_FULLNESS = 20
    const val CHARGE_FULLNESS = 30
    const val DAILY_FULLNESS_DECAY = 10

    /** 포만감이 이 이하면 배고픈 상태 — 특산물은 이때만 먹일 수 있다. */
    const val HUNGRY_FULLNESS = 50
    const val FOOD_FULLNESS = 30

    fun isHungry(fullness: Int): Boolean = fullness <= HUNGRY_FULLNESS
    const val MIN_FULLNESS = 20
}
