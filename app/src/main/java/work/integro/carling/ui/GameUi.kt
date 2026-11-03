package work.integro.carling.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/*
 * 게임 HUD 스타일 부품. 두꺼운 외곽선, 광택, 눌리는 3D 버튼, 빛나는 보석.
 */

private val Outline = Color(0xFF0A0C26)
private val GoldLight = Color(0xFFFFE9A8)
private val GoldDeep = Color(0xFFE0A93B)

/** 글자에 테두리 그림자를 줘서 게임 글씨처럼. */
fun gameText(size: TextUnit, color: Color = Color.White) = TextStyle(
    fontSize = size,
    fontWeight = FontWeight.ExtraBold,
    color = color,
    shadow = Shadow(Color(0xCC000000), Offset(0f, 3f), 6f),
)

/** 금테 두른 패널 + 위쪽 제목 리본. */
@Composable
fun GameFrame(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Box(modifier.padding(top = 22.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF2B318A), Color(0xFF171C52), Color(0xFF10143D))), shape)
                .border(4.dp, Brush.verticalGradient(listOf(GoldLight, GoldDeep, Color(0xFF8E6A2A))), shape)
                .padding(4.dp)
                .border(2.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
                .padding(start = 22.dp, end = 22.dp, top = 34.dp, bottom = 22.dp),
        ) { content() }
        // 리본
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-22).dp)
                .background(Brush.verticalGradient(listOf(GoldLight, GoldDeep)), RoundedCornerShape(50))
                .border(3.dp, Color(0xFF6B4A12), RoundedCornerShape(50))
                .padding(horizontal = 28.dp, vertical = 6.dp),
        ) {
            Text("✦ $title ✦", style = gameText(22.sp, Color(0xFF4A2E05)).copy(shadow = null))
        }
    }
}

/**
 * 아이콘 배지 + 두툼한 광택 게이지.
 * [markers]는 0~1 위치에 별 표시 (예: 성장 7일, 30일).
 */
@Composable
fun GameGauge(
    icon: String,
    label: String,
    value: String,
    progress: Float,
    fill: List<Color>,
    modifier: Modifier = Modifier,
    markers: List<Float> = emptyList(),
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "gauge")
    Box(modifier.fillMaxWidth().height(62.dp)) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 26.dp)
                .fillMaxWidth()
                .height(36.dp),
        ) {
            Canvas(Modifier.fillMaxWidth().height(36.dp)) {
                val h = size.height
                val r = CornerRadius(h / 2)
                drawRoundRect(Color(0xFF070921), cornerRadius = r)
                drawRoundRect(Color.Black.copy(alpha = 0.35f), size = Size(size.width, h * 0.45f), cornerRadius = r)
                val w = size.width * animated
                if (w > 0f) {
                    val fw = max(w, h)
                    drawRoundRect(Brush.verticalGradient(fill), size = Size(fw, h), cornerRadius = r)
                    // 광택
                    drawRoundRect(
                        Color.White.copy(alpha = 0.4f),
                        topLeft = Offset(h * 0.35f, h * 0.14f),
                        size = Size(max(fw - h * 0.7f, 0f), h * 0.26f),
                        cornerRadius = CornerRadius(h * 0.13f),
                    )
                }
                drawRoundRect(Outline, cornerRadius = r, style = Stroke(3.dp.toPx()))
                drawRoundRect(Color.White.copy(alpha = 0.18f), cornerRadius = r, style = Stroke(1.dp.toPx()))
                // 이정표 별 — 글자와 겹치지 않게 게이지 위쪽 테두리에 얹는다
                markers.forEach { m ->
                    val cx = size.width * m
                    val cy = 0f
                    val reached = animated >= m
                    val s = h * 0.36f
                    val star = Path().apply {
                        moveTo(cx, cy - s)
                        quadraticTo(cx, cy, cx + s, cy)
                        quadraticTo(cx, cy, cx, cy + s)
                        quadraticTo(cx, cy, cx - s, cy)
                        quadraticTo(cx, cy, cx, cy - s)
                        close()
                    }
                    drawPath(star, Outline, style = Stroke(4.dp.toPx()))
                    drawPath(star, if (reached) GoldLight else Color(0xFF8A90C0))
                }
            }
            Row(
                Modifier.fillMaxWidth().height(36.dp).padding(start = 46.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = gameText(19.sp))
                Spacer(Modifier.weight(1f))
                Text(value, style = gameText(19.sp))
            }
        }
        IconBadge(icon, fill.first(), Modifier.align(Alignment.CenterStart))
    }
}

@Composable
private fun IconBadge(icon: String, color: Color, modifier: Modifier = Modifier, size: Dp = 62.dp) {
    Box(
        modifier
            .size(size)
            .background(Brush.radialGradient(listOf(lerp(color, Color.White, 0.55f), color, lerp(color, Color.Black, 0.4f))), CircleShape)
            .border(4.dp, Outline, CircleShape)
            .padding(4.dp)
            .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(icon, fontSize = (size.value * 0.45f).sp)
    }
}

