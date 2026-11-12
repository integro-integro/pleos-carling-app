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
enum class TripPurpose(val label: String, val icon: String) {
    COMMUTE("출퇴근", "🏢"),
    FIELD("외근", "💼"),
    FAMILY("가족과 함께", "👨‍👩‍👧"),
    TRAVEL("여행", "🧳"),
    HOBBY("취미", "🎨"),
    OTHER("기타", "✨"),
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
    NOTE_PIN("음표 핀", "🎵");

    companion object {
        fun of(purpose: TripPurpose): Decoration? = when (purpose) {
            TripPurpose.COMMUTE -> TIE
            TripPurpose.FIELD -> BADGE
            TripPurpose.FAMILY -> HEART_PIN
            TripPurpose.TRAVEL -> BACKPACK
            TripPurpose.HOBBY -> NOTE_PIN
            TripPurpose.OTHER -> null
        }
    }
}

/**
 * 성장 단계. 카링이 차에 타고 자란다(차량 탑승형 진화, 11/12 확정).
 * 그림은 사용자 전달 원본(11/12). 단계 3·4의 이름과 누적 포인트는 [제안].
 */
@Serializable
enum class Stage(val label: String, val minGp: Int, val line: String) {
    EGG("젤리알", 0, "함께 달리면 알이 깨어나요."),
    CRACKED("깨어난 알", 0, "알에 첫 금이 가고 민트빛이 새어 나와요."),
    BABY("아기카링", 1_500, "통통 튀는 아기카링이 동그란 차에 탔어요."),
    BREEZE("산들카링", 3_500, "산들바람 날개가 돋고 차가 더 날렵해졌어요."),
    MYCARLING("마이카링", 6_000, "바람을 닮은 마이카링이 반짝이는 차를 몰아요.");

    val next: Stage? get() = entries.getOrNull(ordinal + 1)

    companion object {
        /** 보상을 한 번도 받지 않았으면 알, 받은 뒤에는 누적 포인트로 정한다. 단계는 내려가지 않는다. */
        fun of(totalGp: Int, rewards: Int): Stage {
            if (rewards == 0) return EGG
            return entries.last { it == CRACKED || (it.ordinal > CRACKED.ordinal && totalGp >= it.minGp) }
        }
    }
}

/** 럭키박스 수집 배지. [확정 11/12] 6종, 이름은 [제안]. */
@Serializable
enum class Badge(val label: String, val icon: String) {
    MORNING_SUN("아침 햇살", "🌅"),
    NIGHT_STAR("밤하늘 별", "🌙"),
    COMMUTE_COFFEE("출근길 커피", "☕"),
    WEEKEND_OUTING("주말 나들이", "🧺"),
    REGULAR_ROAD("단골길", "🛣️"),
    MINT_HEART("민트 하트", "💚"),
}

/** 럭키박스에서 나오는 자동차 꾸미기 아이템. 카링의 차에 하나를 붙인다. [제안] */
@Serializable
enum class CarItem(val label: String, val icon: String) {
    STAR_STICKER("별 스티커", "⭐"),
    HEART_STICKER("하트 스티커", "💖"),
    RAINBOW_DECAL("무지개 데칼", "🌈"),
    FLOWER_ANTENNA("꽃 안테나", "🌼"),
    ROOF_FLAG("루프 깃발", "🚩"),
    RIBBON("리본", "🎀"),
    BOLT_DECAL("번개 데칼", "⚡"),
    CLOVER("네잎클로버", "🍀"),
}

/** 카링 개성 3축의 결과. */
@Serializable
data class Personality(
    val body: TimeBucket = TimeBucket.EVENING,
    val decoration: TripPurpose? = null,
    val heartBright: Boolean = false,
) {
    val decorationItem: Decoration? get() = decoration?.let(Decoration::of)
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
    fun bodyReason(): String = if (sampleSize < Rules.PERSONALITY_MIN) "카드가 ${Rules.PERSONALITY_MIN}장 모이면 몸 색이 정해져요"
    else "최근 ${sampleSize}장 중 ${personality.body.label} 출발이 ${bucketCounts[personality.body] ?: 0}장이에요"

    fun decorationReason(): String {
        val p = personality.decoration
        return when {
            sampleSize < Rules.PERSONALITY_MIN -> "카드가 ${Rules.PERSONALITY_MIN}장 모이면 장식이 생겨요"
            p == null -> "같은 목적이 ${Rules.DECORATION_MIN}장 이상 모이면 장식이 생겨요"
            else -> "최근 ${sampleSize}장 중 ${p.label}이 ${purposeCounts[p] ?: 0}장이에요"
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
        return PersonalityResult(Personality(body, decoration, bright), recent.map { it.tripId }, buckets, purposes, avg)
    }

    private fun <T> mostRecentWinner(order: List<T>, counts: Map<T, Int>): T? {
        if (counts.isEmpty()) return null
        val best = counts.maxOf { it.value }
        return order.lastOrNull { counts[it] == best }
    }
}

/** 상자 생성 시점에 확정하는 럭키박스 내용. */
data class BoxContent(val bonusGp: Int, val badge: Badge?, val carItem: CarItem?)

object LuckyBoxRules {
    /** [roll]은 0~1 난수를 돌려준다(시험에서 고정할 수 있게). 배지와 자동차 아이템은 둘 중 하나만 나온다. */
    fun draw(roll: () -> Double): BoxContent {
        val bonus = Rules.BOX_BONUS_MIN + (roll() * (Rules.BOX_BONUS_MAX - Rules.BOX_BONUS_MIN + 1)).toInt().coerceAtMost(Rules.BOX_BONUS_MAX - Rules.BOX_BONUS_MIN)
        val r = roll()
        val badge = if (r < Rules.BADGE_CHANCE) Badge.entries[(roll() * Badge.entries.size).toInt().coerceAtMost(Badge.entries.lastIndex)] else null
        val car = if (badge == null && r < Rules.BADGE_CHANCE + Rules.CAR_ITEM_CHANCE) CarItem.entries[(roll() * CarItem.entries.size).toInt().coerceAtMost(CarItem.entries.lastIndex)] else null
        return BoxContent(bonus, badge, car)
    }
}
