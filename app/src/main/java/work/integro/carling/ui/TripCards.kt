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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.data.CarlingData
import work.integro.carling.data.Reward
import work.integro.carling.data.TripCard
import work.integro.carling.data.TripRecord
import work.integro.carling.game.CardState
import work.integro.carling.game.GameClock
import work.integro.carling.game.ReportAggregator
import work.integro.carling.game.Rules
import work.integro.carling.game.TimeBucket
import work.integro.carling.game.TripCardRules
import work.integro.carling.game.TripPurpose
import work.integro.carling.game.TripStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Zone: ZoneId = ZoneId.systemDefault()
private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val DateFormat = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

private fun timeOf(millis: Long): String = Instant.ofEpochMilli(millis).atZone(Zone).format(TimeFormat)
private fun dateOf(millis: Long): String = Instant.ofEpochMilli(millis).atZone(Zone).format(DateFormat)
private fun hourOf(millis: Long): Int = Instant.ofEpochMilli(millis).atZone(Zone).hour
fun kmText(km: Float?): String = km?.let { "%.1f".format(it) } ?: "—"
fun minutesText(sec: Long?): String = when {
    sec == null -> "—"
    sec < 60 -> "1분 미만"
    else -> ReportAggregator.duration(sec)
}

/** 시간대별 카드 머리띠 색. */
private fun bandOf(bucket: TimeBucket): List<Color> = when (bucket) {
    TimeBucket.MORNING -> listOf(Color(0xFFFFB79A), Color(0xFFF6A3C7))
    TimeBucket.DAY -> listOf(Color(0xFF7CC4FF), Color(0xFF9C8CFF))
    TimeBucket.EVENING -> listOf(Color(0xFFB39DFF), Color(0xFF7F6BF0))
    TimeBucket.NIGHT -> listOf(Color(0xFF3B3F9E), Color(0xFF23265E))
}

/** 양옆에 반원 홈이 파인 승차권 모양. 값은 픽셀. */
private fun ticketShape(notchPx: Float, radiusPx: Float, cornerPx: Float) = GenericShape { size, _ ->
    val body = androidx.compose.ui.graphics.Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(0f, 0f, size.width, size.height), androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx)))
    }
    val holes = androidx.compose.ui.graphics.Path().apply {
        addOval(Rect(Offset(0f, notchPx), radiusPx))
        addOval(Rect(Offset(size.width, notchPx), radiusPx))
    }
    op(body, holes, androidx.compose.ui.graphics.PathOperation.Difference)
}

/**
 * 트립카드 한 장: 승차권 모양. 머리띠 색은 출발 시간대, 가운데는 출발→도착 경로선과 거리,
 * 아래는 목적·보상 상태. 운행 기록(TripRecord)과 카드 상태(TripCard)를 함께 보여준다.
 */
