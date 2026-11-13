package work.integro.carling.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.data.GrowthCompare
import work.integro.carling.data.KaringAt

/** 성장 ①: 지난 기간 끝 → 이번 기간 끝의 카링을 나란히. 달라진 줄에 표시. */
@Composable
internal fun ChangeBlock(g: GrowthCompare) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            val changed = rows(g.before, g.now).count { it.second != it.third }
            BlockTitle("①", "카링이 어떻게 바뀌었나", "${g.prevLabel} → ${g.nowLabel} · ${changed}가지 달라졌어요")
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    StageThumb(g.before.stage, 96.dp, dim = true)
                    Text("${g.prevLabel} · ${g.before.stage.label}", fontSize = 16.sp, color = Muted)
                }
                Text("→", fontSize = 40.sp, color = Lilac, fontWeight = FontWeight.Bold)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    StageThumb(g.now.stage, 110.dp)
                    Text("${g.nowLabel} · ${g.now.stage.label}", fontSize = 17.sp, color = Ink, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(10.dp))
            rows(g.before, g.now).forEach { (label, a, b, delta) ->
                val diff = a != b
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(if (diff) MintSoft else Paper, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 16.sp, color = Muted, modifier = Modifier.width(150.dp))
                    Text(a, fontSize = 17.sp, color = Muted, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("→", fontSize = 16.sp, color = Faint, modifier = Modifier.padding(horizontal = 8.dp))
                    Text(b, fontSize = 17.sp, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(delta ?: if (diff) "바뀜" else "그대로", fontSize = 15.sp, color = if (diff) Color(0xFF1F9C74) else Faint, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(70.dp))
                }
            }
        }
    }
}

private fun rows(a: KaringAt, b: KaringAt): List<Quad> {
    fun plus(x: Int, y: Int) = if (y > x) "+${"%,d".format(y - x)}" else null
    return listOf(
        Quad("성장 단계", a.stage.label, b.stage.label, null),
        Quad("성장 포인트", "%,d".format(a.gp), "%,d".format(b.gp), plus(a.gp, b.gp)),
        Quad("낮길·밤길", a.personality.vibe?.let { "${it.icon} ${it.label}" } ?: "기본", b.personality.vibe?.let { "${it.icon} ${it.label}" } ?: "기본", null),
        Quad("대표 장식", a.personality.decorationItem?.label ?: "없음", b.personality.decorationItem?.label ?: "없음", null),
        Quad("심장빛", if (a.personality.heartBright) "반짝" else "기본", if (b.personality.heartBright) "반짝" else "기본", null),
        Quad("완성한 카드", "${a.cards}장", "${b.cards}장", plus(a.cards, b.cards)),
        Quad("모은 꾸미기·색", "${a.parts} · ${a.colors}", "${b.parts} · ${b.colors}", plus(a.parts + a.colors, b.parts + b.colors)),
        Quad("모은 배지", "${a.badges}종", "${b.badges}종", plus(a.badges, b.badges)),
        Quad("할 줄 아는 한마디", "${a.talks}가지", "${b.talks}가지", plus(a.talks, b.talks)),
    )
}

private data class Quad(val first: String, val second: String, val third: String, val fourth: String?)

