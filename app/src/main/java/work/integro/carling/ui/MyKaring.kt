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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.data.CarlingData
import work.integro.carling.data.CarlingStore
import work.integro.carling.game.Badge
import work.integro.carling.game.BadgeTier
import work.integro.carling.game.CarColor
import work.integro.carling.game.CarPart
import work.integro.carling.game.CarSlot
import work.integro.carling.game.CarStyle
import work.integro.carling.game.Stage

/** 내 카링 보관함 칸. 슬롯을 누르면 그 칸으로 간다. */
private enum class Shelf(val label: String) { WHEEL("휠캡"), ANTENNA("안테나"), REAR("뒤쪽 소품"), COLOR("차체 색"), BADGE("대표 배지"), LOOK("모습") }

private fun shelfOf(slot: CarSlot) = when (slot) { CarSlot.WHEEL -> Shelf.WHEEL; CarSlot.ANTENNA -> Shelf.ANTENNA; CarSlot.REAR -> Shelf.REAR }
private fun slotOf(s: Shelf) = when (s) { Shelf.WHEEL -> CarSlot.WHEEL; Shelf.ANTENNA -> CarSlot.ANTENNA; Shelf.REAR -> CarSlot.REAR; else -> null }

/**
 * 내 카링(사용자 확정 11/12 시안): 왼쪽은 장착 슬롯 6칸이 카링 미리보기를 둘러싸고, 오른쪽은 보관함.
 * 보관함에서 고르면 미리보기가 바로 그 모습이 되고 '장착'으로 확정한다. '차 내리기'는 서 있는 카링,
 * '장착 전'은 아무것도 달지 않은 모습. 미리보기는 끌어서 360°로 돌려 볼 수 있다(아기링부터).
 */
