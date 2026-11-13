package work.integro.carling.game

import kotlinx.serialization.Serializable
import work.integro.carling.BuildConfig
import kotlin.math.floor

/**
 * 마이카링 MVP 규칙과 수치(PRD v0.1, 2026-11-12). 베타에서 조정할 값이라 한곳에 모은다.
 * [확정]은 공모전 양식·11/12 결정, [제안]은 PRD 제안값.
 */
object Rules {
    const val MINUTE_MILLIS = 60_000L
    const val HOUR_MILLIS = 60 * MINUTE_MILLIS
    const val DAY_MILLIS = 24 * HOUR_MILLIS

    // ---- 여정 ----
    /** [확정 11/12] 카드 생성 최소 기준: 거리 1km 이상 그리고 이동 3분 이상. */
    const val REAL_CARD_MIN_KM = 1f
    const val REAL_CARD_MIN_MOVING_SEC = 180L

    /** 개발 빌드는 에뮬레이터에서 기어만 바꿔도 시험할 수 있게 낮춘다. */
    val CARD_MIN_KM: Float = if (BuildConfig.DEBUG) 0f else REAL_CARD_MIN_KM
    val CARD_MIN_MOVING_SEC: Long = if (BuildConfig.DEBUG) 5L else REAL_CARD_MIN_MOVING_SEC

    /** [확정] P가 이만큼 이어지면 운행 확정. 그 전에 다시 출발하면 같은 여정에 합친다. */
    const val STOP_MERGE_MILLIS = 5 * MINUTE_MILLIS

    /** 주행 중 기록 스냅샷 간격. 앱이 꺼지면 마지막 스냅샷 시각을 주차 시각으로 본다. */
    const val SNAPSHOT_MILLIS = 10_000L

    // ---- 미완성 카드 ----
    /** [확정] 확정 후 7일 안에 재탑승하면 재안내. 재안내 없이 7일이 지나면 만료. */
    const val REMIND_WINDOW_MILLIS = 7 * DAY_MILLIS

    /** [확정] 재안내한 시각부터 24시간 뒤 만료. */
    const val SAVE_AFTER_REMIND_MILLIS = 24 * HOUR_MILLIS

    /** 출발할 때 남은 양이 직전 주차보다 이만큼(%p) 이상 늘었으면 주유·충전으로 본다. [제안] */
    const val REFUEL_MIN_PERCENT = 5

    // ---- 성장 [제안] ----
    const val GP_BASE = 10
    const val GP_CAP = 150

    // ---- 럭키박스 ----
    const val BOX_DAILY_MAX = 5
    const val BOX_BONUS_MIN = 5
    const val BOX_BONUS_MAX = 30
    const val BADGE_CHANCE = 0.30
    /** 사용자 요청(11/12): 자동차를 꾸미는 아이템도 나온다. [제안] */
    const val CAR_ITEM_CHANCE = 0.35

    // ---- 개성 ----
    const val PERSONALITY_WINDOW = 10
    /** [제안] 이보다 적으면 기본형(라벤더, 장식 없음). */
    const val PERSONALITY_MIN = 3
    /** [제안] 평균 운전점수가 이 이상이면 심장빛이 선명하게 반짝인다. */
    const val HEART_BRIGHT_SCORE = 85
    /** 대표 장식은 같은 목적이 이만큼 이상일 때. */
    const val DECORATION_MIN = 2

    // ---- 운전점수 [자체 산출 확정, 산식 제안] ----
    const val HARSH_ACCEL_MPS2 = 3.0f
    const val HARSH_BRAKE_MPS2 = -3.5f
    /** 1km/h. 이보다 빠른 샘플만 이동시간에 넣는다. */
    const val MOVING_MPS = 0.28f
    const val SCORE_FLOOR = 40

    /** 카드를 만들 수 있는 여정인지. 거리·이동시간을 모르면 추측하지 않는다. */
    fun meetsCardMinimum(distanceKm: Float?, movingSec: Long?, minKm: Float = CARD_MIN_KM, minSec: Long = CARD_MIN_MOVING_SEC): Boolean {
        if (minKm > 0f && distanceKm == null) return false
        if (minSec >= REAL_CARD_MIN_MOVING_SEC && movingSec == null) return false
        return (distanceKm ?: 0f) >= minKm && (movingSec ?: minSec) >= minSec
    }

