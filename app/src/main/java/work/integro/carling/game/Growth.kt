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
 * 계열(5계열) — 아기 카링 2단계(15운전일)에 도달할 때 누적 점수로 정해지고 이후 바뀌지 않는다.
 * 데이터가 부족하면 기본 계열(바람). 실제 판정은 [GrowthRules.scoreTrip]이고,
 * [condition]은 기획 표현으로 진화 축하 카드의 이유에 쓴다.
 */
@Serializable
enum class Lineage(val label: String, val color: Long, val names: List<String>, val condition: String, val reason: String) {
    WIND("바람", 0xFF7FC4FF, listOf("산들링", "날개링", "하늘링"), "긴 거리 · 고속 주행이 많을 때", "긴 길을 시원하게 같이 달려서 바람을 닮았대."),
    LIGHT_RAY("빛", 0xFFFFD54A, listOf("반짝링", "햇살링", "태양링"), "도심 가다서다 주행이 많을 때", "도심의 불빛 사이를 같이 달려서 빛을 닮았대."),
    TREE("나무", 0xFF7BD86A, listOf("새싹링", "잎새링", "숲결링"), "산길 · 숲길 주행이 많을 때", "처음 보는 길을 같이 찾아다녀서 나무를 닮았대."),
    WATER("물", 0xFF4FA0F5, listOf("물결링", "파도링", "바다링"), "해안 · 강 · 호수길이 많을 때", "물길 따라 같이 달려서 물을 닮았대."),
    EARTH("흙", 0xFFFF9D5C, listOf("모래링", "소호링", "구미링"), "10km 미만 짧은 트립이 많을 때", "가까운 곳을 자주 같이 다녀서 흙을 닮았대."),
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

/**
 * 카링이 착용하는 꾸미기 아이템. 아기 카링 성장(7운전일)부터 쓸 수 있다.
 * [hidden]은 예전 저장과 호환하려고 남긴 것으로, 고르기 화면에 보이지 않고 그리지도 않는다.
 */
@Serializable
enum class CarlingItem(val label: String, val icon: String, val hidden: Boolean = false) {
    RIBBON("리본", "🎀"),
    CROWN("왕관", "👑"),
    FLOWER_CROWN("꽃 화관", "🌸"),
    PARTY_HAT("고깔 모자", "🎉"),
    STRAW_HAT("밀짚모자", "👒"),
    STAR_PIN("별 머리핀", "⭐"),
    SPROUT("새싹", "🌱"),
    SUNGLASSES("하트 선글라스", "💗"),
    ROUND_GLASSES("동그란 안경", "👓"),
    HEADPHONES("헤드폰", "🎧"),
    NECKLACE("목걸이", "📿", hidden = true),
    SHOES("신발", "👟", hidden = true);

    companion object {
        val shown: List<CarlingItem> get() = entries.filter { !it.hidden }
    }
}

/**
 * 타기 — 모으는 이동수단. 진화 2에 내 계열의 것을 선물로 받고, 진화 3에 하나를 더 골라 받는다.
 * [lineage]는 그 이동수단과 어울리는 계열이다.
 */
@Serializable
enum class Vehicle(val label: String, val lineage: Lineage) {
    WING_CAR("바람 날개카", Lineage.WIND),
    GOLD_RACER("햇살 레이서", Lineage.LIGHT_RAY),
    LEAF_BUGGY("잎새 버기", Lineage.TREE),
    SHELL_CAR("소라 잠수카", Lineage.WATER),
    FOX_BIKE("여우 바이크", Lineage.EARTH);

    companion object {
        fun of(lineage: Lineage): Vehicle = entries.first { it.lineage == lineage }
    }
}

/**
 * 사용하기 — 트립카드를 저장할 때 하나씩 받아 모아 두는 간식. 쓰면 포만감이 차고,
 * 그날 처음 먹이는 거라면 밥 주기 교감(+2 XP)으로 친다. [weight]는 나올 상대 확률이다.
 */
@Serializable
enum class UsableItem(val label: String, val icon: String, val fullness: Int, val weight: Int) {
    RICE_BALL("주먹밥", "🍙", 20, 34),
    COOKIE("하트 쿠키", "🍪", 15, 30),
    MILK("딸기우유", "🥛", 25, 20),
    PUDDING("푸딩", "🍮", 30, 12),
    RAINBOW_JELLY("무지개 젤리", "🍬", 50, 4);

    companion object {
        fun draw(random: kotlin.random.Random = kotlin.random.Random): UsableItem {
            var pick = random.nextInt(entries.sumOf { it.weight })
            return entries.first { pick -= it.weight; pick < 0 }
        }
    }
}

/** 이동수단을 선물로 받는 단계: 진화 2(내 계열), 진화 3(하나 골라 받기). */
const val VEHICLE_GIFT_STAGE = 4
const val VEHICLE_PICK_STAGE = 5

/**
 * (구) 특산물 지역. 지금은 쓰지 않지만 예전 저장 호환을 위해 남긴다. 좌표 범위는 시·도 경계를 사각형으로 어림한 값이다.
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

    enum class Kind { BIRTH, GROWTH, COLOR, EVOLUTION }

    /** 한 단계의 기준: 최소 운전일 + 누적 XP. 둘 다 채우면 열린다. */
    data class StageSpec(
        val index: Int,
        val drivingDays: Int,
        val cumXp: Int,
        val kind: Kind,
        val label: String,
        val evoIndex: Int? = null,
    )

