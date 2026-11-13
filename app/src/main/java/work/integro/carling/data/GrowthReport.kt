package work.integro.carling.data

import work.integro.carling.game.Badge
import work.integro.carling.game.CardState
import work.integro.carling.game.Companion
import work.integro.carling.game.Personality
import work.integro.carling.game.ReachRules
import work.integro.carling.game.ReachTrip
import work.integro.carling.game.ReportPeriod
import work.integro.carling.game.Stage
import work.integro.carling.game.Talk
import work.integro.carling.game.TimeBucket
import work.integro.carling.game.TripCardRules
import work.integro.carling.game.TripPurpose
import work.integro.carling.game.TripStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/*
 * 성장 리포트 비교(사용자 요청 11/12): 지난 기간 끝과 이번 기간 끝의 카링을 나란히 놓고
 * ① 어떻게 바뀌었나 ② 얼마나 똑똑해졌나(목적 맞히기 정확도·알게 된 습관) ③ 어떤 기능이 새로 생겼나를 보여 준다.
 * 모두 저장된 기록을 그 시점까지만 잘라 다시 계산한다.
 */

/** 어느 시점의 카링. */
data class KaringAt(
    val stage: Stage,
    val gp: Int,
    val personality: Personality,
    val parts: Int,
    val colors: Int,
    val badges: Int,
    val cards: Int,
    val talks: Int,
)

/** 카링이 알게 된 습관 한 가지. */
data class Habit(val key: String, val icon: String, val title: String, val detail: String)

/** 목적 맞히기: 추천이 있었던 카드 중 그대로 고른 비율. */
data class Accuracy(val hits: Int, val total: Int) {
    val percent: Int? get() = if (total == 0) null else (hits * 100.0 / total).roundToInt()
}

/** 기능 하나: 열렸는지와 여는 방법. */
data class Ability(val key: String, val icon: String, val title: String, val how: String, val unlocked: Boolean)

data class GrowthCompare(
    val nowLabel: String,
    val prevLabel: String,
    val now: KaringAt,
    val before: KaringAt,
    val accuracy: Accuracy,
    val prevAccuracy: Accuracy,
    val habits: List<Habit>,
    val newHabits: Set<String>,
    val abilities: List<Ability>,
    val newAbilities: Set<String>,
) {
    /** 0~100 하나로: 정확도 절반 + 습관(10개 만점) 절반. 비교용. */
    fun smartScore(acc: Accuracy, habitCount: Int): Int = (((acc.percent ?: 0) * 0.5) + (habitCount.coerceAtMost(10) * 5)).roundToInt()
}

object GrowthReportBuilder {