    /** 카드 1장의 성장 포인트 = 10 + 거리 km(내림) + 이동 분(내림), 상한 150. */
    fun growthPoints(distanceKm: Float?, movingSec: Long?): Int {
        val km = floor((distanceKm ?: 0f).toDouble()).toInt()
        val min = ((movingSec ?: 0L) / 60).toInt()
        return (GP_BASE + km + min).coerceAtMost(GP_CAP)
    }

    /** 운전점수 = 100 − 5 × (급가속·급감속 수 ÷ 주행 10km), 하한 40. 급조작 수를 모르면 null. */
    fun drivingScore(harsh: Int?, distanceKm: Float?): Int? {
        if (harsh == null) return null
        if (harsh == 0) return 100
        val per10km = harsh / ((distanceKm ?: 0f) / 10f).coerceAtLeast(0.1f)
        return (100 - 5 * per10km).toInt().coerceAtLeast(SCORE_FLOOR)
    }
}

/** 운행 목적. [확정 11/12] 하나를 골라야 카드가 완성된다. '기타'는 PRD 결정 2 추천. */
@Serializable
enum class TripPurpose(val label: String, val icon: String, val selectable: Boolean = true) {
    // 2026-11-12 사용자 결정: 목적 10개
    TO_WORK("출근", "🏢"),
    FROM_WORK("퇴근", "🏠"),
    @kotlinx.serialization.SerialName("FIELD") WORK("업무", "💼"),
    MEAL("식사", "🍽️"),
    SHOPPING("장보기", "🛒"),
    TRAVEL("여행", "🧳"),
    DATE("데이트", "💕"),
    EXERCISE("운동", "🏃"),
    HOBBY("취미", "🎨"),
    OTHER("기타", "✨"),

    // 예전 목적. 저장된 값을 읽을 때만 쓰고 불러오면 새 목적으로 옮긴다(CarlingStore.migrate)
    COMMUTE("출퇴근", "🏢", selectable = false),
    FAMILY("가족과 함께", "👨‍👩‍👧", selectable = false);

    companion object {
        val choices: List<TripPurpose> get() = entries.filter { it.selectable }
    }
}

/** 누구랑 갔는지(선택). 2026-11-12 사용자 결정. */
@Serializable
enum class Companion(val label: String, val icon: String) {
    ALONE("혼자", "🙂"),
    FAMILY("가족과", "👨‍👩‍👧"),
    FRIENDS("친구와", "🧑‍🤝‍🧑"),
    PARTNER("연인과", "💑"),
    OTHER("기타", "👥"),
}

/** 출발 시각의 시간대. 카링 몸 색을 정한다. */
@Serializable
enum class TimeBucket(val label: String, val colorName: String, val color: Long) {
    MORNING("아침", "살구빛", 0xFFFFC2A3),
    DAY("낮", "하늘빛", 0xFF9FD3FF),
    EVENING("저녁", "라벤더", 0xFFC7B8FF),
    NIGHT("밤", "남색과 별무늬", 0xFF4B4FA8);

    companion object {
        /** 아침 05~10시 · 낮 10~17시 · 저녁 17~21시 · 밤 21~05시. */
        fun of(hour: Int): TimeBucket = when (hour) {
            in 5..9 -> MORNING
            in 10..16 -> DAY
            in 17..20 -> EVENING
            else -> NIGHT
        }
    }
}

/** 최빈 목적에 따른 대표 장식. '기타'에는 장식이 없다. */
enum class Decoration(val label: String, val icon: String) {
    TIE("넥타이", "👔"),
    BADGE("업무 배지", "📛"),
    HEART_PIN("하트 핀", "💗"),
    BACKPACK("작은 배낭", "🎒"),
    NOTE_PIN("음표 핀", "🎵"),
    SPOON_PIN("숟가락 핀", "🥄"),
    BASKET("장바구니", "🧺"),
    HEADBAND("스포츠 밴드", "🎽");

    /** 이 장식이 나오는 목적(예전 목적 제외). */
    fun purposes(): String = TripPurpose.entries.filter { it != TripPurpose.COMMUTE && it != TripPurpose.FAMILY && of(it) == this }.joinToString("·") { it.label }

