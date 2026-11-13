package work.integro.carling.game

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** 기간 단위(사용자 요청 11/12): 주·월·분기·년. */
enum class PeriodUnit(val label: String) { WEEK("주"), MONTH("월"), QUARTER("분기"), YEAR("년") }

/**
 * 리포트·카드 기간. [offset] 0이면 지금이 든 기간, -1이면 그 앞 기간.
 * 지금 기간은 오늘까지만 센다. 주는 월요일에 시작한다.
 */
data class ReportPeriod(val unit: PeriodUnit, val offset: Int = 0) {

    private fun startOf(today: LocalDate, k: Int): LocalDate = when (unit) {
        PeriodUnit.WEEK -> today.minusDays(today.dayOfWeek.value - 1L).plusWeeks(k.toLong())
        PeriodUnit.MONTH -> today.withDayOfMonth(1).plusMonths(k.toLong())
        PeriodUnit.QUARTER -> LocalDate.of(today.year, (today.monthValue - 1) / 3 * 3 + 1, 1).plusMonths(3L * k)
        PeriodUnit.YEAR -> LocalDate.of(today.year, 1, 1).plusYears(k.toLong())
    }

    private fun endOf(start: LocalDate): LocalDate = when (unit) {
        PeriodUnit.WEEK -> start.plusDays(6)
        PeriodUnit.MONTH -> start.plusMonths(1).minusDays(1)
        PeriodUnit.QUARTER -> start.plusMonths(3).minusDays(1)
        PeriodUnit.YEAR -> start.plusYears(1).minusDays(1)
    }

    /** [today] 기준 (시작, 끝) 날짜. 끝은 포함, 오늘을 넘지 않는다. */
    fun range(today: LocalDate): Pair<LocalDate, LocalDate> {
        val s = startOf(today, offset)
        val e = endOf(s)
        return s to if (e.isAfter(today)) today else e
    }

    /** 비교할 직전 기간(통째로). */
    fun previous(today: LocalDate): Pair<LocalDate, LocalDate> = startOf(today, offset - 1).let { it to endOf(it) }

    /** 앞·뒤 기간. 미래로는 가지 않는다. */
    fun shift(by: Int): ReportPeriod = copy(offset = (offset + by).coerceAtMost(0))

    /** 이 기간을 포함해 앞쪽으로 [n]개(오래된 것 먼저). */
    fun trail(n: Int): List<ReportPeriod> = (n - 1 downTo 0).map { copy(offset = offset - it) }

    /** 버튼·제목: 이번 주, 지난달, 2026년 2분기 … */
    fun label(today: LocalDate): String {
        val s = startOf(today, offset)
        return when (unit) {
            PeriodUnit.WEEK -> when (offset) { 0 -> "이번 주"; -1 -> "지난주"; else -> "${s.monthValue}월 ${(s.dayOfMonth - 1) / 7 + 1}주" }
            PeriodUnit.MONTH -> when (offset) { 0 -> "이번 달"; -1 -> "지난달"; else -> if (s.year == today.year) "${s.monthValue}월" else "${s.year}년 ${s.monthValue}월" }
            PeriodUnit.QUARTER -> when (offset) { 0 -> "이번 분기"; -1 -> "지난 분기"; else -> "${s.year}년 ${(s.monthValue - 1) / 3 + 1}분기" }
            PeriodUnit.YEAR -> when (offset) { 0 -> "올해"; -1 -> "작년"; else -> "${s.year}년" }
        }
    }

    /** 그래프 축에 쓰는 짧은 이름: 11/3, 11월, 2분기, 2026. */
    fun short(today: LocalDate): String {
        val s = startOf(today, offset)
        return when (unit) {
            PeriodUnit.WEEK -> "${s.monthValue}/${s.dayOfMonth}"
            PeriodUnit.MONTH -> "${s.monthValue}월"
            PeriodUnit.QUARTER -> "${(s.monthValue - 1) / 3 + 1}분기"
            PeriodUnit.YEAR -> "${s.year}"
        }
    }

    /** 직전 기간 이름: 지난주, 지난달 … 지금이 아닌 기간이면 '그 전 주'. */
    val previousLabel: String
        get() = when (unit) {
            PeriodUnit.WEEK -> if (offset == 0) "지난주" else "그 전 주"
            PeriodUnit.MONTH -> if (offset == 0) "지난달" else "그 전 달"
            PeriodUnit.QUARTER -> if (offset == 0) "지난 분기" else "그 전 분기"
            PeriodUnit.YEAR -> if (offset == 0) "작년" else "그 전 해"
        }