@Composable
fun TicketCard(trip: TripRecord, card: TripCard, reward: Reward?, modifier: Modifier = Modifier, width: Dp = 540.dp, onClick: (() -> Unit)? = null) {
    val bucket = TimeBucket.of(hourOf(trip.startedAt))
    val faded = card.state == CardState.EXPIRED
    val density = androidx.compose.ui.platform.LocalDensity.current
    val shape = remember(density) { with(density) { ticketShape(284.dp.toPx(), 16.dp.toPx(), 30.dp.toPx()) } }
    Column(
        modifier
            .width(width)
            .clip(shape)
            .background(if (faded) Color(0xFFF1F0F7) else Color.White)
            .border(1.dp, Line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        // ---- 머리띠 ----
        Box(
            Modifier
                .fillMaxWidth()
                .height(104.dp)
                .background(Brush.horizontalGradient(bandOf(bucket).map { if (faded) it.copy(alpha = 0.45f) else it }))
                .padding(horizontal = 26.dp, vertical = 16.dp),
        ) {
            Column {
                Text("TRIP CARD · #%03d".format(card.number), fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(dateOf(trip.startedAt), fontSize = 30.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
                Text(bucket.label, fontSize = 16.sp, color = Color.White, modifier = Modifier.background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp))
                Spacer(Modifier.width(8.dp))
                StateChip(card.state, trip.status)
            }
        }
        // ---- 경로 ----
        Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("출발", fontSize = 15.sp, color = Muted)
                Text(timeOf(trip.startedAt), fontSize = 32.sp, color = Ink, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.weight(1f).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxWidth().height(36.dp)) {
                    val y = size.height * 0.72f
                    drawLine(Faint, Offset(10f, y), Offset(size.width - 10f, y), strokeWidth = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                    drawCircle(Lilac, 9f, Offset(10f, y))
                    drawCircle(Mint, 9f, Offset(size.width - 10f, y))
                }
                Text("${kmText(trip.distanceKm)} km", fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopCenter).background(Color.White).padding(horizontal = 10.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("도착", fontSize = 15.sp, color = Muted)
                Text(timeOf(trip.endAt), fontSize = 32.sp, color = Ink, fontWeight = FontWeight.Bold)
            }
        }
        // ---- 수치 ----
        Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp).height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            StatCell("이동시간", minutesText(trip.movingSec), Modifier.weight(1f))
            VDivider()
            StatCell("경과시간", minutesText(trip.elapsedSec), Modifier.weight(1f))
            VDivider()
            StatCell("운전점수", trip.score?.let { "$it" } ?: "—", Modifier.weight(1f), accent = trip.score?.let { if (it >= Rules.HEART_BRIGHT_SCORE) Mint else Ink })
        }
        // ---- 절취선 ----
        Canvas(Modifier.fillMaxWidth().height(32.dp)) {
            drawLine(Line, Offset(24f, size.height / 2), Offset(size.width - 24f, size.height / 2), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
        }
        // ---- 목적과 보상 ----
        Column(Modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, bottom = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val p = card.purpose
                if (p != null) {
                    Text("${p.icon} ${p.label}", fontSize = 21.sp, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.background(LilacSoft, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 6.dp))
                    card.tags.forEach { Text("  #$it", fontSize = 19.sp, color = Muted) }
                } else {
                    Text(if (faded) "목적 없이 만료됨" else "운행 목적을 골라 완성해요", fontSize = 20.sp, color = Muted)
                }
                Spacer(Modifier.weight(1f))
                if (trip.simulated) Text("시뮬레이션", fontSize = 14.sp, color = Faint)
            }
            Spacer(Modifier.height(10.dp))
            Text(statusLine(trip, card, reward), fontSize = 19.sp, color = when {
                reward != null -> Mint
                card.state == CardState.DRAFT -> Honey
                else -> Muted
            }, fontWeight = FontWeight.Bold)
        }
    }
}

private fun statusLine(trip: TripRecord, card: TripCard, reward: Reward?): String {
    val gp = Rules.growthPoints(trip.distanceKm, trip.movingSec)
    return when {
        reward != null -> "+${reward.gp} 성장 포인트" + if (reward.boxId != null) " · 🎁 럭키박스" else ""
        card.state == CardState.COMPLETED -> "운행이 확정되면 약 ${gp}점이 반영돼요"
        card.state == CardState.EXPIRED -> "기한이 지나 운행 기록만 남았어요"
        trip.status != TripStatus.CONFIRMED -> "완성하면 약 ${gp}점 · 주차 5분 뒤 운행 확정"
        else -> TripCardRules.expiresAt(card.clock(trip))?.let { remaining(it - GameClock.nowMillis()) } ?: "완성하면 약 ${gp}점"
    }
}

private fun remaining(left: Long): String {
    val h = left / Rules.HOUR_MILLIS
    return when {
        left <= 0 -> "곧 만료돼요"
        h >= 24 -> "${h / 24}일 안에 완성하세요"
        h >= 1 -> "${h}시간 안에 완성하세요"
        else -> "${(left / Rules.MINUTE_MILLIS).coerceAtLeast(1)}분 안에 완성하세요"
    }
}

