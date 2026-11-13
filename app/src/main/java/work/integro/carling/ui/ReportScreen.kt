package work.integro.carling.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.data.CarlingData
import work.integro.carling.data.KaringReportBuilder
import work.integro.carling.data.KaringReportData
import work.integro.carling.game.Badge
import work.integro.carling.game.CarPart
import work.integro.carling.game.CarStyle
import work.integro.carling.game.CardState
import work.integro.carling.game.PersonalityResult
import work.integro.carling.game.ReportAggregator
import work.integro.carling.game.ReportData
import work.integro.carling.game.ReportPeriod
import work.integro.carling.game.ReportTrip
import work.integro.carling.game.Rules
import work.integro.carling.game.Stage
import work.integro.carling.game.TimeBucket
import work.integro.carling.game.TripStatus
import work.integro.carling.game.PeriodUnit
import androidx.compose.runtime.LaunchedEffect
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private val PurposeColors = listOf(Color(0xFF8C7CF0), Color(0xFF3CCB9A), Color(0xFFF2A93B), Color(0xFF6FB6FF), Color(0xFFF07BA8), Color(0xFFB0A6D9))
private val WeekdayLabels = listOf("월", "화", "수", "목", "금", "토", "일")
private val Zone: ZoneId = ZoneId.systemDefault()

/** 확정된 운행을 리포트 입력으로 바꾼다. 목적은 완성한 카드에서만 가져온다. */
fun reportTrips(data: CarlingData): List<ReportTrip> = data.trips.filter { it.status == TripStatus.CONFIRMED }.map { t ->
    val card = data.card(t.tripId)
    val reward = data.rewards[t.tripId]
    ReportTrip(
        tripId = t.tripId,
        startedAt = t.startedAt,
        distanceKm = t.distanceKm,
        movingSec = t.movingSec,
        elapsedSec = t.elapsedSec,
        score = t.score,
        purpose = card?.takeIf { it.state == CardState.COMPLETED }?.purpose,
        hasCard = card != null,
        cardState = card?.state,
        rewardedGp = reward?.gp,
        rewardedAt = reward?.grantedAt,
        harsh = t.harsh,
        efficiency = t.efficiency,
        ev = t.ev,
        companion = card?.takeIf { it.state == CardState.COMPLETED }?.companion,
        custom = card?.takeIf { it.state == CardState.COMPLETED }?.customPurpose,
    )
}

/** 활동 반경 입력: 확정 운행의 1회 거리와 출발·도착 동네. */
fun reachOf(data: CarlingData, period: ReportPeriod, today: LocalDate, atlas: work.integro.carling.geo.KoreaAtlas?): work.integro.carling.game.ReachData {
    val all = data.trips.filter { it.status == TripStatus.CONFIRMED }.map { work.integro.carling.game.ReachTrip(it.startedAt, it.distanceKm, it.startDong, it.endDong) }
    fun day(t: work.integro.carling.game.ReachTrip) = Instant.ofEpochMilli(t.startedAt).atZone(Zone).toLocalDate()
    val (from, to) = period.range(today)
    val (pFrom, pTo) = period.previous(today)
    val lookup: (String) -> work.integro.carling.game.Place? = { code ->
        atlas?.region(code)?.let { work.integro.carling.game.Place(it.code, it.name, it.centerLat.toDouble(), it.centerLng.toDouble(), it.areaKm2) }
    }
    return work.integro.carling.game.ReachRules.build(
        period = all.filter { day(it) in from..to },
        previous = all.filter { day(it) in pFrom..pTo },
        earlier = all.filter { day(it) < from },
        all = all,
        lookup = lookup,
        zoneOffsetMillis = Zone.rules.getOffset(Instant.ofEpochMilli(today.toEpochDay() * 86_400_000L)).totalSeconds * 1000L,
    )
}

/** 리포트 종류. */
private enum class Kind(val label: String) { DRIVING("운행 리포트"), KARING("성장 리포트") }

/**
 * 리포트 탭: 운행 리포트와 카링 리포트. 기간(이번 달·지난 달·최근 7일)은 둘이 함께 쓴다.
 * 운행 숫자는 자동으로 남은 모든 확정 운행에서, 목적별·성장 내역은 완성한 카드에서 나온다.
 */