    companion object {
        val THIS_WEEK = ReportPeriod(PeriodUnit.WEEK)
        val THIS_MONTH = ReportPeriod(PeriodUnit.MONTH)
        val LAST_MONTH = ReportPeriod(PeriodUnit.MONTH, -1)
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
    val harsh: Int? = null,
    /** 효율(전기차 km/kWh, 내연기관 km/L). */
    val efficiency: Float? = null,
    val ev: Boolean? = null,
    val companion: Companion? = null,
    /** '기타'에 직접 적은 목적. */
    val custom: String? = null,
)

/** 주유·충전 한 번(리포트용). */
data class RefuelPoint(val at: Long, val ev: Boolean, val fromPercent: Int, val toPercent: Int)

/** 효율 한 묶음(평균과 운행 수). */
data class EffRow(val label: String, val avg: Float, val count: Int)

data class PurposeRow(val purpose: TripPurpose?, val count: Int, val km: Double, val sec: Long, val timeShare: Int)

data class Totals(val km: Double, val sec: Long, val days: Int, val count: Int) {
    val hasData: Boolean get() = count > 0
}

data class ReportData(
    val period: ReportPeriod,
    /** 이번 주·지난달 같은 기간 이름. */
    val label: String,
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
    // ---- 세부 지표 ----
    /** 평균 속도(km/h, 이동시간 기준). */
    val avgSpeedKmh: Double?,
    val avgKm: Double,
    val avgMovingSec: Long,
    /** 경과시간 중 멈춰 있던 비율(%): 신호·정체·짧은 정차. */
    val stopRatio: Int?,
    /** 월~일 운행 횟수와 거리. */
    val weekdayCounts: List<Int>,
    val weekdayKm: List<Double>,
    /** 요일(월~일) × 시간대(아침·낮·저녁·밤) 운행 횟수. */
    val heat: List<List<Int>>,
    /** 거리 구간별 운행 수. */
    val distanceBins: List<Pair<String, Int>>,
    /** 기간 안 가장 긴 연속 운전일, 기간 끝날 기준 연속 운전일. */
    val longestStreak: Int,
    val currentStreak: Int,
    val topTrips: List<ReportTrip>,
    val cardsCreated: Int,
    val cardsCompleted: Int,
    val cardsDraft: Int,
    val cardsExpired: Int,
    val bestScore: Int?,
    val smoothTrips: Int,
    val harshTotal: Int?,
    val busiestDay: Pair<LocalDate, Double>?,
    // ---- 누구랑 ----
    val companions: List<Pair<Companion?, Int>> = emptyList(),
    /** '기타'에 직접 적은 목적별 횟수(많은 순). */
    val customPurposes: List<Pair<String, Int>> = emptyList(),
    // ---- 효율·주유 ----
    /** 전기차면 true(전비 km/kWh), 아니면 연비 km/L. 기록이 없으면 null. */
    val ev: Boolean? = null,
    val avgEfficiency: Float? = null,
    val prevEfficiency: Float? = null,
    val bestEfficiency: ReportTrip? = null,
    val worstEfficiency: ReportTrip? = null,
    val efficiencyByPurpose: List<EffRow> = emptyList(),
    val efficiencyByBucket: List<EffRow> = emptyList(),
    val efficiencyByDistance: List<EffRow> = emptyList(),
    val smoothVsHarsh: Pair<Float?, Float?> = null to null,
    val dailyEfficiency: List<Pair<LocalDate, Float?>> = emptyList(),
    val refuels: List<RefuelPoint> = emptyList(),
    val prevRefuels: Int = 0,
    /** 주유·충전 사이 평균 간격(일)과 평균 주행거리. */
    val refuelIntervalDays: Double? = null,
    val kmPerRefuel: Double? = null,
) {
    val efficiencyUnit: String get() = if (ev == false) "km/L" else "km/kWh"
    val efficiencyName: String get() = if (ev == false) "연비" else "전비"
    val refuelName: String get() = if (ev == false) "주유" else "충전"
}

/**
 * 리포트 집계(생성형 AI 미사용). 운행 숫자는 확정된 모든 운행에서, 목적별·성장 내역은 완성한 카드에서 나온다.
 * 만료된 카드의 운행도 숫자에는 그대로 남는다.
 */
object ReportAggregator {
    const val SHORT_KM = 10.0
    const val LONG_KM = 30.0
    const val MIN_TRIPS_FOR_FINDINGS = 3