    companion object {
        fun of(purpose: TripPurpose): Decoration? = when (purpose) {
            TripPurpose.TO_WORK, TripPurpose.FROM_WORK, TripPurpose.COMMUTE -> TIE
            TripPurpose.WORK -> BADGE
            TripPurpose.DATE, TripPurpose.FAMILY -> HEART_PIN
            TripPurpose.TRAVEL -> BACKPACK
            TripPurpose.HOBBY -> NOTE_PIN
            TripPurpose.MEAL -> SPOON_PIN
            TripPurpose.SHOPPING -> BASKET
            TripPurpose.EXERCISE -> HEADBAND
            TripPurpose.OTHER -> null
        }
    }
}

/**
 * 성장 단계. 카링이 차에 타고 자란다(차량 탑승형 진화, 11/12 확정).
 * 그림은 사용자 전달 원본(11/12). 단계 3·4의 이름과 누적 포인트는 [제안].
 */
@Serializable
enum class Stage(val label: String, val minGp: Int, val line: String, val open: Boolean = true) {
    EGG("알", 0, "함께 달리면 알이 깨어나요."),
    CRACKED("젤리링", 0, "알에서 말랑한 젤리링이 깨어났어요."),
    BABY("아기링", 1_500, "산들바람 날개가 돋은 아기링이 차에 탔어요."),
    BREEZE("꼬마링", 3_500, "불꽃 같은 머리칼의 꼬마링, 차가 더 날렵해졌어요."),
    /** 추후 공개(11/13 사용자 결정): 포인트가 모여도 여기서 멈춘다. */
    MYCARLING("버디링", 6_000, "곧 만나요. 버디링은 추후 공개돼요.", open = false),
    SOUL("소울링", 10_000, "곧 만나요. 소울링은 추후 공개돼요.", open = false);

    val next: Stage? get() = entries.getOrNull(ordinal + 1)

    companion object {
        /** 지금 열려 있는 마지막 단계. */
        val lastOpen: Stage get() = entries.last { it.open }

        /** 보상을 한 번도 받지 않았으면 알, 받은 뒤에는 누적 포인트로 정한다. 단계는 내려가지 않고, 공개 전 단계로는 가지 않는다. */
        fun of(totalGp: Int, rewards: Int): Stage {
            if (rewards == 0) return EGG
            return entries.last { it.open && (it == CRACKED || (it.ordinal > CRACKED.ordinal && totalGp >= it.minGp)) }
        }
    }
}

/** 럭키박스 수집 배지. [확정 11/12] 6종, 이름은 [제안]. */
@Serializable
enum class Badge(val label: String, val icon: String, val how: String) {
    MORNING_SUN("아침 햇살", "🌅", "아침(5~10시)에 출발한 여정"),
    NIGHT_STAR("밤하늘 별", "🌙", "밤(21~5시)에 달린 여정"),
    COMMUTE_COFFEE("출근길 커피", "☕", "출근·퇴근 카드"),
    WEEKEND_OUTING("주말 나들이", "🧺", "토·일요일 여정"),
    REGULAR_ROAD("단골길", "🛣️", "같은 목적을 3번 넘게 완성"),
    MINT_HEART("민트 하트", "💚", "운전점수 85점 이상"),
}

/** 배지 등급: 같은 배지를 더 받으면 오른다(사용자 확정 11/12 '진열장 + 세트 보상'). */
enum class BadgeTier(val label: String, val min: Int, val rim: Long) {
    BRONZE("동", 1, 0xFFD9A27A),
    SILVER("은", 3, 0xFFB9C3D6),
    GOLD("금", 5, 0xFFF2C14E);

    val next: BadgeTier? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun of(count: Int): BadgeTier? = entries.lastOrNull { count >= it.min }
    }
}

object BadgeRules {
    /**
     * 이 여정이 받을 수 있는 배지. 럭키박스에서 배지가 나오면 이 중 하나를 고른다(없으면 아무거나).
     * 배지가 '내 운전 습관의 기록'이 되도록.
     */
    fun eligible(bucket: TimeBucket, weekend: Boolean, purpose: TripPurpose?, samePurposeCount: Int, score: Int?): List<Badge> = buildList {
        if (bucket == TimeBucket.MORNING) add(Badge.MORNING_SUN)
        if (bucket == TimeBucket.NIGHT) add(Badge.NIGHT_STAR)
        if (purpose == TripPurpose.TO_WORK || purpose == TripPurpose.FROM_WORK) add(Badge.COMMUTE_COFFEE)
        if (weekend) add(Badge.WEEKEND_OUTING)
        if (purpose != null && purpose != TripPurpose.OTHER && samePurposeCount >= 3) add(Badge.REGULAR_ROAD)
        if ((score ?: 0) >= Rules.HEART_BRIGHT_SCORE) add(Badge.MINT_HEART)
    }

