package work.integro.carling.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import work.integro.carling.data.GeoDot
import work.integro.carling.data.MapRoute
import work.integro.carling.data.roadShape
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * 카링의 지도. 어두운 지도에서 함께 달린 길만 켜진다.
 * 지도 SDK 없이 Canvas로 그리며, 바탕은 해안선만 어림한 그림이다.
 * 길은 주행 중 기록한 실제 경로로 그리고, 기록이 없는 길은 선 대신 도착 불빛 점으로만 표시한다.
 */

private val Sea = Color(0xFF070B1E)
private val Land = Color(0xFF141C38)
private val Coast = Color(0xFF2A3560)
private val Grid = Color(0x14FFFFFF)
private val Road = Color(0xFFFFD98A)
private val RoadCore = Color(0xFFFFF6DC)
private const val NOTICE_MS = 4_500L

private const val LNG_SCALE = 0.81f // 위도 36° 부근에서 경도 1°의 상대 길이
private const val MIN_ZOOM = 0.8f
private const val MAX_ZOOM = 400f
private const val HOME_LNG = 127.9f
private const val HOME_LAT = 36.05f

private val Peninsula = listOf(
    124.6f to 40.2f, 124.9f to 39.2f, 125.1f to 38.3f, 125.4f to 37.72f,
    126.0f to 37.72f, 126.42f to 37.68f, 126.58f to 37.42f, 126.62f to 37.12f,
    126.86f to 36.95f, 126.42f to 36.98f, 126.15f to 36.72f, 126.38f to 36.36f,
    126.52f to 36.2f, 126.6f to 35.97f, 126.47f to 35.63f, 126.4f to 35.3f,
    126.3f to 34.78f, 126.52f to 34.32f, 126.95f to 34.42f, 127.32f to 34.5f,
    127.72f to 34.7f, 128.02f to 34.82f, 128.42f to 34.84f, 128.72f to 34.78f,
    129.0f to 35.04f, 129.22f to 35.18f, 129.42f to 35.48f, 129.57f to 36.05f,
    129.42f to 36.42f, 129.45f to 37.0f, 129.2f to 37.45f, 128.95f to 37.76f,
    128.6f to 38.2f, 128.36f to 38.62f, 128.0f to 39.0f, 127.5f to 39.3f,
    127.6f to 39.8f, 128.3f to 40.2f, 129.6f to 41.5f, 124.6f to 41.5f,
)

private val Jeju = listOf(
    126.15f to 33.3f, 126.35f to 33.47f, 126.7f to 33.55f, 126.95f to 33.46f,
    126.82f to 33.3f, 126.45f to 33.22f,
)

private class MapView(val centerLng: Float, val centerLat: Float, val pxPerDegree: Float, val width: Float, val height: Float) {
    fun toScreen(lng: Float, lat: Float) = Offset(
        width / 2 + (lng - centerLng) * LNG_SCALE * pxPerDegree,
        height / 2 + (centerLat - lat) * pxPerDegree,
    )
}

/** 전국 축척(zoom = 1)에서 위도 6.4°가 화면 높이에 맞는다. */
private fun pxPerDegree(height: Float, zoom: Float) = height / 6.4f * zoom

private class Fit(val lng: Float, val lat: Float, val zoom: Float)

/** 켜진 길이 모두 보이는 위치와 축척. */
private fun fitRoads(roads: List<List<GeoDot>>, widthPx: Float, heightPx: Float): Fit {
    val dots = roads.flatten()
    val minLat = dots.minOf { it.lat }.toFloat()
    val maxLat = dots.maxOf { it.lat }.toFloat()
    val minLng = dots.minOf { it.lng }.toFloat()
    val maxLng = dots.maxOf { it.lng }.toFloat()
    val byHeight = 6.4f * 0.55f / max(maxLat - minLat, 0.001f)
    val byWidth = (widthPx / heightPx) * 6.4f * 0.55f / max((maxLng - minLng) * LNG_SCALE, 0.001f)
    return Fit((minLng + maxLng) / 2, (minLat + maxLat) / 2, min(byHeight, byWidth).coerceIn(1f, MAX_ZOOM))
}