@Composable
private fun StateChip(state: CardState, status: TripStatus) {
    val (label, color) = when (state) {
        CardState.COMPLETED -> (if (status == TripStatus.CONFIRMED) "완성" else "완성 · 확정 대기") to Mint
        CardState.DRAFT -> "임시" to Honey
        CardState.EXPIRED -> "만료" to Muted
    }
    Text(label, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.background(color, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp))
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier, accent: Color? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 15.sp, color = Muted)
        Text(value, fontSize = 23.sp, color = accent ?: Ink, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun VDivider() = Box(Modifier.width(1.dp).fillMaxHeight(0.6f).background(Line))

/** 카드 시트를 띄운 이유. */
enum class SheetMode { ARRIVAL, REMINDER, OPEN, EDIT }

/**
 * 트립카드 시트. 운행 목적 하나를 고르면 '완성하기'가 켜진다(11/12 확정). 칩 1탭 + 완성하기 1탭.
 * 태그는 선택. 완성한 카드는 목적만 고칠 수 있다(EDIT).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripCardSheet(
    trip: TripRecord,
    card: TripCard,
    reward: Reward?,
    mode: SheetMode,
    suggested: TripPurpose?,
    customTags: List<String>,
    otherDrafts: Int,
    onComplete: (TripPurpose, List<String>) -> Unit,
    onCompleteAll: () -> Unit,
    onLater: () -> Unit,
) {
    var purpose by remember(card.tripId) { mutableStateOf(card.purpose ?: suggested) }
    var tags by remember(card.tripId) { mutableStateOf(card.tags) }
    var typing by remember(card.tripId) { mutableStateOf(false) }
    var text by remember(card.tripId) { mutableStateOf("") }
    fun addTyped() {
        TripCardRules.cleanTag(text)?.let { if (it !in tags) tags = tags + it }
        text = ""
        typing = false
    }
    FullScrim {
        Panel(Modifier.widthIn(max = 1240.dp), padding = 28.dp) {
            Row {
                TicketCard(trip, card.copy(purpose = purpose, tags = tags), reward, width = 520.dp)
                Spacer(Modifier.width(30.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (mode) {
                            SheetMode.ARRIVAL -> "도착했어요! 어떤 여정이었나요?"
                            SheetMode.REMINDER -> "아직 완성하지 않은 여정이 있어요"
                            SheetMode.OPEN -> "이 여정을 완성할까요?"
                            SheetMode.EDIT -> "운행 목적 고치기"
                        },
                        fontSize = 30.sp,
                        color = Ink,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when (mode) {
                            SheetMode.REMINDER -> "지금부터 24시간 안에 완성할 수 있어요. 지나면 운행 기록만 남아요."
                            SheetMode.EDIT -> "목적을 바꾸면 카링의 개성도 다시 계산돼요."
                            else -> "목적 하나를 고르면 완성돼요. 운행이 확정되면 성장 포인트와 럭키박스가 와요."
                        },
                        fontSize = 19.sp,
                        color = Muted,
                    )
                    Spacer(Modifier.height(18.dp))
                    Column(Modifier.height(330.dp).verticalScroll(rememberScrollState())) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 3) {
                            TripPurpose.entries.forEach { p ->
                                PurposeChip(p, on = p == purpose) { purpose = p }
                            }
                        }
                        if (suggested != null && purpose == suggested && card.purpose == null) {
                            Text("✨ 비슷한 시간에 자주 고른 목적이에요", fontSize = 17.sp, color = Lilac, modifier = Modifier.padding(top = 8.dp))
                        }
                        if (mode != SheetMode.EDIT) {
                            Spacer(Modifier.height(18.dp))
                            Text("태그 (선택)", fontSize = 19.sp, color = Muted, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                (customTags.asReversed() + tags).distinct().forEach { t ->
                                    Pill("#$t", on = t in tags, fontSize = 18) { tags = if (t in tags) tags - t else tags + t }
                                }
                                if (!typing) Pill("＋ 태그", on = false, fontSize = 18) { typing = true }
                            }
                            if (typing) {
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = text,
                                        onValueChange = { text = it.take(12) },
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 22.sp, color = Ink),
                                        placeholder = { Text("예: 헬스장", fontSize = 19.sp) },
                                        modifier = Modifier.background(Color.White).width(320.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Button(onClick = ::addTyped, enabled = text.isNotBlank(), modifier = Modifier.height(58.dp)) { Text("추가", fontSize = 20.sp) }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = onLater, modifier = Modifier.height(68.dp)) { Text(if (mode == SheetMode.EDIT || mode == SheetMode.OPEN) "닫기" else "나중에", fontSize = 23.sp) }
                        if (otherDrafts > 0 && mode != SheetMode.EDIT) {
                            OutlinedButton(onClick = onCompleteAll, modifier = Modifier.height(68.dp)) { Text("모두 완성 (${otherDrafts + 1}장)", fontSize = 21.sp) }
                        }
                        Button(
                            onClick = {
                                if (typing) addTyped()
                                purpose?.let { onComplete(it, tags) }
                            },
                            enabled = purpose != null,
                            colors = ButtonDefaults.buttonColors(containerColor = Lilac, disabledContainerColor = LilacSoft, disabledContentColor = Faint),
                            modifier = Modifier.height(68.dp).widthIn(min = 200.dp),
                        ) { Text(if (mode == SheetMode.EDIT) "저장" else "완성하기", fontSize = 25.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PurposeChip(p: TripPurpose, on: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(196.dp)
            .background(if (on) Lilac else Color.White, RoundedCornerShape(22.dp))
            .border(2.dp, if (on) Lilac else Line, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(p.icon, fontSize = 30.sp)
        Text(p.label, fontSize = 21.sp, color = if (on) Color.White else Ink, fontWeight = FontWeight.Bold)
    }
}

/** 카드 목록 필터. */
private enum class CardFilter(val label: String) { ALL("전체"), DRAFT("임시"), COMPLETED("완성"), EXPIRED("만료") }

/**
 * 트립카드 탭: 최신 카드부터. 상태(임시·완성·만료) 필터, 임시 카드 모두 완성.
 * 카드를 누르면 임시는 완성 시트, 완성은 목적 고치기.
 */
@Composable
fun TripCardsTab(data: CarlingData, today: LocalDate, onOpen: (TripCard) -> Unit, onCompleteAll: () -> Unit, modifier: Modifier = Modifier) {
    var filter by remember { mutableStateOf(CardFilter.ALL) }
    val cards = remember(data.cards, data.trips, filter) {
        data.cards.sortedByDescending { data.trip(it.tripId)?.startedAt ?: it.tripId }.filter {
            when (filter) {
                CardFilter.ALL -> true
                CardFilter.DRAFT -> it.state == CardState.DRAFT
                CardFilter.COMPLETED -> it.state == CardState.COMPLETED
                CardFilter.EXPIRED -> it.state == CardState.EXPIRED
            }
        }
    }
    val weekFrom = today.minusDays(6)
    val week = data.trips.filter { it.status == TripStatus.CONFIRMED && Instant.ofEpochMilli(it.startedAt).atZone(Zone).toLocalDate() >= weekFrom }
    Column(modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp)) {
        // 오른쪽 위 음성·도움말 버튼과 겹치지 않게 비워 둔다
        Row(Modifier.padding(end = 280.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("트립카드", fontSize = 34.sp, color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(20.dp))
            Text("최근 7일 ${week.size}회 · ${ReportAggregator.km(week.sumOf { (it.distanceKm ?: 0f).toDouble() })} · 완성 ${data.cards.count { it.state == CardState.COMPLETED }}장", fontSize = 20.sp, color = Muted)
            Spacer(Modifier.weight(1f))
            CardFilter.entries.forEach { f ->
                val n = when (f) {
                    CardFilter.ALL -> data.cards.size
                    CardFilter.DRAFT -> data.cards.count { it.state == CardState.DRAFT }
                    CardFilter.COMPLETED -> data.cards.count { it.state == CardState.COMPLETED }
                    CardFilter.EXPIRED -> data.cards.count { it.state == CardState.EXPIRED }
                }
                Pill("${f.label} $n", on = f == filter, modifier = Modifier.padding(start = 10.dp), fontSize = 19) { filter = f }
            }
        }
        Spacer(Modifier.height(20.dp))
        if (cards.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Speech(if (data.cards.isEmpty()) "아직 카드가 없어요. 1km 이상, 3분 넘게 함께 달리고 주차하면 카드가 생겨요." else "이 상태의 카드가 없어요.")
            }
        } else {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                items(cards, key = { it.tripId }) { card ->
                    val trip = data.trip(card.tripId) ?: return@items
                    TicketCard(trip, card, data.rewards[card.tripId], width = 500.dp, onClick = if (card.state != CardState.EXPIRED) ({ onOpen(card) }) else null)
                }
            }
        }
        val drafts = data.draftCards.size
        if (drafts > 0) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onCompleteAll, modifier = Modifier.height(60.dp)) { Text("임시 카드 모두 완성 (${drafts}장 · 추천 목적, 없으면 기타)", fontSize = 20.sp) }
        }
    }
}