    /** 6종을 모두 모으면 받는 차체 색. */
    val SET_REWARD = CarColor.AURORA
}

/** 카링의 차에 아이템을 다는 자리. 자리마다 하나씩 동시에 달 수 있다(2026-11-12 사용자 확정). */
@Serializable
enum class CarSlot(val label: String) {
    WHEEL("휠캡"),
    ANTENNA("안테나"),
    REAR("뒤쪽 소품"),
}

/** 럭키박스에서 나오는 자동차 꾸미기 아이템 12종(자리마다 4종). 그림은 원본 생성(2026-11-12). */
@Serializable
enum class CarPart(val label: String, val slot: CarSlot) {
    WHEEL_DAISY("데이지 휠캡", CarSlot.WHEEL),
    WHEEL_CLOVER("클로버 휠캡", CarSlot.WHEEL),
    WHEEL_CANDY("별사탕 휠캡", CarSlot.WHEEL),
    WHEEL_PINWHEEL("바람개비 휠캡", CarSlot.WHEEL),
    ANTENNA_SPROUT("새싹 안테나", CarSlot.ANTENNA),
    ANTENNA_HEART("하트 안테나", CarSlot.ANTENNA),
    ANTENNA_STAR("별 안테나", CarSlot.ANTENNA),
    ANTENNA_PLANET("행성 안테나", CarSlot.ANTENNA),
    REAR_SUITCASE("여행가방", CarSlot.REAR),
    REAR_BACKPACK("캠핑 배낭", CarSlot.REAR),
    REAR_PICNIC("피크닉 바구니", CarSlot.REAR),
    REAR_SURFBOARD("서핑보드", CarSlot.REAR),
}

/**
 * 차체 색(2026-11-12 사용자 확정: 8색, 꾸미기와 별도 수집). 펄 화이트가 기본이고 나머지는 럭키박스에서 모은다.
 * 그림의 밝기는 그대로 두고 [light](밝은 곳)~[dark](그늘) 사이 색으로 칠한다.
 */
@Serializable
enum class CarColor(val label: String, val light: Long, val dark: Long) {
    PEARL("펄 화이트", 0xFFFFFFFF, 0xFF9A98B8),
    MINT("민트", 0xFFA6F0D6, 0xFF2E8F70),
    SAKURA("벚꽃 핑크", 0xFFFFCFE0, 0xFFB45A7C),
    SKY("하늘", 0xFFB3DDFF, 0xFF3D6FA8),
    LEMON("레몬", 0xFFFFF0A6, 0xFFA88A2A),
    LAVENDER("라벤더", 0xFFDCD1FF, 0xFF5E4FA8),
    CORAL("코랄", 0xFFFFC2AE, 0xFFB0523C),
    NAVY("밤하늘 네이비", 0xFF7C86D6, 0xFF1E2358),
    /** 배지 6종을 모두 모으면 받는 특별 색(럭키박스에선 나오지 않는다). */
    AURORA("오로라", 0xFFC8F5EE, 0xFF6B4FC8);

    companion object {
        /** 럭키박스에서 나오는 색(펄 화이트는 처음부터 가지고, 오로라는 배지 세트 보상). */
        val collectible: List<CarColor> get() = entries.filter { it != PEARL && it != AURORA }
    }
}

/**
 * 카링 탭의 차 안 배경(2026-11-12 사용자 결정: 두 구도 중 고르기). 시간대마다 아침·낮·저녁·밤 그림으로 바뀐다.
 * [anchorX]·[anchorY]: 카링 차의 바퀴 바닥이 닿는 자리, [heightFrac]: 카링 그림 높이(배경 그림 대비).
 */
@Serializable
enum class CabinView(val label: String, val anchorX: Float, val anchorY: Float, val heightFrac: Float, val wander: Float) {
    DASH("대시보드", 0.46f, 0.745f, 0.44f, 0.13f),
    DRIVER("운전석 시점", 0.66f, 0.535f, 0.26f, 0.06f),
}