@Composable
fun ReportTab(
    data: CarlingData, today: LocalDate, personality: PersonalityResult, onLocation: (Boolean) -> Unit = {}, store: work.integro.carling.data.CarlingStore? = null, modifier: Modifier = Modifier,
    /** '짜잔' 알림에서 '리포트 보기'를 누르면 그달 운행 리포트로 바로 간다. */
    focus: java.time.YearMonth? = null,
    onFocused: () -> Unit = {},
) {
    var kind by rememberSaveable { mutableStateOf(Kind.DRIVING) }
    var period by rememberPeriod()
    LaunchedEffect(focus) {
        if (focus != null) {
            val diff = (java.time.YearMonth.from(today).year - focus.year) * 12 + java.time.YearMonth.from(today).monthValue - focus.monthValue
            period = ReportPeriod(PeriodUnit.MONTH, -diff)
            kind = Kind.DRIVING
            onFocused()
        }
    }
    // 리포트는 기간이 꽉 차야 만들어진다(11/13): 이번 달·분기·해는 '준비 중', 주는 틈틈이 업데이트
    val pending = period.offset == 0 && period.unit != PeriodUnit.WEEK
    val savedMonth = if (period.unit == PeriodUnit.MONTH && period.offset < 0) store?.monthReport(java.time.YearMonth.from(period.range(today).first)) else null
    val trips = remember(data.trips, data.cards, data.rewards) { reportTrips(data) }
    val report = remember(trips, period, today, data.refuels) {
        ReportAggregator.build(period, today, Zone, trips, data.draftCards.size, data.refuels.map { work.integro.carling.game.RefuelPoint(it.at, it.ev, it.fromPercent, it.toPercent) })
    }
    // 동네 경계는 무거워서 처음 한 번 백그라운드에서 읽는다(위치 동의했을 때만)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val atlas by androidx.compose.runtime.produceState<work.integro.carling.geo.KoreaAtlas?>(null, data.locationConsent) {
        if (data.locationConsent == true) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { work.integro.carling.geo.KoreaAtlas.load(ctx) }.getOrNull() }
    }
    val reach = remember(data.trips, period, today, atlas) { reachOf(data, period, today, atlas) }
    val stats = remember(trips, period, today) { work.integro.carling.game.ImprovementRules.stats(period, today, Zone, trips) }
    val readHealth = LocalVehicleHealth.current
    var healthLoading by remember { mutableStateOf(true) }
    val health by androidx.compose.runtime.produceState<work.integro.carling.vehicle.VehicleHealth?>(null) {
        value = runCatching { readHealth() }.getOrNull()
        healthLoading = false
    }
    val price = if (report.ev == false) data.fuelPrice else data.kwhPrice
    val insights = remember(data.trips, data.cards, period, today, atlas, reach, price) {
        val its = data.trips.filter { it.status == TripStatus.CONFIRMED }.map { t ->
            val c = data.card(t.tripId)?.takeIf { it.state == CardState.COMPLETED }
            work.integro.carling.game.InsightTrip(t.startedAt, t.distanceKm, t.movingSec, c?.purpose, t.startDong, t.endDong, t.score, t.harsh, t.efficiency)
        }
        work.integro.carling.game.InsightRules.build(period, today, Zone, its, report.ev != false, price, { atlas?.region(it)?.name }, reach.takeIf { data.locationConsent == true })
    }
    var showLog by remember { mutableStateOf(false) }
    val growth = remember(data, period, today) { work.integro.carling.data.GrowthReportBuilder.build(period, today, Zone, data) }
    val karing = remember(data, period, today, personality) { KaringReportBuilder.build(period, today, Zone, data, personality) }
    Column(modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
        Row(Modifier.padding(end = 280.dp), verticalAlignment = Alignment.CenterVertically) {
            // 리포트 종류: 두 칸짜리 토글
            Row(Modifier.background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(50)).border(1.dp, Line, RoundedCornerShape(50)).padding(5.dp)) {
                Kind.entries.forEach { k ->
                    Text(
                        k.label,
                        fontSize = 23.sp,
                        color = if (k == kind) Color.White else Ink,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(if (k == kind) Lilac else Color.Transparent, RoundedCornerShape(50)).clickable { kind = k }.padding(horizontal = 26.dp, vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            PeriodBar(period, today, { period = it })
            Spacer(Modifier.weight(1f))
            if (kind == Kind.DRIVING) Text(
                "📝 운행일지 만들기",
                fontSize = 19.sp, color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Mint, RoundedCornerShape(50)).clickable { showLog = true }.padding(horizontal = 18.dp, vertical = 9.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.weight(1f)) {
            when (kind) {
                Kind.DRIVING -> if (pending) PendingReport(period, today, report, onWeek = { period = ReportPeriod(PeriodUnit.WEEK) }) else DrivingReport(
                    report,
                    insights = {
                        if (period.unit == PeriodUnit.WEEK) WeekBanner(period, today)
                        else InsightCards(report.label, savedMonth?.insights ?: insights, saved = savedMonth?.let { m -> java.time.Instant.ofEpochMilli(m.createdAt).atZone(Zone).toLocalDate() })
                        // 생활반경 혜택(4.15): 꼭 알아야 할 3가지 아래 기간당 1장
                        val h = health
                        val warnings = buildList {
                            h?.tires?.filter { it.low == true }?.forEach { add("${it.label} 타이어 공기압") }
                            if (h?.engineOil == "LOW" || h?.engineOil == "CRITICALLY_LOW") add("엔진오일")
                            if (h?.brakeFluid == "LOW" || h?.brakeFluid == "CRITICALLY_LOW") add("브레이크액")
                            if (h?.lowWasher == true) add("워셔액")
                            h?.warnings?.forEach { add("$it 경고등") }
                        }
                        val match = remember(data.trips, data.cards, data.refuels, data.benefitSnoozed, data.benefitConsent, warnings, atlas) {
                            if (data.benefitConsent != true) null else work.integro.carling.game.BenefitRules.pick(
                                data.trips.filter { it.status == TripStatus.CONFIRMED }.map { t ->
                                    work.integro.carling.game.BenefitTrip(t.startedAt, t.distanceKm, data.card(t.tripId)?.takeIf { it.state == CardState.COMPLETED }?.purpose, t.startDong, t.endDong)
                                },
                                data.refuels.map { it.at }, report.ev != false, warnings, store?.clock?.now() ?: System.currentTimeMillis(), Zone,
                                { atlas?.region(it)?.name }, data.benefitSnoozed,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        BenefitCard(
                            data.benefitConsent, match,
                            onConsent = { store?.setBenefitConsent(it) },
                            onSnooze = { store?.snoozeBenefit(it) },
                            onUse = { store?.logBenefit("benefit_used") },
                        )
                    },
                    reach = { ReachBlock(reach, report.period.previousLabel, data.locationConsent, atlas, onLocation) },
                    improvement = { ImprovementBlock(stats, report.efficiencyUnit) },
                    health = { VehicleHealthBlock(health, healthLoading) },
                    savings = { SavingsRow(report, price, store?.let { s -> { w: Int -> s.setPrice(report.ev != false, w) } }) },
                )
                Kind.KARING -> KaringReport(karing, data, growth)
            }
        }
        Text(
            when (kind) {
                Kind.DRIVING -> "운행 숫자는 자동으로 남은 기록 기준, 목적별은 완성한 카드 기준이에요."
                Kind.KARING -> "성장은 보상이 지급된 카드와 연 럭키박스, 비교는 각 기간 끝 시점의 카링 기준이에요. 목적 맞히기는 그때까지의 카드로 추천을 다시 계산해요."
            } + if (data.trips.any { it.simulated }) " 시뮬레이션 데이터가 포함돼 있어요." else "",
            fontSize = 15.sp,
            color = Ink,
            modifier = Modifier.padding(top = 10.dp).background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 5.dp),
        )
    }
    if (showLog) {
        val (from, to) = period.range(today)
        val inPeriod = trips.filter { Instant.ofEpochMilli(it.startedAt).atZone(Zone).toLocalDate() in from..to }
        DrivingLogDialog(
            report, inPeriod,
            work.integro.carling.game.ImprovementRules.verdicts(stats),
            work.integro.carling.game.ImprovementRules.savings(report.totals.km, report.avgEfficiency, report.prevEfficiency, price),
            health, data.logEmail,
            onSaveEmail = { store?.setLogEmail(it) },
            onClose = { showLog = false },
        )
    }
}

// ======================= 운행 리포트 =======================

@Composable
private fun DrivingReport(r: ReportData, insights: @Composable () -> Unit, reach: @Composable () -> Unit, improvement: @Composable () -> Unit, health: @Composable () -> Unit, savings: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
    // 맨 위: 꼭 알아야 할 3가지. 아래 ①~⑨는 자세히 보기
    insights()
    Text("자세히 보기 ↓", fontSize = 17.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 5.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(Modifier.weight(1.05f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Findings(r)
            TotalsBlock(r)
            improvement()
            reach()
            DailyBlock(r)
            WeekdayBlock(r)
            TopTripsBlock(r)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            health()
            EfficiencyBlock(r, savings)
            RefuelBlock(r)
            PurposeBlock(r)
            CompanionBlock(r)
            PatternBlock(r)
            HeatBlock(r)
            DistanceBlock(r)
            HabitBlock(r)
        }
    }
    }
}

@Composable
internal fun BlockTitle(no: String, title: String, sub: String? = null) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(no, fontSize = 18.sp, color = Lilac, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 24.sp, color = Ink, fontWeight = FontWeight.Bold)
        if (sub != null) {
            Spacer(Modifier.width(10.dp))
            Text(sub, fontSize = 16.sp, color = Muted)
        }
    }
    Spacer(Modifier.height(14.dp))
}

/** ① 핵심 발견 + 다음에 살펴볼 일. */
@Composable
private fun Findings(r: ReportData) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(Color(0xFF8C7CF0), Color(0xFF6E8BF5))), RoundedCornerShape(28.dp))
            .padding(28.dp),
    ) {
        Text("${r.label}의 발견", fontSize = 18.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (!r.enoughForFindings) {
            Text("기록이 조금 더 쌓이면 발견을 알려드릴게요.", fontSize = 26.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Text("운행이 ${ReportAggregator.MIN_TRIPS_FOR_FINDINGS}회 이상인 기간부터 보여드려요. (지금 ${r.totals.count}회)", fontSize = 18.sp, color = Color.White.copy(alpha = 0.85f))
        } else {
            r.findings.forEach { line ->
                Row(Modifier.padding(vertical = 5.dp)) {
                    Text("●", fontSize = 14.sp, color = Color(0xFF9BF5D4), modifier = Modifier.padding(top = 9.dp, end = 12.dp))
                    Text(line, fontSize = 25.sp, color = Color.White, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
                }
            }
        }
        if (r.nextSteps.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.3f)))
            Spacer(Modifier.height(12.dp))
            Text("다음에 살펴볼 일", fontSize = 17.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
            r.nextSteps.forEach { Text("→ $it", fontSize = 20.sp, color = Color.White, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}

/** ② 운행 현황: 숫자 8개와 직전 기간 대비 증감. */
@Composable
private fun TotalsBlock(r: ReportData) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            BlockTitle("②", "운행 현황", "${r.period.previousLabel} 대비")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("총거리", ReportAggregator.km(r.totals.km), delta(r.totals.km, r.previous.km), Modifier.weight(1f))
                Tile("운전시간", ReportAggregator.duration(r.totals.sec).ifEmpty { "0분" }, delta(r.totals.sec.toDouble(), r.previous.sec.toDouble()), Modifier.weight(1f))
                Tile("운전일", "${r.totals.days}일", delta(r.totals.days.toDouble(), r.previous.days.toDouble()), Modifier.weight(1f))
                Tile("운행 횟수", "${r.totals.count}회", delta(r.totals.count.toDouble(), r.previous.count.toDouble()), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat("평균 속도", r.avgSpeedKmh?.let { "${it.roundToInt()}km/h" } ?: "—", Modifier.weight(1f), sub = "이동시간 기준")
                MiniStat("1회 평균 거리", ReportAggregator.km(r.avgKm), Modifier.weight(1f))
                MiniStat("1회 평균 시간", minutesText(r.avgMovingSec.takeIf { r.totals.count > 0 }), Modifier.weight(1f))
                MiniStat("멈춰 있던 비율", r.stopRatio?.let { "$it%" } ?: "—", Modifier.weight(1f), sub = "신호·정체·짧은 정차")
            }
        }
    }
}

private fun delta(now: Double, before: Double): Pair<String, Color>? {
    if (before <= 0.0) return if (now > 0) "새 기록" to Lilac else null
    val pct = ((now - before) / before * 100).roundToInt()
    return when {
        pct > 0 -> "▲ $pct%" to Mint
        pct < 0 -> "▼ ${-pct}%" to Coral
        else -> "변화 없음" to Muted
    }
}

@Composable
private fun Tile(label: String, value: String, delta: Pair<String, Color>?, modifier: Modifier) {
    Column(modifier.background(Paper, RoundedCornerShape(20.dp)).padding(16.dp)) {
        Text(label, fontSize = 16.sp, color = Muted)
        Text(value, fontSize = 27.sp, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(delta?.first ?: " ", fontSize = 15.sp, color = delta?.second ?: Muted, fontWeight = FontWeight.Bold)
    }
}

/** 날짜별 거리 막대. */
@Composable
private fun DailyBlock(r: ReportData) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            // 분기는 주별, 년은 월별로 묶는다(막대가 너무 많아지지 않게)
            val unit = r.period.unit
            val sub = r.busiestDay?.let { "가장 많이 달린 날 ${it.first.monthValue}/${it.first.dayOfMonth} ${ReportAggregator.km(it.second)}" }
            when (unit) {
                work.integro.carling.game.PeriodUnit.WEEK, work.integro.carling.game.PeriodUnit.MONTH -> {
                    BlockTitle("②", "날마다 달린 거리", sub)
                    Bars(r.dailyKm.map { it.second }, r.dailyKm.map { if (it.first.dayOfWeek.value >= 6) Mint else Lilac }, Modifier.fillMaxWidth().height(150.dp))
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Text("${r.from.monthValue}/${r.from.dayOfMonth}", fontSize = 15.sp, color = Muted)
                        Spacer(Modifier.weight(1f))
                        Legend(Lilac, "주중")
                        Spacer(Modifier.width(12.dp))
                        Legend(Mint, "주말")
                        Spacer(Modifier.weight(1f))
                        Text("${r.to.monthValue}/${r.to.dayOfMonth}", fontSize = 15.sp, color = Muted)
                    }
                }
                else -> {
                    val byWeek = unit == work.integro.carling.game.PeriodUnit.QUARTER
                    val groups = r.dailyKm.groupBy { (d, _) -> if (byWeek) d.minusDays(d.dayOfWeek.value - 1L) else d.withDayOfMonth(1) }.toList()
                    BlockTitle("②", if (byWeek) "주마다 달린 거리" else "달마다 달린 거리", sub)
                    Bars(groups.map { g -> g.second.sumOf { it.second } }, groups.map { Lilac }, Modifier.fillMaxWidth().height(150.dp))
                    Spacer(Modifier.height(6.dp))
                    Row {
                        groups.forEachIndexed { i, (d, _) ->
                            Text(
                                if (byWeek) (if (i % 2 == 0) "${d.monthValue}/${d.dayOfMonth}" else "") else "${d.monthValue}월",
                                fontSize = 13.sp, color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 요일별 횟수·거리. */
@Composable
private fun WeekdayBlock(r: ReportData) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            val top = r.weekdayKm.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }
            BlockTitle("②", "요일별 운행", top?.let { "${WeekdayLabels[it.index]}요일에 가장 많이 달려요" })
            val maxKm = (r.weekdayKm.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
            Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                r.weekdayKm.forEachIndexed { i, km ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (km > 0) "${km.roundToInt()}" else "", fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold)
                        Box(Modifier.fillMaxWidth(0.7f).height((90 * km / maxKm).toFloat().coerceAtLeast(3f).dp).background(if (i >= 5) Mint else Lilac, RoundedCornerShape(8.dp)))
                        Text(WeekdayLabels[i], fontSize = 15.sp, color = Muted)
                        Text("${r.weekdayCounts[i]}회", fontSize = 13.sp, color = Faint)
                    }
                }
            }
        }
    }
}