@Composable
fun MyKaringTab(data: CarlingData, store: CarlingStore, modifier: Modifier = Modifier) {
    var shelf by remember { mutableStateOf(Shelf.WHEEL) }
    var picked by remember { mutableStateOf<Any?>(null) }
    val riding = data.riding
    var bare by remember { mutableStateOf(false) }
    // 미리보기: 고른 아이템을 달아 본 모습
    val preview = remember(data, picked, bare) {
        when {
            bare -> data.copy(equipped = emptyMap(), carColor = null)
            picked is CarPart -> data.copy(equipped = data.equipped + ((picked as CarPart).slot to picked as CarPart))
            picked is CarColor -> data.copy(carColor = (picked as CarColor).takeIf { it != CarColor.PEARL })
            picked is Stage -> data.copy(look = picked as Stage)
            else -> data
        }
    }
    Row(modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 84.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        // ---- 왼쪽: 슬롯 + 미리보기 ----
        Panel(Modifier.weight(1.12f).fillMaxHeight(), padding = 20.dp) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(CarSlot.ANTENNA, CarSlot.WHEEL, CarSlot.REAR).forEach { slot ->
                            val part = data.equipped[slot]
                            EquipSlot(slot.label, part?.label?.removeSuffix(" 휠캡")?.removeSuffix(" 안테나"), if (part != null) "장착" else null, shelf == shelfOf(slot),
                                image = part?.let { partRes(it) }) { shelf = shelfOf(slot); picked = null }
                        }
                    }
                    Box(Modifier.weight(1f).height(654.dp).background(Brush.radialGradient(listOf(Color.White, Color(0xFFE3DEFF), Color(0xFFC9C3F5))), RoundedCornerShape(26.dp)), contentAlignment = Alignment.Center) {
                        val lv = work.integro.carling.game.LevelRules.of(data.totalGp)
                        if (riding && preview.shownStage.ordinal >= Stage.BABY.ordinal) KaringTurntable(preview, 500.dp, autoSpin = false, modifier = Modifier.padding(top = 70.dp))
                        else KaringView(preview.shownStage, if (riding) Pose.CAR else Pose.STAND, preview.personality, if (riding) preview.equipped else emptyMap(), height = 440.dp, carColor = preview.carColor, modifier = Modifier.padding(top = 90.dp))
                        Row(Modifier.align(Alignment.TopStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MiniToggle("차 타기", riding) { store.setRiding(true) }
                            MiniToggle("차 내리기", !riding) { store.setRiding(false) }
                        }
                        MiniToggle(if (bare) "장착 후 보기" else "장착 전 보기", bare, Modifier.align(Alignment.TopEnd).padding(12.dp)) { bare = !bare }
                        Text(
                            when {
                                bare -> "아무것도 달지 않은 모습이에요"
                                picked != null -> "미리보기 중 · '장착'을 누르면 바뀌어요"
                                riding && preview.shownStage.ordinal >= Stage.BABY.ordinal -> "좌우로 끌어서 돌려 보세요"
                                else -> ""
                            },
                            fontSize = 15.sp, color = Muted,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val color = data.carColor ?: CarColor.PEARL
                        EquipSlot("차체 색", color.label, "칠함", shelf == Shelf.COLOR, swatch = Color(color.light)) { shelf = Shelf.COLOR; picked = null }
                        EquipSlot("대표 배지", data.displayBadge?.label, data.displayBadge?.let { BadgeTier.of(data.badges[it] ?: 0)?.label }, shelf == Shelf.BADGE, badge = data.displayBadge) { shelf = Shelf.BADGE; picked = null }
                        EquipSlot("모습", data.shownStage.label, if (data.look != null) "바꿈" else null, shelf == Shelf.LOOK, stage = data.shownStage) { shelf = Shelf.LOOK; picked = null }
                    }
                }
                Spacer(Modifier.height(14.dp))
                // 정보
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val lv = work.integro.carling.game.LevelRules.of(data.totalGp)
                    LevelEmblem(lv, 84.dp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("${karingName(data.name, data.stage)} · ${data.stage.label}", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold)
                        Text("LV.$lv ${work.integro.carling.game.EmblemTier.of(lv).label} 엠블럼 · " + if (lv < work.integro.carling.game.LevelRules.MAX) "다음 레벨까지 %,d점".format(work.integro.carling.game.LevelRules.need(lv + 1) - data.totalGp) else "최고 레벨", fontSize = 16.sp, color = Muted)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("%,d점".format(data.totalGp), fontSize = 20.sp, color = Lilac, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    data.style?.let { Text("${it.icon} ${it.label}", fontSize = 17.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.background(Mint, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp)) }
                }
                Spacer(Modifier.height(6.dp))
                GrowthBar(data)
                Spacer(Modifier.height(8.dp))
                val p = data.personality
                Text("${p.vibe?.let { "${it.icon} ${it.eyewear}" } ?: "기본형"} · 대표 장식 ${p.decorationItem?.label ?: "없음"} · 심장빛 ${if (p.heartBright) "반짝" else "기본"}", fontSize = 17.sp, color = Muted)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Collect("꾸미기", data.parts.size, CarPart.entries.size, Modifier.weight(1f))
                    Collect("차체 색", data.colors.size + 1, CarColor.entries.size, Modifier.weight(1f))
                    Collect("배지", data.badges.size, Badge.entries.size, Modifier.weight(1f))
                    Collect("스타일", data.styles.size, CarStyle.entries.size, Modifier.weight(1f))
                }
            }
        }
        // ---- 오른쪽: 보관함 ----
        Panel(Modifier.weight(1f).fillMaxHeight(), padding = 20.dp) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Shelf.entries.forEach { s ->
                        val on = s == shelf
                        Text(s.label, fontSize = 17.sp, color = if (on) Color.White else Ink, fontWeight = FontWeight.Bold, maxLines = 1,
                            modifier = Modifier.background(if (on) Lilac else Paper, RoundedCornerShape(50)).clickable { shelf = s; picked = null }.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    when (shelf) {
                        Shelf.WHEEL, Shelf.ANTENNA, Shelf.REAR -> {
                            val slot = slotOf(shelf)!!
                            ItemGrid(CarPart.entries.filter { it.slot == slot }) { part ->
                                val n = data.parts[part] ?: 0
                                ItemCell(part.label, n, owned = n > 0, equipped = data.equipped[slot] == part, picked = picked == part, image = partRes(part)) { if (n > 0) picked = part }
                            }
                        }
                        Shelf.COLOR -> ItemGrid(CarColor.entries) { c ->
                            val owned = c == CarColor.PEARL || (data.colors[c] ?: 0) > 0
                            ItemCell(c.label, if (c == CarColor.PEARL) 1 else data.colors[c] ?: 0, owned, (data.carColor ?: CarColor.PEARL) == c, picked == c, swatch = Color(c.light),
                                lockedHint = if (c == CarColor.AURORA) "배지 6종" else "럭키박스") { if (owned) picked = c }
                        }
                        Shelf.BADGE -> ItemGrid(Badge.entries) { b ->
                            val n = data.badges[b] ?: 0
                            ItemCell(b.label, n, n > 0, data.displayBadge == b, picked == b, badge = b, lockedHint = b.how) { if (n > 0) picked = b }
                        }
                        Shelf.LOOK -> ItemGrid(Stage.entries.filter { it != Stage.CRACKED }) { s ->
                            val can = s == data.stage || (data.canChooseLook && s.ordinal in Stage.BABY.ordinal..data.stage.ordinal)
                            ItemCell(s.label, if (s.ordinal <= data.stage.ordinal) 1 else 0, can, data.shownStage == s, picked == s, stage = s,
                                lockedHint = if (!s.open) "추후 공개" else if (s.ordinal > data.stage.ordinal) "${"%,d".format(s.minGp)}점" else "꼬마링부터") { if (can) picked = s }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(detailOf(picked, data, shelf), fontSize = 16.sp, color = Ink, modifier = Modifier.fillMaxWidth().background(LilacSoft, RoundedCornerShape(12.dp)).padding(12.dp))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton("해제", Modifier.weight(1f), enabled = canTakeOff(shelf, data)) {
                        when (shelf) {
                            Shelf.WHEEL, Shelf.ANTENNA, Shelf.REAR -> store.unequip(slotOf(shelf)!!)
                            Shelf.COLOR -> store.paint(CarColor.PEARL)
                            Shelf.BADGE -> store.setDisplayBadge(null)
                            Shelf.LOOK -> store.chooseLook(data.stage)
                        }
                        picked = null
                    }
                    ActionButton("장착", Modifier.weight(1f), enabled = picked != null, primary = true) {
                        when (val x = picked) {
                            is CarPart -> store.equip(x)
                            is CarColor -> store.paint(x)
                            is Badge -> store.setDisplayBadge(x)
                            is Stage -> store.chooseLook(x)
                        }
                        picked = null
                    }
                    ActionButton("모두 해제", Modifier.weight(1f), enabled = data.equipped.isNotEmpty() || data.carColor != null) { store.unequipAll(); picked = null }
                }
            }
        }
    }
}