@Composable
fun CarlingMapScreen(routes: List<MapRoute>, tracks: Map<String, List<GeoDot>>, onClose: () -> Unit) {
    // 캐시를 쓰면 같은 글자의 외곽선 스타일이 채움에도 재사용되어 글자가 흰색으로만 그려진다
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val roads = remember(routes, tracks) { routes.map { roadShape(it, tracks[it.trackKey]) } }
    val hasMadeUpRoad = remember(routes, tracks) { routes.any { tracks[it.trackKey] == null } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Sea)) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val first = remember { if (roads.isEmpty()) null else fitRoads(roads, widthPx, heightPx) }
        var centerLng by remember { mutableFloatStateOf(first?.lng ?: HOME_LNG) }
        var centerLat by remember { mutableFloatStateOf(first?.lat ?: HOME_LAT) }
        var zoom by remember { mutableFloatStateOf(first?.zoom ?: 1f) }

        fun move(lng: Float, lat: Float, newZoom: Float) {
            centerLng = lng.coerceIn(124.5f, 131f)
            centerLat = lat.coerceIn(33f, 39.2f)
            zoom = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        val scale = pxPerDegree(size.height.toFloat(), zoom)
                        move(centerLng - pan.x / (LNG_SCALE * scale), centerLat + pan.y / scale, zoom * gestureZoom)
                    }
                },
        ) {
            val view = MapView(centerLng, centerLat, pxPerDegree(size.height, zoom), size.width, size.height)
            drawDarkLand(view, zoom)
            drawRoads(view, routes, roads, measurer)
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MapPillButton("‹ 닫기", onClick = onClose)
                Text(
                    "카링의 지도",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    modifier = Modifier.background(Panel, RoundedCornerShape(24.dp)).padding(horizontal = 22.dp, vertical = 11.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MapPillButton("전국") { move(HOME_LNG, HOME_LAT, 1f) }
                if (roads.isNotEmpty()) {
                    MapPillButton("내 길 보기") {
                        val fit = fitRoads(roads, widthPx, heightPx)
                        move(fit.lng, fit.lat, fit.zoom)
                    }
                }
            }
        }

        Column(
            Modifier.align(Alignment.CenterEnd).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MapRoundButton("+") { move(centerLng, centerLat, zoom * 1.6f) }
            MapRoundButton("−") { move(centerLng, centerLat, zoom / 1.6f) }
        }

        if (routes.isEmpty()) {
            Text(
                "아직 밝혀진 길이 없어요.\n함께 달리면 여기에 길이 하나씩 켜져요.",
                fontSize = 24.sp,
                lineHeight = 36.sp,
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Panel.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 32.dp, vertical = 20.dp),
            )
        }

        if (hasMadeUpRoad) Text(
            "아직 경로를 기록 못한 곳은 불빛으로 보여요",
            fontSize = 16.sp,
            color = Muted,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .background(Panel.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        if (routes.isNotEmpty()) {
            Text(
                "밝혀진 길 ${routes.size}개",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Gold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .background(Panel, RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            )
        }
    }
}

/** 새 길 알림. 누르면 지도를 연다. */
@Composable
fun MapNotice(onOpen: () -> Unit, onDone: () -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(260))
        delay(NOTICE_MS)
        appear.animateTo(0f, tween(220))
        onDone()
    }
    Box(Modifier.fillMaxSize().padding(top = 28.dp), contentAlignment = Alignment.TopCenter) {
        Surface(
            onClick = onOpen,
            shape = RoundedCornerShape(28.dp),
            color = Night,
            modifier = Modifier
                .graphicsLayer {
                    alpha = appear.value
                    translationY = (appear.value - 1f) * 40f * density
                }
                .border(2.dp, Road, RoundedCornerShape(28.dp)),
        ) {
            Row(Modifier.padding(horizontal = 28.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🗺", fontSize = 32.sp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("새로운 길 발견!", fontSize = 17.sp, color = Gold, fontWeight = FontWeight.Bold)
                    Text("새로운 길이 지도에 반영됐어요", fontSize = 24.sp, color = Ink, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.width(20.dp))
                Text("지도 보기 ›", fontSize = 20.sp, color = Gold)
            }
        }
    }
}

private fun DrawScope.landPath(view: MapView, points: List<Pair<Float, Float>>): Path = Path().apply {
    points.forEachIndexed { i, (lng, lat) ->
        val s = view.toScreen(lng, lat)
        if (i == 0) moveTo(s.x, s.y) else lineTo(s.x, s.y)
    }
    close()
}