/** 가장 긴 여정 5개. */
@Composable
private fun TopTripsBlock(r: ReportData) {
    if (r.topTrips.isEmpty()) return
    Panel(Modifier.fillMaxWidth()) {
        Column {
            BlockTitle("②", "가장 긴 여정")
            r.topTrips.forEachIndexed { i, t ->
                val d = Instant.ofEpochMilli(t.startedAt).atZone(Zone)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", fontSize = 18.sp, color = Lilac, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                    Text("${d.monthValue}/${d.dayOfMonth} %02d:%02d".format(d.hour, d.minute), fontSize = 18.sp, color = Ink, modifier = Modifier.width(130.dp))
                    Text(t.purpose?.let { "${it.icon} ${it.label}" } ?: "미분류", fontSize = 18.sp, color = Muted, modifier = Modifier.weight(1f))
                    Text(ReportAggregator.km((t.distanceKm ?: 0f).toDouble()), fontSize = 19.sp, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
                    Text(minutesText(t.movingSec), fontSize = 17.sp, color = Muted, modifier = Modifier.width(110.dp))
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(color, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 15.sp, color = Muted)
    }
}

/** ③ 목적별 운행: 횟수·거리·시간·비중, 미분류는 따로. */
@Composable
private fun PurposeBlock(r: ReportData) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            BlockTitle("③", "목적별 운행", "운전시간 비중")
            if (r.purposes.isEmpty()) Text("이 기간에는 운행이 없어요.", fontSize = 19.sp, color = Muted)
            if (r.totals.sec > 0) {
                Row(Modifier.fillMaxWidth().height(22.dp).background(Paper, RoundedCornerShape(11.dp))) {
                    r.purposes.forEachIndexed { i, row ->
                        if (row.timeShare > 0) Box(
                            Modifier.weight(row.timeShare.toFloat()).fillMaxHeight()
                                .background(if (row.purpose == null) Faint else PurposeColors[i % PurposeColors.size], RoundedCornerShape(11.dp)),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            r.purposes.forEachIndexed { i, row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).background(if (row.purpose == null) Faint else PurposeColors[i % PurposeColors.size], RoundedCornerShape(4.dp)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.purpose?.let { "${it.icon} ${it.label}" } ?: "미분류", fontSize = 21.sp, color = Ink, fontWeight = FontWeight.Bold)
                        if (row.purpose == null) {
                            val parts = listOfNotNull(
                                r.unclassifiedDrafts.takeIf { it > 0 }?.let { "미완성 $it" },
                                r.unclassifiedExpired.takeIf { it > 0 }?.let { "만료 $it" },
                                r.belowMinimum.takeIf { it > 0 }?.let { "기준 미달 $it" },
                            )
                            if (parts.isNotEmpty()) Text(parts.joinToString(" · "), fontSize = 15.sp, color = Muted)
                        }
                        if (row.purpose == work.integro.carling.game.TripPurpose.OTHER && r.customPurposes.isNotEmpty()) {
                            Text(r.customPurposes.take(4).joinToString(" · ") { (n, c) -> "$n $c" }, fontSize = 15.sp, color = Lilac)
                        }
                    }
                    Text("${row.count}회", fontSize = 18.sp, color = Muted, modifier = Modifier.width(64.dp))
                    Text(ReportAggregator.km(row.km), fontSize = 18.sp, color = Muted, modifier = Modifier.width(100.dp))
                    Text(ReportAggregator.duration(row.sec).ifEmpty { "0분" }, fontSize = 18.sp, color = Muted, modifier = Modifier.width(118.dp))
                    Text("${row.timeShare}%", fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
                }
            }
        }
    }
}

/** ⑥ 연비(전비) 비교: 평균과 직전 기간 대비, 목적·시간대·거리별, 부드러운 운전 vs 급조작, 최고·최저 운행, 날짜별 추이. */
@Composable
private fun EfficiencyBlock(r: ReportData, savings: @Composable () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            BlockTitle("⑥", "${r.efficiencyName} 비교", r.efficiencyUnit + " · 높을수록 좋아요")
            if (r.avgEfficiency == null) {
                Text("${r.efficiencyName} 기록이 아직 없어요. 다음 운행부터 주차할 때 차량 값을 함께 남겨요.", fontSize = 18.sp, color = Muted)
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("이 기간 평균", "%.1f".format(r.avgEfficiency), r.prevEfficiency?.let { prev -> delta(r.avgEfficiency.toDouble(), prev.toDouble()) }, Modifier.weight(1f))
                Tile(r.period.previousLabel, r.prevEfficiency?.let { "%.1f".format(it) } ?: "—", null, Modifier.weight(1f))
                Tile("가장 좋았던 운행", r.bestEfficiency?.efficiency?.let { "%.1f".format(it) } ?: "—", r.bestEfficiency?.let { tripLabel(it) to Muted }, Modifier.weight(1f))
                Tile("아쉬웠던 운행", r.worstEfficiency?.efficiency?.let { "%.1f".format(it) } ?: "—", r.worstEfficiency?.let { tripLabel(it) to Muted }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            savings()
            val (smooth, harsh) = r.smoothVsHarsh
            if (smooth != null && harsh != null) {
                Spacer(Modifier.height(12.dp))
                val diff = ((smooth - harsh) / harsh * 100).roundToInt()
                Text(
                    if (diff > 0) "부드럽게 달린 날(운전점수 ${Rules.HEART_BRIGHT_SCORE}+)의 ${r.efficiencyName}가 ${diff}%% 더 좋았어요 · %.1f vs %.1f".format(smooth, harsh)
                    else "운전점수와 ${r.efficiencyName}의 차이는 크지 않았어요 · %.1f vs %.1f".format(smooth, harsh),
                    fontSize = 19.sp,
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(MintSoft, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                EffBars("목적별", r.efficiencyByPurpose, r.avgEfficiency, Modifier.weight(1f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    EffBars("출발 시간대", r.efficiencyByBucket, r.avgEfficiency, Modifier.fillMaxWidth())
                    EffBars("1회 거리", r.efficiencyByDistance, r.avgEfficiency, Modifier.fillMaxWidth())
                }
            }
            val pts = r.dailyEfficiency.filter { it.second != null }
            if (pts.size >= 2) {
                Spacer(Modifier.height(14.dp))
                Text("날짜별 ${r.efficiencyName}", fontSize = 16.sp, color = Muted)
                val max = pts.maxOf { it.second!! } * 1.1f
                val min = (pts.minOf { it.second!! } * 0.85f).coerceAtLeast(0f)
                Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                    val n = r.dailyEfficiency.size.coerceAtLeast(2)
                    val avgY = size.height - (r.avgEfficiency - min) / (max - min) * size.height
                    drawLine(Faint, Offset(0f, avgY), Offset(size.width, avgY), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                    val p = Path()
                    var first = true
                    r.dailyEfficiency.forEachIndexed { i, (_, v) ->
                        if (v == null) return@forEachIndexed
                        val x = size.width * i / (n - 1)
                        val y = size.height - (v - min) / (max - min) * size.height
                        if (first) p.moveTo(x, y) else p.lineTo(x, y)
                        first = false
                        drawCircle(Mint, 6f, Offset(x, y))
                    }
                    drawPath(p, Mint, style = Stroke(width = 4f))
                }
                Text("점선은 이 기간 평균", fontSize = 13.sp, color = Faint)
            }
        }
    }
}

private fun tripLabel(t: ReportTrip): String {
    val d = Instant.ofEpochMilli(t.startedAt).atZone(Zone)
    return "${d.monthValue}/${d.dayOfMonth} " + (t.purpose?.label ?: "미분류")
}

@Composable
private fun EffBars(title: String, rows: List<work.integro.carling.game.EffRow>, avg: Float, modifier: Modifier) {
    Column(modifier) {
        Text(title, fontSize = 16.sp, color = Muted, fontWeight = FontWeight.Bold)
        if (rows.isEmpty()) Text("—", fontSize = 16.sp, color = Faint)
        val max = (rows.maxOfOrNull { it.avg } ?: 1f).coerceAtLeast(avg) * 1.05f
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(row.label, fontSize = 15.sp, color = Ink, modifier = Modifier.width(78.dp), maxLines = 1)
                Box(Modifier.weight(1f).height(16.dp).background(Paper, RoundedCornerShape(8.dp))) {
                    Box(Modifier.fillMaxWidth(row.avg / max).fillMaxHeight().background(if (row.avg >= avg) Mint else Lilac.copy(alpha = 0.6f), RoundedCornerShape(8.dp)))
                }
                Text("%.1f".format(row.avg), fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp).padding(start = 6.dp))
            }
        }
    }
}

/** ⑦ 주유·충전: 이 기간 횟수와 직전 대비, 평균 간격, 1회당 달린 거리, 기록 목록. */
@Composable
private fun RefuelBlock(r: ReportData) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            BlockTitle("⑦", "${r.refuelName} 기록", "남은 양이 ${Rules.REFUEL_MIN_PERCENT}%p 이상 늘어난 때")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("${r.refuelName} 횟수", "${r.refuels.size}번", if (r.prevRefuels > 0 || r.refuels.isNotEmpty()) delta(r.refuels.size.toDouble(), r.prevRefuels.toDouble()) else null, Modifier.weight(1f))
                Tile("평균 간격", r.refuelIntervalDays?.let { "%.1f일".format(it) } ?: "—", null, Modifier.weight(1f))
                Tile("1번에 달린 거리", r.kmPerRefuel?.let { ReportAggregator.km(it) } ?: "—", null, Modifier.weight(1f))
            }
            if (r.refuels.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                r.refuels.takeLast(6).asReversed().forEach { f ->
                    val d = Instant.ofEpochMilli(f.at).atZone(Zone)
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (f.ev) "⚡" else "⛽", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("${d.monthValue}/${d.dayOfMonth} %02d:%02d".format(d.hour, d.minute), fontSize = 17.sp, color = Ink, modifier = Modifier.width(120.dp))
                        Box(Modifier.weight(1f).height(14.dp).background(Paper, RoundedCornerShape(7.dp))) {
                            Box(Modifier.fillMaxWidth(f.toPercent / 100f).fillMaxHeight().background(MintSoft, RoundedCornerShape(7.dp)))
                            Box(Modifier.fillMaxWidth(f.fromPercent / 100f).fillMaxHeight().background(Coral.copy(alpha = 0.6f), RoundedCornerShape(7.dp)))
                        }
                        Text("${f.fromPercent}% → ${f.toPercent}%", fontSize = 16.sp, color = Ink, modifier = Modifier.width(110.dp).padding(start = 8.dp))
                    }
                }
            } else Text("이 기간에는 ${r.refuelName} 기록이 없어요.", fontSize = 17.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** ③ 누구랑 갔나. */
@Composable
private fun CompanionBlock(r: ReportData) {
    if (r.companions.isEmpty()) return
    Panel(Modifier.fillMaxWidth()) {
        Column {
            BlockTitle("③", "누구랑 달렸나", "완성한 카드 기준")
            val total = r.companions.sumOf { it.second }.coerceAtLeast(1)
            r.companions.forEach { (c, n) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(c?.let { "${it.icon} ${it.label}" } ?: "고르지 않음", fontSize = 18.sp, color = if (c == null) Muted else Ink, modifier = Modifier.width(140.dp))
                    Box(Modifier.weight(1f).height(16.dp).background(Paper, RoundedCornerShape(8.dp))) {
                        Box(Modifier.fillMaxWidth(n.toFloat() / total).fillMaxHeight().background(if (c == null) Faint else Mint, RoundedCornerShape(8.dp)))
                    }
                    Text("${n}회 · ${n * 100 / total}%", fontSize = 16.sp, color = Ink, modifier = Modifier.width(96.dp).padding(start = 8.dp))
                }
            }
        }
    }
}

