package work.integro.carling.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.game.BenefitKind
import work.integro.carling.game.BenefitMatch

private val AdInk = Color(0xFF854F0B)
private val AdBg = Color(0xFFFFF3D6)

/**
 * 생활반경 혜택 카드(기획안 4.15): 리포트 '꼭 알아야 할 3가지' 아래 기간당 1장. 늘 '광고 · 예시 제휴' 표시.
 * [consent]가 null이면 선택 동의를 묻고, false면 다시 켜는 작은 칩만, true면 맞는 혜택(없으면 한 줄).
 */
@Composable
internal fun BenefitCard(consent: Boolean?, match: BenefitMatch?, onConsent: (Boolean) -> Unit, onSnooze: (String) -> Unit, onUse: () -> Unit) {
    when (consent) {
        null -> Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(24.dp)).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🏷️ 내 생활반경 혜택 (선택)", fontSize = 21.sp, color = Ink, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                AdTag()
            }
            Text(
                "자주 가는 동네와 충전·운행 습관으로 충전·주유·주차·세차·정비 혜택을 골라 드려요. 위치와 기록은 이 차 안에서만 쓰고 밖으로 보내지 않아요. 지금은 예시 제휴예요.",
                fontSize = 16.sp, color = Muted, modifier = Modifier.padding(top = 6.dp),
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallButton("혜택 받아 보기", primary = true) { onConsent(true) }
                SmallButton("괜찮아요") { onConsent(false) }
            }
        }
        false -> Text("🏷️ 생활반경 혜택 켜기", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold,
            modifier = Modifier.background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(50)).clickable { onConsent(true) }.padding(horizontal = 14.dp, vertical = 5.dp))
        true -> if (match == null) {
            Text("🏷️ 지금 내 기록에 맞는 혜택이 없어요 · 혜택 끄기", fontSize = 15.sp, color = Color.White,
                modifier = Modifier.background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(50)).clickable { onConsent(false) }.padding(horizontal = 14.dp, vertical = 5.dp))
        } else {
            var used by remember(match.offer.id) { mutableStateOf(false) }
            val o = match.offer
            Row(
                Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(24.dp)).border(2.dp, AdBg, RoundedCornerShape(24.dp)).padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(o.kind.icon, fontSize = 40.sp, modifier = Modifier.background(AdBg, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 10.dp))
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("내 생활반경 혜택 · ${o.kind.label}", fontSize = 16.sp, color = AdInk, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        AdTag()
                    }
                    Text(match.reason, fontSize = 17.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
                    Text(o.title, fontSize = 25.sp, color = Ink, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 2.dp))
                    Text("${o.detail} · ${o.partner}", fontSize = 16.sp, color = Muted)
                    Text(
                        if (used) "예시 제휴라 실제 쿠폰은 없어요. 제휴가 열리면 여기서 바로 쓸 수 있어요." else "💡 ${match.action}",
                        fontSize = 17.sp, color = AdInk, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp).background(AdBg, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                    SmallButton("혜택 받기", primary = true) { used = true; onUse() }
                    SmallButton("닫기 · 30일") { onSnooze(o.id) }
                    Text("혜택 끄기", fontSize = 14.sp, color = Faint, modifier = Modifier.clickable { onConsent(false) }.padding(4.dp))
                }
            }
        }
    }
}

@Composable
private fun AdTag() {
    Text("광고 · 예시 제휴", fontSize = 13.sp, color = AdInk, fontWeight = FontWeight.Bold, modifier = Modifier.background(AdBg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp))
}

@Composable
private fun SmallButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (primary) Color.White else Ink,
        modifier = Modifier.background(if (primary) Color(0xFFF2A93B) else Paper, RoundedCornerShape(14.dp)).border(1.dp, if (primary) Color(0xFFF2A93B) else Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp))
}

/** 트립카드 상세의 한 줄. */
internal fun benefitLine(kind: BenefitKind, title: String, partner: String): String = "${kind.icon} 이 동네 혜택: $title · $partner (광고 · 예시 제휴)"
