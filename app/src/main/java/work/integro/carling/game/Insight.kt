package work.integro.carling.game

import java.time.Instant
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * "이번 달 꼭 알아야 할 3가지"(사용자 확정 11/13): 리포트 맨 위에서 와, 도움된다를 만드는 카드.
 * 카드마다 ① 내 숫자로 ② 몰랐던 사실 ③ 돈이나 시간으로 환산 ④ 할 일 하나를 담는다.
 * 데이터가 모자라면 "N번 더 달리면 알려 줄게요"로 다가오는 인사이트를 보여 준다. 생성형 AI 없이 규칙으로.
 */

@Serializable
enum class InsightKind(val icon: String) {
    DEPART("⏰"), HABIT_MONEY("💚"), COST("💰"), SAFETY("🌙"), LIFE("🗺️"), UPCOMING("🔜")
}

/** 카드 한 장. [big]은 크게 보여 줄 숫자 한 줄, [action]은 할 일 하나, [basis]는 카링이 본 근거. */
@Serializable
data class Insight(
    val kind: InsightKind,
    val title: String,
    val big: String,
    val detail: String,
    val action: String,
    val basis: String,
    val priority: Int,
)

/** 인사이트 입력: 확정 운행 한 번. */
data class InsightTrip(
    val startedAt: Long,
    val km: Float?,
    val movingSec: Long?,
    val purpose: TripPurpose?,
    val startDong: String?,
    val endDong: String?,
    val score: Int?,
    val harsh: Int?,
    val efficiency: Float?,
)

object InsightRules {
    /** 출발 시각을 묶는 칸(분). */
    const val SLOT_MIN = 20
    /** 칸마다 최소 운행 수. */
    const val SLOT_TRIPS = 2
    /** 이만큼 넘게 빨라야 알려 준다(분). */
    const val MIN_GAIN_MIN = 5
    /** 출발 시각·습관은 이 기간을 본다(일). */
    const val LOOKBACK_DAYS = 60L

    fun build(
        period: ReportPeriod,
        today: LocalDate,
        zone: ZoneId,
        all: List<InsightTrip>,
        ev: Boolean,
        price: Int,
        name: (String) -> String?,
        reach: ReachData?,
    ): List<Insight> {
        val (from, to) = period.range(today)
        val (pFrom, pTo) = period.previous(today)
        fun day(t: InsightTrip) = Instant.ofEpochMilli(t.startedAt).atZone(zone).toLocalDate()
        val now = all.filter { day(it) in from..to }
        val before = all.filter { day(it) in pFrom..pTo }
        val recent = all.filter { day(it) in to.minusDays(LOOKBACK_DAYS)..to }
        val found = listOfNotNull(
            depart(recent, zone, name),
            habitMoney(recent, now, from, to, ev, price),
            cost(now, ev, price),
            safety(now, before, zone),
            life(reach),
        ).sortedByDescending { it.priority }.take(3)
        return found + upcoming(recent, zone).take(3 - found.size)
    }

    // ---- ① 출발 시각 ----

