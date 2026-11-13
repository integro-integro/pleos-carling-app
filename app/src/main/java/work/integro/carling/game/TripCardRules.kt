package work.integro.carling.game

import kotlinx.serialization.Serializable

/** 운행 상태: 기록 중 → 주차됨 → 확정. 카드 상태와 따로 움직인다. */
@Serializable
enum class TripStatus { RECORDING, PARKED, CONFIRMED }

/** 카드 상태: 임시 → 완성. 확정 뒤에도 임시로 남으면 기한이 지나 만료된다. */
@Serializable
enum class CardState { DRAFT, COMPLETED, EXPIRED }

/** 카드의 기한을 판단하는 데 쓰는 값만 모은 것. */
data class CardClock(
    val state: CardState,
    /** 운행이 확정된 시각. 확정 전이면 null(기한이 흐르지 않는다). */
    val confirmedAt: Long?,
    /** 재안내한 시각. */
    val remindedAt: Long?,
)

/**
 * 미완성 카드의 재안내·만료 규칙(양식 그대로).
 * 확정 후 임시로 남은 카드는 7일 안에 재탑승하면 주차 상태에서 재안내하고, 재안내 시점부터 24시간 뒤 만료된다.
 * 재안내 없이 7일이 지나도 만료된다. 앱을 다시 켜도 기한은 늘어나지 않는다. 만료돼도 운행 기록은 남는다.
 */
object TripCardRules {
    fun expiresAt(card: CardClock): Long? {
        if (card.state != CardState.DRAFT) return null
        val confirmed = card.confirmedAt ?: return null
        return card.remindedAt?.let { it + Rules.SAVE_AFTER_REMIND_MILLIS } ?: (confirmed + Rules.REMIND_WINDOW_MILLIS)
    }

    fun isExpired(card: CardClock, now: Long): Boolean = expiresAt(card)?.let { now >= it } ?: false

    /** 재안내할 카드인지: 임시·확정됨·아직 재안내 전·이번 시동 전에 확정된 카드. */
    fun needsReminder(card: CardClock, ignitionMillis: Long, now: Long): Boolean {
        val confirmed = card.confirmedAt ?: return false
        return card.state == CardState.DRAFT && card.remindedAt == null && confirmed < ignitionMillis && !isExpired(card, now)
    }

    const val SUGGEST_HOUR_RANGE = 1
    const val SUGGEST_MIN_MATCHES = 2

    /**
     * 위치 없이 고르는 기본 목적: 주중/주말이 같고 출발 시각이 ±1시간인 지난 완성 카드에서 가장 많이 고른 목적.
     * 2장 미만이면 제안하지 않는다. 동률이면 가장 최근. [history]는 (출발 시, 주말 여부, 목적) 오래된 순.
     */
    fun suggestPurpose(hour: Int, weekend: Boolean, history: List<Triple<Int, Boolean, TripPurpose>>): TripPurpose? {
        val matches = history.filter { (h, w, _) -> w == weekend && hourDistance(h, hour) <= SUGGEST_HOUR_RANGE }
        if (matches.size < SUGGEST_MIN_MATCHES) return null
        val counts = matches.groupingBy { it.third }.eachCount()
        val best = counts.maxOf { it.value }
        return matches.asReversed().first { counts[it.third] == best }.third
    }

    private fun hourDistance(a: Int, b: Int): Int {
        val d = kotlin.math.abs(a - b) % 24
        return minOf(d, 24 - d)
    }

    /** 사용자 태그 정리: 앞뒤 공백·'#' 제거, 최대 12자. */
    fun cleanTag(raw: String): String? = raw.trim().trimStart('#').trim().take(12).ifEmpty { null }
}