private fun DrawScope.drawDarkLand(view: MapView, zoom: Float) {
    listOf(Peninsula, Jeju).forEach { outline ->
        val path = landPath(view, outline)
        drawPath(path, Land)
        drawPath(path, Coast, style = Stroke(2f))
    }
    // 크게 볼 때는 길 말고 보이는 것이 없어 격자로 움직임을 알 수 있게 한다
    if (zoom < 4f) return
    val step = if (zoom >= 40f) 0.01f else 0.1f
    val halfLng = size.width / 2 / (LNG_SCALE * view.pxPerDegree)
    val halfLat = size.height / 2 / view.pxPerDegree
    var lng = floor((view.centerLng - halfLng) / step) * step
    while (lng < view.centerLng + halfLng) {
        val x = view.toScreen(lng, view.centerLat).x
        drawLine(Grid, Offset(x, 0f), Offset(x, size.height), 1f)
        lng += step
    }
    var lat = floor((view.centerLat - halfLat) / step) * step
    while (lat < view.centerLat + halfLat) {
        val y = view.toScreen(view.centerLng, lat).y
        drawLine(Grid, Offset(0f, y), Offset(size.width, y), 1f)
        lat += step
    }
}

/** 개발자 도구의 가상 주행이 붙이는 이름. 장소를 가리키지 않아 지도에 글자로 띄우지 않는다. */
private val DefaultNames = setOf("새로운 길", "늘 가던 길")

/**
 * 켜진 길. 자주 달린 길일수록 밝고 굵다.
 * 실제 경로를 기록한 길만 선으로 그리고, 기록이 없는 길은 지어낸 선 대신 도착 불빛 점으로만 표시한다.
 * 라벨은 내비 장소 이름이고, 서로 겹치면 생략한다.
 */
private fun DrawScope.drawRoads(view: MapView, routes: List<MapRoute>, roads: List<List<GeoDot>>, measurer: TextMeasurer) {
    // 1패스: 길 선과 도착 불빛
    routes.forEachIndexed { i, route ->
        val bright = (0.6f + 0.1f * route.visits).coerceAtMost(1f)
        val at = view.toScreen(route.toLng.toFloat(), route.toLat.toFloat())
        if (route.trackKey != null) {
            val path = Path().apply {
                roads[i].forEachIndexed { k, dot ->
                    val s = view.toScreen(dot.lng.toFloat(), dot.lat.toFloat())
                    if (k == 0) moveTo(s.x, s.y) else lineTo(s.x, s.y)
                }
            }
            val w = 4f + route.visits.coerceAtMost(5) * 0.8f
            drawPath(path, Road.copy(alpha = 0.16f * bright), style = Stroke(w * 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, Road.copy(alpha = bright), style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, RoadCore.copy(alpha = bright), style = Stroke(w * 0.35f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // 도착 불빛 — 기록 없는 길은 이 점이 유일한 표시다. 자주 간 곳일수록 크고 밝게.
        val glow = 13f + route.visits.coerceAtMost(6) * 3f
        drawCircle(Road.copy(alpha = 0.22f * bright), glow, at)
        drawCircle(Road.copy(alpha = 0.5f * bright), glow * 0.5f, at)
        drawCircle(RoadCore.copy(alpha = bright), if (route.trackKey != null) 6.5f else 5f, at)
    }

    // 2패스: 라벨. 이미 그린 라벨과 겹치면 건너뛴다.
    val drawn = ArrayList<Rect>()
    routes.forEach { route ->
        val name = route.name ?: return@forEach
        if (name in DefaultNames) return@forEach
        val at = view.toScreen(route.toLng.toFloat(), route.toLat.toFloat())
        if (at.x < -40f || at.y < -40f || at.x > size.width + 40f || at.y > size.height + 40f) return@forEach
        val text = name.take(14)
        val offset = at + Offset(14f, -36f)
        val measured = measurer.measure(text, TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold))
        val rect = Rect(offset.x - 3f, offset.y - 3f, offset.x + measured.size.width + 3f, offset.y + measured.size.height + 3f)
        if (drawn.any { it.overlaps(rect) }) return@forEach
        drawn += rect
        drawText(measurer, text, offset, TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Sea, drawStyle = Stroke(6f, join = StrokeJoin.Round)))
        drawText(measurer, text, offset, TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink))
    }
}

@Composable
private fun MapPillButton(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = Panel, shadowElevation = 3.dp) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp))
    }
}

@Composable
private fun MapRoundButton(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Panel, shadowElevation = 3.dp, modifier = Modifier.size(64.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
        }
    }
}
