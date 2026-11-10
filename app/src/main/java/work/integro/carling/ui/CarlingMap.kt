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
import work.integro.carling.data.AdminRegion
import work.integro.carling.data.FOG_CELL_KM2
import work.integro.carling.data.FOG_REVEAL_KM
import work.integro.carling.data.KoreaAtlas
import work.integro.carling.data.densify
import work.integro.carling.data.fogCellCenter
import work.integro.carling.data.revealedCells
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * 카링의 지도 — 대한민국 행정구역 지도 위에 안개가 덮여 있고, 달린 길 주변 1km만 밝혀진다(미니맵의 안개 걷기).
 * 처음에는 전국 필터뿐이고, 다닌 시·도 → 시군구 → 읍면동마다 필터가 생겨 누르면 그 지역으로 들어간다.
 * 읍면동은 MVP에서 경계와 이름만 보여준다.
 */

private val Sea = Color(0xFF070B1E)
private val Land = Color(0xFF2E4A7A)
private val Border = Color(0xFF5A7BB4)
private val Fog = Color(0xD905070F)
private val Road = Color(0xFF9FE1CB)
private val Spot = Color(0xFFFAC775)
private const val NOTICE_MS = 4_500L
private const val LNG_SCALE = 0.81f

private class MapView(val centerLng: Float, val centerLat: Float, val pxPerDegree: Float, val width: Float, val height: Float) {
    fun toScreen(lng: Float, lat: Float) = Offset(
        width / 2 + (lng - centerLng) * LNG_SCALE * pxPerDegree,
        height / 2 + (centerLat - lat) * pxPerDegree,
    )
    val pxPerKm get() = pxPerDegree / 110.57f
}

/** 지도 화면 하나에 필요한 계산 결과. 무거우니 한 번만 만든다. */
private class FogMap(
    val atlas: KoreaAtlas,
    val roads: List<List<GeoDot>>,
    /** 밝혀진 격자 칸과 그 칸이 속한 가장 작은 행정구역 코드. */
    val cellCodes: Map<Long, String?>,
    /** 실제로 지나간 행정구역 코드(시·도/시군구/읍면동 모두). */
    val visited: Set<String>,
) {
    fun percent(code: String?): Float {
        val area = code?.let { atlas.region(it)?.areaKm2 } ?: atlas.provinces.sumOf { it.areaKm2 }
        val cells = if (code == null) cellCodes.values.count { it != null } else cellCodes.values.count { it?.startsWith(code) == true }
        return (cells * FOG_CELL_KM2 / area * 100).toFloat().coerceIn(0f, 100f)
    }
}

private fun buildFogMap(atlas: KoreaAtlas, routes: List<MapRoute>, tracks: Map<String, List<GeoDot>>): FogMap {
    // 경로를 기록하지 못한 길은 도착지 주변만 밝힌다
    val roads = routes.map { r -> tracks[r.trackKey] ?: listOf(GeoDot(r.toLat, r.toLng)) }
    val cells = revealedCells(roads)
    val cellCodes = cells.associateWith { c -> fogCellCenter(c).let { (la, ln) -> atlas.locate(ln.toFloat(), la.toFloat()) } }
    val visited = HashSet<String>()
    roads.forEach { r ->
        densify(r).forEachIndexed { i, p ->
            if (i % 3 != 0) return@forEachIndexed
            atlas.locate(p.lng.toFloat(), p.lat.toFloat())?.let { code -> visited += code.take(2); if (code.length >= 5) visited += code.take(5); if (code.length >= 7) visited += code }
        }
    }
    return FogMap(atlas, roads, cellCodes, visited)
}

