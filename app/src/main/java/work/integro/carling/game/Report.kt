package work.integro.carling.game

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** 리포트 기간. 이번 달·지난 달·최근 7일로 시작한다(PRD 4.6). */
enum class ReportPeriod(val label: String, val sentence: String) {
    THIS_MONTH("이번 달", "이번 달"),
    LAST_MONTH("지난 달", "지난달"),
    LAST_7_DAYS("최근 7일", "최근 7일");

    /** [today] 기준 (시작, 끝) 날짜. 끝은 포함. */
    fun range(today: LocalDate): Pair<LocalDate, LocalDate> = when (this) {
        THIS_MONTH -> today.withDayOfMonth(1) to today
        LAST_MONTH -> YearMonth.from(today).minusMonths(1).let { it.atDay(1) to it.atEndOfMonth() }
        LAST_7_DAYS -> today.minusDays(6) to today
    }

    /** 비교할 직전 기간. */
    fun previous(today: LocalDate): Pair<LocalDate, LocalDate> = when (this) {
        THIS_MONTH -> YearMonth.from(today).minusMonths(1).let { it.atDay(1) to it.atEndOfMonth() }
        LAST_MONTH -> YearMonth.from(today).minusMonths(2).let { it.atDay(1) to it.atEndOfMonth() }
        LAST_7_DAYS -> today.minusDays(13) to today.minusDays(7)
    }

    val previousLabel: String
        get() = when (this) {
            THIS_MONTH -> "지난달"
            LAST_MONTH -> "그 전 달"
            LAST_7_DAYS -> "그 전 7일"
        }
}

/** 리포트에 넣을 운행 한 건(확정된 운행). */
data class ReportTrip(
    val tripId: Long,
    val startedAt: Long,
    val distanceKm: Float?,
    val movingSec: Long?,
    val elapsedSec: Long,
    val score: Int?,
    /** 완성한 카드의 목적. 미완성·만료·기준 미달은 null(미분류). */
    val purpose: TripPurpose?,
    val hasCard: Boolean,
    val cardState: CardState?,
    val rewardedGp: Int?,
    val rewardedAt: Long?,
)

data class PurposeRow(val purpose: TripPurpose?, val count: Int, val km: Double, val sec: Long, val timeShare: Int)

data class Totals(val km: Double, val sec: Long, val days: Int, val count: Int) {
    val hasData: Boolean get() = count > 0
}

data class ReportData(
    val period: ReportPeriod,
    val from: LocalDate,
    val to: LocalDate,
    val totals: Totals,
    val previous: Totals,
    val findings: List<String>,
    val nextSteps: List<String>,
    val purposes: List<PurposeRow>,
    val unclassifiedDrafts: Int,
    val unclassifiedExpired: Int,
    val belowMinimum: Int,
    val buckets: Map<TimeBucket, Int>,
    val weekday: Int,
    val weekend: Int,
    val weekendKm: Double,
    val shortTrips: Int,
    val midTrips: Int,
    val longTrips: Int,
    val perDrivingDay: Double,
    val dailyKm: List<Pair<LocalDate, Double>>,
    val longest: ReportTrip?,
    val avgScore: Int?,
    val completedCards: Int,
    val gpEarned: Int,
    val enoughForFindings: Boolean,
)

/**
 * 리포트 집계(생성형 AI 미사용). 운행 숫자는 확정된 모든 운행에서, 목적별·성장 내역은 완성한 카드에서 나온다.
 * 만료된 카드의 운행도 숫자에는 그대로 남는다.
 */
object ReportAggregator {
    const val SHORT_KM = 10.0
    const val LONG_KM = 30.0
    const val MIN_TRIPS_FOR_FINDINGS = 3

