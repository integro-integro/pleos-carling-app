package work.integro.carling.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.data.CarlingData
import work.integro.carling.game.Badge
import work.integro.carling.game.BadgeRules
import work.integro.carling.game.BadgeTier
import work.integro.carling.game.CarColor

/**
 * 배지 진열장(사용자 확정 11/12). 배지는 럭키박스에서 그 여정에 맞는 것으로 나온다(아침에 달렸으면 아침 햇살 …).
 * 같은 배지를 더 받으면 동 → 은(3개) → 금(5개)으로 테두리가 바뀌고, 6종을 다 모으면 오로라 차체 색을 받는다.
 */
@Composable
fun BadgeShelfDialog(data: CarlingData, onPaint: (CarColor) -> Unit, onClose: () -> Unit) {
    val owned = Badge.entries.count { (data.badges[it] ?: 0) > 0 }
    val set = BadgeRules.SET_REWARD
    val hasSet = (data.colors[set] ?: 0) > 0
    FullScrim {
        Panel(Modifier.width(1240.dp), padding = 30.dp) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("배지 진열장 · $owned/${Badge.entries.size}", fontSize = 30.sp, color = Ink, fontWeight = FontWeight.Bold)
                        Text("배지는 럭키박스에서 그 여정에 어울리는 것으로 나와요. 같은 배지를 또 받으면 동 → 은(3개) → 금(5개)으로 빛나요.", fontSize = 18.sp, color = Muted)
                    }
                    Spacer(Modifier.width(20.dp))
                    SetRewardCard(owned, hasSet, set, onPaint)
                }
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Badge.entries.forEach { b -> BadgeTile(b, data.badges[b] ?: 0, Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Lilac), modifier = Modifier.height(60.dp)) { Text("닫기", fontSize = 22.sp) }
                }
            }
        }
    }
}

/** 배지 한 칸: 메달, 이름, 등급과 다음 등급까지, 얻는 법. */
@Composable
private fun BadgeTile(b: Badge, count: Int, modifier: Modifier) {
    val tier = BadgeTier.of(count)
    Column(
        modifier.background(if (tier != null) Color.White else Paper, RoundedCornerShape(22.dp)).border(1.dp, Line, RoundedCornerShape(22.dp)).padding(vertical = 18.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Medal(b, tier, 130.dp)
        Spacer(Modifier.height(10.dp))
        Text(b.label, fontSize = 21.sp, color = if (tier != null) Ink else Faint, fontWeight = FontWeight.Bold)
        Text(
            tier?.let { "${it.label} 배지 · ${count}개" } ?: "아직 없어요",
            fontSize = 16.sp, color = tier?.let { Color(it.rim).copy(alpha = 1f).let { c -> if (it == BadgeTier.SILVER) Muted else c } } ?: Faint, fontWeight = FontWeight.Bold,
        )
        // 다음 등급까지
        val next = if (tier == null) BadgeTier.BRONZE else tier.next
        Spacer(Modifier.height(8.dp))
        if (next != null) {
            val from = tier?.min ?: 0
            Box(Modifier.fillMaxWidth(0.8f).height(10.dp).background(LilacSoft, RoundedCornerShape(5.dp))) {
                Box(Modifier.fillMaxWidth(((count - from).toFloat() / (next.min - from)).coerceIn(0f, 1f)).fillMaxHeight().background(Lilac, RoundedCornerShape(5.dp)))
            }
            Text("${next.label}까지 ${next.min - count}개", fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
        } else Text("최고 등급!", fontSize = 14.sp, color = Honey, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(8.dp))
        Text("얻는 법: ${b.how}", fontSize = 14.sp, color = Muted, textAlign = TextAlign.Center)
    }
}

/** 메달: 리본 두 갈래 + 등급색 테두리 + 아이콘. 못 모은 배지는 회색 실루엣과 물음표. */
@Composable
fun Medal(b: Badge, tier: BadgeTier?, size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension * 0.36f
            val c = Offset(this.size.width / 2, this.size.height * 0.42f)
            val rim = tier?.let { Color(it.rim) } ?: Color(0xFFDCDBEA)
            // 리본
            val ribbon = if (tier != null) listOf(Lilac, Mint) else listOf(Color(0xFFE6E5F0), Color(0xFFE6E5F0))
            listOf(-1f, 1f).forEachIndexed { i, side ->
                val p = Path().apply {
                    moveTo(c.x + side * r * 0.15f, c.y + r * 0.6f)
                    lineTo(c.x + side * r * 0.75f, c.y + r * 1.55f)
                    lineTo(c.x + side * r * 0.45f, c.y + r * 1.4f)
                    lineTo(c.x + side * r * 0.3f, c.y + r * 1.65f)
                    lineTo(c.x - side * r * 0.15f, c.y + r * 0.7f)
                    close()
                }
                drawPath(p, ribbon[i])
            }
            drawCircle(Brush.radialGradient(listOf(rim.copy(alpha = 0.95f), rim), c, r * 1.1f), r * 1.08f, c)
            drawCircle(if (tier != null) Color.White else Color(0xFFF1F0F7), r * 0.86f, c)
            drawCircle(rim.copy(alpha = 0.6f), r * 0.86f, c, style = Stroke(r * 0.05f))
            if (tier == BadgeTier.GOLD) {
                // 금 배지는 별 반짝이
                listOf(Offset(-1.05f, -0.9f), Offset(1.0f, -1.0f), Offset(1.15f, 0.2f)).forEach { o ->
                    drawCircle(Color(0xFFFFF3C4), r * 0.09f, Offset(c.x + o.x * r, c.y + o.y * r))
                }
            }
        }
        Text(
            if (tier != null) b.icon else "?",
            fontSize = (size.value * 0.28f).sp,
            color = Faint,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = size * 0.16f),
        )
    }
}

