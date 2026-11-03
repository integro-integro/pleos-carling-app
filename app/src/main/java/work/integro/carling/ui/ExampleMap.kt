package work.integro.carling.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 네이버 지도와 비슷한 느낌의 예시 지도. 지도 SDK 없이 Canvas로 그린다.
 * 해안선·도로는 위경도를 따라 단순화한 그림이라 내비게이션에 쓸 수 없고, 내 트립 경로도 아직 표시하지 않는다.
 */

private val Sea = Color(0xFFB4D9F2)
private val Land = Color(0xFFF4F1E9)
private val Park = Color(0xFFCFE8C2)
private val Expressway = Color(0xFFF6C66F)
private val ExpresswayEdge = Color(0xFFD99F3F)
private val CityRoad = Color(0xFFFFFFFF)
private val CityBlock = Color(0xFFECE8DE)
private val PinGreen = Color(0xFF03C75A)
private val MapInk = Color(0xFF26303D)

private const val LNG_SCALE = 0.81f // 위도 36° 부근에서 경도 1°의 상대 길이
private const val MIN_ZOOM = 0.8f
private const val MAX_ZOOM = 14f

private data class GeoPoint(val lng: Float, val lat: Float)

private data class MapPlace(
    val name: String,
    val at: GeoPoint,
    val focusZoom: Float,
    /** 전국 축척에서 서로 겹치지 않도록 이름을 옮기는 값. */
    val farLabelDx: Float = 16f,
    val farLabelDy: Float = -20f,
)

private val Places = listOf(
    MapPlace("양평", GeoPoint(127.487f, 37.491f), 9f),
    MapPlace("성수", GeoPoint(127.056f, 37.544f), 12f, farLabelDx = 18f, farLabelDy = -44f),
    MapPlace("강남", GeoPoint(127.047f, 37.517f), 12f, farLabelDx = 18f, farLabelDy = -4f),
    MapPlace("강서", GeoPoint(126.850f, 37.551f), 12f, farLabelDx = -62f, farLabelDy = -40f),
    MapPlace("강원도", GeoPoint(128.250f, 37.750f), 3f),
    MapPlace("대구", GeoPoint(128.600f, 35.870f), 9f),
    MapPlace("부산", GeoPoint(129.075f, 35.180f), 9f),
)

private val Peninsula = listOf(
    GeoPoint(124.6f, 40.2f), GeoPoint(124.9f, 39.2f), GeoPoint(125.1f, 38.3f), GeoPoint(125.4f, 37.72f),
    GeoPoint(126.0f, 37.72f), GeoPoint(126.42f, 37.68f), GeoPoint(126.58f, 37.42f), GeoPoint(126.62f, 37.12f),
    GeoPoint(126.86f, 36.95f), GeoPoint(126.42f, 36.98f), GeoPoint(126.15f, 36.72f), GeoPoint(126.38f, 36.36f),
    GeoPoint(126.52f, 36.2f), GeoPoint(126.6f, 35.97f), GeoPoint(126.47f, 35.63f), GeoPoint(126.4f, 35.3f),
    GeoPoint(126.3f, 34.78f), GeoPoint(126.52f, 34.32f), GeoPoint(126.95f, 34.42f), GeoPoint(127.32f, 34.5f),
    GeoPoint(127.72f, 34.7f), GeoPoint(128.02f, 34.82f), GeoPoint(128.42f, 34.84f), GeoPoint(128.72f, 34.78f),
    GeoPoint(129.0f, 35.04f), GeoPoint(129.22f, 35.18f), GeoPoint(129.42f, 35.48f), GeoPoint(129.57f, 36.05f),
    GeoPoint(129.42f, 36.42f), GeoPoint(129.45f, 37.0f), GeoPoint(129.2f, 37.45f), GeoPoint(128.95f, 37.76f),
    GeoPoint(128.6f, 38.2f), GeoPoint(128.36f, 38.62f), GeoPoint(128.0f, 39.0f), GeoPoint(127.5f, 39.3f),
    GeoPoint(127.6f, 39.8f), GeoPoint(128.3f, 40.2f), GeoPoint(129.6f, 41.5f), GeoPoint(124.6f, 41.5f),
)

