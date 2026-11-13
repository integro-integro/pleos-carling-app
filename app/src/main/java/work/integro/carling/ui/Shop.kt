package work.integro.carling.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.data.CarlingData
import work.integro.carling.game.CarSlot
import work.integro.carling.game.EmblemTier
import work.integro.carling.game.LevelRules
import work.integro.carling.game.ShopItem
import work.integro.carling.game.TimeBucket

/** 상점 칸. */
private enum class ShopTab(val label: String) { PARTS("자동차 꾸미기"), COLORS("차체 색"), THEMES("배경 테마"), EMBLEM("엠블럼") }

/** 젤리 칩(홈 위쪽). */
@Composable
fun JellyChip(jelly: Int, onClick: () -> Unit) {
    Text("🍬 %,d · 상점".format(jelly), fontSize = 18.sp, color = Color(0xFF633806), fontWeight = FontWeight.Bold,
        modifier = Modifier.background(Color(0xFFFFE9C2), RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp))
}

/**
 * 상점(사용자 확정 11/12): 카드·럭키박스로 모은 젤리로 자동차 꾸미기·차체 색·배경 테마(시간대 고정)를 산다.
 * 성장 포인트는 줄지 않는다. 엠블럼은 사지 못하고 레벨로만 바뀐다(다음 단계 미리보기).
 */
@Composable
fun ShopDialog(data: CarlingData, onBuy: (ShopItem) -> Boolean, onUseTheme: (TimeBucket?) -> Unit, onClose: () -> Unit) {
    var tab by remember { mutableStateOf(ShopTab.PARTS) }
    var picked by remember { mutableStateOf<ShopItem?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    FullScrim {
        Panel(Modifier.width(1320.dp), padding = 28.dp) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🛍️ 상점", fontSize = 32.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(16.dp))
                    Text("젤리는 카드 보상·럭키박스·겹친 아이템·레벨업으로 모여요. 써도 성장 포인트는 그대로예요.", fontSize = 17.sp, color = Muted, modifier = Modifier.weight(1f))
                    Text("🍬 %,d".format(data.jelly), fontSize = 26.sp, color = Color(0xFF633806), fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Color(0xFFFFE9C2), RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 8.dp))
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShopTab.entries.forEach { t -> Pill(t.label, on = t == tab, fontSize = 19) { tab = t; picked = null; message = null } }
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.height(520.dp)) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        when (tab) {
                            ShopTab.PARTS -> ShopGrid(ShopItem.all.filterIsInstance<ShopItem.Part>().sortedBy { it.part.slot }, data, picked) { picked = it }
                            ShopTab.COLORS -> ShopGrid(ShopItem.all.filterIsInstance<ShopItem.Paint>(), data, picked) { picked = it }
                            ShopTab.THEMES -> ShopGrid(ShopItem.all.filterIsInstance<ShopItem.Theme>(), data, picked) { picked = it }
                            ShopTab.EMBLEM -> EmblemLadder(data)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        message ?: picked?.let { "${it.label} · 🍬 ${it.price}" + ownedText(it, data) } ?: if (tab == ShopTab.THEMES) "테마를 사면 차 안 배경이 그 시간대로 고정돼요. 산 테마는 언제든 바꾸거나 '자동'으로 돌릴 수 있어요." else "물건을 고르세요",
                        fontSize = 19.sp, color = Ink, modifier = Modifier.weight(1f),
                    )
                    if (tab == ShopTab.THEMES && data.theme != null) OutlinedButton(onClick = { onUseTheme(null); message = "배경이 다시 시간대를 따라가요" }, modifier = Modifier.height(60.dp).padding(end = 10.dp)) { Text("자동 배경으로", fontSize = 19.sp) }
                    val p = picked
                    if (p is ShopItem.Theme && p.bucket in data.themes) {
                        Button(onClick = { onUseTheme(p.bucket); message = "${p.bucket.label} 배경으로 바꿨어요" }, colors = ButtonDefaults.buttonColors(containerColor = Mint), modifier = Modifier.height(60.dp)) { Text("이 배경 쓰기", fontSize = 20.sp) }
                    } else if (p != null) {
                        Button(
                            onClick = { message = if (onBuy(p)) "${p.label}을(를) 샀어요!" + if (p is ShopItem.Part || p is ShopItem.Paint) " 내 카링에서 달 수 있어요" else "" else "젤리가 모자라요" },
                            enabled = data.jelly >= p.price,
                            colors = ButtonDefaults.buttonColors(containerColor = Lilac),
                            modifier = Modifier.height(60.dp),
                        ) { Text("🍬 ${p.price} 사기", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(onClick = onClose, modifier = Modifier.height(60.dp)) { Text("닫기", fontSize = 20.sp) }
                }
            }
        }
    }
}

private fun ownedText(i: ShopItem, d: CarlingData): String = when (i) {
    is ShopItem.Part -> (d.parts[i.part] ?: 0).let { if (it > 0) " · 가진 것 ${it}개" else " · 아직 없어요" }
    is ShopItem.Paint -> (d.colors[i.color] ?: 0).let { if (it > 0) " · 가진 색" else " · 아직 없어요" }
    is ShopItem.Theme -> if (i.bucket in d.themes) " · 산 테마" else ""
}