/**
 * 차 안 배경 톤(11/13 시안 3종 중 사용자가 A 차분한 뉴트럴을 골랐다): 원본 그림은 파스텔이 강해 색 보정으로 낮춘다.
 * [sat] 채도, [contrast] 대비, [r]·[g]·[b] 채널 배율, [lift] 밝기 더하기(0~255).
 */
@Serializable
enum class CabinTone(val label: String, val sat: Float, val contrast: Float, val r: Float, val g: Float, val b: Float, val lift: Float) {
    ORIGINAL("원본", 1f, 1f, 1f, 1f, 1f, 0f),
    CALM("A 차분한 뉴트럴", 0.45f, 1.06f, 1f, 1f, 0.98f, -4f),
    WARM("B 웜 그레이지", 0.38f, 1.05f, 1.05f, 1.0f, 0.92f, -2f),
    DEEP("C 딥 그레이 실내", 0.32f, 1.14f, 0.92f, 0.93f, 0.98f, -30f),
}

/** 같은 테마 아이템 세 개를 맞추면 붙는 장착 스타일(사용자 확정, 구성은 구현 제안). */
@Serializable
enum class CarStyle(val label: String, val icon: String, val parts: Set<CarPart>) {
    SPROUT_DRIVE("새싹 드라이브", "🌱", setOf(CarPart.ANTENNA_SPROUT, CarPart.WHEEL_CLOVER, CarPart.REAR_PICNIC)),
    WEEKEND_TRAVELER("주말 여행자", "🧳", setOf(CarPart.ANTENNA_STAR, CarPart.WHEEL_DAISY, CarPart.REAR_SUITCASE)),
    STARLIGHT("별빛 탐험가", "🪐", setOf(CarPart.ANTENNA_PLANET, CarPart.WHEEL_CANDY, CarPart.REAR_BACKPACK)),
    CLOUD_WALK("구름 산책", "☁️", setOf(CarPart.ANTENNA_HEART, CarPart.WHEEL_PINWHEEL, CarPart.REAR_SURFBOARD));

    companion object {
        /** 지금 달린 아이템으로 완성된 스타일. */
        fun of(equipped: Map<CarSlot, CarPart>): CarStyle? = entries.firstOrNull { it.parts == equipped.values.toSet() }
    }
}

/**
 * 카드 등급(시안 v1 제안, 2026-11-12 적용). 보상량은 같고 모습만 다르다.
 * 특별: 첫 카드 또는 100km 이상 장거리. 반짝: 운전점수 85점 이상(심장빛 기준과 같다).
 */
enum class CardTier(val label: String) {
    BASIC("기본"),
    SHINE("반짝"),
    SPECIAL("특별");

    companion object {
        const val SPECIAL_KM = 100f

        fun of(cardNumber: Int, distanceKm: Float?, score: Int?): CardTier = when {
            cardNumber == 1 || (distanceKm ?: 0f) >= SPECIAL_KM -> SPECIAL
            score != null && score >= Rules.HEART_BRIGHT_SCORE -> SHINE
            else -> BASIC
        }
    }
}

/** 카링 개성 3축의 결과. */
@Serializable
data class Personality(
    val body: TimeBucket = TimeBucket.EVENING,
    val decoration: TripPurpose? = null,
    val heartBright: Boolean = false,
    /** 낮형(살구빛 + 선글라스) / 밤형(형광빛 + 안경). 카드가 모자라 정해지지 않았으면 null. */
    val vibe: Vibe? = null,
) {
    val decorationItem: Decoration? get() = decoration?.let(Decoration::of)
}

/**
 * 몸 분위기(11/13 사용자 확정): 최근 카드 10장에서 아침·낮 출발이 많으면 이마에 드라이빙 고글과 햇살 테두리,
 * 저녁·밤 출발이 많으면 이마에 헤드램프와 머리 위를 도는 반딧불, 달빛 테두리. 몸 색은 물들이지 않는다.
 */
@Serializable
enum class Vibe(val label: String, val colorName: String, val eyewear: String, val icon: String, val color: Long, val buckets: Set<TimeBucket>) {
    // 11/13 2차 시안에서 사용자 선택: 낮형 C 드라이빙 고글, 밤형 B 헤드램프 + C 반딧불
    SUNNY("낮길형", "햇살 테두리", "드라이빙 고글", "🥽", 0xFFFFC94D, setOf(TimeBucket.MORNING, TimeBucket.DAY)),
    NEON("밤길형", "달빛 테두리", "헤드램프와 반딧불", "🔦", 0xFF8FC8FF, setOf(TimeBucket.EVENING, TimeBucket.NIGHT));