private val Jeju = listOf(
    GeoPoint(126.15f, 33.3f), GeoPoint(126.35f, 33.47f), GeoPoint(126.7f, 33.55f), GeoPoint(126.95f, 33.46f),
    GeoPoint(126.82f, 33.3f), GeoPoint(126.45f, 33.22f),
)

private val HanRiver = listOf(
    GeoPoint(127.72f, 37.55f), GeoPoint(127.49f, 37.5f), GeoPoint(127.32f, 37.53f), GeoPoint(127.18f, 37.57f),
    GeoPoint(127.09f, 37.54f), GeoPoint(127.03f, 37.53f), GeoPoint(126.96f, 37.52f), GeoPoint(126.88f, 37.56f),
    GeoPoint(126.75f, 37.62f), GeoPoint(126.6f, 37.72f),
)

private val Expressways = listOf(
    // 경부
    listOf(GeoPoint(127.03f, 37.5f), GeoPoint(127.13f, 37.0f), GeoPoint(127.38f, 36.36f), GeoPoint(128.0f, 36.15f), GeoPoint(128.6f, 35.88f), GeoPoint(129.05f, 35.25f)),
    // 영동
    listOf(GeoPoint(126.72f, 37.42f), GeoPoint(127.1f, 37.3f), GeoPoint(127.6f, 37.35f), GeoPoint(128.0f, 37.46f), GeoPoint(128.5f, 37.65f), GeoPoint(128.88f, 37.75f)),
    // 경강(양평 방면)
    listOf(GeoPoint(127.1f, 37.53f), GeoPoint(127.49f, 37.47f), GeoPoint(127.95f, 37.35f)),
    // 서울양양
    listOf(GeoPoint(127.15f, 37.63f), GeoPoint(127.73f, 37.82f), GeoPoint(128.3f, 37.93f), GeoPoint(128.62f, 38.05f)),
    // 서해안
    listOf(GeoPoint(126.85f, 37.42f), GeoPoint(126.8f, 36.9f), GeoPoint(126.62f, 36.3f), GeoPoint(126.68f, 35.5f), GeoPoint(126.55f, 34.85f)),
    // 호남·남해
    listOf(GeoPoint(127.38f, 36.36f), GeoPoint(127.1f, 35.82f), GeoPoint(126.85f, 35.2f), GeoPoint(127.7f, 35.0f), GeoPoint(128.6f, 35.2f), GeoPoint(129.05f, 35.25f)),
)

/** 산과 공원: 중심과 반지름(위도 기준 도). */
private val Parks = listOf(
    GeoPoint(128.35f, 38.1f) to 0.17f, // 설악
    GeoPoint(128.1f, 37.6f) to 0.2f,
    GeoPoint(128.65f, 37.3f) to 0.22f,
    GeoPoint(127.7f, 35.35f) to 0.2f, // 지리
    GeoPoint(127.75f, 37.72f) to 0.14f,
    GeoPoint(127.6f, 37.5f) to 0.08f,
    GeoPoint(128.65f, 35.77f) to 0.06f, // 앞산
    GeoPoint(129.06f, 35.26f) to 0.03f, // 금정
    GeoPoint(126.985f, 37.67f) to 0.04f, // 북한
    GeoPoint(126.99f, 37.551f) to 0.008f, // 남산
    GeoPoint(127.04f, 37.545f) to 0.005f, // 서울숲
)

private class MapView(val center: GeoPoint, val pxPerDegree: Float, val width: Float, val height: Float) {
    fun toScreen(p: GeoPoint) = Offset(
        width / 2 + (p.lng - center.lng) * LNG_SCALE * pxPerDegree,
        height / 2 + (center.lat - p.lat) * pxPerDegree,
    )
}

