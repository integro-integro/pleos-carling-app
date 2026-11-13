package work.integro.carling.data

import work.integro.carling.game.Badge
import work.integro.carling.game.CarPart
import work.integro.carling.game.CarSlot
import work.integro.carling.game.CarStyle
import work.integro.carling.game.CardState
import work.integro.carling.game.PersonalityResult
import work.integro.carling.game.ReportPeriod
import work.integro.carling.game.Stage
import work.integro.carling.game.TimeBucket
import work.integro.carling.game.TripPurpose
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.roundToInt

/** 개성 분석에 들어간 카드 한 장(최근 10장 표시용). */
data class WindowCard(val tripId: Long, val date: LocalDate, val bucket: TimeBucket, val purpose: TripPurpose?, val score: Int?)

/** 카링 리포트: 성장·개성·수집·럭키박스를 기간별로 모은다. */
data class KaringReportData(
    val period: ReportPeriod,
    val label: String,
    val from: LocalDate,
    val to: LocalDate,
    // ---- 성장 ----
    val cardGp: Int,
    val bonusGp: Int,
    val rewards: Int,
    val dailyGp: List<Pair<LocalDate, Int>>,
    /** 전체 기간 누적 포인트(보상 지급일 기준, 럭키박스 보너스 포함). */
    val cumulative: List<Pair<LocalDate, Int>>,
    val stageChanges: List<StageChange>,
    val stage: Stage,
    val totalGp: Int,
    /** 최근 14일 하루 평균 획득 포인트. */
    val recentDailyGp: Double,
    /** 다음 단계까지 예상 일수(최근 속도 기준). 마지막 단계거나 속도가 0이면 null. */
    val etaDays: Int?,
    // ---- 개성 ----
    val personality: PersonalityResult,
    val window: List<WindowCard>,
    val snapshots: List<PersonalitySnapshot>,
    val purposeShare: List<Pair<TripPurpose, Int>>,
    // ---- 수집·럭키박스 ----
    val badges: Map<Badge, Int>,
    val parts: Map<CarPart, Int>,
    val equipped: Map<CarSlot, CarPart>,
    val styles: Set<CarStyle>,
    val colors: Map<work.integro.carling.game.CarColor, Int>,
    val boxesEarned: Int,
    val boxesOpened: Int,
    val boxesWaiting: Int,
    val avgBonus: Int?,
    val badgeRate: Int?,
    val carRate: Int?,
    val duplicates: Int,
    val bestBox: LuckyBox?,
)

object KaringReportBuilder {
    fun build(period: ReportPeriod, today: LocalDate, zone: ZoneId, data: CarlingData, personality: PersonalityResult): KaringReportData {
        val (from, to) = period.range(today)
        fun day(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val rewardsIn = data.rewards.filterValues { day(it.grantedAt) in from..to }
        val opened = data.boxes.filter { it.openedAt != null }
        val openedIn = opened.filter { day(it.openedAt!!) in from..to }

        // 날짜별 포인트: 카드 보상(지급일) + 럭키박스 보너스(연 날)
        val events = data.rewards.values.map { day(it.grantedAt) to it.gp } + opened.map { day(it.openedAt!!) to it.bonusGp }
        val byDay = events.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        val dailyGp = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { it to (byDay[it] ?: 0) }.toList()
        var acc = 0
        val cumulative = byDay.keys.sorted().map { d -> acc += byDay.getValue(d); d to acc }

        val recentFrom = today.minusDays(13)
        val recent = events.filter { it.first in recentFrom..today }.sumOf { it.second } / 14.0
        val next = data.stage.next
        val eta = if (next != null && next.minGp > 0 && recent > 0) ceil((next.minGp - data.totalGp).coerceAtLeast(0) / recent).toInt() else null

        val window = personality.used.mapNotNull { id ->
            data.trip(id)?.let { t ->
                WindowCard(id, day(t.startedAt), TimeBucket.of(Instant.ofEpochMilli(t.startedAt).atZone(zone).hour), data.card(id)?.purpose, t.score)
            }
        }
        val purposeShare = data.rewards.keys.mapNotNull { data.card(it)?.takeIf { c -> c.state == CardState.COMPLETED }?.purpose }
            .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }

        val withBadge = opened.count { it.badge != null }
        val withCar = opened.count { it.part != null || it.color != null }
        return KaringReportData(
            period = period,
            label = period.label(today),
            from = from,
            to = to,
            cardGp = rewardsIn.values.sumOf { it.gp },
            bonusGp = openedIn.sumOf { it.bonusGp },
            rewards = rewardsIn.size,
            dailyGp = dailyGp,
            cumulative = cumulative,
            stageChanges = data.stageHistory,
            stage = data.stage,
            totalGp = data.totalGp,
            recentDailyGp = recent,
            etaDays = eta,
            personality = personality,
            window = window,
            snapshots = data.snapshots.asReversed(),
            purposeShare = purposeShare,
            badges = data.badges,
            parts = data.parts,
            equipped = data.equipped,
            styles = data.styles,
            colors = data.colors,
            boxesEarned = data.boxes.count { day(it.createdAt) in from..to },
            boxesOpened = openedIn.size,
            boxesWaiting = data.unopenedBoxes.size,
            avgBonus = if (opened.isEmpty()) null else (opened.sumOf { it.bonusGp }.toDouble() / opened.size).roundToInt(),
            badgeRate = if (opened.isEmpty()) null else (withBadge * 100.0 / opened.size).roundToInt(),
            carRate = if (opened.isEmpty()) null else (withCar * 100.0 / opened.size).roundToInt(),
            duplicates = data.badges.values.sumOf { (it - 1).coerceAtLeast(0) } + data.parts.values.sumOf { (it - 1).coerceAtLeast(0) },
            bestBox = opened.maxByOrNull { it.bonusGp + (if (it.badge != null || it.part != null) 100 else 0) },
        )
    }
}