    fun build(period: ReportPeriod, today: LocalDate, zone: ZoneId, trips: List<ReportTrip>, pendingDrafts: Int, refuels: List<RefuelPoint> = emptyList()): ReportData {
        val label = period.label(today)
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
        val findings = if (enough) findings(period, label, totals, previous, purposes, buckets, weekendTrips.size, weekendTrips.sumOf(kmOf), longest, completed, zone) else emptyList()
        val next = nextSteps(totals, unclassified.size, pendingDrafts)

        // ---- 세부 지표 ----
        val movingTotal = inPeriod.sumOf { it.movingSec ?: 0L }
        val elapsedTotal = inPeriod.sumOf { it.elapsedSec }
        val weekdayCounts = (1..7).map { d -> inPeriod.count { dateOf(it).dayOfWeek.value == d } }
        val weekdayKm = (1..7).map { d -> inPeriod.filter { dateOf(it).dayOfWeek.value == d }.sumOf(kmOf) }
        val heat = (1..7).map { d -> TimeBucket.entries.map { b -> inPeriod.count { dateOf(it).dayOfWeek.value == d && TimeBucket.of(hourOf(it)) == b } } }
        val bins = listOf("5km 미만" to (0.0 to 5.0), "5~10" to (5.0 to 10.0), "10~30" to (10.0 to 30.0), "30~50" to (30.0 to 50.0), "50~100" to (50.0 to 100.0), "100km 이상" to (100.0 to Double.MAX_VALUE))
            .map { (label, r) -> label to inPeriod.count { kmOf(it) >= r.first && kmOf(it) < r.second } }
        val days = inPeriod.map(::dateOf).toSortedSet()
        var longestStreak = 0
        var run = 0
        var prev: LocalDate? = null
        days.forEach { d -> run = if (prev != null && prev!!.plusDays(1) == d) run + 1 else 1; longestStreak = maxOf(longestStreak, run); prev = d }
        var current = 0
        var cursor = to
        while (cursor in days) { current++; cursor = cursor.minusDays(1) }
        val harshKnown = inPeriod.mapNotNull { it.harsh }

        return ReportData(
            period, label, from, to, totals, previous, findings, next, purposes, drafts, expired, below, buckets,
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
            avgSpeedKmh = if (movingTotal > 0) totals.km / (movingTotal / 3600.0) else null,
            avgKm = if (totals.count > 0) totals.km / totals.count else 0.0,
            avgMovingSec = if (totals.count > 0) movingTotal / totals.count else 0L,
            stopRatio = if (elapsedTotal > 0) (((elapsedTotal - movingTotal).coerceAtLeast(0) * 100.0) / elapsedTotal).roundToInt() else null,
            weekdayCounts = weekdayCounts,
            weekdayKm = weekdayKm,
            heat = heat,
            distanceBins = bins,
            longestStreak = longestStreak,
            currentStreak = current,
            topTrips = inPeriod.sortedByDescending(kmOf).take(5),
            cardsCreated = inPeriod.count { it.hasCard },
            cardsCompleted = inPeriod.count { it.cardState == CardState.COMPLETED },
            cardsDraft = inPeriod.count { it.cardState == CardState.DRAFT },
            cardsExpired = inPeriod.count { it.cardState == CardState.EXPIRED },
            bestScore = scores.maxOrNull(),
            smoothTrips = scores.count { it >= Rules.HEART_BRIGHT_SCORE },
            harshTotal = if (harshKnown.isEmpty()) null else harshKnown.sum(),
            busiestDay = dailyKm.filter { it.second > 0 }.maxByOrNull { it.second },
            customPurposes = inPeriod.mapNotNull { it.custom }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second },
            companions = inPeriod.filter { it.purpose != null }.groupBy { it.companion }.map { it.key to it.value.size }.sortedByDescending { it.second },
            ev = (inPeriod + inPrev).mapNotNull { it.ev }.let { l -> if (l.isEmpty()) refuels.lastOrNull()?.ev else l.count { it } >= l.size / 2.0 },
            avgEfficiency = weightedEff(inPeriod),
            prevEfficiency = weightedEff(inPrev),
            bestEfficiency = inPeriod.filter { it.efficiency != null && (it.distanceKm ?: 0f) >= 3f }.maxByOrNull { it.efficiency!! },
            worstEfficiency = inPeriod.filter { it.efficiency != null && (it.distanceKm ?: 0f) >= 3f }.minByOrNull { it.efficiency!! },
            efficiencyByPurpose = inPeriod.filter { it.efficiency != null }.groupBy { it.purpose?.label ?: "미분류" }.map { (k, l) -> EffRow(k, weightedEff(l)!!, l.size) }.sortedByDescending { it.avg },
            efficiencyByBucket = TimeBucket.entries.mapNotNull { b -> inPeriod.filter { it.efficiency != null && TimeBucket.of(hourOf(it)) == b }.takeIf { it.isNotEmpty() }?.let { EffRow(b.label, weightedEff(it)!!, it.size) } },
            efficiencyByDistance = listOf("10km 미만" to (0.0 to SHORT_KM), "10~30km" to (SHORT_KM to LONG_KM), "30km 이상" to (LONG_KM to Double.MAX_VALUE)).mapNotNull { (label, r) ->
                inPeriod.filter { it.efficiency != null && kmOf(it) >= r.first && kmOf(it) < r.second }.takeIf { it.isNotEmpty() }?.let { EffRow(label, weightedEff(it)!!, it.size) }
            },
            smoothVsHarsh = weightedEff(inPeriod.filter { (it.score ?: 0) >= Rules.HEART_BRIGHT_SCORE }) to weightedEff(inPeriod.filter { it.score != null && it.score < Rules.HEART_BRIGHT_SCORE }),
            dailyEfficiency = dailyKm.map { (d, _) -> d to weightedEff(inPeriod.filter { dateOf(it) == d }) },
            refuels = refuels.filter { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() in from..to }.sortedBy { it.at },
            prevRefuels = refuels.count { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() in pFrom..pTo },
            refuelIntervalDays = refuels.sortedBy { it.at }.zipWithNext { a, b -> (b.at - a.at) / 86_400_000.0 }.takeIf { it.isNotEmpty() }?.average(),
            kmPerRefuel = refuels.filter { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() in from..to }.size.takeIf { it > 0 }?.let { totals.km / it },
        )
    }