/** 지도 화면. 예시 데이터임을 화면에 밝힌다. */
@Composable
fun ExampleMapScreen(onClose: () -> Unit) {
    var centerLng by remember { mutableFloatStateOf(HOME_LNG) }
    var centerLat by remember { mutableFloatStateOf(HOME_LAT) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var selected by remember { mutableStateOf<String?>(null) }
    // 캐시를 쓰면 같은 글자의 외곽선 스타일이 채움에도 재사용되어 글자가 흰색으로만 그려진다
    val measurer = rememberTextMeasurer(cacheSize = 0)

    fun move(lng: Float, lat: Float, newZoom: Float) {
        centerLng = lng.coerceIn(124.5f, 131f)
        centerLat = lat.coerceIn(33f, 39.2f)
        zoom = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    Box(Modifier.fillMaxSize().background(Sea)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        val scale = pxPerDegree(size.height.toFloat(), zoom)
                        move(
                            centerLng - pan.x / (LNG_SCALE * scale),
                            centerLat + pan.y / scale,
                            zoom * gestureZoom,
                        )
                    }
                },
        ) {
            val view = MapView(GeoPoint(centerLng, centerLat), pxPerDegree(size.height, zoom), size.width, size.height)
            drawBaseMap(view, zoom)
            Places.forEach { drawPlace(view, it, zoom, it.name == selected, measurer) }
        }

        // 위: 닫기와 지역 버튼
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MapPillButton("‹ 닫기", onClick = onClose)
                Text(
                    "카링의 지도",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MapInk,
                    modifier = Modifier.background(Color.White, RoundedCornerShape(24.dp)).padding(horizontal = 22.dp, vertical = 11.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MapPillButton("전국", active = selected == null && zoom == 1f) {
                    selected = null
                    move(HOME_LNG, HOME_LAT, 1f)
                }
                MapPillButton("수도권") {
                    selected = null
                    move(127.17f, 37.53f, 7f)
                }
                Places.forEach { place ->
                    MapPillButton(place.name, active = selected == place.name) {
                        selected = place.name
                        move(place.at.lng, place.at.lat, place.focusZoom)
                    }
                }
            }
        }

        // 오른쪽: 확대·축소
        Column(
            Modifier.align(Alignment.CenterEnd).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MapRoundButton("+") { move(centerLng, centerLat, zoom * 1.6f) }
            MapRoundButton("−") { move(centerLng, centerLat, zoom / 1.6f) }
        }

        Text(
            "예시 지도 · 실제 지도 데이터가 아니에요",
            fontSize = 16.sp,
            color = MapInk,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private const val HOME_LNG = 127.9f
private const val HOME_LAT = 36.05f

/** 전국 축척(zoom = 1)에서 위도 6.4°가 화면 높이에 맞는다. */
private fun pxPerDegree(height: Float, zoom: Float) = height / 6.4f * zoom

private fun DrawScope.geoPath(view: MapView, points: List<GeoPoint>, closed: Boolean): Path = Path().apply {
    points.forEachIndexed { i, p ->
        val s = view.toScreen(p)
        if (i == 0) moveTo(s.x, s.y) else lineTo(s.x, s.y)
    }
    if (closed) close()
}

private fun DrawScope.drawBaseMap(view: MapView, zoom: Float) {
    val scale = view.pxPerDegree
    drawPath(geoPath(view, Peninsula, closed = true), Land)
    drawPath(geoPath(view, Jeju, closed = true), Land)

    Parks.forEach { (at, radius) -> drawCircle(Park, radius * scale, view.toScreen(at)) }

    if (zoom >= 4f) drawCityBlocks(view)

    // 한강 — 확대할수록 실제 폭(약 1km)에 가까워진다
    drawPath(
        geoPath(view, HanRiver, closed = false),
        Sea,
        style = Stroke(maxOf(3f, 0.012f * scale), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    Expressways.forEach { road ->
        val path = geoPath(view, road, closed = false)
        val w = (2.5f + zoom * 0.7f).coerceAtMost(11f)
        drawPath(path, ExpresswayEdge, style = Stroke(w + 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, Expressway, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** 확대했을 때 도시 주변에 보이는 격자 도로. 실제 도로가 아닌 분위기용 그림이다. */
private fun DrawScope.drawCityBlocks(view: MapView) {
    val alpha = 1f
    val cities = listOf(
        GeoPoint(127.0f, 37.53f) to 0.19f,
        GeoPoint(127.487f, 37.491f) to 0.035f,
        GeoPoint(128.6f, 35.87f) to 0.09f,
        GeoPoint(129.075f, 35.18f) to 0.09f,
    )
    cities.forEach { (center, radius) ->
        val c = view.toScreen(center)
        val r = radius * view.pxPerDegree
        if (r < 100f) return@forEach // 아직 작게 보이면 격자가 점처럼 뭉개진다
        if (c.x + r < 0 || c.x - r > size.width || c.y + r < 0 || c.y - r > size.height) return@forEach
        drawCircle(CityBlock, r, c)
        val step = (radius / 7f)
        var d = -radius
        while (d <= radius) {
            val half = kotlin.math.sqrt(radius * radius - d * d) * view.pxPerDegree
            val offset = d * view.pxPerDegree
            drawLine(CityRoad.copy(alpha = alpha), Offset(c.x - half, c.y + offset), Offset(c.x + half, c.y + offset), 2.5f)
            drawLine(CityRoad.copy(alpha = alpha), Offset(c.x + offset, c.y - half), Offset(c.x + offset, c.y + half), 2.5f)
            d += step
        }
    }
}

private fun DrawScope.drawPlace(view: MapView, place: MapPlace, zoom: Float, selected: Boolean, measurer: TextMeasurer) {
    val at = view.toScreen(place.at)
    val pin = if (selected) 1.25f else 1f
    val head = 14f * pin
    val top = at - Offset(0f, 34f * pin)
    val tail = Path().apply {
        moveTo(at.x, at.y)
        lineTo(top.x - head * 0.8f, top.y + head * 0.5f)
        lineTo(top.x + head * 0.8f, top.y + head * 0.5f)
        close()
    }
    drawCircle(Color.Black.copy(alpha = 0.18f), 5f, at)
    drawPath(tail, PinGreen)
    drawCircle(PinGreen, head, top)
    drawCircle(Color.White, head * 0.42f, top)

    val far = zoom < 3f
    val label = if (far) Offset(at.x + place.farLabelDx, at.y + place.farLabelDy) else Offset(at.x + head + 6f, at.y - 44f * pin)
    drawOutlinedText(measurer, place.name, label, selected)
}

private fun DrawScope.drawOutlinedText(measurer: TextMeasurer, text: String, topLeft: Offset, bold: Boolean) {
    val weight = if (bold) FontWeight.ExtraBold else FontWeight.Bold
    val outline = TextStyle(fontSize = 21.sp, fontWeight = weight, color = Color.White, drawStyle = Stroke(7f, join = StrokeJoin.Round))
    val fill = TextStyle(fontSize = 21.sp, fontWeight = weight, color = MapInk)
    drawText(measurer, text, topLeft, outline)
    drawText(measurer, text, topLeft, fill)
}

@Composable
private fun MapPillButton(text: String, active: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = if (active) PinGreen else Color.White, shadowElevation = 3.dp) {
        Text(
            text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = if (active) Color.White else MapInk,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp),
        )
    }
}

@Composable
private fun MapRoundButton(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.White, shadowElevation = 3.dp, modifier = Modifier.size(64.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MapInk)
        }
    }
}