    /**
     * 같은 길(출발·도착 동네, 없으면 같은 목적)을 평일에 여러 번 달렸으면, 출발 시각 20분 칸별 평균 이동시간을 비교한다.
     * 가장 자주 나가는 칸보다 5분 넘게 빠른 칸이 있으면: 한 번에 아끼는 시간 × 한 달 그 길 횟수.
     */
    fun depart(recent: List<InsightTrip>, zone: ZoneId, name: (String) -> String?): Insight? {
        fun z(t: InsightTrip) = Instant.ofEpochMilli(t.startedAt).atZone(zone)
        val weekday = recent.filter { z(it).dayOfWeek.value <= 5 && it.movingSec != null && it.movingSec > 0 }
        // 같은 길: 출발·도착 동네가 같거나, 동네가 없으면 출근·퇴근·업무처럼 늘 같은 곳을 오가는 목적만
        val routine = setOf(TripPurpose.TO_WORK, TripPurpose.FROM_WORK, TripPurpose.WORK)
        val groups = weekday.groupBy { t ->
            if (t.startDong != null && t.endDong != null && t.startDong != t.endDong) "route:${t.startDong}>${t.endDong}"
            else t.purpose?.takeIf { it in routine }?.let { "purpose:${it.name}" }
        }.filterKeys { it != null }
        var best: Insight? = null
        groups.forEach { (key, list) ->
            val all = list.groupBy { (z(it).hour * 60 + z(it).minute) / SLOT_MIN }.filter { it.value.size >= SLOT_TRIPS }
            if (all.size < 2) return@forEach
            val usual = all.maxBy { it.value.size }
            // 평소 출발 ±1시간 안에서만 비교한다(아침 출근과 저녁 일정을 섞지 않게)
            val slots = all.filterKeys { kotlin.math.abs(it - usual.key) * SLOT_MIN <= 60 }
            if (slots.size < 2) return@forEach
            val avg = slots.mapValues { (_, l) -> l.mapNotNull { it.movingSec }.average() / 60.0 }
            val fastest = avg.minBy { it.value }
            val gain = avg.getValue(usual.key) - fastest.value
            if (fastest.key == usual.key || gain < MIN_GAIN_MIN) return@forEach
            val days = (recent.maxOf { it.startedAt } - recent.minOf { it.startedAt }) / 86_400_000.0 + 1
            val perMonth = list.size / days.coerceAtLeast(7.0) * 30
            val monthMin = (gain * perMonth).roundToInt()
            val label = labelOf(key!!, list.first(), name)
            // 칸 이름은 그 칸 실제 출발 시각의 가운데 값(5분 단위)
            fun at(slot: Int): String {
                val ms = slots.getValue(slot).map { z(it).hour * 60 + z(it).minute }.sorted()
                val m = ms[ms.size / 2] / 5 * 5
                return "%d:%02d".format(m / 60, m % 60)
            }
            val ins = Insight(
                InsightKind.DEPART,
                "$label, ${at(fastest.key)}에 나가면 ${gain.roundToInt()}분 빨라요",
                "한 달에 ${duration(monthMin)} 아껴요",
                "${at(usual.key)} 출발은 평균 ${avg.getValue(usual.key).roundToInt()}분, ${at(fastest.key)} 출발은 ${fastest.value.roundToInt()}분 걸렸어요.",
                "평소보다 ${at(fastest.key)}쯤 출발해 보세요",
                "평일 $label ${list.size}번을 비교했어요",
                100 + monthMin,
            )
            if (best == null || ins.priority > best!!.priority) best = ins
        }
        return best
    }

    private fun labelOf(key: String, t: InsightTrip, name: (String) -> String?): String =
        if (key.startsWith("route:")) {
            val a = t.startDong?.let(name)
            val b = t.endDong?.let(name)
            // 동네 이름을 모르면(위치 꺼짐·지도 준비 전) 목적만
            val route = if (a != null && b != null) "$a → $b" else null
            when {
                t.purpose != null && route != null -> "${t.purpose.label}($route)"
                t.purpose != null -> t.purpose.label
                route != null -> route
                else -> "같은 길"
            }
        } else t.purpose!!.label

    // ---- ② 습관이 돈이 되는 순간 ----

    /** 부드럽게 달린 운행(85점+)과 아닌 운행의 효율 차이를 1년 주행으로 환산. 모두 부드러우면 칭찬. */
    fun habitMoney(recent: List<InsightTrip>, now: List<InsightTrip>, from: LocalDate, to: LocalDate, ev: Boolean, price: Int): Insight? {
        fun eff(l: List<InsightTrip>): Float? {
            val x = l.filter { it.efficiency != null && it.efficiency > 0f && (it.km ?: 0f) > 0f }
            if (x.size < 2) return null
            return (x.sumOf { it.km!!.toDouble() } / x.sumOf { it.km!!.toDouble() / it.efficiency!! }).toFloat()
        }
        val smooth = recent.filter { (it.score ?: 0) >= Rules.HEART_BRIGHT_SCORE }
        val rough = recent.filter { it.score != null && it.score < Rules.HEART_BRIGHT_SCORE }
        val es = eff(smooth) ?: return null
        val unit = if (ev) "kWh" else "L"
        val name = if (ev) "전비" else "연비"
        val periodDays = (to.toEpochDay() - from.toEpochDay() + 1).coerceAtLeast(1)
        val yearKm = now.sumOf { (it.km ?: 0f).toDouble() } / periodDays * 365
        val er = eff(rough)
        if (er == null || rough.size < 2) {
            if (smooth.size < 5) return null
            return Insight(InsightKind.HABIT_MONEY, "최근 ${smooth.size}번 모두 부드럽게 달렸어요", "$name %.1f km/$unit".format(es),
                "급조작이 적어 에너지를 아끼고 있어요. 카링의 심장도 반짝이고 있어요.", "지금처럼만 달려요", "최근 ${LOOKBACK_DAYS}일 운행 ${recent.size}번", 50)
        }
        val better = (es - er) / er
        if (better < 0.05f || yearKm <= 0) return null
        val saveWon = ((yearKm / er - yearKm / es) * price).roundToInt()
        return Insight(
            InsightKind.HABIT_MONEY,
            "부드럽게 달린 날 ${name}가 ${(better * 100).roundToInt()}% 좋았어요",
            "1년에 ${won(saveWon)} 아낄 수 있어요",
            "운전점수 ${Rules.HEART_BRIGHT_SCORE}점 이상 ${smooth.size}번은 %.1f, 그 밖 ${rough.size}번은 %.1f km/$unit. 지금 주행량으로 1년이면 이만큼 차이 나요.".format(es, er),
            "급가속·급감속만 줄여도 돼요",
            "최근 ${LOOKBACK_DAYS}일 운행 ${recent.size}번",
            80 + saveWon / 10_000,
        )
    }

