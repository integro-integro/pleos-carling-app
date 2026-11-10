package work.integro.carling.ui

import androidx.compose.foundation.Image
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import work.integro.carling.game.Vehicle
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.data.CarlingData
import work.integro.carling.data.Personality
import work.integro.carling.game.CarlingItem
import work.integro.carling.game.GrowthRules
import work.integro.carling.game.UsableItem
import androidx.compose.ui.graphics.Color

@Composable
private fun CurrentLook(data: CarlingData, personality: Personality) {
    CarlingDrawing(
        size = 240.dp,
        stageIndex = data.reachedStage,
        glow = data.glowColor(),
        item = data.equippedItem,
        mood = personality,
        lineage = data.lineage,
        vehicle = data.ridingVehicle,
    )
}

/** 성장 1에 처음 닿았을 때 한 번 보여주는 안내. */
@Composable
fun ItemUnlockDialog(data: CarlingData, personality: Personality, onOpenItems: () -> Unit, onLater: () -> Unit) {
    Scrim {
        Text("아이템이 열렸어요!", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Gold)
        Spacer(Modifier.height(8.dp))
        CurrentLook(data, personality)
        Speech("이제 꾸밀 수 있어요! 뭐가 어울릴까요?")
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedButton(onClick = onLater, modifier = Modifier.height(64.dp).width(200.dp)) { Text("나중에", fontSize = 24.sp) }
            Button(onClick = onOpenItems, modifier = Modifier.height(64.dp).width(240.dp)) { Text("아이템 보기", fontSize = 24.sp) }
        }
    }
}

private enum class ItemTab(val label: String) { DECORATE("🎀 꾸미기"), RIDE("🚗 타기"), USE("🍙 사용하기") }

/**
 * 아이템 — 꾸미기(한 번에 하나 착용, 다시 누르면 벗기)와 타기(모은 이동수단에 타고 내리기).
 */
@Composable
fun ItemsDialog(
    data: CarlingData,
    personality: Personality,
    onSelect: (CarlingItem?) -> Unit,
    onRide: (Vehicle?) -> Unit,
    onPickVehicle: (Vehicle) -> Unit,
    onUse: (UsableItem) -> Unit,
    onClose: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(if (data.canPickVehicle) ItemTab.RIDE else ItemTab.DECORATE) }
    Scrim {
        Text("아이템", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(6.dp))
        CurrentLook(data, personality)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ItemTab.entries.forEach { t ->
                FilterChip(
                    selected = tab == t,
                    onClick = { tab = t },
                    label = {
                        Text(t.label + when (t) {
                            ItemTab.RIDE -> " ${data.ownedVehicles.size}/${Vehicle.entries.size}"
                            ItemTab.USE -> " ${data.usables.values.sum() + data.coupons.size}"
                            else -> ""
                        }, fontSize = 19.sp)
                    },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            ItemTab.DECORATE -> DecorateTab(data, onSelect)
            ItemTab.RIDE -> RideTab(data, onRide, onPickVehicle)
            ItemTab.USE -> UseTab(data, onUse)
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = onClose, modifier = Modifier.height(60.dp).width(200.dp)) { Text("닫기", fontSize = 24.sp) }
    }
}