    companion object {
        fun of(bucket: TimeBucket): Vibe = entries.first { bucket in it.buckets }
    }
}

/** 개성 분석에 쓰는 카드 한 장의 값. */
data class PersonalityInput(val tripId: Long, val bucket: TimeBucket, val purpose: TripPurpose?, val score: Int?)

/** 개성 계산 결과와 근거. */
data class PersonalityResult(
    val personality: Personality,
    val used: List<Long>,
    val bucketCounts: Map<TimeBucket, Int>,
    val purposeCounts: Map<TripPurpose, Int>,
    val avgScore: Int?,
) {
    val sampleSize: Int get() = used.size

    /** 몸 색 근거 한 줄. */
    fun bodyReason(): String {
        val v = personality.vibe ?: return "카드가 ${Rules.PERSONALITY_MIN}장 모이면 고글이나 헤드램프를 써요"
        val n = v.buckets.sumOf { bucketCounts[it] ?: 0 }
        return "최근 ${sampleSize}장 중 ${v.buckets.joinToString("·") { it.label }} 출발이 ${n}장 → ${v.eyewear}"
    }

    fun decorationReason(): String {
        val p = personality.decoration
        return when {
            sampleSize < Rules.PERSONALITY_MIN -> "카드가 ${Rules.PERSONALITY_MIN}장 모이면 장식이 생겨요"
            p == null -> "같은 목적이 ${Rules.DECORATION_MIN}장 이상 모이면 장식이 생겨요"
            else -> "최근 ${sampleSize}장 중 ${p.label.iGa()} ${purposeCounts[p] ?: 0}장이에요"
        }
    }

    fun heartReason(): String = when {
        avgScore == null -> "운전점수가 쌓이면 심장빛이 반응해요"
        personality.heartBright -> "최근 평균 운전점수 ${avgScore}점, 심장이 선명하게 반짝여요"
        else -> "최근 평균 운전점수 ${avgScore}점 · ${Rules.HEART_BRIGHT_SCORE}점 이상이면 더 반짝여요"
    }
}

object PersonalityRules {
    /**
     * 최근 저장 카드 10장(오래된 것 → 최근 순으로 받음)으로 개성을 정한다.
     * 10장 미만이면 있는 만큼, 3장 미만이면 기본형. 동률이면 가장 최근 카드 쪽이 이긴다.
     * 미분류 목적과 없는 점수는 추정하지 않는다.
     */
    fun compute(cards: List<PersonalityInput>): PersonalityResult {
        val recent = cards.takeLast(Rules.PERSONALITY_WINDOW)
        val buckets = recent.groupingBy { it.bucket }.eachCount()
        val purposes = recent.mapNotNull { it.purpose }.groupingBy { it }.eachCount()
        val scores = recent.mapNotNull { it.score }
        val avg = if (scores.isEmpty()) null else scores.average().toInt()
        if (recent.size < Rules.PERSONALITY_MIN) return PersonalityResult(Personality(), recent.map { it.tripId }, buckets, purposes, avg)

        val body = mostRecentWinner(recent.map { it.bucket }, buckets) ?: TimeBucket.EVENING
        val decoCandidates = purposes.filterKeys { it != TripPurpose.OTHER }.filterValues { it >= Rules.DECORATION_MIN }
        val decoration = mostRecentWinner(recent.mapNotNull { it.purpose }.filter { it in decoCandidates }, decoCandidates)
        val bright = avg != null && avg >= Rules.HEART_BRIGHT_SCORE
        val vibes = recent.groupingBy { Vibe.of(it.bucket) }.eachCount()
        val vibe = mostRecentWinner(recent.map { Vibe.of(it.bucket) }, vibes)
        return PersonalityResult(Personality(body, decoration, bright, vibe), recent.map { it.tripId }, buckets, purposes, avg)
    }

    private fun <T> mostRecentWinner(order: List<T>, counts: Map<T, Int>): T? {
        if (counts.isEmpty()) return null
        val best = counts.maxOf { it.value }
        return order.lastOrNull { counts[it] == best }
    }
}