/** ④ 생활 패턴: 시간대 분포, 주중·주말, 거리 구간, 하루 평균. */
@Composable
private fun PatternBlock(r: ReportData) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            BlockTitle("④", "생활 패턴", "출발 시간대")
            val maxB = (r.buckets.values.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
                TimeBucket.entries.forEach { b ->
                    val n = r.buckets[b] ?: 0
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${n}회", fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Bold)
                        Box(Modifier.fillMaxWidth(0.6f).height((80f * n / maxB).coerceAtLeast(4f).dp).background(Color(b.color), RoundedCornerShape(10.dp)))
                        Text(b.label, fontSize = 16.sp, color = Muted)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat("주중 / 주말", "${r.weekday} / ${r.weekend}회", Modifier.weight(1f), sub = "주말 ${ReportAggregator.km(r.weekendKm)}")
                MiniStat("단·중·장거리", "${r.shortTrips}·${r.midTrips}·${r.longTrips}", Modifier.weight(1f), sub = "10km 미만 · 30km 이상")
                MiniStat("운전일 하루 평균", "%.1f회".format(r.perDrivingDay), Modifier.weight(1f))
            }
        }
    }
}

/** ④ 요일 × 시간대 히트맵. */
@Composable
private fun HeatBlock(r: ReportData) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            BlockTitle("④", "언제 달렸나", "요일 × 출발 시간대")
            val max = (r.heat.flatten().maxOrNull() ?: 0).coerceAtLeast(1)
            Row(Modifier.padding(start = 44.dp)) {
                TimeBucket.entries.forEach { Text(it.label, fontSize = 14.sp, color = Muted, modifier = Modifier.weight(1f)) }
            }
            r.heat.forEachIndexed { d, row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(WeekdayLabels[d], fontSize = 15.sp, color = Muted, modifier = Modifier.width(44.dp))
                    row.forEach { n ->
                        Box(
                            Modifier.weight(1f).height(30.dp).padding(horizontal = 3.dp)
                                .background(if (n == 0) Paper else Lilac.copy(alpha = 0.18f + 0.82f * n / max), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) { if (n > 0) Text("$n", fontSize = 14.sp, color = if (n.toFloat() / max > 0.5f) Color.White else Ink, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

/** ④ 거리 구간 분포. */
@Composable
private fun DistanceBlock(r: ReportData) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            BlockTitle("④", "한 번에 얼마나 달렸나", "1회 거리 분포")
            val max = (r.distanceBins.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
            r.distanceBins.forEach { (label, n) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 16.sp, color = Muted, modifier = Modifier.width(110.dp))
                    Box(Modifier.weight(1f).height(18.dp).background(Paper, RoundedCornerShape(9.dp))) {
                        if (n > 0) Box(Modifier.fillMaxWidth(n.toFloat() / max).fillMaxHeight().background(Lilac, RoundedCornerShape(9.dp)))
                    }
                    Text("${n}회", fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp).padding(start = 10.dp))
                }
            }
        }
    }
}