@Composable
private fun Collect(label: String, n: Int, total: Int, modifier: Modifier) {
    Column(modifier.background(Paper, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Row { Text(label, fontSize = 15.sp, color = Muted, modifier = Modifier.weight(1f)); Text("$n/$total", fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).background(LilacSoft, RoundedCornerShape(4.dp))) {
            Box(Modifier.fillMaxWidth(n.toFloat() / total.coerceAtLeast(1)).fillMaxHeight().background(Lilac, RoundedCornerShape(4.dp)))
        }
    }
}

private fun canTakeOff(s: Shelf, d: CarlingData): Boolean = when (s) {
    Shelf.WHEEL, Shelf.ANTENNA, Shelf.REAR -> slotOf(s) in d.equipped
    Shelf.COLOR -> d.carColor != null
    Shelf.BADGE -> d.displayBadge != null
    Shelf.LOOK -> d.look != null
}

private fun detailOf(x: Any?, d: CarlingData, s: Shelf): String = when (x) {
    is CarPart -> "${x.label} · ${d.parts[x] ?: 0}개" + (CarStyle.entries.firstOrNull { x in it.parts }?.let { st -> " · ${st.icon} ${st.label} 세트 ${st.parts.count { it in d.equipped.values || it == x }}/3" } ?: "")
    is CarColor -> "${x.label} 차체 색" + if (x == CarColor.AURORA) " · 배지 6종 세트 보상" else ""
    is Badge -> "${x.label} · ${BadgeTier.of(d.badges[x] ?: 0)?.label ?: ""} 배지 ${d.badges[x] ?: 0}개 · 홈 상태표에 대표로 보여요"
    is Stage -> "${x.label} 모습으로 바꿔요 · 성장 단계와 포인트는 그대로예요"
    else -> when (s) {
        Shelf.LOOK -> "꼬마링부터 지나온 모습 중 하나를 고를 수 있어요 · 버디링·소울링은 추후 공개"
        Shelf.BADGE -> "모은 배지 하나를 대표로 달아요"
        else -> "보관함에서 고르면 왼쪽에서 미리 볼 수 있어요"
    }
}

@Composable
private fun EquipSlot(title: String, value: String?, state: String?, on: Boolean, image: Int? = null, swatch: Color? = null, badge: Badge? = null, stage: Stage? = null, onClick: () -> Unit) {
    Column(
        Modifier.width(150.dp).height(210.dp)
            .background(if (value != null) LilacSoft else Paper, RoundedCornerShape(18.dp))
            .border(if (on) 3.dp else 1.5.dp, if (on) Lilac else if (value != null) Color(0xFFAFA9EC) else Line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, fontSize = 13.sp, color = Muted, modifier = Modifier.weight(1f), maxLines = 1)
            state?.let { Text(it, fontSize = 12.sp, color = Color(0xFF1F9C74), fontWeight = FontWeight.Bold) }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            when {
                image != null -> Image(painterResource(image), null, Modifier.size(90.dp))
                swatch != null -> Box(Modifier.size(56.dp).background(swatch, CircleShape).border(2.dp, Color.White, CircleShape))
                badge != null -> Medal(badge, BadgeTier.BRONZE, 74.dp)
                stage != null -> StageThumb(stage, 70.dp)
                else -> Text("＋", fontSize = 30.sp, color = Faint)
            }
        }
        Text(value ?: "비어 있음", fontSize = 15.sp, color = if (value != null) Ink else Faint, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun MiniToggle(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(label, fontSize = 16.sp, color = if (on) Color.White else Ink, fontWeight = FontWeight.Bold,
        modifier = modifier.background(if (on) Mint else Color.White.copy(alpha = 0.92f), RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp))
}

@Composable
private fun <T> ItemGrid(items: List<T>, cell: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { Box(Modifier.weight(1f)) { cell(it) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ItemCell(
    label: String, count: Int, owned: Boolean, equipped: Boolean, picked: Boolean,
    image: Int? = null, swatch: Color? = null, badge: Badge? = null, stage: Stage? = null, lockedHint: String = "럭키박스",
    onClick: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().aspectRatio(1.05f)
            .background(if (owned) Color.White else Paper, RoundedCornerShape(18.dp))
            .border(if (picked) 3.dp else 1.dp, if (picked) Lilac else if (equipped) Mint else Line, RoundedCornerShape(18.dp))
            .clickable(enabled = owned, onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth()) {
            if (equipped) Text("✓ 장착중", fontSize = 12.sp, color = Color(0xFF1F9C74), fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (owned && count > 1) Text("×$count", fontSize = 13.sp, color = Muted, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            val a = if (owned) 1f else 0.25f
            when {
                image != null -> Image(painterResource(image), null, Modifier.size(84.dp), alpha = a)
                swatch != null -> Box(Modifier.size(62.dp).background(swatch.copy(alpha = if (owned) 1f else 0.35f), CircleShape).border(2.dp, Line, CircleShape))
                badge != null -> Medal(badge, if (owned) BadgeTier.BRONZE else null, 84.dp)
                stage != null -> StageThumb(stage, 80.dp, dim = !owned)
            }
            if (!owned) Text("🔒", fontSize = 24.sp)
        }
        Text(label, fontSize = 15.sp, color = if (owned) Ink else Faint, fontWeight = FontWeight.Bold, maxLines = 1)
        if (!owned) Text(lockedHint, fontSize = 12.sp, color = Faint, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ActionButton(label: String, modifier: Modifier, enabled: Boolean, primary: Boolean = false, onClick: () -> Unit) {
    Text(
        label, fontSize = 21.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold,
        color = when { !enabled -> Faint; primary -> Color.White; else -> Ink },
        modifier = modifier.background(when { !enabled -> Paper; primary -> Lilac; else -> Color.White }, RoundedCornerShape(16.dp))
            .border(1.dp, if (primary && enabled) Lilac else Line, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp),
    )
}
