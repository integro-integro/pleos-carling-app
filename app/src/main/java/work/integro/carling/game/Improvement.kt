package work.integro.carling.game

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/*
 * 내 운전이 얼마나 나아졌나(사용자 요청 11/12): 같은 단위(주·월·분기·년) 기간을 여러 개 나란히 놓고
 * 운전점수·급조작·효율·부드러운 운행 비율이 어떻게 변했는지 본다. 생성형 AI 없이 규칙으로 판정한다.
 */

/** 기간 하나의 운전 지표. 운행이 없으면 숫자는 null. */
data class PeriodStat(
    val period: ReportPeriod,
    val short: String,
    val trips: Int,
    val km: Double,
    /** 운행 거리 가중 평균 운전점수. */
    val score: Int?,
    /** 100km당 급가속·급감속 횟수. */
    val harshPer100: Double?,
    /** 거리 가중 효율(km/kWh 또는 km/L). */
    val efficiency: Float?,
    /** 운전점수 85+ 운행 비율(%). */
    val smoothShare: Int?,
)

/** 지표 하나의 판정: 직전 기간과 비교해 좋아졌는지. */
data class Verdict(val metric: Metric, val now: Double, val before: Double, val better: Boolean, val changePct: Int)

enum class Metric(val label: String, val higherIsBetter: Boolean) {
    SCORE("운전점수", true),
    HARSH("급조작(100km당)", false),
    EFFICIENCY("효율", true),
    SMOOTH("부드러운 운행", true),
}

/** 연비로 아낀(+)·더 쓴(-) 금액과 에너지. */
data class Savings(val energyNow: Double, val energyAtPrev: Double, val won: Int) {
    val saved: Boolean get() = won >= 0
}

object ImprovementRules {
    /** 판정에 쓰는 최소 변화(%). 이보다 작으면 '비슷해요'. */
    const val MIN_CHANGE_PCT = 3

    fun stats(period: ReportPeriod, today: LocalDate, zone: ZoneId, trips: List<ReportTrip>, count: Int = 6): List<PeriodStat> =
        period.trail(count).map { p ->
            val (from, to) = p.range(today)
            val list = trips.filter { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() in from..to }
            stat(p, p.short(today), list)
        }

    fun stat(p: ReportPeriod, short: String, list: List<ReportTrip>): PeriodStat {
        val km = list.sumOf { (it.distanceKm ?: 0f).toDouble() }
        val scored = list.filter { it.score != null && (it.distanceKm ?: 0f) > 0f }
        val score = if (scored.isEmpty()) null else (scored.sumOf { it.score!! * it.distanceKm!!.toDouble() } / scored.sumOf { it.distanceKm!!.toDouble() }).roundToInt()
        val harshKnown = list.filter { it.harsh != null }
        val harshKm = harshKnown.sumOf { (it.distanceKm ?: 0f).toDouble() }
        return PeriodStat(
            period = p,
            short = short,
            trips = list.size,
            km = km,
            score = score,
            harshPer100 = if (harshKm >= 1.0) harshKnown.sumOf { it.harsh!! } * 100.0 / harshKm else null,
            efficiency = ReportAggregator.weightedEff(list),
            smoothShare = if (list.none { it.score != null }) null else (list.count { (it.score ?: 0) >= Rules.HEART_BRIGHT_SCORE } * 100.0 / list.count { it.score != null }).roundToInt(),
        )
    }

    /** 마지막 기간과 그 앞 기간을 지표별로 비교. 둘 다 값이 있는 지표만. */
    fun verdicts(stats: List<PeriodStat>): List<Verdict> {
        val now = stats.lastOrNull() ?: return emptyList()
        val before = stats.dropLast(1).lastOrNull { it.trips > 0 } ?: return emptyList()
        fun v(m: Metric, a: Double?, b: Double?): Verdict? {
            if (a == null || b == null) return null
            val pct = if (b == 0.0) (if (a == 0.0) 0 else 100) else ((a - b) / b * 100).roundToInt()
            val better = if (m.higherIsBetter) a > b else a < b
            return Verdict(m, a, b, better, pct)
        }
        return listOfNotNull(
            v(Metric.SCORE, now.score?.toDouble(), before.score?.toDouble()),
            v(Metric.HARSH, now.harshPer100, before.harshPer100),
            v(Metric.EFFICIENCY, now.efficiency?.toDouble(), before.efficiency?.toDouble()),
            v(Metric.SMOOTH, now.smoothShare?.toDouble(), before.smoothShare?.toDouble()),
        )
    }

    /** 한 줄 요약: 좋아진 지표 수로. */
    fun headline(verdicts: List<Verdict>): String {
        val meaningful = verdicts.filter { kotlin.math.abs(it.changePct) >= MIN_CHANGE_PCT }
        val up = meaningful.count { it.better }
        val down = meaningful.size - up
        return when {
            verdicts.isEmpty() -> "비교할 앞 기간 기록이 아직 없어요"
            meaningful.isEmpty() -> "앞 기간과 거의 같게 달렸어요"
            down == 0 && up < verdicts.size -> "${up}가지 좋아지고 나머지는 비슷해요"
            down == 0 -> "${up}가지 모두 좋아졌어요"
            up == 0 -> "${down}가지가 조금 아쉬워졌어요"
            else -> "${up}가지 좋아지고 ${down}가지는 아쉬워요"
        }
    }

    /**
     * 같은 거리를 직전 기간 효율로 달렸을 때와 비교한 에너지·금액.
     * [price]는 kWh당(전기차) 또는 L당(내연기관) 원.
     */
    fun savings(km: Double, eff: Float?, prevEff: Float?, price: Int): Savings? {
        if (eff == null || prevEff == null || eff <= 0f || prevEff <= 0f || km <= 0.0) return null
        val now = km / eff
        val atPrev = km / prevEff
        return Savings(now, atPrev, ((atPrev - now) * price).roundToInt())
    }

    /** 기본 단가: 전기 공공 급속 kWh당, 휘발유 L당(원). 리포트에서 바꿀 수 있다. */
    const val DEFAULT_KWH_PRICE = 324
    const val DEFAULT_FUEL_PRICE = 1650
}