/** ④ 습관: 연속 운전일, 카드 완성률, 운전점수. */
@Composable
private fun HabitBlock(r: ReportData) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            BlockTitle("④", "운전 습관과 카드")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat("가장 긴 연속 운전", "${r.longestStreak}일", Modifier.weight(1f), sub = "기간 끝 기준 ${r.currentStreak}일째")
                MiniStat("카드 완성률", if (r.cardsCreated > 0) "${r.cardsCompleted * 100 / r.cardsCreated}%" else "—", Modifier.weight(1f), sub = "완성 ${r.cardsCompleted} · 임시 ${r.cardsDraft} · 만료 ${r.cardsExpired}")
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat("평균 운전점수", r.avgScore?.let { "${it}점" } ?: "—", Modifier.weight(1f), sub = "자체 산출 · 베타")
                MiniStat("최고 점수", r.bestScore?.let { "${it}점" } ?: "—", Modifier.weight(1f))
                MiniStat("부드러운 운행", "${r.smoothTrips}회", Modifier.weight(1f), sub = "${Rules.HEART_BRIGHT_SCORE}점 이상")
                MiniStat("급가속·급감속", r.harshTotal?.let { "${it}회" } ?: "—", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier, sub: String? = null) {
    Column(modifier.background(Paper, RoundedCornerShape(18.dp)).padding(14.dp)) {
        Text(label, fontSize = 15.sp, color = Muted, maxLines = 1)
        Text(value, fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1)
        if (sub != null) Text(sub, fontSize = 13.sp, color = Faint, maxLines = 2)
    }
}