/** 세트 보상 카드: 6종을 모으면 오로라 차체 색. */
@Composable
private fun SetRewardCard(owned: Int, hasSet: Boolean, set: CarColor, onPaint: (CarColor) -> Unit) {
    Row(
        Modifier.background(if (hasSet) MintSoft else LilacSoft, RoundedCornerShape(22.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).background(Brush.sweepGradient(listOf(Color(set.light), Color(0xFFB9A8FF), Color(0xFF8FE3D0), Color(set.light))), CircleShape).border(3.dp, Color.White, CircleShape))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(if (hasSet) "세트 보상을 받았어요" else "세트 보상 · $owned/${Badge.entries.size}", fontSize = 19.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text("6종을 모두 모으면 ${set.label} 차체 색", fontSize = 15.sp, color = Muted)
            if (hasSet) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { onPaint(set) }, modifier = Modifier.height(44.dp)) { Text("${set.label}으로 칠하기", fontSize = 16.sp) }
            }
        }
    }
}

/** 6종을 처음 다 모았을 때 한 번 뜨는 안내. */
@Composable
fun SetRewardDialog(onPaint: () -> Unit, onClose: () -> Unit) {
    FullScrim {
        Panel(Modifier.width(720.dp), padding = 32.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Badge.entries.forEach { Medal(it, BadgeTier.BRONZE, 84.dp) } }
                Spacer(Modifier.height(14.dp))
                Text("배지 6종을 모두 모았어요!", fontSize = 30.sp, color = Ink, fontWeight = FontWeight.Bold)
                Text("세트 보상으로 특별한 ${BadgeRules.SET_REWARD.label} 차체 색을 받았어요.", fontSize = 20.sp, color = Muted)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onClose, modifier = Modifier.height(60.dp)) { Text("나중에", fontSize = 21.sp) }
                    Button(onClick = onPaint, colors = ButtonDefaults.buttonColors(containerColor = Lilac), modifier = Modifier.height(60.dp)) { Text("바로 칠하기", fontSize = 21.sp) }
                }
            }
        }
    }
}
