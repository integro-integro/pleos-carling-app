package work.integro.carling.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import work.integro.carling.game.CareIssue
import work.integro.carling.game.CareRules
import work.integro.carling.game.Coupon

private val Warn = Color(0xFFFF9D5C)

/** 홈 화면의 돌봄 경고 칩. 누르면 퀘스트 카드가 열린다. */
@Composable
fun CareChip(issues: Set<CareIssue>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val top = issues.minByOrNull { it.priority } ?: return
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = Color(0xE6311A12), modifier = modifier.border(2.dp, Warn, RoundedCornerShape(24.dp))) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(top.icon, fontSize = 24.sp)
            Spacer(Modifier.width(8.dp))
            Text(top.chip, fontSize = 19.sp, color = Ink, fontWeight = FontWeight.Bold)
            if (issues.size > 1) Text("  외 ${issues.size - 1}개", fontSize = 16.sp, color = Muted)
            Spacer(Modifier.width(10.dp))
            Text("돌봄 퀘스트 ›", fontSize = 16.sp, color = Warn, fontWeight = FontWeight.Bold)
        }
    }
}

/** 진행 중인 돌봄 퀘스트. 완료는 차량 신호로 자동 판정한다. */
@Composable
fun CareQuestDialog(issues: Set<CareIssue>, intimacy: Int, heart: Int, onClose: () -> Unit) {
    Scrim {
        Text("돌봄 퀘스트", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text("차가 아프면 카링도 아파요. 차를 돌봐 주면 카링과 더 가까워져요.", fontSize = 18.sp, color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        issues.sortedBy { it.priority }.forEach { q ->
            Column(
                Modifier.fillMaxWidth().padding(bottom = 12.dp).background(PanelLight, RoundedCornerShape(20.dp))
                    .border(1.5.dp, Warn.copy(alpha = 0.7f), RoundedCornerShape(20.dp)).padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(q.icon, fontSize = 34.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(q.quest, fontSize = 23.sp, color = Ink, fontWeight = FontWeight.Bold)
                        Text(q.chip, fontSize = 16.sp, color = Warn)
                    }
                    Text("💗 +${CareRules.CARE_INTIMACY}", fontSize = 19.sp, color = Gold, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text("“${q.line}”", fontSize = 18.sp, color = Ink, lineHeight = 26.sp)
                Spacer(Modifier.height(6.dp))
                Text(q.how, fontSize = 16.sp, color = Muted, lineHeight = 23.sp)
            }
        }
        Text(
            "친밀도 $intimacy · 마음 포인트 $heart / ${CareRules.GIFT_HEART} — 친밀도가 가득 찬 뒤 쌓인 마음 포인트로 카링이 정비 쿠폰을 선물해요.",
            fontSize = 16.sp, color = Muted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Button(onClick = onClose, modifier = Modifier.height(60.dp).width(200.dp)) { Text("닫기", fontSize = 24.sp) }
    }
}

/** 돌봄 퀘스트 완료 알림. 화면 위쪽에 잠깐 뜬다. */
@Composable
fun CareDoneNotice(issue: CareIssue, onDone: () -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(issue) {
        appear.animateTo(1f, tween(260)); delay(3_200); appear.animateTo(0f, tween(220)); onDone()
    }
    Box(Modifier.fillMaxSize().padding(top = 28.dp), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.graphicsLayer { alpha = appear.value; translationY = (appear.value - 1f) * 40f * density }
                .background(Night, RoundedCornerShape(28.dp)).border(2.dp, Color(0xFF5DCAA5), RoundedCornerShape(28.dp))
                .padding(horizontal = 26.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✨", fontSize = 30.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("돌봄 퀘스트 완료 · ${issue.quest}", fontSize = 16.sp, color = Color(0xFF5DCAA5), fontWeight = FontWeight.Bold)
                Text("${issue.doneLine}  💗 +${CareRules.CARE_INTIMACY}", fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** 카링이 내미는 선물 상자 — 정비 쿠폰. */
@Composable
fun GiftDialog(coupon: Coupon, onClaim: () -> Unit) {
    Scrim {
        Text("🎁", fontSize = 72.sp)
        Text("카링이 선물을 준비했어요!", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(6.dp))
        Speech("차를 잘 돌봐 줘서 고마워요. 이거 받아요!")
        Spacer(Modifier.height(16.dp))
        CouponCard(coupon)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClaim, modifier = Modifier.height(60.dp).width(240.dp)) { Text("받을게요", fontSize = 24.sp) }
    }
}

/** 정비 쿠폰 한 장. 블루핸즈 제휴 전이라 시연용으로 표시한다. */
@Composable
fun CouponCard(coupon: Coupon, modifier: Modifier = Modifier) {
    Row(
        modifier.widthIn(max = 560.dp).fillMaxWidth().background(Color(0xFFFFF6E2), RoundedCornerShape(18.dp))
            .border(2.dp, Color(0xFF378ADD), RoundedCornerShape(18.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(coupon.kind.icon, fontSize = 40.sp)
        Column(Modifier.weight(1f)) {
            Text("정비 쿠폰 · ${coupon.kind.title}", fontSize = 20.sp, color = Color(0xFF0C447C), fontWeight = FontWeight.Bold)
            Text(coupon.kind.detail, fontSize = 16.sp, color = Color(0xFF185FA5))
            Text("시연용 · 블루핸즈 제휴 시 사용 가능(예정) · 받은 날 ${coupon.date}", fontSize = 13.sp, color = Color(0xFF5F5E5A))
        }
        Box(Modifier.width(64.dp).height(64.dp).border(1.5.dp, Color(0xFF888780), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Text("QR\n자리", fontSize = 12.sp, color = Color(0xFF888780), textAlign = TextAlign.Center)
        }
    }
}