@Composable
private fun Bars(values: List<Double>, colors: List<Color>, modifier: Modifier) {
    val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    Canvas(modifier) {
        val n = values.size.coerceAtLeast(1)
        val slot = size.width / n
        val bar = (slot * 0.62f).coerceAtMost(36f)
        drawLine(Line, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 2f)
        values.forEachIndexed { i, v ->
            val h = (v / max * (size.height - 8f)).toFloat()
            if (v > 0) drawRoundRect(colors[i], Offset(i * slot + (slot - bar) / 2, size.height - h), Size(bar, h), CornerRadius(bar / 2.5f, bar / 2.5f))
            else drawCircle(Faint, 3f, Offset(i * slot + slot / 2, size.height - 6f))
        }
    }
}

// ======================= 카링 리포트 =======================

@Composable
private fun KaringReport(k: KaringReportData, data: CarlingData, g: work.integro.carling.data.GrowthCompare) {
    Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(Modifier.weight(1.05f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            KaringHeadline(k, data)
            ChangeBlock(g)
            SmartBlock(g)
            GrowthTotals(k)
            CumulativeBlock(k)
            DailyGpBlock(k)
            StageTimeline(k)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            AbilityBlock(g, data.talks)
            PersonalityBlock(k)
            WindowBlock(k)
            HistoryBlock(k)
            CollectionBlock(k)
            BoxStatsBlock(k)
        }
    }
}

/** 카링 한 줄 요약 카드. */
@Composable
private fun KaringHeadline(k: KaringReportData, data: CarlingData) {
    Row(
        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF8C7CF0), Color(0xFF3CCB9A))), RoundedCornerShape(28.dp)).padding(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StageThumb(k.stage, 110.dp)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text("${k.label}의 카링", fontSize = 17.sp, color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Bold)
            Text("${karingName(data.name, k.stage)} · ${k.stage.label}", fontSize = 28.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "${k.label} +${k.cardGp + k.bonusGp}점을 모았어요" + (k.etaDays?.let { " · 이 속도면 ${k.stage.next?.label}까지 약 ${it}일" } ?: ""),
                fontSize = 20.sp,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun GrowthTotals(k: KaringReportData) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            BlockTitle("①", "성장 현황")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat("카드 보상", "+${k.cardGp}", Modifier.weight(1f), sub = "${k.rewards}장 지급")
                MiniStat("럭키박스 보너스", "+${k.bonusGp}", Modifier.weight(1f), sub = "${k.boxesOpened}개 열기")
                MiniStat("누적 포인트", "%,d".format(k.totalGp), Modifier.weight(1f))
                MiniStat("하루 평균", "%.0f점".format(k.recentDailyGp), Modifier.weight(1f), sub = "최근 14일")
            }
        }
    }
}