    // ---- ③ 목적별 돈 ----

    /** 이 기간 에너지 비용과 가장 큰 목적. 짧은 장보기·식사가 잦으면 묶기를 제안한다. */
    fun cost(now: List<InsightTrip>, ev: Boolean, price: Int): Insight? {
        val withEff = now.filter { it.efficiency != null && it.efficiency > 0f && (it.km ?: 0f) > 0f }
        if (withEff.size < 3) return null
        val avgEff = withEff.sumOf { it.km!!.toDouble() } / withEff.sumOf { it.km!!.toDouble() / it.efficiency!! }
        fun costOf(t: InsightTrip) = (t.km ?: 0f) / (t.efficiency?.takeIf { it > 0f } ?: avgEff.toFloat()) * price
        val total = now.sumOf { costOf(it).toDouble() }.roundToInt()
        if (total < 1_000) return null
        val byPurpose = now.filter { it.purpose != null }.groupBy { it.purpose!! }.mapValues { (_, l) -> l.sumOf { costOf(it).toDouble() } }
        val top = byPurpose.maxByOrNull { it.value }
        val errands = now.filter { (it.purpose == TripPurpose.SHOPPING || it.purpose == TripPurpose.MEAL) && (it.km ?: 99f) < 15f }
        val action: String
        val detail: String
        if (errands.size >= 3) {
            val half = errands.size / 2
            val km = errands.sortedBy { it.km }.take(half).sumOf { (it.km ?: 0f).toDouble() }
            val save = errands.sortedBy { it.km }.take(half).sumOf { costOf(it).toDouble() }.roundToInt()
            detail = (top?.let { "${it.key.label}에 ${won(it.value.roundToInt())}(${(it.value * 100 / total).roundToInt()}%)를 썼어요. " } ?: "") + "가까운 장보기·식사가 ${errands.size}번 있었어요."
            action = "가까운 일정을 묶으면 ${ReportAggregator.km(km)}·${won(save)} 아껴요"
        } else {
            detail = top?.let { "그중 ${it.key.label}이 ${won(it.value.roundToInt())}(${(it.value * 100 / total).roundToInt()}%)로 가장 많아요." } ?: "목적을 남기면 어디에 썼는지 나눠 볼 수 있어요."
            action = top?.let { "${it.key.label} 길의 출발 시각과 운전 습관부터 살펴봐요" } ?: "카드에 목적을 남겨요"
        }
        return Insight(InsightKind.COST, "이번 기간 차 ${if (ev) "충전" else "연료"}비 ${won(total)}", won(total), detail, action, "운행 ${now.size}번 · ${if (ev) "kWh" else "L"}당 ${won(price)}", 70)
    }

    // ---- ④ 안전 ----