    /** 거리 가중 평균 효율(총거리 ÷ 운행별 사용량 합). 효율이 있는 운행이 없으면 null. */
    fun weightedEff(list: List<ReportTrip>): Float? {
        val l = list.filter { it.efficiency != null && it.efficiency > 0f && (it.distanceKm ?: 0f) > 0f }
        if (l.isEmpty()) return null
        val km = l.sumOf { it.distanceKm!!.toDouble() }
        val used = l.sumOf { it.distanceKm!!.toDouble() / it.efficiency!! }
        return (km / used).toFloat()
    }

    /** 핵심 발견: 위에서부터 조건을 검사해 맞는 문장을 최대 3개. */
    private fun findings(
        period: ReportPeriod,
        label: String,
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
        val p = label
        // 1. 완성 카드가 3장 이상이면 가장 큰 목적의 시간 비중
        val top = purposes.firstOrNull { it.purpose != null }
        if (completed >= 3 && top?.purpose != null) out += "$p 운전시간의 ${top.timeShare}%는 ${top.purpose.label.ieosseoyo()}."
        // 2. 직전 기간 대비 운전시간이 10% 이상 달라졌으면
        if (previous.sec > 0) {
            val diff = totals.sec - previous.sec
            if (abs(diff) >= previous.sec * 0.1) out += "${period.previousLabel}보다 운전시간이 ${duration(abs(diff))} ${if (diff > 0) "늘었어요" else "줄었어요"}."
        }
        // 3. 한 시간대가 전체 횟수의 40% 이상이면
        val topBucket = buckets.maxByOrNull { it.value }
        if (topBucket != null && topBucket.value >= totals.count * 0.4) out += "가장 자주 출발한 시간은 ${topBucket.key.label.ieosseoyo()}."
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