/** 누적 포인트 선 그래프와 단계 기준선. */
@Composable
private fun CumulativeBlock(k: KaringReportData) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            BlockTitle("①", "자라온 길", "누적 포인트와 단계 기준")
            if (k.cumulative.size < 2) {
                Text("보상이 두 번 이상 쌓이면 그래프가 그려져요.", fontSize = 18.sp, color = Muted)
                return@Column
            }
            val maxY = maxOf(k.cumulative.last().second, (k.stage.next?.minGp ?: 0)).coerceAtLeast(1).toFloat() * 1.08f
            val first = k.cumulative.first().first
            val span = (k.cumulative.last().first.toEpochDay() - first.toEpochDay()).coerceAtLeast(1).toFloat()
            Box(Modifier.fillMaxWidth().height(190.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    Stage.entries.filter { it.minGp > 0 && it.minGp <= maxY }.forEach { s ->
                        val y = size.height - s.minGp / maxY * size.height
                        drawLine(Faint, Offset(0f, y), Offset(size.width, y), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                    }
                    val pts = k.cumulative.map { (d, v) -> Offset((d.toEpochDay() - first.toEpochDay()) / span * size.width, size.height - v / maxY * size.height) }
                    val area = Path().apply { moveTo(pts.first().x, size.height); pts.forEach { lineTo(it.x, it.y) }; lineTo(pts.last().x, size.height); close() }
                    drawPath(area, Brush.verticalGradient(listOf(Lilac.copy(alpha = 0.35f), Color.Transparent)))
                    val line = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                    drawPath(line, Lilac, style = Stroke(width = 5f))
                    drawCircle(Mint, 9f, pts.last())
                }
                Stage.entries.filter { it.minGp > 0 && it.minGp <= maxY }.forEach { s ->
                    Text(s.label, fontSize = 13.sp, color = Muted, modifier = Modifier.align(Alignment.TopEnd).padding(top = ((1f - s.minGp / maxY) * 190f - 18f).coerceAtLeast(0f).dp))
                }
            }
            Row {
                Text("${first.monthValue}/${first.dayOfMonth}", fontSize = 14.sp, color = Muted)
                Spacer(Modifier.weight(1f))
                Text("지금 %,d점".format(k.totalGp), fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DailyGpBlock(k: KaringReportData) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            BlockTitle("①", "날마다 모은 포인트", "카드 보상 + 럭키박스")
            Bars(k.dailyGp.map { it.second.toDouble() }, k.dailyGp.map { Mint }, Modifier.fillMaxWidth().height(130.dp))
            Spacer(Modifier.height(6.dp))
            Row {
                Text("${k.from.monthValue}/${k.from.dayOfMonth}", fontSize = 15.sp, color = Muted)
                Spacer(Modifier.weight(1f))
                Text("${k.to.monthValue}/${k.to.dayOfMonth}", fontSize = 15.sp, color = Muted)
            }
        }
    }
}

/** 단계 변화 기록과 다음 단계 예상. */
@Composable
private fun StageTimeline(k: KaringReportData) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            BlockTitle("①", "단계 기록")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                Stage.entries.filter { it != Stage.CRACKED }.forEach { s ->
                    val reached = k.stageChanges.firstOrNull { it.to == s }?.at
                    val now = s.ordinal <= k.stage.ordinal || (s == Stage.EGG)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        StageThumb(s, 60.dp, dim = !now)
                        Text(s.label, fontSize = 14.sp, color = if (now) Ink else Faint, fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                s == Stage.EGG -> "시작"
                                reached != null -> Instant.ofEpochMilli(reached).atZone(Zone).let { "${it.monthValue}/${it.dayOfMonth}" }
                                else -> "%,d점".format(s.minGp)
                            },
                            fontSize = 13.sp,
                            color = Muted,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                k.stage.next?.let { n -> "${n.label}까지 %,d점 남았어요".format((n.minGp - k.totalGp).coerceAtLeast(0)) + (k.etaDays?.let { " · 최근 속도면 약 ${it}일" } ?: "") }
                    ?: "지금 공개된 마지막 단계예요",
                fontSize = 18.sp,
                color = Ink,
            )
        }
    }
}

/** 개성 3축과 근거. */
@Composable
private fun PersonalityBlock(k: KaringReportData) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            BlockTitle("②", "지금의 개성", "최근 저장 카드 ${k.personality.sampleSize}장")
            val p = k.personality.personality
            AxisRow(Color(p.vibe?.color ?: p.body.color), "낮길·밤길", p.vibe?.let { "${it.icon} ${it.label} · ${it.eyewear}" } ?: "기본", k.personality.bodyReason())
            AxisRow(null, "대표 장식", p.decorationItem?.let { "${it.icon} ${it.label}" } ?: "없음", k.personality.decorationReason())
            AxisRow(if (p.heartBright) Mint else MintSoft, "심장빛", if (p.heartBright) "선명하게 반짝" else "기본", k.personality.heartReason())
            if (k.purposeShare.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("지금까지 완성한 카드의 목적", fontSize = 16.sp, color = Muted)
                Spacer(Modifier.height(6.dp))
                val total = k.purposeShare.sumOf { it.second }.coerceAtLeast(1)
                Row(Modifier.fillMaxWidth().height(20.dp).background(Paper, RoundedCornerShape(10.dp))) {
                    k.purposeShare.forEachIndexed { i, (_, n) -> Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(PurposeColors[i % PurposeColors.size], RoundedCornerShape(10.dp))) }
                }
                Spacer(Modifier.height(6.dp))
                Text(k.purposeShare.joinToString("  ·  ") { (p, n) -> "${p.label} ${n * 100 / total}%" }, fontSize = 15.sp, color = Muted)
            }
        }
    }
}

@Composable
private fun AxisRow(dot: Color?, title: String, value: String, reason: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(18.dp).background(dot ?: Color.Transparent, CircleShape).border(1.dp, Line, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 17.sp, color = Muted)
                Spacer(Modifier.width(10.dp))
                Text(value, fontSize = 21.sp, color = Ink, fontWeight = FontWeight.Bold)
            }
            Text(reason, fontSize = 16.sp, color = Muted)
        }
    }
}

