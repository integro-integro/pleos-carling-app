package work.integro.carling.ui

import androidx.compose.foundation.Canvas
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import work.integro.carling.game.CardTier
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
private val DateFormat = DateTimeFormatter.ofPattern("yyyy. M. d (E)", Locale.KOREAN)

private fun timeOf(millis: Long): String = Instant.ofEpochMilli(millis).atZone(Zone).format(TimeFormat)
private fun dateOf(millis: Long): String = Instant.ofEpochMilli(millis).atZone(Zone).format(DateFormat)
private fun hourOf(millis: Long): Int = Instant.ofEpochMilli(millis).atZone(Zone).hour
fun kmText(km: Float?): String = km?.let { "%.1f".format(it) } ?: "—"
fun minutesText(sec: Long?): String = when {
    sec == null -> "—"
    sec < 60 -> "1분 미만"
    else -> ReportAggregator.duration(sec)
}

/** 목적별 칩 색(배경, 글자). 목적이 없으면 라벤더. */
private fun purposeColors(p: TripPurpose?): Pair<Color, Color> = when (p) {
    TripPurpose.COMMUTE -> Color(0xFFECEBFF) to Color(0xFF5B4FD6)
    TripPurpose.FIELD -> Color(0xFFE3F0FF) to Color(0xFF2F6FC4)
    TripPurpose.FAMILY -> Color(0xFFFFEEDE) to Color(0xFFC46A1F)
    TripPurpose.TRAVEL -> Color(0xFFDDF3FF) to Color(0xFF1F7FB3)
    TripPurpose.HOBBY -> Color(0xFFFFE6F1) to Color(0xFFC2477D)
    TripPurpose.OTHER -> Color(0xFFDFF8EE) to Color(0xFF1F9C74)
    null -> LilacSoft to Muted
}

private val ShineColors = listOf(Color(0xFF8CFFD6), Color(0xFFB9B0FF), Color(0xFF9FD3FF), Color(0xFF8CFFD6))
private val SpecialColors = listOf(Color(0xFFFFE3A3), Color(0xFFFFB7D5), Color(0xFFC7B8FF), Color(0xFFFFE3A3))

fun tierOf(trip: TripRecord, card: TripCard): CardTier = CardTier.of(card.number, trip.distanceKm, trip.score)

/**
 * 트립카드 한 장(시안 v1). 위에는 목적 풍경과 출발 시간대 하늘, 아래는 목적 칩·날짜·거리·시간과 보상 상태.
 * 반짝(운전점수 85+)은 민트 무지갯빛, 특별(첫 카드·100km 이상)은 금빛 테두리가 천천히 돈다.
 * 목적을 아직 고르지 않았으면 하늘만 보이고, 고르는 순간 풍경이 드러난다.
 */
