package work.integro.carling.ui

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.size
import work.integro.carling.game.GrowthRules
import work.integro.carling.game.Lineage
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
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
import work.integro.carling.game.CarCatalog
import work.integro.carling.game.CarRarity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CardTime = DateTimeFormatter.ofPattern("HH:mm")

private val TripCard.distanceText: String
    get() = distanceKm?.takeIf { it > 0.05f }?.let { "%.1fkm".format(it) } ?: "거리 측정 안 됨"

private val TripCard.startTime: String
    get() = CardTime.format(Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault()))

private val TripCard.routeText: String
    get() = when {
        newPlace == true -> "새로운 목적지"
        newRoute -> "새로운 길"
        newPlace == false -> "익숙한 길"
        else -> "위치 사용 안 함"
    }

/** 운행이 끝나면 나오는 새 트립카드. 심장 뒷면에서 저장하면 자동차가 공개된다. */
@Composable
fun NewTripCardDialog(card: TripCard, canSave: Boolean, xpIfSaved: Int, onSave: () -> Unit, onClose: () -> Unit) {
    Scrim {
        Text(if (card.saved) "트립카드를 저장했어요!" else "새 트립카드를 얻었어요", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(14.dp))
        TripCardReveal(card, canSave, xpIfSaved, width = 300.dp, onSave = onSave)
        Spacer(Modifier.height(14.dp))
        Button(onClick = onClose, modifier = Modifier.height(60.dp).width(200.dp)) { Text(if (card.saved) "닫기" else "나중에", fontSize = 24.sp) }
    }
}

/**
 * 우리의 트립카드. 운전할 때마다 쌓이고, 저장하지 않은 카드는 심장 뒷면이다. 누르면 크게 보고 저장할 수 있다.
 */