    fun build(period: ReportPeriod, today: LocalDate, zone: ZoneId, trips: List<ReportTrip>, pendingDrafts: Int): ReportData {
        val (from, to) = period.range(today)
        val (pFrom, pTo) = period.previous(today)
        fun dateOf(t: ReportTrip): LocalDate = Instant.ofEpochMilli(t.startedAt).atZone(zone).toLocalDate()
        fun hourOf(t: ReportTrip): Int = Instant.ofEpochMilli(t.startedAt).atZone(zone).hour
        val inPeriod = trips.filter { dateOf(it) in from..to }.sortedBy { it.startedAt }
        val inPrev = trips.filter { dateOf(it) in pFrom..pTo }

        fun totals(list: List<ReportTrip>) = Totals(
            km = list.sumOf { (it.distanceKm ?: 0f).toDouble() },
            sec = list.sumOf { it.movingSec ?: 0L },
            days = list.map(::dateOf).distinct().size,
            count = list.size,
        )
        val totals = totals(inPeriod)
        val previous = totals(inPrev)

        // 목적별: 완성 카드의 목적, 나머지는 미분류
        val totalSec = totals.sec.coerceAtLeast(1)
        val purposes = inPeriod.groupBy { it.purpose }.map { (p, list) ->
            val sec = list.sumOf { it.movingSec ?: 0L }
            PurposeRow(p, list.size, list.sumOf { (it.distanceKm ?: 0f).toDouble() }, sec, (sec * 100.0 / totalSec).roundToInt())
        }.sortedWith(compareBy<PurposeRow> { it.purpose == null }.thenByDescending { it.sec })
        val unclassified = inPeriod.filter { it.purpose == null }
        val drafts = unclassified.count { it.cardState == CardState.DRAFT }
        val expired = unclassified.count { it.cardState == CardState.EXPIRED }
        val below = unclassified.count { !it.hasCard }

        val buckets = TimeBucket.entries.associateWith { b -> inPeriod.count { TimeBucket.of(hourOf(it)) == b } }
        val weekendTrips = inPeriod.filter { dateOf(it).dayOfWeek.value >= 6 }
        val kmOf = { t: ReportTrip -> (t.distanceKm ?: 0f).toDouble() }
        val dailyKm = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
            .map { d -> d to inPeriod.filter { dateOf(it) == d }.sumOf(kmOf) }.toList()
        val scores = inPeriod.mapNotNull { it.score }
        val completed = inPeriod.count { it.purpose != null }
        val gp = inPeriod.sumOf { it.rewardedGp ?: 0 }
        val longest = inPeriod.maxByOrNull(kmOf)

        val enough = totals.count >= MIN_TRIPS_FOR_FINDINGS
        val findings = if (enough) findings(period, totals, previous, purposes, buckets, weekendTrips.size, weekendTrips.sumOf(kmOf), longest, completed, zone) else emptyList()
        val next = nextSteps(totals, unclassified.size, pendingDrafts)

        return ReportData(
            period, from, to, totals, previous, findings, next, purposes, drafts, expired, below, buckets,
            weekday = inPeriod.size - weekendTrips.size,
            weekend = weekendTrips.size,
            weekendKm = weekendTrips.sumOf(kmOf),
            shortTrips = inPeriod.count { kmOf(it) < SHORT_KM },
            midTrips = inPeriod.count { kmOf(it) >= SHORT_KM && kmOf(it) < LONG_KM },
            longTrips = inPeriod.count { kmOf(it) >= LONG_KM },
            perDrivingDay = if (totals.days == 0) 0.0 else totals.count.toDouble() / totals.days,
            dailyKm = dailyKm,
            longest = longest,
            avgScore = if (scores.isEmpty()) null else scores.average().roundToInt(),
            completedCards = completed,
            gpEarned = gp,
            enoughForFindings = enough,
        )
    }

    /** 핵심 발견: 위에서부터 조건을 검사해 맞는 문장을 최대 3개. */
    private fun findings(
        period: ReportPeriod,
        totals: Totals,
        previous: Totals,
        purposes: List<PurposeRow>,
        buckets: Map<TimeBucket, Int>,
        weekendCount: Int,
        weekendKm: Double,
        longest: ReportTrip?,
        completed: Int,
        zone: ZoneId,
    ): List<String> {
        val out = mutableListOf<String>()
        val p = period.sentence
        // 1. 완성 카드가 3장 이상이면 가장 큰 목적의 시간 비중
        val top = purposes.firstOrNull { it.purpose != null }
        if (completed >= 3 && top?.purpose != null) out += "$p 운전시간의 ${top.timeShare}%는 ${top.purpose.label}이었어요."
        // 2. 직전 기간 대비 운전시간이 10% 이상 달라졌으면
        if (previous.sec > 0) {
            val diff = totals.sec - previous.sec
            if (abs(diff) >= previous.sec * 0.1) out += "${period.previousLabel}보다 운전시간이 ${duration(abs(diff))} ${if (diff > 0) "늘었어요" else "줄었어요"}."
        }
        // 3. 한 시간대가 전체 횟수의 40% 이상이면
        val topBucket = buckets.maxByOrNull { it.value }
        if (topBucket != null && topBucket.value >= totals.count * 0.4) out += "가장 자주 출발한 시간은 ${topBucket.key.label}이었어요."
        // 4. 주말 운행이 있으면
        if (weekendCount > 0) out += "주말에는 ${weekendCount}번, 모두 ${km(weekendKm)}를 함께 달렸어요."
        // 5. 해당이 없으면 가장 긴 여정
        if (out.isEmpty() && longest != null) {
            val d = Instant.ofEpochMilli(longest.startedAt).atZone(zone).toLocalDate()
            out += "$p 가장 긴 여정은 ${d.monthValue}월 ${d.dayOfMonth}일의 ${km((longest.distanceKm ?: 0f).toDouble())}였어요."
        }
        return out.take(3)
    }

    /** 다음에 살펴볼 일. */
    private fun nextSteps(totals: Totals, unclassified: Int, pendingDrafts: Int): List<String> {
        val out = mutableListOf<String>()
        if (totals.count > 0 && unclassified >= totals.count * 0.2) out += "목적을 남기지 않은 여정이 ${unclassified}건 있어요. 남기면 리포트가 더 정확해져요."
        if (pendingDrafts > 0) out += "아직 완성하지 않은 여정이 ${pendingDrafts}건 있어요."
        return out.take(2)
    }

    fun km(v: Double): String = if (v >= 100) "%,dkm".format(v.roundToInt()) else "%.1fkm".format(v)

    fun duration(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        return when {
            h > 0 && m > 0 -> "${h}시간 ${m}분"
            h > 0 -> "${h}시간"
            else -> "${m}분"
        }
    }
}