/** 개성 분석에 들어간 최근 10장. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WindowBlock(k: KaringReportData) {
    if (k.window.isEmpty()) return
    Panel(Modifier.fillMaxWidth()) {
        Column {
            BlockTitle("②", "개성을 만든 카드", "오래된 것 → 최근")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                k.window.forEach { c ->
                    Column(
                        Modifier.width(92.dp).background(Color(c.bucket.color).copy(alpha = 0.25f), RoundedCornerShape(14.dp)).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("${c.date.monthValue}/${c.date.dayOfMonth}", fontSize = 13.sp, color = Muted)
                        Text(c.purpose?.icon ?: "·", fontSize = 22.sp)
                        Text(c.bucket.label, fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Bold)
                        Text(c.score?.let { "${it}점" } ?: "—", fontSize = 12.sp, color = Muted)
                    }
                }
            }
        }
    }
}

/** 개성 변화 기록. */
@Composable
private fun HistoryBlock(k: KaringReportData) {
    if (k.snapshots.isEmpty()) return
    Panel(Modifier.fillMaxWidth()) {
        Column {
            BlockTitle("②", "개성 변화 기록")
            k.snapshots.take(6).forEach { s ->
                val d = Instant.ofEpochMilli(s.createdAt).atZone(Zone)
                Row(Modifier.padding(vertical = 6.dp)) {
                    Text("${d.monthValue}/${d.dayOfMonth}", fontSize = 16.sp, color = Lilac, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                    Text(s.line.ifEmpty { "개성이 기본으로 돌아왔어요" }, fontSize = 16.sp, color = Ink, lineHeight = 23.sp)
                }
            }
        }
    }
}

/** 수집 현황: 배지 6종, 자동차 꾸미기 8종. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionBlock(k: KaringReportData) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            BlockTitle("③", "수집", "배지 ${k.badges.size}/${Badge.entries.size} · 자동차 꾸미기 ${k.parts.size}/${CarPart.entries.size} · 스타일 ${k.styles.size}/${CarStyle.entries.size}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge.entries.forEach { b -> CollectChip(b.icon, b.label, k.badges[b] ?: 0, Color(0xFFFFF7E3), false) }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CarPart.entries.forEach { c -> CollectChip("", c.label, k.parts[c] ?: 0, Color(0xFFE6FAF2), k.equipped[c.slot] == c, image = partRes(c)) }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CarStyle.entries.forEach { st -> CollectChip(st.icon, st.label, if (st in k.styles) 1 else 0, Color(0xFFEDEAFF), false) }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                work.integro.carling.game.CarColor.collectible.forEach { c -> CollectChip("●", c.label, k.colors[c] ?: 0, Color(c.light).copy(alpha = 0.5f), false) }
            }
        }
    }
}

@Composable
private fun CollectChip(icon: String, label: String, n: Int, fill: Color, equipped: Boolean, image: Int? = null) {
    Row(
        Modifier.background(if (n > 0) fill else Paper, RoundedCornerShape(14.dp)).border(2.dp, if (equipped) Mint else Color.Transparent, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (image != null) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(image), contentDescription = null, modifier = Modifier.size(26.dp).then(if (n > 0) Modifier else Modifier.background(Color.Transparent)), alpha = if (n > 0) 1f else 0.25f)
        else Text(if (n > 0) icon else "？", fontSize = 18.sp, color = if (n > 0) Color.Unspecified else Faint)
        Spacer(Modifier.width(6.dp))
        Text(if (n > 0) label + (if (n > 1) " ×$n" else "") + (if (equipped) " · 장착" else "") else "???", fontSize = 15.sp, color = if (n > 0) Ink else Faint)
    }
}

/** 럭키박스 통계. */
@Composable
private fun BoxStatsBlock(k: KaringReportData) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            BlockTitle("③", "럭키박스")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat("받은 상자", "${k.boxesEarned}개", Modifier.weight(1f), sub = "이 기간")
                MiniStat("연 상자", "${k.boxesOpened}개", Modifier.weight(1f), sub = "기다리는 상자 ${k.boxesWaiting}개")
                MiniStat("평균 보너스", k.avgBonus?.let { "+$it" } ?: "—", Modifier.weight(1f), sub = "전체 기준")
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat("배지가 나온 비율", k.badgeRate?.let { "$it%" } ?: "—", Modifier.weight(1f))
                MiniStat("꾸미기가 나온 비율", k.carRate?.let { "$it%" } ?: "—", Modifier.weight(1f))
                MiniStat("중복으로 받은 것", "${k.duplicates}개", Modifier.weight(1f))
            }
        }
    }
}

/**
 * 아직 꽉 차지 않은 달·분기·해: 리포트 대신 '준비 중'. 지금까지 모인 숫자만 살짝 보여 주고,
 * 그때그때 보고 싶으면 이번 주 기록으로 안내한다. 달이 꽉 차면 '짜잔' 알림과 함께 리포트가 만들어진다.
 */
@Composable
private fun PendingReport(period: ReportPeriod, today: LocalDate, report: work.integro.carling.game.ReportData, onWeek: () -> Unit) {
    val (_, end) = period.range(today).let { (s, _) -> s to when (period.unit) {
        PeriodUnit.MONTH -> s.plusMonths(1).minusDays(1)
        PeriodUnit.QUARTER -> s.plusMonths(3).minusDays(1)
        PeriodUnit.YEAR -> s.plusYears(1).minusDays(1)
        PeriodUnit.WEEK -> s.plusDays(6)
    } }
    val left = java.time.temporal.ChronoUnit.DAYS.between(today, end).toInt() + 1
    val name = when (period.unit) { PeriodUnit.MONTH -> "이번 달"; PeriodUnit.QUARTER -> "이번 분기"; else -> "올해" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF8C7CF0), Color(0xFF6FB6FF))), RoundedCornerShape(28.dp))
                .padding(28.dp),
        ) {
            Text("$name, 꼭 알아야 할 3가지", fontSize = 26.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf("⏰" to "출발 시각", "💚" to "습관 → 돈", "💰" to "비용").forEach { (icon, title) ->
                    Column(Modifier.weight(1f).background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(22.dp)).padding(20.dp)) {
                        Text("$icon $title", fontSize = 18.sp, color = Muted, fontWeight = FontWeight.Bold)
                        Text("준비 중입니다", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                        Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(10.dp).background(LilacSoft, RoundedCornerShape(5.dp)))
                        Box(Modifier.padding(top = 8.dp).fillMaxWidth(0.6f).height(10.dp).background(LilacSoft, RoundedCornerShape(5.dp)))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "${name}이 꽉 차면 카링이 짜잔! 하고 알려 드릴게요 · ${left}일 남았어요" + if (period.unit == PeriodUnit.MONTH) "" else " (월간 리포트는 매달 1일)",
                fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold,
            )
        }
        Row(
            Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(24.dp)).padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("지금까지 모인 기록", fontSize = 18.sp, color = Muted, fontWeight = FontWeight.Bold)
                Text("운행 ${report.totals.count}번 · ${"%,.1f".format(report.totals.km)}km · ${report.totals.sec / 3600}시간 ${report.totals.sec % 3600 / 60}분", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold)
                Text("주 단위 리포트는 운행할 때마다 틈틈이 업데이트돼요.", fontSize = 17.sp, color = Muted)
            }
            Text(
                "이번 주 기록 보기 →",
                fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Lilac, RoundedCornerShape(50)).clickable(onClick = onWeek).padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
    }
}

/** 주 단위: 꼭 알아야 할 3가지 대신 '틈틈이 업데이트' 안내 한 줄. */
@Composable
private fun WeekBanner(period: ReportPeriod, today: LocalDate) {
    val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
    val left = java.time.temporal.ChronoUnit.DAYS.between(today, monthEnd).toInt() + 1
    Row(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(24.dp)).border(2.dp, LilacSoft, RoundedCornerShape(24.dp)).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🔄", fontSize = 28.sp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(if (period.offset == 0) "이번 주는 운행할 때마다 틈틈이 업데이트돼요" else "주 단위 기록이에요", fontSize = 21.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("꼭 알아야 할 3가지는 한 달이 꽉 차면 만들어져요 · 이번 달 ${left}일 남음", fontSize = 17.sp, color = Muted)
        }
    }
}