@Composable
private fun ShopGrid(items: List<ShopItem>, data: CarlingData, picked: ShopItem?, onPick: (ShopItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item ->
                    val owned = when (item) {
                        is ShopItem.Part -> (data.parts[item.part] ?: 0) > 0
                        is ShopItem.Paint -> (data.colors[item.color] ?: 0) > 0
                        is ShopItem.Theme -> item.bucket in data.themes
                    }
                    Column(
                        Modifier.weight(1f).background(Color.White, RoundedCornerShape(20.dp))
                            .border(if (picked?.key == item.key) 3.dp else 1.dp, if (picked?.key == item.key) Lilac else Line, RoundedCornerShape(20.dp))
                            .clickable { onPick(item) }.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(14.dp)).background(Paper), contentAlignment = Alignment.Center) {
                            when (item) {
                                is ShopItem.Part -> Image(painterResource(partRes(item.part)), null, Modifier.size(96.dp))
                                is ShopItem.Paint -> Box(Modifier.size(80.dp).background(Color(item.color.light), CircleShape).border(3.dp, Color.White, CircleShape))
                                is ShopItem.Theme -> Image(painterResource(cabinRes(data.cabin, item.bucket)), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1.5f))
                            }
                            if (owned) Text("가짐", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(Mint, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp))
                            if (item is ShopItem.Theme && data.theme == item.bucket) Text("사용 중", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(Lilac, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(item.label, fontSize = 17.sp, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1)
                        if (item is ShopItem.Part) Text(item.part.slot.label, fontSize = 14.sp, color = Faint)
                        Text("🍬 ${item.price}", fontSize = 17.sp, color = Color(0xFF633806), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp).background(Color(0xFFFFF3D6), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 2.dp))
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 엠블럼 단계 미리보기: 레벨로만 바뀐다. */
@Composable
private fun EmblemLadder(data: CarlingData) {
    val lv = LevelRules.of(data.totalGp)
    Column {
        Text("엠블럼은 살 수 없고 레벨이 오르면 바뀌어요. 지금 LV.$lv · ${EmblemTier.of(lv).label}", fontSize = 19.sp, color = Ink, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EmblemTier.entries.forEach { t ->
                val reached = lv >= t.minLevel
                Column(Modifier.weight(1f).background(if (reached) Color.White else Paper, RoundedCornerShape(20.dp)).border(if (EmblemTier.of(lv) == t) 3.dp else 1.dp, if (EmblemTier.of(lv) == t) Lilac else Line, RoundedCornerShape(20.dp)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.graphicsAlpha(if (reached) 1f else 0.4f)) { LevelEmblem(if (reached) lv.coerceAtMost(t.minLevel + 9).coerceAtLeast(t.minLevel) else t.minLevel, 170.dp) }
                    Text(t.label, fontSize = 20.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Text("LV.${t.minLevel}" + if (t.minLevel < 40) "–${t.minLevel + 9}" else "–${LevelRules.MAX}", fontSize = 15.sp, color = Muted)
                    if (!reached) Text("성장 %,d점부터".format(LevelRules.need(t.minLevel)), fontSize = 14.sp, color = Faint, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private fun Modifier.graphicsAlpha(a: Float): Modifier = this.then(Modifier.graphicsLayer { alpha = a })

/** 레벨업 축하: 새 엠블럼과 받은 젤리. */
@Composable
fun LevelUpDialog(from: Int, to: Int, onClose: () -> Unit) {
    FullScrim {
        Panel(Modifier.width(720.dp), padding = 32.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                LevelEmblem(to, 220.dp)
                Spacer(Modifier.height(10.dp))
                Text("레벨 업! LV.$from → LV.$to", fontSize = 32.sp, color = Ink, fontWeight = FontWeight.Bold)
                val tierUp = EmblemTier.of(to) != EmblemTier.of(from)
                Text(
                    (if (tierUp) "엠블럼이 ${EmblemTier.of(to).label}(으)로 더 화려해졌어요! " else "") + "젤리 🍬 ${(to - from) * work.integro.carling.game.JellyRules.LEVEL_UP}개를 받았어요.",
                    fontSize = 20.sp, color = Muted, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Lilac), modifier = Modifier.height(62.dp)) { Text("좋아요", fontSize = 22.sp) }
            }
        }
    }
}

/**
 * 친구 초대(비활성, 사용자 요청 11/12·11/13): 초대한 친구와 같은 시간에 운전하고 있으면 서로 어디쯤 달리는지 보여 주는 기능.
 * 지금은 설명과 초대 코드만 보이고 '곧 열려요'.
 */
@Composable
fun InviteDialog(code: String, onClose: () -> Unit) {
    FullScrim {
        Panel(Modifier.width(760.dp), padding = 32.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("👥 친구 초대하기", fontSize = 30.sp, color = Ink, fontWeight = FontWeight.Bold)
                Text("친구를 초대하면, 둘이 같은 시간에 운전하고 있을 때 서로 어디쯤 달리고 있는지 볼 수 있어요.", fontSize = 19.sp, color = Ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("둘 다 운전 중일 때만 위치를 주고받고, 주차하면 바로 멈춰요. 아직 준비 중이라 곧 열려요.", fontSize = 17.sp, color = Muted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                Text("내 초대 코드", fontSize = 16.sp, color = Faint)
                Text(code, fontSize = 40.sp, color = Faint, fontWeight = FontWeight.Black, letterSpacing = 6.sp,
                    modifier = Modifier.padding(top = 4.dp).background(Paper, RoundedCornerShape(16.dp)).border(2.dp, Line, RoundedCornerShape(16.dp)).padding(horizontal = 28.dp, vertical = 10.dp))
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {}, enabled = false, modifier = Modifier.height(62.dp)) { Text("초대하기 · 곧 열려요", fontSize = 21.sp) }
                    OutlinedButton(onClick = onClose, modifier = Modifier.height(62.dp)) { Text("닫기", fontSize = 21.sp) }
                }
            }
        }
    }
}
