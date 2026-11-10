package work.integro.carling.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import java.time.ZoneId
import java.time.Instant
import work.integro.carling.game.Light
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import work.integro.carling.data.TripCard
import work.integro.carling.game.CarBody
import work.integro.carling.game.CarCatalog
import work.integro.carling.game.CarModel
import work.integro.carling.game.UsableItem

/*
 * 트립카드 — 모든 카드는 같은 민트빛 젤리 심장 뒷면으로 시작하고, 저장하는 순간 뒤집혀 앞면의 자동차가 공개된다.
 * 앞면: 번호 · 차 이름 · 희귀도 · 미니카 · 출발→도착 · 거리·시간 · 얻은 아이템 · 저장 완료 심장 도장.
 * 미니카는 그림을 받기 전까지 차종별 생김새를 코드로 그린다.
 */

private val Mint = Color(0xFF5FE3C0)
private val MintDeep = Color(0xFF0E4A44)
private val Parchment = Color(0xFFFBEFD8)
private val ParchmentDeep = Color(0xFFEBD3A8)
private val CardBrown = Color(0xFF5A3A22)
private val CardGold = Color(0xFFD9B56A)

/** 카드의 가로:세로 비율. */
const val CARD_RATIO = 1.42f

/** 저장하며 받은 간식(사용하기에 담긴다). 저장 전이면 null. */
fun TripCard.acquiredItem(): UsableItem? = itemReward

val TripCard.routeLabel: String
    get() = "${startName ?: "출발"} → ${placeName ?: if (newPlace == true) "새로운 곳" else "도착"}"

val TripCard.statLabel: String
    get() = listOfNotNull(distanceKm?.takeIf { it > 0.05f }?.let { "%.1f km".format(it) }, "${minutes}분").joinToString(" · ")

/** 시간대별 카드 하늘색: 바탕, 글자, 경로선, 별. */
private data class Sky(val bg: Color, val ink: Color, val sub: Color, val line: Color, val dark: Boolean)

private fun skyOf(light: Light) = when (light) {
    Light.DAWN -> Sky(Color(0xFFEEEDFE), Color(0xFF3C3489), Color(0xFF534AB7), Color(0xFF7F77DD), false)
    Light.DAY -> Sky(Color(0xFFE6F1FB), Color(0xFF0C447C), Color(0xFF185FA5), Color(0xFF185FA5), false)
    Light.SUNSET -> Sky(Color(0xFFFAD9CB), Color(0xFF712B13), Color(0xFF993C1D), Color(0xFF993C1D), false)
    Light.STAR -> Sky(Color(0xFF1A2350), Color(0xFFFFF6E2), Color(0xFFB5D4F4), Color(0xFFE1F5EE), true)
}

private val WeekDays = listOf("월", "화", "수", "목", "금", "토", "일")

/**
 * 트립카드 한 장. 위 2/3는 그 트립(도착지·날짜·별자리 경로·거리/시간/안전),
 * 아래 1/3은 보상 띠(자동차·아이템·저장 완료 도장). 저장 전에는 보상 띠가 잠겨 있다.
 * [stripFlip]은 0..180으로 보상 띠가 뒤집히는 정도, [stamp]는 도장이 찍히는 정도.
 */