    /** 운전일 90일 · 7가지 모습(서비스 시각화 V3, 규칙 v0.5). 젤리 알은 깨어나기 전이라 여기 없다. */
    val STAGES: List<StageSpec> = listOf(
        StageSpec(0, 1, 0, Kind.BIRTH, "아기 카링 1단계"),
        StageSpec(1, 7, 175, Kind.GROWTH, "아기 카링 성장"),
        StageSpec(2, 15, 375, Kind.COLOR, "아기 카링 2단계"),
        StageSpec(3, 30, 750, Kind.EVOLUTION, "계열 진화 1", evoIndex = 1),
        StageSpec(4, 60, 1500, Kind.EVOLUTION, "계열 진화 2", evoIndex = 2),
        StageSpec(5, 90, 2250, Kind.EVOLUTION, "계열 진화 3", evoIndex = 3),
    )

    val MAX_STAGE = STAGES.last().index

    /** 계열이 정해지는 단계(아기 카링 2단계). */
    const val LINEAGE_STAGE = 2

    const val EGG_NAME = "젤리 알"

    /** 화면에 보이는 단계 이름. 진화 전까지는 모든 계열이 아기 카링이고, 진화 1·2·3에서 계열 이름이 된다. */
    fun stageName(stageIndex: Int, lineage: Lineage?): String {
        val spec = STAGES[stageIndex.coerceIn(0, MAX_STAGE)]
        val evo = spec.evoIndex ?: return spec.label
        return (lineage ?: Lineage.WIND).names[evo - 1]
    }

    // ---- XP — 트립카드를 저장해야 들어온다. 하루 최대 71 ----
    const val CARD_XP = 10                 // 저장한 트립카드 1장
    const val DAILY_REWARDED_CARDS = 5     // 운전일마다 5장까지 보상
    const val SAFE_SCORE_MIN = 95
    const val SAFE_BONUS = 3               // 저장한 카드의 안전 점수가 95점 이상이면 +3
    const val INTIMACY_XP_EACH = 2         // 쓰다듬기·밥 주기·놀아주기 각 하루 1회
    val INTIMACY_ACTIONS = setOf("PET", "FEED", "PLAY")
    const val DAILY_XP_MAX = DAILY_REWARDED_CARDS * (CARD_XP + SAFE_BONUS) + 3 * INTIMACY_XP_EACH

    /** 트립카드를 저장할 수 있는 시간(도착 후 24시간). 지나면 저장하지 않은 카드는 사라진다. */
    const val SAVE_WINDOW_MILLIS = 24L * 60 * 60 * 1000

    /** 트립 안전 점수. 급조작 수를 모르면(차속 권한 없음, 복구된 주행) 점수가 없다. */
    fun safetyScore(harshEvents: Int?): Int? = when (harshEvents) {
        null -> null
        0 -> 95
        else -> (92 - harshEvents * 4).coerceAtLeast(72)
    }

    /** 안전 보너스를 받는 트립인지. 점수가 없으면 보너스를 주지 않는다. */
    fun isSafeTrip(score: Int?): Boolean = score != null && score >= SAFE_SCORE_MIN

    /** 이 카드를 저장하면 받을 XP. 그 운전일에 이미 5장을 보상받았으면 0. */
    fun cardXp(safety: Int?, rewardedOnDay: Int): Int = when {
        rewardedOnDay >= DAILY_REWARDED_CARDS -> 0
        isSafeTrip(safety) -> CARD_XP + SAFE_BONUS
        else -> CARD_XP
    }

    /** 교감 행동 하나의 XP. 오늘 이미 한 행동이거나 교감 3종이 아니면 0. */
    fun intimacyXp(action: String, doneToday: Set<String>): Int =
        if (action in INTIMACY_ACTIONS && action !in doneToday) INTIMACY_XP_EACH else 0

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

    /** 아이템이 열리는 단계(아기 카링 성장, 7운전일). */
    const val ITEM_UNLOCK_STAGE = 1

    fun itemsUnlocked(stageIndex: Int): Boolean = stageIndex >= ITEM_UNLOCK_STAGE

    /** 다음 단계까지의 게이지. XP는 차감하지 않으므로 이전 단계 기준값부터 센다. */
    fun stageProgress(total: Int, from: Int, to: Int): Float =
        if (to <= from) 1f else ((total - from).toFloat() / (to - from)).coerceIn(0f, 1f)

    /** 지금 도달한 단계 인덱스. 최소 운전일과 누적 XP를 모두 채워야 하고, 건너뛰지 않는다. 퇴화는 없다. */
    fun stageIndex(drivingDays: Int, totalXp: Int): Int {
        var reached = 0
        for (i in 1..MAX_STAGE) {
            val s = STAGES[i]
            if (drivingDays >= s.drivingDays && totalXp >= s.cumXp) reached = i else break
        }
        return reached
    }

    /** 진화 외형 단계: 0=아기, 1·2·3=계열 진화. */
    fun evoTier(stageIndex: Int): Int = STAGES[stageIndex.coerceIn(0, MAX_STAGE)].evoIndex ?: 0

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

    /** 유효 트립: 주행 3분 이상. 짧은 이동 반복으로 카드를 늘리는 것을 막는다. 개발 빌드에서는 시험하기 쉽게 5초. */
    val MIN_TRIP_SECONDS: Long = if (work.integro.carling.BuildConfig.DEBUG) 5L else 180L

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

    /** 포만감이 이 이하면 배고픈 상태. */
    const val HUNGRY_FULLNESS = 50
    const val FOOD_FULLNESS = 30

    fun isHungry(fullness: Int): Boolean = fullness <= HUNGRY_FULLNESS
    const val MIN_FULLNESS = 20
}