@Composable
fun TripCardsDialog(cards: List<TripCard>, onOpen: (Int) -> Unit, onClose: () -> Unit) {
    var savedOnly by remember { mutableStateOf(false) }
    val shown = remember(cards, savedOnly) { cards.asReversed().filter { !savedOnly || it.saved } }
    Scrim {
        Text("트립카드", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(selected = !savedOnly, onClick = { savedOnly = false }, label = { Text("전체 ${cards.size}", fontSize = 18.sp) })
            FilterChip(selected = savedOnly, onClick = { savedOnly = true }, label = { Text("저장한 카드 ${cards.count { it.saved }}", fontSize = 18.sp) })
        }
        Spacer(Modifier.height(8.dp))
        Text("운전할 때마다 카드가 한 장 생겨요. 24시간 안에 저장해야 자동차와 아이템을 받고, 저장하지 않은 카드는 사라져요.", fontSize = 17.sp, color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().height(460.dp),
        ) {
            items(shown, key = { it.number }) { card ->
                Surface(onClick = { onOpen(card.number) }, color = Color.Transparent) { MiniTripCard(card, width = 150.dp) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClose, modifier = Modifier.height(60.dp).width(200.dp)) { Text("닫기", fontSize = 24.sp) }
    }
}

/**
 * 도감 — 트립카드를 저장할 때마다 나오는 자동차를 모은다. 못 모은 차는 카드 뒷면, 모은 차는 누르면 그 카드를 연다.
 */
@Composable
fun DexDialog(cards: List<TripCard>, lineage: Lineage?, stage: Int, onOpen: (Int) -> Unit, onClose: () -> Unit) {
    val counts = remember(cards) { cards.mapNotNull { it.carModel }.groupingBy { it }.eachCount() }
    var carlingTab by remember { mutableStateOf(false) }
    val owned = listOfNotNull(lineage)
    Scrim {
        Text("도감", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(selected = !carlingTab, onClick = { carlingTab = false }, label = { Text("🚗 자동차 ${counts.size} / ${CarCatalog.ALL.size}", fontSize = 18.sp) })
            FilterChip(selected = carlingTab, onClick = { carlingTab = true }, label = { Text("💚 카링 ${owned.size} / ${Lineage.entries.size}", fontSize = 18.sp) })
        }
        Spacer(Modifier.height(8.dp))
        if (carlingTab) {
            CarlingDex(owned, stage)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onClose, modifier = Modifier.height(60.dp).width(200.dp)) { Text("닫기", fontSize = 24.sp) }
            return@Scrim
        }
        Text("트립카드를 저장할 때마다 현대·기아·제네시스 자동차가 한 대씩 나와요. 못 모은 차는 뒷면으로 보이고, 모은 차를 누르면 카드를 볼 수 있어요.", fontSize = 17.sp, color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CarRarity.entries.forEach { r ->
                val have = CarCatalog.ALL.count { it.rarity == r && it.id in counts }
                Text("● ${r.label} $have/${CarCatalog.ALL.count { it.rarity == r }}", fontSize = 16.sp, color = Color(r.color))
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().height(460.dp),
        ) {
            items(CarCatalog.ALL, key = { it.id }) { car ->
                val latest = cards.lastOrNull { it.carModel == car.id }
                Box(
                    Modifier.height(170.dp).fillMaxWidth()
                        .then(if (latest != null) Modifier.clickable { onOpen(latest.number) } else Modifier),
                ) { DexTile(car, counts[car.id] ?: 0, Modifier.fillMaxSize()) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClose, modifier = Modifier.height(60.dp).width(200.dp)) { Text("닫기", fontSize = 24.sp) }
    }
}

/** 카링 도감. 만난 카링은 지금까지 자란 모습과 이름, 아직 못 만난 카링은 젤리 실루엣만 보여준다. */
@Composable
private fun CarlingDex(owned: List<Lineage>, stage: Int) {
    Text("운전 습관에 따라 다른 카링이 태어나요. 만난 카링만 모습이 보이고, 나머지는 실루엣으로 남아 있어요.", fontSize = 17.sp, color = Muted, textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Lineage.entries.forEach { l ->
            val met = l in owned
            val look = if (met) stage.coerceAtLeast(GrowthRules.LINEAGE_STAGE) else GrowthRules.MAX_STAGE
            val art = carlingArt(look, l)
            val shape = RoundedCornerShape(18.dp)
            Column(
                Modifier.weight(1f)
                    .background(if (met) Brush.verticalGradient(listOf(Color(l.color).copy(alpha = 0.35f), Panel)) else Brush.verticalGradient(listOf(PanelLight, Panel)), shape)
                    .border(if (met) 2.dp else 1.dp, if (met) Color(l.color) else Accent.copy(alpha = 0.3f), shape)
                    .padding(vertical = 12.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Image(
                        androidx.compose.ui.res.painterResource(art.res),
                        contentDescription = null,
                        // 못 만난 카링은 반투명한 젤리 실루엣
                        colorFilter = if (met) null else androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF9FB4F2), androidx.compose.ui.graphics.BlendMode.SrcIn),
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = if (art.mirrored) -1f else 1f
                            alpha = if (met) 1f else 0.55f
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(if (met) GrowthRules.stageName(look, l) else "???", fontSize = 18.sp, color = if (met) Ink else Muted, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${l.label} 계열", fontSize = 14.sp, color = if (met) Color(l.color) else Muted)
                if (!met) Text(l.condition, fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

/** 트립카드 한 장 크게 보기. 저장 전이면 여기서 저장해 공개할 수 있다. */
@Composable
fun TripCardDetailDialog(card: TripCard, canSave: Boolean, xpIfSaved: Int, onSave: () -> Unit, onClose: () -> Unit) {
    Scrim {
        TripCardReveal(card, canSave, xpIfSaved, width = 320.dp, onSave = onSave)
        Spacer(Modifier.height(12.dp))
        Text("${card.date} ${card.startTime} · ${card.light.label} · 안전운전 ${card.safety?.let { "${it}점" } ?: "측정 안 됨"} · ${card.routeText}", fontSize = 17.sp, color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
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
