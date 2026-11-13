package work.integro.carling.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.game.PeriodUnit
import work.integro.carling.game.ReportPeriod
import java.time.LocalDate

/** 화면을 오가도 기간이 남도록 단위·오프셋을 따로 저장한다. */
@Composable
fun rememberPeriod(unit: PeriodUnit = PeriodUnit.MONTH): MutableState<ReportPeriod> {
    var u by rememberSaveable { mutableIntStateOf(unit.ordinal) }
    var o by rememberSaveable { mutableIntStateOf(0) }
    val state = remember { mutableStateOf(ReportPeriod(PeriodUnit.entries[u], o)) }
    // 저장값과 맞춘다
    if (state.value.unit.ordinal != u || state.value.offset != o) { u = state.value.unit.ordinal; o = state.value.offset }
    return state
}

/** ‹ 이번 달 · 11.1–11.12 › 와 주·월·분기·년 단위 칩. */
@Composable
fun PeriodBar(period: ReportPeriod, today: LocalDate, onChange: (ReportPeriod) -> Unit, modifier: Modifier = Modifier) {
    val (from, to) = period.range(today)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(50)).border(1.dp, Line, RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Arrow("‹") { onChange(period.shift(-1)) }
            Text(
                "${period.label(today)} · ${from.monthValue}.${from.dayOfMonth}–${to.monthValue}.${to.dayOfMonth}",
                fontSize = 19.sp, color = Ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 220.dp),
            )
            Arrow("›", enabled = period.offset < 0) { onChange(period.shift(1)) }
        }
        Spacer(Modifier.width(12.dp))
        Row(Modifier.background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(50)).border(1.dp, Line, RoundedCornerShape(50)).padding(4.dp)) {
            PeriodUnit.entries.forEach { u ->
                val on = u == period.unit
                Text(
                    u.label, fontSize = 19.sp, color = if (on) Color.White else Ink, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(if (on) Lilac else Color.Transparent, RoundedCornerShape(50))
                        .clickable { onChange(ReportPeriod(u)) }.padding(horizontal = 18.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun Arrow(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text, fontSize = 28.sp, color = if (enabled) Lilac else Faint, fontWeight = FontWeight.Bold,
        modifier = Modifier.then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 14.dp),
    )
}