    fun build(period: ReportPeriod, today: LocalDate, zone: ZoneId, d: CarlingData): GrowthCompare {
        val (from, to) = period.range(today)
        val (pFrom, pTo) = period.previous(today)
        val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val prevEnd = pTo.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val habitsNow = habits(d, end, zone)
        val habitsBefore = habits(d, prevEnd, zone).map { it.key }.toSet()
        val abilitiesNow = abilities(d, end, zone)
        val abilitiesBefore = abilities(d, prevEnd, zone).filter { it.unlocked }.map { it.key }.toSet()
        fun day(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
        return GrowthCompare(
            nowLabel = period.label(today),
            prevLabel = period.previousLabel,
            now = at(d, end),
            before = at(d, prevEnd),
            accuracy = accuracy(d, zone) { day(it) in from..to },
            prevAccuracy = accuracy(d, zone) { day(it) in pFrom..pTo },
            habits = habitsNow,
            newHabits = habitsNow.map { it.key }.toSet() - habitsBefore,
            abilities = abilitiesNow,
            newAbilities = abilitiesNow.filter { it.unlocked }.map { it.key }.toSet() - abilitiesBefore,
        )
    }

    fun stageAt(d: CarlingData, t: Long): Stage =
        d.stageHistory.filter { it.at <= t }.maxByOrNull { it.at }?.to
            ?: d.stageHistory.minByOrNull { it.at }?.from ?: if (d.stageHistory.isEmpty()) d.stage else Stage.EGG

    fun at(d: CarlingData, t: Long): KaringAt {
        val opened = d.boxes.filter { (it.openedAt ?: Long.MAX_VALUE) <= t }
        val gp = d.rewards.values.filter { it.grantedAt <= t }.sumOf { it.gp } + opened.sumOf { it.bonusGp }
        return KaringAt(
            stage = stageAt(d, t),
            gp = gp,
            personality = d.snapshots.filter { it.createdAt <= t }.maxByOrNull { it.createdAt }?.after ?: Personality(),
            parts = opened.mapNotNull { it.part }.distinct().size,
            colors = opened.mapNotNull { it.color }.distinct().size + 1,
            badges = opened.mapNotNull { it.badge }.distinct().size,
            cards = d.cards.count { it.state == CardState.COMPLETED && (it.completedAt ?: Long.MAX_VALUE) <= t },
            talks = Talk.known(stageAt(d, t)).size,
        )
    }

    /**
     * 목적 맞히기 정확도: [inRange] 기간에 완성한 카드마다, 그보다 먼저 완성한 카드만으로 추천을 다시 계산해
     * 추천이 있었던 카드 중 고른 목적과 같았던 비율.
     */
    fun accuracy(d: CarlingData, zone: ZoneId, inRange: (Long) -> Boolean): Accuracy {
        fun hour(t: Long) = Instant.ofEpochMilli(t).atZone(zone).hour
        fun weekend(t: Long) = Instant.ofEpochMilli(t).atZone(zone).dayOfWeek.value >= 6
        val done = d.cards.filter { it.state == CardState.COMPLETED && it.purpose != null && it.completedAt != null }
            .mapNotNull { c -> d.trip(c.tripId)?.let { c to it } }
            .sortedBy { it.first.completedAt }
        var hits = 0
        var total = 0
        done.forEachIndexed { i, (c, t) ->
            if (!inRange(t.startedAt)) return@forEachIndexed
            val history = done.take(i).map { (hc, ht) -> Triple(hour(ht.startedAt), weekend(ht.startedAt), hc.purpose!!) }
            val s = TripCardRules.suggestPurpose(hour(t.startedAt), weekend(t.startedAt), history) ?: return@forEachIndexed
            total++
            if (s == c.purpose) hits++
        }
        return Accuracy(hits, total)
    }

    /** [t]까지의 기록으로 카링이 알게 된 습관. */
    fun habits(d: CarlingData, t: Long, zone: ZoneId): List<Habit> {
        val trips = d.trips.filter { it.status == TripStatus.CONFIRMED && it.startedAt <= t }
        val cards = d.cards.filter { it.state == CardState.COMPLETED && (it.completedAt ?: Long.MAX_VALUE) <= t }
        fun zt(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone)
        val out = mutableListOf<Habit>()
        fun timeOf(p: TripPurpose): String? {
            val ts = cards.filter { it.purpose == p }.mapNotNull { d.trip(it.tripId) }.map { zt(it.startedAt).let { z -> z.hour * 60 + z.minute } }.sorted()
            if (ts.size < 3) return null
            val m = ts[ts.size / 2]
            return "%d:%02d쯤".format(m / 60, m % 60 / 10 * 10)
        }
        timeOf(TripPurpose.TO_WORK)?.let { out += Habit("to_work", "🏢", "출근 시간", "보통 $it 출발해요") }
        timeOf(TripPurpose.FROM_WORK)?.let { out += Habit("from_work", "🏠", "퇴근 시간", "보통 $it 출발해요") }
        if (trips.size >= 5) {
            val byDay = trips.groupingBy { zt(it.startedAt).dayOfWeek.value }.eachCount()
            val (dow, n) = byDay.maxBy { it.value }
            if (n * 4 >= trips.size) out += Habit("weekday", "📅", "자주 달리는 요일", "${listOf("월", "화", "수", "목", "금", "토", "일")[dow - 1]}요일에 가장 많이 달려요")
            val bucket = trips.groupingBy { TimeBucket.of(zt(it.startedAt).hour) }.eachCount().maxBy { it.value }.key
            out += Habit("bucket", "🕒", "자주 출발하는 때", "${bucket.label}에 가장 많이 출발해요")
        }
        cards.mapNotNull { it.purpose }.filter { it != TripPurpose.OTHER }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.takeIf { it.value >= 3 }?.let {
            out += Habit("purpose", it.key.icon, "단골 목적", "${it.key.label} 카드를 가장 많이 만들어요(${it.value}장)")
        }
        cards.mapNotNull { it.companion }.filter { it != Companion.ALONE }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.takeIf { it.value >= 3 }?.let {
            out += Habit("companion", it.key.icon, "자주 함께하는 사람", "${it.key.label} 자주 달려요")
        }
        cards.mapNotNull { it.customPurpose }.distinct().takeIf { it.isNotEmpty() }?.let {
            out += Habit("custom", "✨", "내가 적은 목적", it.take(3).joinToString(", ") + if (it.size > 3) " 외 ${it.size - 3}개" else "")
        }
        val reach = trips.map { ReachTrip(it.startedAt, it.distanceKm, it.startDong, it.endDong) }
        ReachRules.homeOf(reach)?.let { out += Habit("home", "📍", "집 동네", "자주 출발하는 동네를 알아요") }
        val refuels = d.refuels.filter { it.at <= t }.sortedBy { it.at }
        if (refuels.size >= 2) {
            val days = refuels.zipWithNext { a, b -> (b.at - a.at) / 86_400_000.0 }.average()
            out += Habit("refuel", if (refuels.last().ev) "⚡" else "⛽", if (refuels.last().ev) "충전 주기" else "주유 주기", "약 ${days.roundToInt().coerceAtLeast(1)}일마다 ${if (refuels.last().ev) "충전" else "주유"}해요")
        }
        val scored = trips.mapNotNull { it.score }
        if (scored.size >= 5 && scored.average() >= work.integro.carling.game.Rules.HEART_BRIGHT_SCORE) out += Habit("smooth", "💚", "운전 스타일", "부드럽게 달리는 편이에요(평균 ${scored.average().roundToInt()}점)")
        return out
    }

    /** [t] 시점에 열려 있던 기능. */
    fun abilities(d: CarlingData, t: Long, zone: ZoneId): List<Ability> {
        val stage = stageAt(d, t)
        val rewarded = d.rewards.values.any { it.grantedAt <= t }
        val suggested = accuracy(d, zone) { it <= t }.total > 0
        val opened = d.boxes.filter { (it.openedAt ?: Long.MAX_VALUE) <= t }
        val allBadges = opened.mapNotNull { it.badge }.toSet().containsAll(Badge.entries)
        val reach = d.trips.filter { it.startedAt <= t }.map { ReachTrip(it.startedAt, it.distanceKm, it.startDong, it.endDong) }
        return buildList {
            add(Ability("box", "🎁", "럭키박스", "첫 카드 보상을 받으면", rewarded))
            add(Ability("suggest", "✨", "목적 추천", "비슷한 시간에 같은 목적을 3번 넘게 고르면", suggested))
            add(Ability("turn", "🔄", "360° 보기 · 차 꾸미기", "아기링이 되면", stage.ordinal >= Stage.BABY.ordinal))
            add(Ability("look", "🪞", "모습 바꾸기", "꼬마링이 되면", stage.ordinal >= Stage.BREEZE.ordinal))
            Talk.entries.forEach { talk -> add(Ability("talk_${talk.name}", "🗣️", "주행 한마디 '${talk.label}'", "${talk.minStage.label}이 되면", stage.ordinal >= talk.minStage.ordinal)) }
            add(Ability("map", "🗺️", "동네 지도", "위치를 켜고 같은 동네에서 두 번 출발하면", ReachRules.homeOf(reach) != null))
            add(Ability("aurora", "🌈", "오로라 차체 색", "배지 6종을 모두 모으면", allBadges))
        }
    }
}