/** 빛나는 보석 하나 — 시간대(빛)별 주행 횟수. [leading]이면 맥동하는 링. */
@Composable
fun LightGem(label: String, color: Color, count: Int, leading: Boolean, modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "gem").animateFloat(
        0.6f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(68.dp)) {
                val c = center
                val r = size.minDimension * 0.34f
                if (leading) {
                    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.7f * pulse), Color.Transparent), c, r * 1.6f), r * 1.6f, c)
                }
                // 팔각 보석
                val gem = Path().apply {
                    for (i in 0 until 8) {
                        val a = Math.toRadians(22.5 + i * 45.0)
                        val p = Offset(c.x + (r * kotlin.math.cos(a)).toFloat(), c.y + (r * kotlin.math.sin(a)).toFloat())
                        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                    close()
                }
                val dim = if (count == 0) 0.45f else 1f
                drawPath(gem, Brush.radialGradient(listOf(Color.White, color, lerp(color, Color.Black, 0.45f)), c - Offset(r * 0.3f, r * 0.35f), r * 1.4f), alpha = dim)
                drawPath(gem, Outline, style = Stroke(3.dp.toPx()))
                drawCircle(Color.White.copy(alpha = 0.75f * dim), r * 0.16f, c - Offset(r * 0.35f, r * 0.38f))
                if (leading) drawPath(gem, GoldLight.copy(alpha = pulse), style = Stroke(2.dp.toPx()))
            }
            Text("$count", style = gameText(20.sp), modifier = Modifier.offset(y = 2.dp))
        }
        Text(label, style = gameText(15.sp, color).copy(fontWeight = FontWeight.Bold))
    }
}

/** 눌리면 아래로 꺼지는 3D 버튼. */
@Composable
fun GameButton(
    text: String,
    icon: String,
    top: Color,
    bottom: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lip = 7.dp
    val sink by animateDpAsState(if (pressed && enabled) lip else 0.dp, tween(60), label = "sink")
    val c1 = if (enabled) top else Color(0xFF5A5F7E)
    val c2 = if (enabled) bottom else Color(0xFF3A3E58)
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .height(height + lip)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .align(Alignment.BottomCenter)
                .background(lerp(c2, Color.Black, 0.45f), shape)
                .border(3.dp, Outline, shape),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = sink)
                .background(Brush.verticalGradient(listOf(lerp(c1, Color.White, 0.25f), c1, c2)), shape)
                .border(3.dp, Outline, shape)
                .padding(3.dp)
                .border(2.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(19.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(icon, fontSize = 28.sp, modifier = Modifier.alpha(if (enabled) 1f else 0.5f))
                Spacer(Modifier.width(10.dp))
                Text(text, style = gameText(23.sp, if (enabled) Color.White else Color(0xFFB8BCD8)))
            }
        }
    }
}

/** 인벤토리 칸처럼 생긴 정사각 버튼 (+ 숫자 배지). */
@Composable
fun GameSlot(
    icon: String,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    badge: Int? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(20.dp)
    val ring = if (selected) Brush.verticalGradient(listOf(GoldLight, GoldDeep)) else Brush.verticalGradient(listOf(Color(0xFF6C74C9), Color(0xFF3A4192)))
    Box(modifier.clickable(interaction, indication = null, enabled = enabled, onClick = onClick)) {
        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = if (pressed && enabled) 3.dp else 0.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF323A9A), Color(0xFF1C2163))), shape)
                .border(3.dp, ring, shape)
                .padding(vertical = 10.dp)
                .alpha(if (enabled) 1f else 0.45f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(icon, fontSize = 34.sp)
            Text(label, style = gameText(17.sp))
        }
        if (badge != null && badge > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-8).dp)
                    .size(34.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFFFF7A8A), Color(0xFFE0405A))), CircleShape)
                    .border(3.dp, Outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(if (badge > 99) "99+" else "$badge", style = gameText(if (badge > 99) 12.sp else 15.sp)) }
        }
    }
}

/** 게임 설정창 같은 ON/OFF 토글. */
@Composable
fun GameToggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val knob by animateDpAsState(if (checked) 50.dp else 4.dp, tween(150), label = "knob")
    val track = if (checked) listOf(Color(0xFF7BE495), Color(0xFF2FA35A)) else listOf(Color(0xFF6A6F8E), Color(0xFF3E4260))
    Box(
        modifier
            .size(width = 96.dp, height = 46.dp)
            .background(Brush.verticalGradient(track), RoundedCornerShape(50))
            .border(3.dp, Outline, RoundedCornerShape(50))
            .clickable(remember { MutableInteractionSource() }, indication = null) { onChange(!checked) },
    ) {
        Text(
            if (checked) "ON" else "OFF",
            style = gameText(15.sp),
            modifier = Modifier.align(if (checked) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 12.dp),
        )
        Box(
            Modifier
                .offset(x = knob, y = 4.dp)
                .size(38.dp)
                .background(Brush.radialGradient(listOf(Color.White, Color(0xFFD9DCEF))), CircleShape)
                .border(3.dp, Outline, CircleShape),
        )
    }
}