    /** 밤 운전이 늘었거나, 밤에 급조작이 낮보다 잦으면. */
    fun safety(now: List<InsightTrip>, before: List<InsightTrip>, zone: ZoneId): Insight? {
        fun night(t: InsightTrip) = TimeBucket.of(Instant.ofEpochMilli(t.startedAt).atZone(zone).hour) == TimeBucket.NIGHT
        val n = now.count(::night)
        val b = before.count(::night)
        fun rate(l: List<InsightTrip>): Double? {
            val km = l.filter { it.harsh != null }.sumOf { (it.km ?: 0f).toDouble() }
            return if (km < 10) null else l.sumOf { it.harsh ?: 0 } * 100.0 / km
        }
        val nightRate = rate(now.filter(::night))
        val dayRate = rate(now.filterNot(::night))
        return when {
            nightRate != null && dayRate != null && dayRate > 0 && nightRate >= dayRate * 2 && n >= 3 -> Insight(
                InsightKind.SAFETY, "밤에 급조작이 낮보다 ${(nightRate / dayRate).roundToInt()}배 많아요", "밤 ${n}번 운행",
                "100km당 밤 %.1f회, 낮 %.1f회예요. 어두우면 거리 판단이 늦어져요.".format(nightRate, dayRate),
                "밤엔 차간 거리를 한 대 더 넉넉히", "이 기간 운행 ${now.size}번", 65)
            n >= 3 && b > 0 && n >= b * 1.5 -> Insight(
                InsightKind.SAFETY, "밤 운전이 지난 기간보다 ${((n - b) * 100.0 / b).roundToInt()}% 늘었어요", "밤 ${b}번 → ${n}번",
                "늦은 시간 운전이 잦아졌어요. 피곤할 때 사고 위험이 커져요.", "2시간마다 한 번 쉬어 가요", "이 기간 운행 ${now.size}번", 60)
            else -> null
        }
    }

    // ---- ⑤ 생활의 변화 ----

    fun life(reach: ReachData?): Insight? {
        reach ?: return null
        val r = reach.radiusKm ?: return null
        val prev = reach.prevRadiusKm
        val grew = prev != null && r >= prev * 1.2
        if (!grew && reach.newPlaces.isEmpty()) return null
        return Insight(
            InsightKind.LIFE,
            if (grew) "생활반경이 ${ReachRules.km(r)}로 넓어졌어요" else "처음 가 본 동네가 ${reach.newPlaces.size}곳 있어요",
            if (reach.newPlaces.isNotEmpty()) "새 동네 ${reach.newPlaces.size}곳" else ReachRules.km(r),
            (prev?.let { "지난 기간 ${ReachRules.km(it)}보다 넓게 다녔어요. " } ?: "") + (reach.farthest?.let { "가장 먼 곳은 ${it.place.name}(${ReachRules.km(it.fromHomeKm)})." } ?: ""),
            "새로 간 곳을 카드 태그로 남겨 두세요",
            "동네 ${reach.dongCount}곳 기록",
            40,
        )
    }

    // ---- 다가오는 인사이트 ----

    /** 아직 알려 줄 게 없을 때: 무엇을 몇 번 더 하면 알려 줄지. */
    fun upcoming(recent: List<InsightTrip>, zone: ZoneId): List<Insight> {
        val commute = recent.count { (it.purpose == TripPurpose.TO_WORK || it.purpose == TripPurpose.FROM_WORK) && Instant.ofEpochMilli(it.startedAt).atZone(zone).dayOfWeek.value <= 5 }
        val withEff = recent.count { it.efficiency != null }
        val scored = recent.count { it.score != null }
        return listOf(
            Insight(InsightKind.UPCOMING, "가장 빠른 출근 시각을 찾는 중", "출근 ${(6 - commute).coerceAtLeast(1)}번 더",
                "같은 길을 다른 시각에 달린 기록이 쌓이면 몇 분 일찍 나가면 얼마나 아끼는지 알려 줄게요.", "출근 카드를 '출근'으로 남겨 주세요", "지금 출근 ${commute}번", 0),
            Insight(InsightKind.UPCOMING, "운전 습관이 돈이 되는지 보는 중", "운행 ${(6 - minOf(withEff, scored)).coerceAtLeast(1)}번 더",
                "부드럽게 달린 날과 아닌 날의 ${"전비"}를 비교해 1년에 얼마 아낄 수 있는지 알려 줄게요.", "평소처럼 달리면 돼요", "지금 운행 ${recent.size}번", 0),
            Insight(InsightKind.UPCOMING, "어디에 돈을 쓰는지 나눠 보는 중", "목적 카드 3장 더",
                "카드에 목적을 남기면 출근·장보기 같은 목적별 비용과 아끼는 방법을 알려 줄게요.", "카드를 완성해 주세요", "지금 운행 ${recent.size}번", 0),
        )
    }

    // ---- 표시 ----

    fun won(v: Int): String = when {
        abs(v) >= 10_000 -> "%.1f만원".format(v / 10_000.0).replace(".0만원", "만원")
        else -> "%,d원".format(v)
    }

    fun duration(min: Int): String = if (min >= 60) "${min / 60}시간" + (if (min % 60 > 0) " ${min % 60}분" else "") else "${min}분"
}
