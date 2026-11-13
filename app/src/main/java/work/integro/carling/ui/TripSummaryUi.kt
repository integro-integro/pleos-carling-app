package work.integro.carling.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.data.Reward
import work.integro.carling.data.TripCard
import work.integro.carling.data.TripRecord
import work.integro.carling.game.Companion
import work.integro.carling.game.ReachData
import work.integro.carling.game.ReachRules
import work.integro.carling.game.ReportAggregator
import work.integro.carling.game.TimeBucket
import work.integro.carling.game.TripPurpose
import work.integro.carling.game.TripSummaryData

private val SumColors = listOf(Color(0xFF8C7CF0), Color(0xFF3CCB9A), Color(0xFFF2A93B), Color(0xFF6FB6FF), Color(0xFFF07BA8), Color(0xFFB0A6D9))
private val Days = listOf("월", "화", "수", "목", "금", "토", "일")

/**
 * 트립카드 요약(시안 A, 사용자 확정 11/12): 한 줄 요약, 왜·언제·어디·누구랑 4칸, 생활반경, 루틴·특별한 여정.
 */
@Composable
internal fun TripSummaryPanel(s: TripSummaryData, reach: ReachData, prevLabel: String, located: Boolean, onPurpose: (TripPurpose?) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(26.dp)).padding(18.dp)) {
        // 한 줄
        Text(
            s.headline,
            fontSize = 21.sp, color = Color(0xFF3C3489), fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().background(LilacSoft, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // 왜
            SumCell("왜 · 목적", Modifier.weight(1f)) {
                val total = s.purposes.sumOf { it.second }.coerceAtLeast(1)
                val top = s.purposes.firstOrNull { it.first != null }
                Text(top?.let { "${it.first!!.icon} ${it.first!!.label} ${it.second * 100 / total}%" } ?: "—", fontSize = 21.sp, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().height(10.dp).background(Paper, RoundedCornerShape(5.dp))) {
                    s.purposes.forEachIndexed { i, (p, n) -> Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(if (p == null) Faint else SumColors[i % SumColors.size])) }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.purposes.take(4).forEachIndexed { i, (p, n) ->
                        Text("${p?.label ?: "미완성"} $n", fontSize = 14.sp, color = Muted, maxLines = 1,
                            modifier = Modifier.clickable { onPurpose(p) }.background(Paper, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
            // 언제
            SumCell("언제 · 요일 × 시간대", Modifier.weight(1f)) {
                Text(s.peak?.let { "${Days[it.first - 1]}요일 ${it.second.label}" } ?: "—", fontSize = 21.sp, color = Ink, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                val max = (s.heat.flatten().maxOrNull() ?: 0).coerceAtLeast(1)
                Canvas(Modifier.fillMaxWidth().height(58.dp)) {
                    val cw = size.width / 7
                    val ch = size.height / 4
                    s.heat.forEachIndexed { d, row ->
                        row.forEachIndexed { b, n ->
                            val a = if (n == 0) 0.08f else 0.25f + 0.75f * n / max
                            drawRoundRect(Lilac.copy(alpha = a), Offset(d * cw + 2, b * ch + 2), Size(cw - 4, ch - 4), CornerRadius(4f, 4f))
                        }
                    }
                }
                Row { Days.forEach { Text(it, fontSize = 12.sp, color = Faint, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) } }
            }
            // 어디
            SumCell("어디 · 자주 간 곳", Modifier.weight(1f)) {
                if (s.places.isEmpty()) {
                    Text(if (located) "동네 기록이 아직 적어요" else "위치를 켜면 동네가 보여요", fontSize = 17.sp, color = Muted)
                    if (!located) Text("리포트 ⑤에서 '위치 켜기'", fontSize = 14.sp, color = Faint)
                } else {
                    Text(s.places.first().first, fontSize = 21.sp, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                    s.places.forEach { (n, c) -> Text("📍 $n · ${c}번", fontSize = 15.sp, color = Muted, maxLines = 1) }
                }
            }
            // 누구랑
            SumCell("누구랑 (선택)", Modifier.weight(1f)) {
                val total = s.companions.sumOf { it.second }.coerceAtLeast(1)
                val alone = s.companions.firstOrNull { it.first == null }?.second ?: 0
                Text("혼자 ${alone * 100 / total}%", fontSize = 21.sp, color = Ink, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().height(10.dp).background(Paper, RoundedCornerShape(5.dp))) {
                    s.companions.forEach { (c, n) -> Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(if (c == null) Faint else companionColor(c))) }
                }
                Spacer(Modifier.height(6.dp))
                Text(s.companions.filter { it.first != null }.take(3).joinToString(" · ") { "${it.first!!.label} ${it.second * 100 / total}%" }.ifEmpty { "함께한 여정이 아직 없어요" }, fontSize = 14.sp, color = Muted, maxLines = 2)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // 생활반경
            SumCell("생활반경", Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiniReach(reach, Modifier.size(84.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        val r = reach.radiusKm ?: reach.typicalKm
                        Text(r?.let { "${ReachRules.km(it)}" + if (reach.dongCount > 0) " · 동네 ${reach.dongCount}곳" else "" } ?: "—", fontSize = 21.sp, color = Ink, fontWeight = FontWeight.Bold)
                        val prev = reach.prevRadiusKm ?: reach.prevTypicalKm
                        if (r != null && prev != null) Text("$prevLabel ${ReachRules.km(prev)}보다 " + if (r > prev * 1.05) "넓어졌어요" else if (r < prev * 0.95) "좁아졌어요" else "비슷해요", fontSize = 15.sp, color = Muted)
                        Text(
                            if (reach.radiusKm != null) "집 동네에서 도착지 80%가 드는 거리" + if (reach.newPlaces.isNotEmpty()) " · 새 동네: ${reach.newPlaces.first().name}" else ""
                            else "1회 거리 80%가 드는 거리(위치를 켜면 동네 기준)",
                            fontSize = 14.sp, color = Faint, maxLines = 2,
                        )
                    }
                }
            }
            // 루틴·특별한 여정
            SumCell("루틴 · 특별한 여정", Modifier.weight(1f)) {
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.routines.forEach { r -> Tag("🔁 ${r.label} ${r.count}회", MintSoft, Color(0xFF085041)) { onPurpose(r.purpose) } }
                    s.farthestKm?.let { Tag("🛣️ 가장 먼 여정 ${kmText(it)}km", Paper, Ink) }
                    if (reach.newPlaces.isNotEmpty()) Tag("✨ 처음 간 곳 ${reach.newPlaces.size}", Paper, Ink)
                    if (s.shine > 0) Tag("💚 반짝 카드 ${s.shine}", Paper, Ink)
                    if (s.special > 0) Tag("⭐ 특별 카드 ${s.special}", Color(0xFFFFF3D6), Color(0xFF6B4A00))
                    if (s.routines.isEmpty() && s.farthestKm == null) Text("같은 목적·시간대를 3번 넘게 달리면 루틴이 보여요", fontSize = 15.sp, color = Faint)
                }
            }
        }
    }
}

private fun companionColor(c: Companion): Color = when (c) {
    Companion.FAMILY -> Color(0xFFED93B1)
    Companion.FRIENDS -> Color(0xFF85B7EB)
    Companion.PARTNER -> Color(0xFFF07BA8)
    else -> Color(0xFFB0A6D9)
}

@Composable
private fun SumCell(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier.heightIn(min = 150.dp).background(Paper, RoundedCornerShape(18.dp)).padding(14.dp)) {
        Text(title, fontSize = 15.sp, color = Muted, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun Tag(text: String, bg: Color, fg: Color, onClick: (() -> Unit)? = null) {
    Text(text, fontSize = 15.sp, color = fg, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = Modifier.background(bg, RoundedCornerShape(50)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 10.dp, vertical = 4.dp))
}

/** 작은 과녁: 거리 고리마다 운행 수만큼 진하게. */
@Composable
private fun MiniReach(reach: ReachData, modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val step = size.minDimension / 2 / reach.rings.size
        val max = (reach.rings.maxOfOrNull { it.trips } ?: 0).coerceAtLeast(1)
        for (i in reach.rings.indices.reversed()) {
            drawCircle(Lilac.copy(alpha = 0.08f + 0.5f * reach.rings[i].trips / max), step * (i + 1), c)
            drawCircle(Color.White, step * (i + 1), c, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        }
        drawCircle(Ink, 6f, c)
    }
}

// ======================= 상세보기 =======================

/** 상세보기에 넣을 값(계산은 탭에서). */
class TripDetail(
    val from: String?,
    val to: String?,
    val sameAvg: Pair<Double, Long>?,
    /** 같은 길(출발·도착 동네) 또는 같은 목적 중 몇 번째. */
    val nth: Pair<Int, String>?,
    /** 도착 동네의 생활반경 혜택 한 줄(동의했을 때만). */
    val benefit: String? = null,
)

/**
 * 카드 상세보기(시안 B, 사용자 확정 11/12): 큰 카드 + 출발 → 도착 동네, 숫자 6칸, 같은 목적 평균과 비교.
 * '고치기'를 누르면 지금의 고치기 시트가 열린다.
 */
@Composable
internal fun TripDetailSheet(trip: TripRecord, card: TripCard, reward: Reward?, d: TripDetail, onEdit: () -> Unit, onSamePurpose: (() -> Unit)?, onClose: () -> Unit) {
    FullScrim {
        Panel(Modifier.width(1260.dp), padding = 28.dp) {
            Row {
                TicketCard(trip, card, reward, width = 430.dp)
                Spacer(Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text("${card.purpose?.icon ?: "🎫"} ${card.purposeLabel ?: "목적을 아직 고르지 않았어요"}", fontSize = 30.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Text("트립카드 #%03d · ${TimeBucket.of(java.time.Instant.ofEpochMilli(trip.startedAt).atZone(java.time.ZoneId.systemDefault()).hour).label} 출발".format(card.number), fontSize = 18.sp, color = Muted)
                    Spacer(Modifier.height(14.dp))
                    // 출발 → 도착
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Place(d.from ?: "출발")
                        Text("  →  ", fontSize = 22.sp, color = Lilac, fontWeight = FontWeight.Bold)
                        Place(d.to ?: "도착")
                        Spacer(Modifier.width(12.dp))
                        if (d.from == null && d.to == null) Text("위치를 켜면 동네가 남아요", fontSize = 15.sp, color = Faint)
                    }
                    Spacer(Modifier.height(14.dp))
                    val avg = d.sameAvg
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Kv("거리 · 이동시간", "${kmText(trip.distanceKm)}km · ${minutesText(trip.movingSec)}", Modifier.weight(1f))
                        Kv("운전점수 · 급조작", "${trip.score ?: "—"} · ${trip.harsh ?: "—"}회", Modifier.weight(1f))
                        Kv(if (trip.ev == false) "연비" else "전비", trip.efficiency?.let { "%.1f %s".format(it, if (trip.ev == false) "km/L" else "km/kWh") } ?: "—", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Kv("같은 목적 평균", avg?.let { "${ReportAggregator.km(it.first)} · ${minutesText(it.second)}" } ?: "—", Modifier.weight(1f),
                            sub = avg?.let { a -> trip.distanceKm?.let { k -> if (k >= a.first) "평소보다 ${ReportAggregator.km(k - a.first)} 더" else "평소보다 ${ReportAggregator.km(a.first - k)} 짧게" } })
                        Kv("이 여정 몇 번째", d.nth?.let { "${it.first}번째" } ?: "—", Modifier.weight(1f), sub = d.nth?.second)
                        Kv("보상", reward?.let { "+${it.gp}점" + if (it.boxId != null) " 🎁" else "" } ?: "확정되면 지급", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        card.companion?.let { Text("${it.icon} ${it.label}", fontSize = 18.sp, color = Ink, modifier = Modifier.background(Paper, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp)) }
                        card.tags.forEach { Text("#$it", fontSize = 18.sp, color = Muted, modifier = Modifier.background(Paper, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp)) }
                    }
                    d.benefit?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, fontSize = 16.sp, color = Color(0xFF854F0B), fontWeight = FontWeight.Bold, modifier = Modifier.background(Color(0xFFFFF3D6), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                    Spacer(Modifier.weight(1f, fill = false))
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                        if (onSamePurpose != null) OutlinedButton(onClick = onSamePurpose, modifier = Modifier.height(64.dp)) { Text("같은 목적 카드 보기", fontSize = 20.sp) }
                        OutlinedButton(onClick = onClose, modifier = Modifier.height(64.dp)) { Text("닫기", fontSize = 21.sp) }
                        Button(onClick = onEdit, colors = ButtonDefaults.buttonColors(containerColor = Lilac), modifier = Modifier.height(64.dp)) { Text("✏️ 목적·누구랑·태그 고치기", fontSize = 21.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Place(name: String) {
    Row(Modifier.background(LilacSoft, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(Lilac, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(name, fontSize = 20.sp, color = Color(0xFF3C3489), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Kv(label: String, value: String, modifier: Modifier, sub: String? = null) {
    Column(modifier.background(Paper, RoundedCornerShape(16.dp)).border(1.dp, Line, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text(label, fontSize = 15.sp, color = Muted, maxLines = 1)
        Text(value, fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1)
        sub?.let { Text(it, fontSize = 14.sp, color = Faint, maxLines = 1) }
    }
}
