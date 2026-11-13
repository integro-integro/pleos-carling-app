package work.integro.carling.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.game.Insight
import work.integro.carling.game.InsightKind

private fun tint(k: InsightKind): Pair<Color, Color> = when (k) {
    InsightKind.DEPART -> Color(0xFFE6F1FB) to Color(0xFF185FA5)
    InsightKind.HABIT_MONEY -> Color(0xFFE1F5EE) to Color(0xFF0F6E56)
    InsightKind.COST -> Color(0xFFFFF3D6) to Color(0xFF854F0B)
    InsightKind.SAFETY -> Color(0xFFEEEDFE) to Color(0xFF3C3489)
    InsightKind.LIFE -> Color(0xFFFBEAF0) to Color(0xFF993556)
    InsightKind.UPCOMING -> Color(0xFFF4F3FA) to Color(0xFF767BA6)
}

/**
 * 운행 리포트 맨 위 "꼭 알아야 할 3가지"(사용자 확정 11/13). 카드마다 제목(몰랐던 사실) · 큰 숫자(돈·시간) ·
 * 근거 한 줄 · 할 일 하나 · 카링이 본 기록. 대부분은 이 세 장만 보고 닫아도 가치가 있어야 한다.
 */
@Composable
internal fun InsightCards(label: String, insights: List<Insight>, saved: java.time.LocalDate? = null) {
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.linearGradient(listOf(Color(0xFF8C7CF0), Color(0xFF6FB6FF))), RoundedCornerShape(28.dp))
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$label, 꼭 알아야 할 ${insights.count { it.kind != InsightKind.UPCOMING }.coerceAtLeast(1)}가지", fontSize = 26.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text(saved?.let { "${it.monthValue}월 ${it.dayOfMonth}일에 만들어 저장한 리포트예요" } ?: "카링이 내 운행에서 찾았어요", fontSize = 17.sp, color = Color.White.copy(alpha = 0.85f))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            insights.forEach { InsightCard(it, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun InsightCard(i: Insight, modifier: Modifier) {
    val (bg, ink) = tint(i.kind)
    val soon = i.kind == InsightKind.UPCOMING
    Column(
        modifier.heightIn(min = 300.dp).background(Color.White, RoundedCornerShape(22.dp))
            .then(if (soon) Modifier.border(2.dp, Color(0xFFDCDBEA), RoundedCornerShape(22.dp)) else Modifier)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(i.kind.icon, fontSize = 26.sp, modifier = Modifier.background(bg, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (soon) "곧 알려 줄게요" else when (i.kind) {
                InsightKind.DEPART -> "시간"
                InsightKind.HABIT_MONEY -> "습관 → 돈"
                InsightKind.COST -> "비용"
                InsightKind.SAFETY -> "안전"
                InsightKind.LIFE -> "생활"
                else -> ""
            }, fontSize = 16.sp, color = ink, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text(i.title, fontSize = 21.sp, color = if (soon) Muted else Ink, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
        Spacer(Modifier.height(8.dp))
        Text(i.big, fontSize = if (soon) 24.sp else 30.sp, color = ink, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text(i.detail, fontSize = 16.sp, color = Muted, lineHeight = 23.sp)
        Spacer(Modifier.weight(1f, fill = false))
        Spacer(Modifier.height(12.dp))
        Text("💡 ${i.action}", fontSize = 17.sp, color = ink, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().background(bg, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 9.dp))
        Text(i.basis, fontSize = 13.sp, color = Faint, modifier = Modifier.padding(top = 6.dp))
    }
}