@Composable
fun TripCardFace(card: TripCard, width: Dp, modifier: Modifier = Modifier, stamp: Float = if (card.saved) 1f else 0f, stripFlip: Float = if (card.saved) 180f else 0f) {
    val s = width.value / 300f
    val sky = skyOf(card.light)
    val shape = RoundedCornerShape(24.dp * s)
    val firstVisit = card.newPlace == true
    Column(
        modifier
            .size(width, width * CARD_RATIO)
            .background(sky.bg, shape)
            .border(5.dp * s, Brush.verticalGradient(listOf(Color(0xFFFBF0D2), CardGold, Color(0xFFB8914A))), shape)
            .then(if (firstVisit) Modifier.padding(5.dp * s).border(1.5.dp * s, Mint, RoundedCornerShape(19.dp * s)) else Modifier.padding(5.dp * s)),
    ) {
        // ---- 위 2/3: 트립
        Box(Modifier.fillMaxWidth().weight(2f)) {
            Canvas(Modifier.fillMaxSize()) { drawTripSky(card, sky, s * density) }
            Column(Modifier.fillMaxSize().padding(horizontal = 14.dp * s, vertical = 10.dp * s), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth()) {
                    Text("♡ 모두의카링 · TRIP %03d".format(card.number), fontSize = 11.sp * s, color = if (sky.dark) Color(0xFFF4C0D1) else Color(0xFF993556), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (firstVisit) Text("처음 간 곳", fontSize = 11.sp * s, color = if (sky.dark) Mint else Color(0xFF0F6E56), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp * s))
                Text(card.placeName ?: if (firstVisit) "새로운 곳" else "함께 달린 길", fontSize = 24.sp * s, color = sky.ink, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(card.dateLabel, fontSize = 12.sp * s, color = sky.sub, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text(card.tripStats, fontSize = 13.sp * s, color = sky.ink, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(CardGold))
        // ---- 아래 1/3: 보상 띠
        Box(
            Modifier.fillMaxWidth().weight(1f).graphicsLayer {
                rotationX = stripFlip
                cameraDistance = 12f * density
            },
        ) {
            if (stripFlip < 90f) LockedStrip(s) else Box(Modifier.fillMaxSize().graphicsLayer { rotationX = 180f }) { RewardStrip(card, s, width, stamp) }
        }
    }
}

private val TripCard.dateLabel: String
    get() {
        val t = Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault())
        return "%d.%02d.%02d (%s) · %02d:%02d 출발".format(t.year, t.monthValue, t.dayOfMonth, WeekDays[t.dayOfWeek.value - 1], t.hour, t.minute)
    }

private val TripCard.tripStats: String
    get() = listOfNotNull(
        distanceKm?.takeIf { it > 0.05f }?.let { "%.1fkm".format(it) },
        if (minutes >= 60) "${minutes / 60}시간 ${minutes % 60}분" else "${minutes}분",
        safety?.let { "안전 ${it}점" },
    ).joinToString(" · ")

/** 하늘(밤에는 별, 낮에는 해)과 별자리 경로. 경로 점이 없으면 카드 번호로 정해진 모양을 그린다. */
private fun DrawScope.drawTripSky(card: TripCard, sky: Sky, px: Float) {
    val rnd = kotlin.random.Random(card.number)
    if (sky.dark) repeat(18) { drawCircle(Color.White.copy(alpha = 0.3f + rnd.nextFloat() * 0.5f), (0.6f + rnd.nextFloat()) * px, Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)) }
    else if (card.light == Light.DAY) drawCircle(Color(0xFFFAC775), 16f * px, Offset(size.width * 0.84f, size.height * 0.2f))
    else if (card.light == Light.SUNSET) drawCircle(Color(0xFFF0997B), 22f * px, Offset(size.width * 0.78f, size.height * 0.62f))
    // 경로를 그릴 영역: 제목 아래 ~ 통계 위
    val left = size.width * 0.14f; val right = size.width * 0.86f
    val top = size.height * 0.36f; val bottom = size.height * 0.8f
    val pts: List<Offset> = if (card.routeDots.size >= 2) {
        val la = card.routeDots.map { it.lat }; val ln = card.routeDots.map { it.lng }
        val spanLa = (la.max() - la.min()).coerceAtLeast(1e-6); val spanLn = (ln.max() - ln.min()).coerceAtLeast(1e-6)
        val k = minOf((right - left) / spanLn, (bottom - top) / spanLa)
        val cx = (left + right) / 2; val cy = (top + bottom) / 2
        val mLa = (la.max() + la.min()) / 2; val mLn = (ln.max() + ln.min()) / 2
        card.routeDots.map { Offset((cx + (it.lng - mLn) * k).toFloat(), (cy - (it.lat - mLa) * k).toFloat()) }
    } else {
        // 위치가 없는 카드: 왼쪽 아래에서 오른쪽 위로 가는 별자리
        List(8) { i -> val t = i / 7f; Offset(left + (right - left) * t, bottom - (bottom - top) * t + (rnd.nextFloat() - 0.5f) * (bottom - top) * 0.35f) }
    }
    val path = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
    drawPath(path, sky.line.copy(alpha = 0.25f), style = Stroke(6f * px, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawPath(path, sky.line, style = Stroke(1.6f * px, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    val step = (pts.size / 8).coerceAtLeast(1)
    pts.forEachIndexed { i, p -> if (i % step == 0 && i != 0 && i != pts.lastIndex) drawCircle(sky.line, 2f * px, p) }
    drawStar(pts.first(), 7f * px, sky.ink)
    drawStar(pts.last(), 9f * px, Color(0xFFFAC775))
}

private fun DrawScope.drawStar(at: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(at.x, at.y - r); quadraticTo(at.x, at.y, at.x + r, at.y); quadraticTo(at.x, at.y, at.x, at.y + r)
        quadraticTo(at.x, at.y, at.x - r, at.y); quadraticTo(at.x, at.y, at.x, at.y - r); close()
    }
    drawCircle(color.copy(alpha = 0.3f), r * 0.9f, at)
    drawPath(p, color)
}

/** 저장 전 보상 띠: 민트빛 뒷면에 자동차·아이템이 '?'로 가려져 있다. */
@Composable
private fun LockedStrip(s: Float) {
    Row(
        Modifier.fillMaxSize().background(Color(0xFF0F6E56)).padding(horizontal = 10.dp * s, vertical = 8.dp * s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp * s),
    ) {
        repeat(2) { i ->
            Box(
                Modifier.weight(if (i == 0) 1.5f else 1f).fillMaxHeight().background(Color(0xFF085041), RoundedCornerShape(10.dp * s))
                    .border(1.dp * s, Color(0xFF5DCAA5), RoundedCornerShape(10.dp * s)),
                contentAlignment = Alignment.Center,
            ) { Text("?", fontSize = 22.sp * s, color = Color(0xFF5DCAA5), fontWeight = FontWeight.Bold) }
        }
        Box(Modifier.weight(1.2f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color(0xFF5DCAA5), size.minDimension / 2 * 0.9f, style = Stroke(1.5f * density * s, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
            }
            Text("눌러서\n저장", fontSize = 11.sp * s, color = Color(0xFFE1F5EE), textAlign = TextAlign.Center, lineHeight = 13.sp * s)
        }
    }
}

/** 저장 후 보상 띠: 자동차(희귀도 테두리) · 아이템 · 저장 완료 도장. */
@Composable
private fun RewardStrip(card: TripCard, s: Float, width: Dp, stamp: Float) {
    val car = CarCatalog.of(card.carModel)
    Row(
        Modifier.fillMaxSize().background(Parchment).padding(horizontal = 10.dp * s, vertical = 8.dp * s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp * s),
    ) {
        val rarity = car?.let { Color(it.rarity.color) } ?: CardGold
        Column(
            Modifier.weight(1.5f).fillMaxHeight().background(Color(0xFFFFF6E2), RoundedCornerShape(10.dp * s)).border(1.6.dp * s, rarity, RoundedCornerShape(10.dp * s)).padding(2.dp * s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (car != null) {
                CarDrawing(car, Modifier.fillMaxWidth().weight(1f))
                Text("${car.rarity.label} · ${car.name.removePrefix("현대 ").removePrefix("기아 ").removePrefix("제네시스 ")}", fontSize = 10.sp * s, color = CardBrown, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().background(Color(0xFFFFF6E2), RoundedCornerShape(10.dp * s)).border(1.dp * s, CardGold, RoundedCornerShape(10.dp * s)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val item = card.acquiredItem()
            Text(item?.icon ?: "—", fontSize = 28.sp * s)
            Text(item?.label ?: "아이템 없음", fontSize = 10.sp * s, color = CardBrown, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Box(Modifier.weight(1.2f).fillMaxHeight(), contentAlignment = Alignment.Center) { HeartStamp(stamp, width * 0.24f) }
    }
}

@Composable
private fun CardBack(width: Dp, glow: Float, modifier: Modifier) {
    val s = width.value / 300f
    val shape = RoundedCornerShape(26.dp * s)
    Box(
        modifier
            .size(width, width * CARD_RATIO)
            .background(Brush.verticalGradient(listOf(Color(0xFF17695E), MintDeep, Color(0xFF0A3A35))), shape)
            .border(6.dp * s, Brush.verticalGradient(listOf(Color(0xFFFBF0D2), CardGold, Color(0xFFB8914A))), shape)
            .border((2f + glow * 6f).dp * s, Mint.copy(alpha = 0.2f + glow * 0.8f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(14.dp * s)) {
            val px = s * density
            drawRoundRect(Color(0xFFF6E7C1).copy(alpha = 0.55f), style = Stroke(1.5f * px), cornerRadius = CornerRadius(18f * px))
            // 네 귀퉁이의 월계수
            drawLaurel(Offset(26f * px, 26f * px), 22f * px, 1f, 1f, Color(0xFFF1DFAE))
            drawLaurel(Offset(size.width - 26f * px, 26f * px), 22f * px, -1f, 1f, Color(0xFFF1DFAE))
            drawLaurel(Offset(26f * px, size.height - 26f * px), 22f * px, 1f, -1f, Color(0xFFF1DFAE))
            drawLaurel(Offset(size.width - 26f * px, size.height - 26f * px), 22f * px, -1f, -1f, Color(0xFFF1DFAE))
            // 제목 양옆 줄
            val ty = 36f * px
            drawLine(Color(0xFFF1DFAE), Offset(size.width * 0.12f, ty), Offset(size.width * 0.27f, ty), 1.5f * px)
            drawLine(Color(0xFFF1DFAE), Offset(size.width * 0.73f, ty), Offset(size.width * 0.88f, ty), 1.5f * px)
            // 가운데 젤리 심장과 심전도 선
            val c = Offset(size.width / 2, size.height * 0.47f)
            val r = size.width * 0.27f
            drawCircle(Brush.radialGradient(listOf(Mint.copy(alpha = 0.5f), Color.Transparent), c, r * 1.9f), r * 1.9f, c)
            drawJellyHeart(c, r, Mint)
            val y = size.height * 0.74f
            val p = Path().apply {
                moveTo(size.width * 0.16f, y); lineTo(size.width * 0.42f, y)
                lineTo(size.width * 0.46f, y - r * 0.38f); lineTo(size.width * 0.5f, y + r * 0.3f)
                lineTo(size.width * 0.54f, y - r * 0.15f); lineTo(size.width * 0.57f, y); lineTo(size.width * 0.84f, y)
            }
            drawLine(Mint.copy(alpha = 0.25f), Offset(size.width * 0.16f, y), Offset(size.width * 0.84f, y), 8f * px, cap = StrokeCap.Round)
            drawPath(p, Mint, style = Stroke(3f * px, cap = StrokeCap.Round))
        }
        Text("♡ 모두의카링", fontSize = 18.sp * s, color = Color(0xFFF6E7C1), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp * s))
    }
}

/** 월계수 가지 한 묶음. [dx]·[dy]로 귀퉁이 방향을 정한다. */
private fun DrawScope.drawLaurel(at: Offset, len: Float, dx: Float, dy: Float, color: Color) {
    val stem = Path().apply {
        moveTo(at.x, at.y)
        quadraticTo(at.x + dx * len * 0.2f, at.y + dy * len * 0.9f, at.x + dx * len, at.y + dy * len * 1.1f)
    }
    drawPath(stem, color, style = Stroke(len * 0.07f, cap = StrokeCap.Round))
    for (i in 1..4) {
        val t = i / 5f
        val p = Offset(at.x + dx * len * (0.2f * t * 2f + t * t * 0.6f), at.y + dy * len * (t * 1.05f))
        drawOval(color, Offset(p.x - len * 0.16f + dx * len * 0.12f, p.y - len * 0.06f), Size(len * 0.3f, len * 0.13f))
        drawOval(color, Offset(p.x - len * 0.16f - dx * len * 0.08f, p.y - len * 0.2f * dy - len * 0.04f), Size(len * 0.13f, len * 0.26f))
    }
}

/** 오른쪽 아래 동그라미. 저장하는 순간 심장 도장이 '탁' 찍힌다. */
@Composable
private fun HeartStamp(progress: Float, size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFF2E9E5B).copy(alpha = 0.35f), size.toPx() / 2 * 0.92f, style = Stroke(2f * density, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
        }
        if (progress > 0f) {
            val drop = 1f + (1f - progress) * 1.4f
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = drop; scaleY = drop; rotationZ = -14f; alpha = progress.coerceIn(0f, 1f)
                },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = this.size.minDimension / 2 * 0.9f
                    val green = Color(0xFF2E9E5B)
                    drawCircle(green, r, style = Stroke(4f * density))
                    drawCircle(green.copy(alpha = 0.7f), r * 0.8f, style = Stroke(1.5f * density))
                    drawJellyHeart(center + Offset(0f, r * 0.12f), r * 0.42f, Mint)
                }
                Text("저장 완료", fontSize = (size.value * 0.13f).sp, color = Color(0xFF2E9E5B), fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.TopCenter).padding(top = size * 0.16f))
            }
        }
    }
}

/** 젤리 질감 심장. */
fun DrawScope.drawJellyHeart(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + r * 0.95f)
        cubicTo(c.x - r * 1.5f, c.y - r * 0.1f, c.x - r * 0.65f, c.y - r * 1.15f, c.x, c.y - r * 0.3f)
        cubicTo(c.x + r * 0.65f, c.y - r * 1.15f, c.x + r * 1.5f, c.y - r * 0.1f, c.x, c.y + r * 0.95f)
        close()
    }
    drawPath(p, Brush.radialGradient(listOf(lerp(Color.White, color, 0.4f), color, lerp(color, Color(0xFF0E4A44), 0.35f)), c - Offset(r * 0.35f, r * 0.45f), r * 1.7f))
    drawPath(p, Color.White.copy(alpha = 0.5f), style = Stroke(r * 0.06f))
    drawOval(Color.White.copy(alpha = 0.8f), c - Offset(r * 0.7f, r * 0.55f), Size(r * 0.45f, r * 0.26f))
}

/** 차종별 생김새로 그린 미니카(오른쪽을 본다). [silhouette]면 아직 못 모은 차의 그림자다. */
@Composable
fun CarDrawing(car: CarModel, modifier: Modifier = Modifier, silhouette: Boolean = false) {
    Canvas(modifier.clipToBounds()) { drawCar(car, silhouette) }
}

private data class Shape(val len: Float, val bodyH: Float, val roofH: Float, val roofFrom: Float, val roofTo: Float, val wheel: Float, val lift: Float)

private fun shapeOf(body: CarBody) = when (body) {
    CarBody.HATCH -> Shape(0.80f, 0.20f, 0.20f, 0.22f, 0.86f, 0.10f, 0.0f)
    CarBody.SEDAN -> Shape(0.88f, 0.18f, 0.17f, 0.28f, 0.72f, 0.095f, 0.0f)
    CarBody.SUV -> Shape(0.86f, 0.24f, 0.21f, 0.18f, 0.88f, 0.115f, 0.03f)
    CarBody.SPORTS -> Shape(0.90f, 0.15f, 0.13f, 0.32f, 0.70f, 0.095f, 0.0f)
    CarBody.VAN -> Shape(0.88f, 0.26f, 0.24f, 0.10f, 0.94f, 0.10f, 0.02f)
    CarBody.BOX -> Shape(0.70f, 0.24f, 0.26f, 0.14f, 0.92f, 0.10f, 0.0f)
    CarBody.CONCEPT -> Shape(0.90f, 0.16f, 0.16f, 0.20f, 0.80f, 0.10f, 0.0f)
}

private fun DrawScope.drawCar(car: CarModel, silhouette: Boolean) {
    val sh = shapeOf(car.body)
    val w = size.width
    val h = size.height
    val unit = minOf(w, h * 1.35f)
    val len = unit * sh.len
    val left = (w - len) / 2
    val ground = h * 0.86f
    val wheelR = unit * sh.wheel
    val bodyBottom = ground - wheelR * 0.6f - unit * sh.lift
    val bodyTop = bodyBottom - unit * sh.bodyH
    val roofTop = bodyTop - unit * sh.roofH
    val base = if (silhouette) Color(0xFF3A3F55) else Color(car.color)
    val dark = lerp(base, Color.Black, 0.35f)
    val light = lerp(base, Color.White, 0.45f)

    drawOval(Color.Black.copy(alpha = 0.18f), Offset(left - len * 0.02f, ground - wheelR * 0.25f), Size(len * 1.04f, wheelR * 0.6f))
    // 차체
    val bodyPath = Path().apply {
        val r = unit * 0.06f
        moveTo(left + r, bodyBottom)
        lineTo(left + len - r, bodyBottom)
        quadraticTo(left + len, bodyBottom, left + len, bodyBottom - r)
        lineTo(left + len, bodyTop + r * (if (car.body == CarBody.SPORTS || car.body == CarBody.CONCEPT) 1.8f else 0.8f))
        quadraticTo(left + len * 0.99f, bodyTop, left + len * 0.92f, bodyTop)
        lineTo(left + len * 0.08f, bodyTop)
        quadraticTo(left, bodyTop, left, bodyTop + r)
        lineTo(left, bodyBottom - r)
        quadraticTo(left, bodyBottom, left + r, bodyBottom)
        close()
    }
    // 지붕(유리창 포함)
    val rf = left + len * sh.roofFrom
    val rt = left + len * sh.roofTo
    val slopeBack = len * (if (car.body == CarBody.BOX || car.body == CarBody.VAN) 0.02f else 0.08f)
    val slopeFront = len * (when (car.body) { CarBody.SPORTS, CarBody.CONCEPT -> 0.16f; CarBody.BOX, CarBody.VAN -> 0.05f; else -> 0.11f })
    val cabin = Path().apply {
        moveTo(rf, bodyTop + 1f)
        quadraticTo(rf + slopeBack * 0.3f, roofTop, rf + slopeBack, roofTop)
        lineTo(rt - slopeFront, roofTop)
        quadraticTo(rt - slopeFront * 0.4f, roofTop, rt, bodyTop + 1f)
        close()
    }
    drawPath(cabin, Brush.verticalGradient(listOf(light, base), roofTop, bodyTop))
    drawPath(bodyPath, Brush.verticalGradient(listOf(light, base, dark), bodyTop, bodyBottom))
    if (!silhouette) {
        // 창문
        val inset = unit * 0.022f
        val win = Path().apply {
            moveTo(rf + inset * 1.6f, bodyTop - inset * 0.4f)
            lineTo(rf + slopeBack + inset * 0.6f, roofTop + inset)
            lineTo(rt - slopeFront + inset * 0.2f, roofTop + inset)
            lineTo(rt - inset * 1.8f, bodyTop - inset * 0.4f)
            close()
        }
        drawPath(win, Brush.verticalGradient(listOf(Color(0xFFBFE6FF), Color(0xFF5D86B0)), roofTop, bodyTop))
        val pillar = (rf + rt) / 2 + len * 0.02f
        drawLine(lerp(base, Color.Black, 0.2f), Offset(pillar, roofTop + inset), Offset(pillar, bodyTop), unit * 0.018f)
        // 광택·라이트
        drawLine(Color.White.copy(alpha = 0.55f), Offset(left + len * 0.12f, bodyTop + unit * 0.03f), Offset(left + len * 0.85f, bodyTop + unit * 0.03f), unit * 0.012f, cap = StrokeCap.Round)
        drawOval(Color(0xFFFFF4B8), Offset(left + len - unit * 0.05f, bodyTop + unit * 0.04f), Size(unit * 0.045f, unit * 0.035f))
        drawOval(Color(0xFFFF6A5C), Offset(left + unit * 0.005f, bodyTop + unit * 0.04f), Size(unit * 0.03f, unit * 0.035f))
        if (car.id == "heartcar") drawJellyHeart(Offset(left + len * 0.5f, bodyTop + unit * 0.09f), unit * 0.05f, Color(0xFF5FE3C0))
    }
    // 바퀴
    for (x in listOf(left + len * 0.2f, left + len * 0.8f)) {
        val c = Offset(x, ground - wheelR)
        drawCircle(Color(0xFF22232B), wheelR, c)
        drawCircle(if (silhouette) Color(0xFF555A70) else Color(0xFFC9CED6), wheelR * 0.55f, c)
        drawCircle(Color(0xFF8A909C), wheelR * 0.2f, c)
    }
}

/**
 * 트립카드 공개 연출. 트립 정보는 처음부터 보이고, 저장하면 카드가 두근 → 아래 보상 띠가 뒤집혀 자동차·아이템이 나타남
 * → 도장 '탁' → 보상 줄 순서. [onSave]는 바로 저장을 기록하고, 이 화면은 그 뒤 연출만 맡는다.
 */
@Composable
fun TripCardReveal(card: TripCard, canSave: Boolean, xpIfSaved: Int, width: Dp, onSave: () -> Unit) {
    val beat = remember { Animatable(1f) }
    val flip = remember(card.number) { Animatable(if (card.saved) 180f else 0f) }
    val stamp = remember(card.number) { Animatable(if (card.saved) 1f else 0f) }
    val rewards = remember(card.number) { Animatable(if (card.saved) 1f else 0f) }
    var trigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        repeat(2) { beat.animateTo(1.05f, tween(120)); beat.animateTo(1f, tween(160)) }
        flip.animateTo(180f, tween(600, easing = FastOutSlowInEasing))
        delay(200)
        stamp.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
        delay(150)
        rewards.animateTo(1f, tween(400))
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.graphicsLayer { scaleX = beat.value; scaleY = beat.value }) {
            TripCardFace(card, width, stamp = stamp.value, stripFlip = flip.value)
        }
        Spacer(Modifier.height(16.dp))
        when {
            !card.saved && canSave -> {
                GameButton(
                    text = if (xpIfSaved > 0) "트립카드 저장 (+$xpIfSaved XP)" else "트립카드 저장",
                    icon = "♡",
                    top = Color(0xFF8BF2D4), bottom = Color(0xFF2FB892),
                    enabled = true,
                    modifier = Modifier.width(width + 40.dp),
                    onClick = { onSave(); trigger++ },
                )
                Spacer(Modifier.height(6.dp))
                Text("저장해야 자동차와 아이템을 받아요 · ${card.hoursLeft()}시간 뒤 사라져요", fontSize = 16.sp, color = Muted)
            }
            !card.saved -> Text("저장 기간이 지났어요", fontSize = 17.sp, color = Muted)
            else -> Row(
                Modifier.alpha(rewards.value).background(Panel, RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (card.xpGained > 0) "⚡ 경험치 +${card.xpGained}" else "⚡ 오늘 보상 5장을 다 받았어요", fontSize = 18.sp, color = Ink, fontWeight = FontWeight.Bold)
                card.acquiredItem()?.let { Text("${it.icon} ${it.label} (사용하기에 담김)", fontSize = 18.sp, color = Ink) }
                CarCatalog.of(card.carModel)?.let { Text("🚗 도감 등록", fontSize = 18.sp, color = Color(it.rarity.color), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** 저장까지 남은 시간(올림, 시간 단위). */
fun TripCard.hoursLeft(now: Long = work.integro.carling.game.GameClock.nowMillis()): Int =
    (((endMillis + work.integro.carling.game.GrowthRules.SAVE_WINDOW_MILLIS) - now) / 3_600_000L + 1).toInt().coerceIn(0, 24)

/** 작게 보여줄 때는 300dp로 그린 카드를 줄여, 글자가 잘리지 않고 큰 카드와 같은 모양이 되게 한다. */
@Composable
fun MiniTripCard(card: TripCard, width: Dp) {
    val k = width.value / 300f
    Box(Modifier.size(width, width * CARD_RATIO), contentAlignment = Alignment.Center) {
        Box(Modifier.requiredSize(300.dp, 300.dp * CARD_RATIO).graphicsLayer { scaleX = k; scaleY = k }) {
            TripCardFace(card, width = 300.dp)
        }
    }
}

/** 도감의 차 한 칸. 모은 차는 차 그림과 이름, 아직 못 모은 차는 카드 뒷면이다. */
@Composable
fun DexTile(car: CarModel, count: Int, modifier: Modifier = Modifier) {
    val k = 0.39f
    if (count <= 0) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Box(Modifier.requiredSize(300.dp, 300.dp * CARD_RATIO).graphicsLayer { scaleX = k; scaleY = k }) {
                CardBack(300.dp, 0f, Modifier)
            }
        }
        return
    }
    val shape = RoundedCornerShape(18.dp)
    val rarity = Color(car.rarity.color)
    Column(
        modifier
            .background(Brush.verticalGradient(listOf(Parchment, ParchmentDeep)), shape)
            .border(2.dp, rarity, shape)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CarDrawing(car, Modifier.fillMaxWidth().height(80.dp))
        Text(car.name, fontSize = 15.sp, color = Color(0xFF3A2414), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(rarity, CircleShape))
            Spacer(Modifier.width(4.dp))
            Text(car.rarity.label + if (count > 1) " · ×$count" else "", fontSize = 12.sp, color = CardBrown)
        }
    }
}
