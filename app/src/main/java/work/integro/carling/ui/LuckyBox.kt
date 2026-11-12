package work.integro.carling.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.data.LuckyBox
import kotlin.math.cos
import kotlin.math.sin

/**
 * 럭키박스: 상자 1개를 1탭으로 연다(연출 2초 이내). 내용은 상자가 생길 때 정해져 있다.
 * 여러 개면 '다음 상자'로 이어서 연다.
 */
@Composable
fun LuckyBoxDialog(box: LuckyBox, remaining: Int, onOpen: (LuckyBox) -> Unit, onNext: () -> Unit, onClose: () -> Unit) {
    var opened by remember(box.boxId) { mutableStateOf(false) }
    val lid = remember(box.boxId) { Animatable(0f) }
    LaunchedEffect(opened) { if (opened) lid.animateTo(1f, tween(900)) }
    val t = rememberInfiniteTransition(label = "box")
    val wobble by t.animateFloat(-4f, 4f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "wobble")
    FullScrim {
        Panel(Modifier.width(720.dp), padding = 32.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (opened) "럭키박스를 열었어요!" else "럭키박스가 도착했어요", fontSize = 30.sp, color = Ink, fontWeight = FontWeight.Bold)
                Text(if (opened) "함께 달린 여정이 준 선물이에요" else "상자를 눌러 열어 보세요", fontSize = 19.sp, color = Muted)
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .size(300.dp)
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            if (!opened) {
                                opened = true
                                onOpen(box)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    GiftBox(lidOpen = lid.value, tilt = if (opened) 0f else wobble)
                }
                if (opened) {
                    Column(Modifier.graphicsLayer { alpha = lid.value }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+${box.bonusGp} 성장 포인트", fontSize = 30.sp, color = Mint, fontWeight = FontWeight.Bold)
                        Text(
                            box.badge?.let { "${it.icon} 수집 배지 '${it.label}'" }
                                ?: box.carItem?.let { "${it.icon} 자동차 꾸미기 '${it.label}' — 카링의 차에 붙여 봐요" }
                                ?: "다음 상자에서 배지나 자동차 꾸미기를 만날 수 있어요",
                            fontSize = 22.sp,
                            color = Ink,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (remaining > 0) Button(onClick = onNext, colors = ButtonDefaults.buttonColors(containerColor = Lilac), modifier = Modifier.height(64.dp)) { Text("다음 상자 (${remaining}개)", fontSize = 22.sp) }
                        OutlinedButton(onClick = onClose, modifier = Modifier.height(64.dp)) { Text("닫기", fontSize = 22.sp) }
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onClose, modifier = Modifier.height(60.dp)) { Text("나중에 열기", fontSize = 20.sp) }
                }
            }
        }
    }
}

/** 라벤더 상자와 민트 리본. [lidOpen] 0~1이면 뚜껑이 들리며 빛이 나온다. */
@Composable
private fun GiftBox(lidOpen: Float, tilt: Float, modifier: Modifier = Modifier.size(260.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        rotate(tilt, Offset(w / 2, h * 0.9f)) {
            // 열리면 빛줄기
            if (lidOpen > 0f) {
                val c = Offset(w / 2, h * 0.42f)
                for (i in 0 until 10) {
                    val a = Math.toRadians(i * 36.0 + lidOpen * 30).toFloat()
                    drawLine(Color(0xFFFFF1B8).copy(alpha = 0.7f * lidOpen), c, Offset(c.x + cos(a) * w * 0.55f * lidOpen, c.y + sin(a) * h * 0.55f * lidOpen), strokeWidth = 10f)
                }
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF7D6).copy(alpha = lidOpen), Color.Transparent), c, w * 0.4f), w * 0.4f, c)
            }
            val boxTop = h * 0.45f
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFB9ABFF), Color(0xFF8C7CF0)), startY = boxTop, endY = h * 0.92f), Offset(w * 0.18f, boxTop), Size(w * 0.64f, h * 0.47f), CornerRadius(24f, 24f))
            drawRect(Color(0xFF5FE0B4), Offset(w * 0.46f, boxTop), Size(w * 0.08f, h * 0.47f))
            // 뚜껑
            val lift = lidOpen * h * 0.22f
            rotate(-18f * lidOpen, Offset(w * 0.2f, boxTop - lift)) {
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFCFC5FF), Color(0xFFA596FF)), startY = boxTop - h * 0.12f - lift, endY = boxTop - lift), Offset(w * 0.13f, boxTop - h * 0.12f - lift), Size(w * 0.74f, h * 0.13f), CornerRadius(22f, 22f))
                drawRect(Color(0xFF5FE0B4), Offset(w * 0.46f, boxTop - h * 0.12f - lift), Size(w * 0.08f, h * 0.13f))
                // 리본 매듭
                val knot = Offset(w * 0.5f, boxTop - h * 0.13f - lift)
                drawOval(Color(0xFF5FE0B4), Offset(knot.x - w * 0.16f, knot.y - h * 0.08f), Size(w * 0.15f, h * 0.10f))
                drawOval(Color(0xFF5FE0B4), Offset(knot.x + w * 0.01f, knot.y - h * 0.08f), Size(w * 0.15f, h * 0.10f))
                drawCircle(Color(0xFF3CCB9A), w * 0.035f, knot)
            }
        }
    }
}

/** 홈에 떠 있는 럭키박스 버튼. */
@Composable
fun LuckyBoxButton(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "boxbtn")
    val bob by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bob")
    Row(
        modifier
            .graphicsLayer { translationY = -8f * bob }
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GiftBox(lidOpen = 0f, tilt = 0f, modifier = Modifier.size(96.dp))
        Spacer(Modifier.width(6.dp))
        Text("럭키박스 ${count}개", fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Bold)
    }
}