/** 상자 생성 시점에 확정하는 럭키박스 내용. */
data class BoxContent(val bonusGp: Int, val badge: Badge?, val part: CarPart?, val color: CarColor? = null)

object LuckyBoxRules {
    /** [roll]은 0~1 난수를 돌려준다(시험에서 고정할 수 있게). 배지와 자동차 아이템은 둘 중 하나만 나온다. */
    fun draw(eligible: List<Badge> = Badge.entries, roll: () -> Double): BoxContent {
        val pool = eligible.ifEmpty { Badge.entries }
        val bonus = Rules.BOX_BONUS_MIN + (roll() * (Rules.BOX_BONUS_MAX - Rules.BOX_BONUS_MIN + 1)).toInt().coerceAtMost(Rules.BOX_BONUS_MAX - Rules.BOX_BONUS_MIN)
        val r = roll()
        val badge = if (r < Rules.BADGE_CHANCE) pool[(roll() * pool.size).toInt().coerceAtMost(pool.lastIndex)] else null
        // 자동차 꾸미기 몫에서 아이템 12종과 색 7종을 고르게 뽑는다
        if (badge != null || r >= Rules.BADGE_CHANCE + Rules.CAR_ITEM_CHANCE) return BoxContent(bonus, badge, null)
        val total = CarPart.entries.size + CarColor.collectible.size
        val pick = (roll() * total).toInt().coerceAtMost(total - 1)
        return if (pick < CarPart.entries.size) BoxContent(bonus, null, CarPart.entries[pick])
        else BoxContent(bonus, null, null, CarColor.collectible[pick - CarPart.entries.size])
    }
}

/**
 * 레벨(사용자 확정 11/12): 성장 포인트로 LV.1~50. 처음엔 빨리, 갈수록 천천히 오른다.
 * LV.n이 되려면 누적 40 × (n−1)^1.5 점. 10레벨마다 차 앞 엠블럼이 한 단계 화려해진다.
 */
object LevelRules {
    const val MAX = 50

    fun need(level: Int): Int = (40.0 * Math.pow((level - 1).coerceAtLeast(0).toDouble(), 1.5)).toInt()

    fun of(totalGp: Int): Int = (1..MAX).last { need(it) <= totalGp }

    /** 다음 레벨까지 진행(0~1). 최고 레벨이면 1. */
    fun progress(totalGp: Int): Float {
        val l = of(totalGp)
        if (l >= MAX) return 1f
        return ((totalGp - need(l)).toFloat() / (need(l + 1) - need(l))).coerceIn(0f, 1f)
    }
}

/** 엠블럼 단계: LV 1–9 새싹, 10–19 동, 20–29 은, 30–39 금, 40–50 오로라. */
enum class EmblemTier(val label: String, val minLevel: Int) {
    SPROUT("새싹", 1), BRONZE("동", 10), SILVER("은", 20), GOLD("금", 30), AURORA("오로라", 40);

    companion object {
        fun of(level: Int): EmblemTier = entries.last { level >= it.minLevel }
    }
}

/**
 * 젤리(상점 재화, 사용자 확정 11/12). 성장 포인트와 따로 모으고, 써도 성장은 줄지 않는다.
 * 카드 보상 1회 15 + 거리 5km마다 1(최대 40), 럭키박스 보너스 점수만큼, 이미 가진 배지·꾸미기·색이 나오면 20, 레벨업 50.
 */
object JellyRules {
    fun forReward(km: Float?): Int = 15 + ((km ?: 0f) / 5f).toInt().coerceAtMost(25)
    const val DUPLICATE = 20
    const val LEVEL_UP = 50
}

/** 상점 물건. 꾸미기·색은 기존 그림, 배경 테마는 차 안 배경의 시간대를 고정한다. */
sealed class ShopItem(val key: String, val label: String, val price: Int) {
    class Part(val part: CarPart) : ShopItem("part_${part.name}", part.label, 300)
    class Paint(val color: CarColor) : ShopItem("color_${color.name}", "${color.label} 차체 색", 400)
    class Theme(val bucket: TimeBucket) : ShopItem("theme_${bucket.name}", "${bucket.label} 배경 고정", 500)

    companion object {
        val all: List<ShopItem>
            get() = CarPart.entries.map(::Part) + CarColor.collectible.map(::Paint) + TimeBucket.entries.map(::Theme)
    }
}
