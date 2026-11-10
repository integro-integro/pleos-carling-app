package work.integro.carling.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.data.CarlingData
import work.integro.carling.data.TripCard
import work.integro.carling.game.GrowthRules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun timeOf(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TimeFormat)

private fun fmtKm(km: Float?): String = km?.let { "%.1f km".format(it) } ?: "확인 안 됨"

/** 트립카드 한 장. 보상은 이미 자동 저장되어 있고, 여기서는 보여주기만 한다. */
@Composable
fun TripCardView(card: TripCard, modifier: Modifier = Modifier, width: Dp = 380.dp) {
    Column(
        modifier
            .width(width)
            .background(Color.White, RoundedCornerShape(28.dp))
            .border(3.dp, Rose.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("트립카드 #%03d".format(card.number), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = RoseDeep)
            Spacer(Modifier.weight(1f))
            Text("자동 저장 완료", fontSize = 16.sp, color = Mint.copy(alpha = 1f), fontWeight = FontWeight.Bold)
        }
        Text("${card.date}  ${timeOf(card.startMillis)} – ${timeOf(card.endMillis)}", fontSize = 18.sp, color = Muted)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Stat("거리", fmtKm(card.distanceKm))
            Stat("시간", "${card.elapsedMinutes}분")
            Stat("안전 점수", card.score?.toString() ?: "미지원")
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().background(Blush, RoundedCornerShape(20.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            card.item?.let {
                Image(painterResource(roomItemRes(it)), contentDescription = it.label, modifier = Modifier.size(72.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(
                    card.item?.let { if (card.newPlacement) "${it.label} 획득 · ${it.space.label}에 놓였어요" else "${it.label} 또 받았어요" } ?: "",
                    fontSize = 20.sp,
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                )
                Text(if (card.newPlacement) "친밀도 +1" else "친밀도 변화 없음", fontSize = 17.sp, color = Muted)
            }
        }
        Spacer(Modifier.height(10.dp))
        val xpLine = when {
            card.xpLimitReached -> "오늘의 XP 지급 ${GrowthRules.DAILY_PAID_TRIPS}회 완료 · 이번 운행 XP 0"
            card.xpBonus > 0 -> "XP +${card.xpBase} · 안전운전 보너스 +${card.xpBonus}"
            else -> "XP +${card.xpBase}"
        }
        Text(xpLine, fontSize = 20.sp, color = Honey, fontWeight = FontWeight.Bold)
        card.questLine?.let { Text(it, fontSize = 18.sp, color = Ink) }
        if (card.simulated) Text("시뮬레이션 데이터", fontSize = 15.sp, color = Muted)
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, fontSize = 15.sp, color = Muted)
        Text(value, fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Bold)
    }
}

/** 주차 후 지난 운행 결과. 닫아도, 보지 않아도 보상은 이미 저장되어 있다. */
@Composable
fun TripRewardDialog(cards: List<TripCard>, onClose: () -> Unit) {
    val latest = cards.lastOrNull() ?: return
    FullScrim {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("함께 달렸어요!", fontSize = 30.sp, color = Color.White, fontWeight = FontWeight.Bold)
            if (cards.size > 1) Text("이번에 쌓인 카드 ${cards.size}장 중 마지막 카드예요", fontSize = 18.sp, color = Color.White)
            Spacer(Modifier.height(14.dp))
            TripCardView(latest, width = 460.dp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onClose, modifier = Modifier.height(64.dp)) { Text("방 보러 가기", fontSize = 24.sp) }
        }
    }
}

/** 트립카드를 볼 기간. */
private enum class CardRange(val label: String, val days: Long?) {
    TODAY("오늘", 1),
    WEEK("지난 7일", 7),
    TWO_WEEKS("지난 14일", 14),
    MONTH("지난 30일", 30),
    ALL("전체", null),
}

/** 지난 트립카드를 모두 볼 수 있다. 오늘 카드를 먼저 보여주고, 기간 필터로 7·14·30일을 고른다. 열람만 하고 수령 버튼은 없다. */
@Composable
fun TripCardsScreen(data: CarlingData, today: LocalDate, onClose: () -> Unit) {
    var range by remember { mutableStateOf(CardRange.TODAY) }
    val cards = remember(data.tripCards, range, today) {
        val from = range.days?.let { today.minusDays(it - 1).toString() }
        data.tripCards.asReversed().filter { from == null || it.date >= from }
    }
    val xp = cards.sumOf { it.xpTotal }
    val km = cards.sumOf { (it.distanceKm ?: 0f).toDouble() }
    FullScrim {
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("트립카드", fontSize = 30.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CardRange.entries.forEach { r ->
                    val on = r == range
                    Text(
                        r.label,
                        fontSize = 21.sp,
                        color = if (on) Color.White else Ink,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .background(if (on) Rose else Color.White, RoundedCornerShape(50))
                            .clickable { range = r }
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("${cards.size}장 · %.1f km · XP %d".format(km, xp), fontSize = 20.sp, color = Color.White)
            Spacer(Modifier.height(14.dp))
            if (cards.isEmpty()) {
                Speech(if (range == CardRange.TODAY) "오늘은 아직 카드가 없어요. 2km 이상, 5분 넘게 함께 달리면 카드가 생겨요." else "이 기간에는 카드가 없어요.")
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(cards, key = { it.number }) { TripCardView(it) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onClose, modifier = Modifier.height(64.dp)) { Text("닫기", fontSize = 24.sp) }
        }
    }
}

/** 방 위에 어둡게 깔리는 전체 화면 창. */
@Composable
fun FullScrim(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Scrim)
            .then(Modifier.padding(20.dp)),
        contentAlignment = Alignment.Center,
    ) { content() }
}