/** 성장 ②: 얼마나 똑똑해졌나. 목적 맞히기 정확도와 알게 된 습관. */
@Composable
internal fun SmartBlock(g: GrowthCompare) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            val now = g.smartScore(g.accuracy, g.habits.size)
            val before = g.smartScore(g.prevAccuracy, g.habits.size - g.newHabits.size)
            BlockTitle("②", "얼마나 똑똑해졌나", "똑똑함 $before → ${now}점" + if (now > before) " (+${now - before})" else "")
            Spacer(Modifier.height(12.dp))
            Text("목적 맞히기 · 카링이 추천한 목적을 그대로 고른 비율", fontSize = 16.sp, color = Muted)
            Spacer(Modifier.height(6.dp))
            AccBar(g.prevLabel, g.prevAccuracy.percent, g.prevAccuracy.total, Faint)
            AccBar(g.nowLabel, g.accuracy.percent, g.accuracy.total, Lilac)
            val a = g.accuracy.percent
            val b = g.prevAccuracy.percent
            Text(
                when {
                    a == null -> "아직 추천할 만큼 기록이 없어요. 비슷한 시간에 같은 목적을 3번 고르면 추천을 시작해요."
                    b == null -> "${g.nowLabel}부터 목적을 맞히기 시작했어요."
                    a > b -> "${g.prevLabel}보다 ${a - b}%p 더 잘 맞혀요!"
                    a < b -> "${g.prevLabel}보다 ${b - a}%p 덜 맞혔어요. 새로운 여정이 많았나 봐요."
                    else -> "${g.prevLabel}와 같은 만큼 맞혔어요."
                },
                fontSize = 17.sp, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text("알게 된 습관 ${g.habits.size}가지" + if (g.newHabits.isNotEmpty()) " · 새로 ${g.newHabits.size}가지" else "", fontSize = 16.sp, color = Muted)
            Spacer(Modifier.height(6.dp))
            if (g.habits.isEmpty()) Text("카드를 더 모으면 카링이 내 습관을 알아 가요.", fontSize = 16.sp, color = Faint)
            g.habits.forEach { h ->
                val fresh = h.key in g.newHabits
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(if (fresh) LilacSoft else Paper, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(h.icon, fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(h.title, fontSize = 16.sp, color = Muted, modifier = Modifier.width(140.dp))
                    Text(h.detail, fontSize = 17.sp, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                    if (fresh) Text("NEW", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.background(Lilac, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun AccBar(label: String, pct: Int?, total: Int, color: Color) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 16.sp, color = Muted, modifier = Modifier.width(110.dp), maxLines = 1)
        Box(Modifier.weight(1f).height(18.dp).background(Paper, RoundedCornerShape(9.dp))) {
            if ((pct ?: 0) > 0) Box(Modifier.fillMaxWidth(pct!! / 100f).fillMaxHeight().background(color, RoundedCornerShape(9.dp)))
        }
        Text(pct?.let { "$it% · ${total}장" } ?: "—", fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(110.dp))
    }
}

/** 성장 ③: 어떤 기능이 생겼나. 이번 기간에 열린 것 먼저, 다음에 열릴 것과 여는 방법. */
@Composable
internal fun AbilityBlock(g: GrowthCompare, talkCounts: Map<work.integro.carling.game.Talk, Int>) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            val open = g.abilities.filter { it.unlocked }
            BlockTitle("③", "어떤 기능이 생겼나", "열린 기능 ${open.size}/${g.abilities.size}" + if (g.newAbilities.isNotEmpty()) " · ${g.nowLabel} 새로 ${g.newAbilities.size}개" else "")
            Spacer(Modifier.height(10.dp))
            val sorted = open.sortedByDescending { it.key in g.newAbilities } + g.abilities.filter { !it.unlocked }
            sorted.forEach { a ->
                val fresh = a.key in g.newAbilities
                val said = a.key.removePrefix("talk_").let { k -> work.integro.carling.game.Talk.entries.firstOrNull { it.name == k } }?.let { talkCounts[it] }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        .background(when { fresh -> MintSoft; a.unlocked -> Paper; else -> Color.Transparent }, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(34.dp).background(if (a.unlocked) Color.White else Paper, CircleShape), contentAlignment = Alignment.Center) { Text(if (a.unlocked) a.icon else "🔒", fontSize = 18.sp) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.title, fontSize = 17.sp, color = if (a.unlocked) Ink else Faint, fontWeight = FontWeight.Bold, maxLines = 1)
                        if (!a.unlocked) Text(a.how, fontSize = 14.sp, color = Faint)
                        else if (said != null) Text("주행 중 ${said}번 말했어요", fontSize = 14.sp, color = Muted)
                    }
                    if (fresh) Text("NEW", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.background(Mint, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
    }
}
