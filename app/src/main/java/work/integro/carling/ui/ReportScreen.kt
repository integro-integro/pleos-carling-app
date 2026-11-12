package work.integro.carling.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.data.CarlingData
import work.integro.carling.game.CardState
import work.integro.carling.game.PersonalityResult
import work.integro.carling.game.ReportAggregator
import work.integro.carling.game.ReportData
import work.integro.carling.game.ReportPeriod
import work.integro.carling.game.ReportTrip
import work.integro.carling.game.TimeBucket
import work.integro.carling.game.TripStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private val PurposeColors = listOf(Color(0xFF8C7CF0), Color(0xFF3CCB9A), Color(0xFFF2A93B), Color(0xFF6FB6FF), Color(0xFFF07BA8), Color(0xFFB0A6D9))

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
    )
}

/**
 * 운행 리포트. 맨 위 핵심 발견만 읽고 닫아도 가치가 있게, 숫자는 그 아래에 쌓는다.
 * 운행 숫자는 자동으로 남은 모든 확정 운행에서, 목적별·성장 내역은 완성한 카드에서 나온다.
 */
@Composable
fun ReportTab(data: CarlingData, today: LocalDate, personality: PersonalityResult, modifier: Modifier = Modifier) {
    var period by remember { mutableStateOf(ReportPeriod.THIS_MONTH) }
    val trips = remember(data.trips, data.cards, data.rewards) { reportTrips(data) }
    val report = remember(trips, period, today) { ReportAggregator.build(period, today, ZoneId.systemDefault(), trips, data.draftCards.size) }
    val stageChanges = data.stageHistory.filter { Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault()).toLocalDate() in report.from..report.to }
    Column(modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
        Row(Modifier.padding(end = 280.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("운행 리포트", fontSize = 34.sp, color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(18.dp))
            Text("${report.from.monthValue}.${report.from.dayOfMonth} – ${report.to.monthValue}.${report.to.dayOfMonth}", fontSize = 20.sp, color = Muted)
            Spacer(Modifier.weight(1f))
            ReportPeriod.entries.forEach { p -> Pill(p.label, on = p == period, modifier = Modifier.padding(start = 10.dp), fontSize = 19) { period = p } }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Column(Modifier.weight(1.05f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Findings(report)
                TotalsBlock(report)
                DailyBlock(report)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                PurposeBlock(report)
                PatternBlock(report)
                GrowthBlock(report, data, personality, stageChanges.map { "${it.from.label} → ${it.to.label}" })
            }
        }
        Text(
            "운행 숫자는 자동으로 남은 기록 기준, 목적별·성장 내역은 완성한 카드 기준이에요." + if (data.trips.any { it.simulated }) " 시뮬레이션 데이터가 포함돼 있어요." else "",
            fontSize = 16.sp,
            color = Muted,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun BlockTitle(no: String, title: String, sub: String? = null) {
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
        Text("${r.period.label}의 발견", fontSize = 18.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
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

/** ② 운행 현황: 숫자 4개와 직전 기간 대비 증감. */
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
            BlockTitle("②", "날마다 달린 거리", r.longest?.let { "가장 긴 여정 ${ReportAggregator.km((it.distanceKm ?: 0f).toDouble())}" })
            val max = (r.dailyKm.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                val n = r.dailyKm.size.coerceAtLeast(1)
                val slot = size.width / n
                val bar = (slot * 0.62f).coerceAtMost(36f)
                drawLine(Line, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 2f)
                r.dailyKm.forEachIndexed { i, (d, km) ->
                    val h = (km / max * (size.height - 8f)).toFloat()
                    val weekend = d.dayOfWeek.value >= 6
                    if (km > 0) drawRoundRect(
                        if (weekend) Mint else Lilac,
                        Offset(i * slot + (slot - bar) / 2, size.height - h),
                        Size(bar, h),
                        CornerRadius(bar / 2.5f, bar / 2.5f),
                    ) else drawCircle(Faint, 3f, Offset(i * slot + slot / 2, size.height - 6f))
                }
            }
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
            // 한 줄짜리 비중 막대
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

/** ④ 생활 패턴: 시간대 분포, 주중·주말, 거리 구간, 하루 평균. */
@Composable
private fun PatternBlock(r: ReportData) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            BlockTitle("④", "생활 패턴")
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
                MiniStat("주중 / 주말", "${r.weekday} / ${r.weekend}회", Modifier.weight(1f))
                MiniStat("단·중·장거리", "${r.shortTrips}·${r.midTrips}·${r.longTrips}", Modifier.weight(1f), sub = "10km 미만 · 30km 이상")
                MiniStat("운전일 하루 평균", "%.1f회".format(r.perDrivingDay), Modifier.weight(1f))
                MiniStat("평균 운전점수", r.avgScore?.let { "${it}점" } ?: "—", Modifier.weight(1f), sub = "자체 산출 · 베타")
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier, sub: String? = null) {
    Column(modifier.background(Paper, RoundedCornerShape(18.dp)).padding(14.dp)) {
        Text(label, fontSize = 15.sp, color = Muted)
        Text(value, fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, fontSize = 13.sp, color = Faint)
    }
}

/** ⑤ 카링 성장 내역. */
@Composable
private fun GrowthBlock(r: ReportData, data: CarlingData, personality: PersonalityResult, stageChanges: List<String>) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            BlockTitle("⑤", "카링 성장 내역", "완성한 카드 기준")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat("완성한 카드", "${r.completedCards}장", Modifier.weight(1f), sub = "확정 운행 ${r.totals.count}회 중")
                MiniStat("획득 포인트", "+${r.gpEarned}", Modifier.weight(1f), sub = "럭키박스 보너스 제외")
                MiniStat("단계", data.stage.label, Modifier.weight(1f), sub = stageChanges.lastOrNull() ?: "이 기간 변화 없음")
            }
            Spacer(Modifier.height(14.dp))
            val p = personality.personality
            Text("지금의 개성", fontSize = 17.sp, color = Muted, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "${p.body.colorName} 몸" + (p.decorationItem?.let { " · ${it.icon} ${it.label}" } ?: "") + if (p.heartBright) " · 반짝이는 민트 심장" else "",
                fontSize = 22.sp,
                color = Ink,
                fontWeight = FontWeight.Bold,
            )
            Text(personality.bodyReason(), fontSize = 17.sp, color = Muted)
            if (p.decoration != null) Text(personality.decorationReason(), fontSize = 17.sp, color = Muted)
        }
    }
}
