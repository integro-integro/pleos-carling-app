package work.integro.carling.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.data.TripCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CardTime = DateTimeFormatter.ofPattern("HH:mm")

private val TripCard.distanceText: String
    get() = distanceKm?.takeIf { it > 0.05f }?.let { "%.1fkm".format(it) } ?: "거리 측정 안 됨"

private val TripCard.startTime: String
    get() = CardTime.format(Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault()))

private val TripCard.routeText: String
    get() = when (newPlace) {
        true -> "새로운 길"
        false -> "익숙한 길"
        null -> "위치 사용 안 함"
    }

/** 카드 한 장. 목록과 새 카드 알림에서 같은 모양으로 쓴다. */
@Composable
private fun TripCardTile(card: TripCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    val tint = Color(card.light.color)
    Surface(onClick = onClick, shape = shape, color = Color.Transparent, modifier = modifier) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(PanelLight, Panel)), shape)
                .border(2.dp, if (card.saved) Gold else tint.copy(alpha = 0.55f), shape)
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TRIP CARD #%03d".format(card.number), fontSize = 16.sp, color = Gold, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (card.saved) Text("★ 저장됨", fontSize = 16.sp, color = Gold)
            }
            Spacer(Modifier.height(10.dp))
            Text(card.title, fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text("${card.date} · ${card.startTime}", fontSize = 17.sp, color = Muted)
            Spacer(Modifier.height(10.dp))
            Text("${card.minutes}분 · ${card.distanceText}", fontSize = 19.sp, color = Ink)
            card.highlights.firstOrNull()?.let {
                Spacer(Modifier.height(6.dp))
                Text("✨ $it", fontSize = 17.sp, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 주차 후 새 카드가 나오는 화면. 카드를 누르면 상세로 간다. */
@Composable
fun NewTripCardDialog(card: TripCard, onOpen: () -> Unit, onClose: () -> Unit) {
    Scrim {
        Text("새 트립카드가 나왔어요", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(20.dp))
        TripCardTile(card, onClick = onOpen, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        Text("카드를 누르면 상세를 보고 저장할 수 있어요", fontSize = 19.sp, color = Muted)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onClose, modifier = Modifier.height(60.dp).width(200.dp)) { Text("나중에", fontSize = 24.sp) }
    }
}

/** 우리의 트립카드 — 모든 트립이 최신순으로 쌓이고, 저장한 카드만 따로 볼 수 있다. */
@Composable
fun TripCardsDialog(cards: List<TripCard>, onOpen: (Int) -> Unit, onClose: () -> Unit) {
    var savedOnly by remember { mutableStateOf(false) }
    val shown = (if (savedOnly) cards.filter { it.saved } else cards).asReversed()
    Scrim {
        Text("우리의 트립카드", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(selected = !savedOnly, onClick = { savedOnly = false }, label = { Text("전체 ${cards.size}", fontSize = 18.sp) })
            FilterChip(selected = savedOnly, onClick = { savedOnly = true }, label = { Text("저장한 트립 ${cards.count { it.saved }}", fontSize = 18.sp) })
        }
        Spacer(Modifier.height(14.dp))
        if (shown.isEmpty()) {
            Text(
                if (savedOnly) "아직 저장한 트립이 없어요.\n카드를 눌러 마음에 드는 트립을 저장해 보세요."
                else "아직 트립카드가 없어요. 함께 달리면 한 장씩 생겨요.",
                fontSize = 21.sp, lineHeight = 31.sp, color = Muted, textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 60.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().height(520.dp),
            ) {
                items(shown, key = { it.number }) { card ->
                    TripCardTile(card, onClick = { onOpen(card.number) })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClose, modifier = Modifier.height(60.dp).width(200.dp)) { Text("닫기", fontSize = 24.sp) }
    }
}

/** 트립 상세와 저장. */
@Composable
fun TripCardDetailDialog(card: TripCard, mpIfSaved: Int, onToggleSaved: () -> Unit, onClose: () -> Unit) {
    Scrim {
        Text("TRIP CARD #%03d".format(card.number), fontSize = 18.sp, color = Gold, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(card.title, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        DetailLine("날짜", "${card.date} ${card.startTime} · 함께한 지 ${card.dayNumber}일째")
        DetailLine("함께한 시간", "${card.minutes}분")
        DetailLine("거리", card.distanceText)
        DetailLine("시간대", card.light.label)
        DetailLine("안전운전", card.safety?.let { "${it}점" } ?: "측정 안 됨")
        DetailLine("길", card.routeText)
        if (card.driveXpToday > 0) DetailLine("운전 XP", "이날 누적 ${card.driveXpToday} XP")
        if (card.mpGained > 0) DetailLine("받은 MP", "+${card.mpGained} MP")
        card.highlights.forEach { DetailLine("특별한 순간", it) }
        Spacer(Modifier.height(20.dp))
        GameButton(
            text = when {
                card.saved -> "저장 취소"
                mpIfSaved > 0 -> "이 트립 저장 (+$mpIfSaved MP)"
                else -> "이 트립 저장"
            },
            icon = if (card.saved) "☆" else "★",
            top = if (card.saved) Color(0xFF7C84B8) else Color(0xFF9BD0FF),
            bottom = if (card.saved) Color(0xFF4F568A) else Color(0xFF3B82E6),
            enabled = true,
            modifier = Modifier.widthIn(min = 340.dp),
            onClick = onToggleSaved,
        )
        if (!card.saved && mpIfSaved == 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (card.mpGained > 0) "이 카드의 MP는 이미 받았어요." else "이 날의 MP 2장을 다 받았어요. 저장은 할 수 있어요.",
                fontSize = 17.sp, color = Muted,
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClose, modifier = Modifier.height(60.dp).width(200.dp)) { Text("닫기", fontSize = 24.sp) }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, fontSize = 21.sp, color = Muted, modifier = Modifier.width(170.dp))
        Text(value, fontSize = 21.sp, color = Ink, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}