@Composable
private fun DecorateTab(data: CarlingData, onSelect: (CarlingItem?) -> Unit) {
    if (!GrowthRules.itemsUnlocked(data.reachedStage)) {
        Text("🔒 7운전일에 자라면 꾸미기 아이템이 열려요.", fontSize = 19.sp, color = Muted, textAlign = TextAlign.Center)
        return
    }
    if (data.equippedItem != null && !canWearItem(data.reachedStage, data.lineage, data.equippedItem)) {
        Text("지금 모습에는 이 아이템이 보이지 않아요.", fontSize = 19.sp, color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
    }
    CarlingItem.shown.chunked(5).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            row.forEach { item ->
                val selected = data.equippedItem == item
                val shape = RoundedCornerShape(18.dp)
                Surface(
                    onClick = { onSelect(if (selected) null else item) },
                    shape = shape,
                    color = PanelLight,
                    modifier = Modifier.weight(1f).border(if (selected) 4.dp else 1.dp, if (selected) Gold else Accent.copy(alpha = 0.35f), shape),
                ) {
                    Column(Modifier.padding(vertical = 10.dp, horizontal = 2.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(item.icon, fontSize = 34.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(item.label, fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(if (selected) "착용 중" else "착용", fontSize = 13.sp, color = if (selected) Gold else Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun RideTab(data: CarlingData, onRide: (Vehicle?) -> Unit, onPickVehicle: (Vehicle) -> Unit) {
    Text(
        when {
            data.canPickVehicle -> "🎁 진화 3 선물! 갖고 싶은 이동수단 하나를 골라 받으세요."
            data.ownedVehicles.isEmpty() -> "진화 2가 되면 내 계열의 이동수단을 선물로 받아요."
            else -> "누르면 타고, 다시 누르면 내려요."
        },
        fontSize = 18.sp, color = if (data.canPickVehicle) Gold else Muted, textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(10.dp))
    Vehicle.entries.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            row.forEach { v ->
                val owned = v in data.ownedVehicles
                val riding = data.ridingVehicle == v
                val pickable = !owned && data.canPickVehicle
                val shape = RoundedCornerShape(18.dp)
                Surface(
                    onClick = {
                        when {
                            owned -> onRide(if (riding) null else v)
                            pickable -> onPickVehicle(v)
                        }
                    },
                    enabled = owned || pickable,
                    shape = shape,
                    color = PanelLight,
                    modifier = Modifier.weight(1f).border(if (riding || pickable) 3.dp else 1.dp, if (riding || pickable) Gold else Accent.copy(alpha = 0.35f), shape),
                ) {
                    Column(Modifier.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painterResource(vehicleArt(v).res),
                            contentDescription = v.label,
                            modifier = Modifier.height(110.dp).fillMaxWidth().alpha(if (owned || pickable) 1f else 0.3f),
                        )
                        Text(v.label, fontSize = 17.sp, color = Ink, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(
                            when {
                                riding -> "타는 중 · 누르면 내리기"
                                owned -> "타기"
                                pickable -> "선물로 받기"
                                v.lineage == data.lineage -> "🔒 진화 2 선물"
                                else -> "🔒 ${v.lineage.label} 계열 · 진화 3 선물"
                            },
                            fontSize = 13.sp, color = if (riding || pickable) Gold else Muted, maxLines = 1,
                        )
                    }
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** 사용하기 — 트립카드를 저장하며 받은 간식. 누르면 하나 먹인다. */
@Composable
private fun UseTab(data: CarlingData, onUse: (UsableItem) -> Unit) {
    Text(
        "트립카드를 저장할 때마다 간식을 하나씩 받아요. 먹이면 배가 부르고, 오늘 첫 밥이면 +${GrowthRules.INTIMACY_XP_EACH} XP예요. 지금 포만감 ${data.fullness}",
        fontSize = 18.sp, color = Muted, textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        UsableItem.entries.forEach { item ->
            val count = data.usables[item] ?: 0
            val shape = RoundedCornerShape(18.dp)
            Surface(
                onClick = { onUse(item) },
                enabled = count > 0 && data.fullness < 100,
                shape = shape,
                color = PanelLight,
                modifier = Modifier.weight(1f).border(1.dp, Accent.copy(alpha = 0.35f), shape),
            ) {
                Column(Modifier.padding(vertical = 10.dp, horizontal = 2.dp).fillMaxWidth().alpha(if (count > 0) 1f else 0.35f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(item.icon, fontSize = 34.sp)
                    Text(item.label, fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text("×$count · 포만감 +${item.fullness}", fontSize = 13.sp, color = if (count > 0) Gold else Muted)
                }
            }
        }
    }
    if (data.fullness >= 100) Text("지금은 배가 불러서 못 먹어요.", fontSize = 17.sp, color = Muted)
    Spacer(Modifier.height(14.dp))
    Text("🎫 정비 쿠폰 ${data.coupons.size}장", fontSize = 21.sp, color = Ink, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    if (data.coupons.isEmpty()) Text("차를 돌봐 주면 카링이 가끔 정비 쿠폰을 선물해요.", fontSize = 16.sp, color = Muted)
    data.coupons.asReversed().forEach { CouponCard(it, Modifier.padding(bottom = 8.dp)) }
}