@Composable
fun TicketCard(trip: TripRecord, card: TripCard, reward: Reward?, modifier: Modifier = Modifier, width: Dp = 420.dp, glint: Float = -1f, onClick: (() -> Unit)? = null) {
    val bucket = TimeBucket.of(hourOf(trip.startedAt))
    val faded = card.state == CardState.EXPIRED
    val tier = tierOf(trip, card)
    val (tint, ink) = purposeColors(card.purpose)
    val spin = if (tier != CardTier.BASIC && !faded) {
        val t = rememberInfiniteTransition(label = "tier")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (tier == CardTier.SPECIAL) 5_000 else 6_000, easing = LinearEasing)), label = "spin").value
    } else 0f
    val shape = RoundedCornerShape(28.dp)
    Box(modifier.width(width)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .drawBehind {
                    // 등급 테두리: 카드 뒤에서 도는 무지갯빛 띠
                    if (tier != CardTier.BASIC && !faded) {
                        val colors = if (tier == CardTier.SPECIAL) SpecialColors else ShineColors
                        rotate(spin) {
                            drawCircle(Brush.sweepGradient(colors), radius = size.maxDimension, center = center)
                        }
                    }
                }
                .padding(if (tier != CardTier.BASIC && !faded) 4.dp else 0.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(if (faded) Color(0xFFF1F0F7) else Color.White)
                .border(1.dp, Line, RoundedCornerShape(25.dp))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(10.dp),
        ) {
            // ---- 풍경 ----
            Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(20.dp))) {
                Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = if (faded) 0.45f else 1f }) {
                    drawTripScene(card.purpose, bucket, (trip.tripId % 100_000).toInt())
                    if (glint >= 0f) {
                        val x = size.width * (glint * 2.4f - 1.2f)
                        drawRect(Brush.linearGradient(listOf(Color.Transparent, Color(0xAAFFFFFF), Color.Transparent), Offset(x, 0f), Offset(x + size.width * 0.5f, size.height)))
                    }
                }
                Text(
                    "TRIP CARD · #%03d".format(card.number),
                    fontSize = 14.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color(0x55000000), Offset(0f, 1f), 4f)),
                    modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                )
                Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { StateChip(card.state, trip.status) }
                if (card.purpose == null && !faded) {
                    Text(
                        "목적을 고르면 풍경이 나타나요",
                        fontSize = 17.sp,
                        color = Ink,
                        modifier = Modifier.align(Alignment.Center).background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                if (trip.simulated) Text("시뮬레이션", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp))
            }
            // ---- 내용 ----
            Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.background(tint, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(card.purpose?.icon ?: "🎫", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(card.purpose?.label ?: (if (faded) "목적 없음" else "어떤 여정?"), fontSize = 22.sp, color = ink, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.weight(1f))
                    if (card.tags.isNotEmpty()) Text(card.tags.joinToString(" ") { "#$it" }, fontSize = 16.sp, color = Muted, maxLines = 1)
                }
                Spacer(Modifier.height(10.dp))
                MetaRow(MetaIcon.DATE, "${dateOf(trip.startedAt)}  ${timeOf(trip.startedAt)} – ${timeOf(trip.endAt)}")
                MetaRow(MetaIcon.DISTANCE, "${kmText(trip.distanceKm)} km")
                MetaRow(MetaIcon.TIME, minutesText(trip.movingSec) + if (trip.elapsedSec - (trip.movingSec ?: 0) >= 60) "  ·  경과 ${minutesText(trip.elapsedSec)}" else "", last = true)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${bucket.label} 출발", fontSize = 15.sp, color = Muted)
                    Spacer(Modifier.weight(1f))
                    trip.score?.let {
                        Canvas(Modifier.size(14.dp, 13.dp)) { drawPath(heartPath(size.width, size.height), if (it >= Rules.HEART_BRIGHT_SCORE) Mint else Faint) }
                        Spacer(Modifier.width(6.dp))
                        Text("운전점수 $it", fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    statusLine(trip, card, reward),
                    fontSize = 17.sp,
                    color = when {
                        reward != null -> Color(0xFF1F9C74)
                        card.state == CardState.DRAFT -> Honey
                        else -> Muted
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (tier == CardTier.SPECIAL && !faded) {
            Text(
                "SPECIAL",
                fontSize = 13.sp,
                color = Color(0xFF6B4A00),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.offset(x = (-6).dp, y = 52.dp)
                    .background(Brush.horizontalGradient(listOf(Color(0xFFFFE3A3), Color(0xFFFFD07A))), RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }
    }
}

private enum class MetaIcon { DATE, DISTANCE, TIME }

@Composable
private fun MetaRow(icon: MetaIcon, text: String, last: Boolean = false) {
    Column {
        Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(20.dp)) {
                val s = size.width
                val st = Stroke(width = s * 0.09f, cap = StrokeCap.Round)
                when (icon) {
                    MetaIcon.DATE -> {
                        drawRoundRect(Muted, Offset(s * 0.12f, s * 0.2f), androidx.compose.ui.geometry.Size(s * 0.76f, s * 0.68f), androidx.compose.ui.geometry.CornerRadius(s * 0.14f), style = st)
                        drawLine(Muted, Offset(s * 0.12f, s * 0.42f), Offset(s * 0.88f, s * 0.42f), s * 0.09f)
                        drawLine(Muted, Offset(s * 0.33f, s * 0.1f), Offset(s * 0.33f, s * 0.28f), s * 0.09f, StrokeCap.Round)
                        drawLine(Muted, Offset(s * 0.67f, s * 0.1f), Offset(s * 0.67f, s * 0.28f), s * 0.09f, StrokeCap.Round)
                    }
                    MetaIcon.DISTANCE -> {
                        val p = androidx.compose.ui.graphics.Path().apply {
                            moveTo(s * 0.5f, s * 0.92f)
                            cubicTo(s * 0.2f, s * 0.6f, s * 0.2f, s * 0.48f, s * 0.2f, s * 0.4f)
                            cubicTo(s * 0.2f, s * 0.2f, s * 0.35f, s * 0.08f, s * 0.5f, s * 0.08f)
                            cubicTo(s * 0.65f, s * 0.08f, s * 0.8f, s * 0.2f, s * 0.8f, s * 0.4f)
                            cubicTo(s * 0.8f, s * 0.48f, s * 0.8f, s * 0.6f, s * 0.5f, s * 0.92f)
                            close()
                        }
                        drawPath(p, Muted, style = st)
                        drawCircle(Muted, s * 0.1f, Offset(s * 0.5f, s * 0.4f), style = st)
                    }
                    MetaIcon.TIME -> {
                        drawCircle(Muted, s * 0.38f, center, style = st)
                        drawLine(Muted, center, Offset(s * 0.5f, s * 0.28f), s * 0.09f, StrokeCap.Round)
                        drawLine(Muted, center, Offset(s * 0.66f, s * 0.6f), s * 0.09f, StrokeCap.Round)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(text, fontSize = 19.sp, color = Ink, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
    }
}

private fun heartPath(w: Float, h: Float) = androidx.compose.ui.graphics.Path().apply {
    moveTo(w * 0.5f, h)
    lineTo(w * 0.05f, h * 0.47f)
    cubicTo(-w * 0.1f, h * 0.2f, w * 0.3f, -h * 0.1f, w * 0.5f, h * 0.22f)
    cubicTo(w * 0.7f, -h * 0.1f, w * 1.1f, h * 0.2f, w * 0.95f, h * 0.47f)
    close()
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
                RevealCard(enabled = mode == SheetMode.ARRIVAL, key = card.tripId, tier = tierOf(trip, card)) { glint ->
                    TicketCard(trip, card.copy(purpose = purpose, tags = tags), reward, width = 430.dp, glint = glint)
                }
                Spacer(Modifier.width(30.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (mode) {
                            SheetMode.ARRIVAL -> when (tierOf(trip, card)) {
                                CardTier.SPECIAL -> "특별한 트립카드가 나왔어요!"
                                CardTier.SHINE -> "반짝이는 트립카드를 받았어요!"
                                CardTier.BASIC -> "새 트립카드를 받았어요!"
                            }
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
                            SheetMode.ARRIVAL -> "어떤 여정이었나요? 목적 하나를 고르면 풍경이 담기고 카드가 완성돼요."
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
                    TicketCard(trip, card, data.rewards[card.tripId], width = 400.dp, onClick = if (card.state != CardState.EXPIRED) ({ onOpen(card) }) else null)
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

/**
 * 카드 받는 순간(시안 v1): 뒷면이 위에서 톡 떨어져 뒤집히고, 풍경 위로 빛이 한 번 지나가며 반짝이가 퍼진다.
 * 전체 2초 안. [enabled]가 아니면 그냥 카드만 보여준다.
 */
@Composable
private fun RevealCard(enabled: Boolean, key: Long, tier: CardTier, content: @Composable (glint: Float) -> Unit) {
    val drop = remember(key) { Animatable(if (enabled) -1f else 0f) }
    val turn = remember(key) { Animatable(if (enabled) 180f else 0f) }
    val glint = remember(key) { Animatable(-1f) }
    var burst by remember(key) { mutableStateOf(0L) }
    LaunchedEffect(key) {
        if (!enabled) return@LaunchedEffect
        launch { drop.animateTo(0f, tween(520, easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.25f))) }
        delay(420)
        turn.animateTo(0f, tween(680, easing = CubicBezierEasing(0.6f, 0f, 0.2f, 1f)))
        burst = System.nanoTime()
        glint.snapTo(0f)
        glint.animateTo(1f, tween(700))
        glint.snapTo(-1f)
    }
    Box {
        Box(
            Modifier.graphicsLayer {
                translationY = drop.value * size.height * 1.4f
                alpha = if (drop.value < -0.85f) 0f else 1f
                rotationY = turn.value
                cameraDistance = 16f * density
            },
        ) {
            val back = turn.value > 90f
            Box(Modifier.graphicsLayer { alpha = if (back) 0f else 1f }) { content(glint.value) }
            if (back) CardBack(Modifier.matchParentSize().graphicsLayer { rotationY = 180f })
        }
        SparkleBurst(burst, if (tier == CardTier.SPECIAL) SpecialColors + Color.White else ShineColors + Color.White, Modifier.matchParentSize())
    }
}

/** 카드 뒷면: 라벤더 바탕에 민트 심장. */
@Composable
private fun CardBack(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFFA596FF), Color(0xFF6E5BE0)))), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(120.dp)) {
            drawCircle(Brush.radialGradient(listOf(Color(0xAA8CFFD6), Color.Transparent)), size.minDimension / 2)
            val w = size.width * 0.6f
            val h = size.height * 0.55f
            translate(size.width * 0.2f, size.height * 0.24f) { drawPath(heartPath(w, h), Color(0xFFB9FFE6)) }
        }
        Text("MY CARLING · TRIP CARD", fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Bold, letterSpacing = 3.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp))
    }
}

private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, val s: Float, val c: Color, var life: Float = 1f)

/** 카드 가운데서 퍼지는 반짝이. [key]가 바뀔 때마다 한 번. */
@Composable
private fun SparkleBurst(key: Long, colors: List<Color>, modifier: Modifier) {
    if (key == 0L) return
    var particles by remember(key) { mutableStateOf<List<Particle>>(emptyList()) }
    var frame by remember(key) { mutableStateOf(0) }
    LaunchedEffect(key) {
        val r = kotlin.random.Random(key)
        particles = List(70) {
            val a = r.nextFloat() * 6.283f
            val v = 4f + r.nextFloat() * 10f
            Particle(0f, 0f, kotlin.math.cos(a) * v, kotlin.math.sin(a) * v - 4f, 4f + r.nextFloat() * 6f, colors[r.nextInt(colors.size)])
        }
        while (particles.any { it.life > 0f }) {
            withFrameNanos {
                particles.forEach { p -> p.x += p.vx; p.y += p.vy; p.vy += 0.25f; p.vx *= 0.98f; p.life -= 0.016f }
                frame++
            }
        }
    }
    Canvas(modifier) {
        @Suppress("UNUSED_EXPRESSION") frame
        particles.forEach { p ->
            if (p.life > 0f) sparkle(center.x + p.x * density, center.y + p.y * density, p.s * density / 2, p.c.copy(alpha = p.life.coerceIn(0f, 1f)))
        }
    }
}
