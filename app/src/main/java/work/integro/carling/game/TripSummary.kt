package work.integro.carling.game

import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/*
 * 트립카드 요약(사용자 확정 11/12): 선택한 기간의 카드로 '언제 · 어디를 · (누구랑) · 왜' 갔는지와 생활반경,
 * 반복되는 루틴과 특별한 여정을 뽑는다. 생성형 AI 없이 규칙으로 한 줄 요약을 만든다.
 */

/** 요약에 들어가는 카드 한 장. 목적은 완성한 카드만. */
data class SumTrip(
    val startedAt: Long,
    val km: Float?,
    val movingSec: Long?,
    val purpose: TripPurpose?,
    val custom: String?,
    val companion: Companion?,
    val startDong: String?,
    val endDong: String?,
    val tier: CardTier,
)

/** 반복되는 루틴: 같은 목적 · 주중/주말 · 출발 시간대(· 같은 도착 동네)가 3번 이상. */
data class Routine(val purpose: TripPurpose, val weekend: Boolean, val bucket: TimeBucket, val place: String?, val count: Int, val with: Companion? = null) {
    val label: String get() = "${if (weekend) "주말" else "평일"} ${bucket.label} ${purpose.label}" + (place?.let { " → $it" } ?: "")
}

data class TripSummaryData(
    val cards: Int,
    val headline: String,
    /** 목적별 장수(많은 순). null = 아직 목적 없음. */
    val purposes: List<Pair<TripPurpose?, Int>>,
    /** 요일(월~일) × 시간대 장수. */
    val heat: List<List<Int>>,
    val peak: Pair<Int, TimeBucket>?,
    /** 자주 간 곳(도착 동네 이름, 장수). 위치를 켜지 않았으면 비어 있다. */
    val places: List<Pair<String, Int>>,
    /** 누구랑: 혼자(고르지 않음 포함)와 함께. */
    val companions: List<Pair<Companion?, Int>>,
    val routines: List<Routine>,
    val farthestKm: Float?,
    val shine: Int,
    val special: Int,
)

object TripSummaryRules {
    const val ROUTINE_MIN = 3

    fun build(trips: List<SumTrip>, zone: ZoneId, home: String?, name: (String) -> String?, radiusKm: Double?, newPlaces: Int): TripSummaryData {
        fun z(t: SumTrip) = Instant.ofEpochMilli(t.startedAt).atZone(zone)
        fun bucket(t: SumTrip) = TimeBucket.of(z(t).hour)
        fun weekend(t: SumTrip) = z(t).dayOfWeek.value >= 6
        val purposes = trips.groupingBy { it.purpose }.eachCount().toList().sortedWith(compareBy<Pair<TripPurpose?, Int>> { it.first == null }.thenByDescending { it.second })
        val heat = (1..7).map { d -> TimeBucket.entries.map { b -> trips.count { z(it).dayOfWeek.value == d && bucket(it) == b } } }
        val peak = heat.flatMapIndexed { d, row -> row.mapIndexed { b, n -> Triple(d + 1, TimeBucket.entries[b], n) } }.filter { it.third > 0 }.maxByOrNull { it.third }?.let { it.first to it.second }
        val places = trips.mapNotNull { it.endDong }.filter { it != home }.groupingBy { it }.eachCount().toList()
            .sortedByDescending { it.second }.take(3).mapNotNull { (code, n) -> name(code)?.let { it to n } }
        val companions = trips.filter { it.purpose != null }.groupingBy { it.companion.takeIf { c -> c != Companion.ALONE } }.eachCount().toList().sortedByDescending { it.second }
        val routines = trips.filter { it.purpose != null && it.purpose != TripPurpose.OTHER }
            .groupBy { Triple(it.purpose!!, weekend(it), bucket(it)) }
            .filter { it.value.size >= ROUTINE_MIN }
            .map { (k, list) ->
                val dest = list.mapNotNull { it.endDong }.filter { it != home }.groupingBy { it }.eachCount().maxByOrNull { it.value }
                // 함께한 사람은 그 루틴의 절반 넘게 같을 때만
                val who = list.mapNotNull { it.companion?.takeIf { c -> c != Companion.ALONE } }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.takeIf { it.value * 2 > list.size }?.key
                Routine(k.first, k.second, k.third, dest?.takeIf { it.value * 2 >= list.size }?.key?.let(name), list.size, who)
            }.sortedByDescending { it.count }
        val headline = headline(trips, routines, home?.let(name), radiusKm, newPlaces, companions)
        return TripSummaryData(
            cards = trips.size,
            headline = headline,
            purposes = purposes,
            heat = heat,
            peak = peak,
            places = places,
            companions = companions,
            routines = routines.take(4),
            farthestKm = trips.mapNotNull { it.km }.maxOrNull(),
            shine = trips.count { it.tier == CardTier.SHINE },
            special = trips.count { it.tier == CardTier.SPECIAL },
        )
    }

    /** 한 줄 요약: 평일 대표 루틴 · 주말 대표 루틴 · 생활반경. 루틴이 없으면 가장 많은 목적과 때. */
    private fun headline(trips: List<SumTrip>, routines: List<Routine>, home: String?, radius: Double?, newPlaces: Int, companions: List<Pair<Companion?, Int>>): String {
        if (trips.isEmpty()) return "이 기간에는 카드가 없어요."
        val parts = mutableListOf<String>()
        fun phrase(r: Routine): String {
            val who = r.with?.let { "${it.label} " } ?: ""
            val where = r.place?.let { p -> (if (home != null) "$home → $p " else "$p ") } ?: ""
            return "${if (r.weekend) "주말" else "평일"} ${r.bucket.label}엔 $who$where${r.purpose.label}"
        }
        routines.firstOrNull { !it.weekend }?.let { parts += phrase(it) }
        routines.firstOrNull { it.weekend }?.let { parts += phrase(it) }
        if (parts.isEmpty()) {
            val top = trips.mapNotNull { it.purpose }.groupingBy { it }.eachCount().maxByOrNull { it.value }
            parts += top?.let { "${it.key.label} 카드가 가장 많았어요(${it.value}장)" } ?: "아직 목적을 고르지 않은 카드가 많아요"
        }
        radius?.let { parts += "생활반경 ${ReachRules.km(it)}" + if (newPlaces > 0) ", 새 동네 ${newPlaces}곳" else "" }
        return parts.joinToString(" · ")
    }

    /** 상세보기: 같은 목적 카드들의 평균 거리·시간. */
    fun sameAverage(all: List<SumTrip>, purpose: TripPurpose?): Pair<Double, Long>? {
        val same = all.filter { purpose != null && it.purpose == purpose && it.km != null }
        if (same.size < 2) return null
        return same.map { it.km!!.toDouble() }.average() to same.mapNotNull { it.movingSec }.average().roundToInt().toLong()
    }
}
