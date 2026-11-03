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
import work.integro.carling.game.Region

@Composable
private fun CurrentLook(data: CarlingData, personality: Personality) {
    CarlingDrawing(
        size = 240.dp,
        stageIndex = data.reachedStage,
        glow = data.glowColor(),
        item = data.equippedItem,
        mood = personality,
        lineage = data.lineage,
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

/** 아이템 고르기. 한 번에 하나만 착용하고, 같은 아이템을 다시 누르면 벗는다. */
@Composable
fun ItemsDialog(data: CarlingData, personality: Personality, onSelect: (CarlingItem?) -> Unit, onClose: () -> Unit) {
    val wearable = canWearItem(data.reachedStage, data.lineage, data.equippedItem)
    Scrim {
        Text("아이템", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(6.dp))
        CurrentLook(data, personality)
        if (!wearable) {
            Text(
                "지금 모습에는 이 아이템이 보이지 않아요.",
                fontSize = 19.sp, color = Muted, textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(14.dp))
        CarlingItem.entries.chunked(5).forEach { row ->
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
        Spacer(Modifier.height(10.dp))
        Button(onClick = onClose, modifier = Modifier.height(60.dp).width(200.dp)) { Text("닫기", fontSize = 24.sp) }
    }
}

/** 지역 특산물. 그 지역을 방문하면 받고, 배고플 때 먹이면 보너스 경험치를 준다. */
@Composable
fun FoodsDialog(data: CarlingData, onEat: (Region) -> Unit, onClose: () -> Unit) {
    val hungry = GrowthRules.isHungry(data.fullness)
    Scrim {
        Text("지역 특산물", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            if (hungry) "배가 고파요! 특산물을 먹이면 경험치가 더 많이 올라요."
            else "포만감이 ${GrowthRules.HUNGRY_FULLNESS} 이하일 때 먹일 수 있어요. 지금 포만감 ${data.fullness}",
            fontSize = 19.sp, color = if (hungry) Gold else Muted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Region.entries.forEach { region ->
            val count = data.foods[region] ?: 0
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).background(PanelLight, RoundedCornerShape(18.dp)).padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (count > 0) region.foodIcon else "🔒", fontSize = 36.sp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(region.foodName, fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Medium)
                    Text("${region.label} 방문 · 먹이면 +${region.bonusXp} XP", fontSize = 16.sp, color = Muted)
                }
                Text("${count}개", fontSize = 20.sp, color = Gold, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(14.dp))
                Button(onClick = { onEat(region) }, enabled = hungry && count > 0, modifier = Modifier.height(52.dp)) { Text("먹이기", fontSize = 19.sp) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Button(onClick = onClose, modifier = Modifier.height(60.dp).width(200.dp)) { Text("닫기", fontSize = 24.sp) }
    }
}