@Composable
fun CarlingMapScreen(routes: List<MapRoute>, tracks: Map<String, List<GeoDot>>, onClose: () -> Unit) {
    val context = LocalContext.current
    val measurer = rememberTextMeasurer(cacheSize = 0)
    var fog by remember { mutableStateOf<FogMap?>(null) }
    LaunchedEffect(routes, tracks) {
        fog = withContext(Dispatchers.Default) { buildFogMap(KoreaAtlas.load(context), routes, tracks) }
    }
    // 지금 들어가 있는 지역: null = 전국, 2자리 = 시·도, 5자리 = 시군구, 7자리 = 읍면동
    var focus by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Sea)) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        var centerLng by remember { mutableFloatStateOf(127.8f) }
        var centerLat by remember { mutableFloatStateOf(36.0f) }
        var scale by remember { mutableFloatStateOf(heightPx / 6.2f) }
        val home = heightPx / 6.2f

        fun fit(r: AdminRegion?) {
            if (r == null) { centerLng = 127.8f; centerLat = 36.0f; scale = home; return }
            centerLng = r.centerLng; centerLat = r.centerLat
            val byH = heightPx * 0.8f / (r.maxLat - r.minLat).coerceAtLeast(0.005f)
            val byW = widthPx * 0.8f / ((r.maxLng - r.minLng) * LNG_SCALE).coerceAtLeast(0.005f)
            scale = minOf(byH, byW)
        }

        val f = fog
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(home * 0.8f, home * 600f)
                    centerLng = (centerLng - pan.x / (LNG_SCALE * scale)).coerceIn(124f, 132f)
                    centerLat = (centerLat + pan.y / scale).coerceIn(33f, 39f)
                }
            },
        ) {
            if (f != null) {
                val focusRegion = focus?.let { f.atlas.region(it) }
                // 1. 땅과 경계
                Canvas(Modifier.fillMaxSize()) {
                    val v = MapView(centerLng, centerLat, scale, size.width, size.height)
                    f.atlas.provinces.forEach { drawRegion(v, it, Land, Border, 1.2f) }
                    // 들어간 단계의 하위 경계
                    val inner = when {
                        focus == null -> emptyList()
                        focus!!.length == 2 -> f.atlas.children(focus)
                        else -> f.atlas.children(focus!!.take(5))
                    }
                    inner.forEach { drawRegion(v, it, null, Border.copy(alpha = 0.7f), 0.8f) }
                    focusRegion?.let { drawRegion(v, it, null, Color(0xFFB5D4F4), 2f) }
                }
                // 2. 안개 — 길 주변 1km를 지운다
                Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                    val v = MapView(centerLng, centerLat, scale, size.width, size.height)
                    drawRect(Fog)
                    val w = (FOG_REVEAL_KM * 2).toFloat() * v.pxPerKm
                    f.roads.forEach { r ->
                        if (r.size >= 2) drawPath(roadPath(v, r), Color.Black, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Clear)
                        else r.firstOrNull()?.let { drawCircle(Color.Black, w / 2, v.toScreen(it.lng.toFloat(), it.lat.toFloat()), blendMode = BlendMode.Clear) }
                    }
                }
                // 3. 달린 길, 다녀온 곳, 지역 이름
                Canvas(Modifier.fillMaxSize()) {
                    val v = MapView(centerLng, centerLat, scale, size.width, size.height)
                    // 안개 위에도 나라 모양은 희미하게 보이게
                    f.atlas.provinces.forEach { drawRegion(v, it, null, Border.copy(alpha = 0.35f), 0.8f) }
                    f.roads.forEach { r ->
                        if (r.size >= 2) drawPath(roadPath(v, r), Road, style = Stroke(2.5f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        r.lastOrNull()?.let { p -> val o = v.toScreen(p.lng.toFloat(), p.lat.toFloat()); drawCircle(Spot.copy(alpha = 0.3f), 9f * density, o); drawCircle(Spot, 4f * density, o) }
                    }
                    val labels = when {
                        focus == null -> f.atlas.provinces
                        focus!!.length == 2 -> f.atlas.children(focus)
                        else -> f.atlas.children(focus!!.take(5))
                    }
                    labels.forEach { r ->
                        val o = v.toScreen(r.centerLng, r.centerLat)
                        if (o.x < 0 || o.y < 0 || o.x > size.width || o.y > size.height) return@forEach
                        val seen = r.code in f.visited
                        val text = if (r.code.length == 2) r.shortName else r.name
                        val layout = measurer.measure(text, TextStyle(fontSize = if (seen) 15.sp else 12.sp, color = if (seen) Ink else Color(0x99B5BCD8), fontWeight = if (seen) FontWeight.Bold else FontWeight.Normal))
                        drawText(layout, topLeft = o - Offset(layout.size.width / 2f, layout.size.height / 2f))
                    }
                }
            }
        }

        // 위: 닫기 · 제목 · 필터
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MapPillButton("‹ 닫기", onClick = onClose)
                Text("카링의 지도", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.background(Panel, RoundedCornerShape(24.dp)).padding(horizontal = 22.dp, vertical = 11.dp))
            }
            if (f != null) {
                // 지금까지 들어온 경로(전국 › 서울 › 성동구)와 다음 단계 필터
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterPill("전국", selected = focus == null) { focus = null; fit(null) }
                    listOfNotNull(focus?.take(2), focus?.takeIf { it.length >= 5 }?.take(5), focus?.takeIf { it.length >= 7 }).forEach { code ->
                        val r = f.atlas.region(code) ?: return@forEach
                        Text("›", fontSize = 20.sp, color = Muted)
                        FilterPill(if (code.length == 2) r.shortName else r.name, selected = focus == code) { focus = code; fit(r) }
                    }
                }
                val next = f.atlas.children(focus?.let { if (it.length >= 7) it.take(5) else it }).filter { it.code in f.visited && it.code != focus }
                if (next.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        next.forEach { r -> FilterPill(if (r.code.length == 2) r.shortName else r.name, selected = false) { focus = r.code; fit(r) } }
                    }
                }
            }
        }

        Column(Modifier.align(Alignment.CenterEnd).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MapRoundButton("+") { scale = (scale * 1.6f).coerceAtMost(home * 600f) }
            MapRoundButton("−") { scale = (scale / 1.6f).coerceAtLeast(home * 0.8f) }
        }

        when {
            f == null -> Text("지도를 펼치는 중…", fontSize = 22.sp, color = Ink, modifier = Modifier.align(Alignment.Center))
            routes.isEmpty() -> Text(
                "아직 밝혀진 땅이 없어요.\n함께 달리면 지나간 길 주변이 하나씩 밝아져요.",
                fontSize = 24.sp, lineHeight = 36.sp, color = Ink, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).background(Panel.copy(alpha = 0.85f), RoundedCornerShape(24.dp)).padding(horizontal = 32.dp, vertical = 20.dp),
            )
        }
        if (f != null && routes.isNotEmpty()) {
            val name = focus?.let { f.atlas.region(it) }?.let { if (it.code.length == 2) it.shortName else it.name } ?: "전국"
            Text(
                "$name 밝혀진 땅 ${"%.1f".format(f.percent(focus))}%",
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Gold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).background(Panel, RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 9.dp),
            )
            Text(
                "달린 길 주변 1km가 밝혀져요 · 다닌 지역은 필터로 생겨요",
                fontSize = 16.sp, color = Muted,
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp).background(Panel.copy(alpha = 0.85f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private fun DrawScope.roadPath(v: MapView, r: List<GeoDot>) = Path().apply {
    r.forEachIndexed { i, p -> val o = v.toScreen(p.lng.toFloat(), p.lat.toFloat()); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
}

private fun DrawScope.drawRegion(v: MapView, r: AdminRegion, fill: Color?, stroke: Color, widthDp: Float) {
    // 화면 밖이면 그리지 않는다
    val a = v.toScreen(r.minLng, r.maxLat); val b = v.toScreen(r.maxLng, r.minLat)
    if (b.x < 0 || b.y < 0 || a.x > size.width || a.y > size.height) return
    r.rings.forEach { ring ->
        val path = Path().apply {
            for (i in ring.indices step 2) { val o = v.toScreen(ring[i], ring[i + 1]); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
            close()
        }
        if (fill != null) drawPath(path, fill)
        drawPath(path, stroke, style = Stroke(widthDp * density))
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = if (selected) Color(0xFF5DCAA5) else Panel) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (selected) Color(0xFF04342C) else Ink, modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp))
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
