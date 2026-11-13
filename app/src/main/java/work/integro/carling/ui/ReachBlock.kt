package work.integro.carling.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.game.Place
import work.integro.carling.game.ReachData
import work.integro.carling.game.ReachRules
import work.integro.carling.geo.AdminRegion
import work.integro.carling.geo.KoreaAtlas
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

/**
 * ⑤ 내 활동 반경. 위쪽은 위치 없이도 보이는 거리 고리, 아래쪽은 위치 동의 뒤 동네 지도.
 * [consent]가 null/false면 지도 자리에 동의 안내를 둔다.
 */
@Composable
internal fun ReachBlock(reach: ReachData, prevLabel: String, consent: Boolean?, atlas: KoreaAtlas?, onConsent: (Boolean) -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Column {
            val headline = reach.radiusKm?.let { "집 동네에서 ${ReachRules.km(it)} 안을 주로 다녀요" }
                ?: reach.typicalKm?.let { "한 번에 보통 ${ReachRules.km(it)} 안쪽을 달려요" }
            BlockTitle("⑤", "내 활동 반경", headline)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RingDiagram(reach, Modifier.size(260.dp))
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    reach.rings.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(14.dp).background(ringColor(reach.rings.indexOf(r)), CircleShape))
                            Text(r.label, fontSize = 16.sp, color = Muted, modifier = Modifier.padding(start = 8.dp).width(96.dp))
                            Text("${r.trips}회", fontSize = 16.sp, color = Ink, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ReachStat("보통 거리", reach.typicalKm?.let(ReachRules::km) ?: "—", reach.prevTypicalKm?.let { "$prevLabel ${ReachRules.km(it)}" }, Modifier.weight(1f))
                        ReachStat("가장 먼 운행", reach.farthestTripKm?.let(ReachRules::km) ?: "—", null, Modifier.weight(1f))
                    }
                }
            }
            Text("보통 거리: 이 기간 운행의 80%가 이 거리 안이에요. 1회 주행 거리 기준.", fontSize = 13.sp, color = Faint, modifier = Modifier.padding(top = 6.dp))

            Spacer(Modifier.height(16.dp))
            when {
                consent != true -> ConsentCard(consent, onConsent)
                reach.locatedTrips == 0 || reach.home == null -> Text(
                    if (reach.locatedTrips == 0) "📍 위치가 켜져 있어요. 다음 운행부터 출발·도착 동네가 남아 지도가 그려져요."
                    else "📍 동네 기록이 쌓이는 중이에요. 같은 동네에서 두 번 이상 출발하면 집 동네를 찾아 지도를 그려요.",
                    fontSize = 16.sp, color = Ink,
                    modifier = Modifier.fillMaxWidth().background(MintSoft, RoundedCornerShape(16.dp)).padding(16.dp),
                )
                else -> PlaceSection(reach, prevLabel, atlas, onConsent)
            }
        }
    }
}

@Composable
private fun ReachStat(label: String, value: String, sub: String?, modifier: Modifier) {
    Column(modifier.background(Paper, RoundedCornerShape(16.dp)).padding(12.dp)) {
        Text(label, fontSize = 14.sp, color = Muted, maxLines = 1)
        Text(value, fontSize = 21.sp, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1)
        if (sub != null) Text(sub, fontSize = 13.sp, color = Faint, maxLines = 1)
    }
}

private fun ringColor(i: Int): Color = listOf(Lilac, Color(0xFF6FB6FF), Mint, Honey, Coral).getOrElse(i) { Faint }

/** 가운데가 출발점인 과녁. 고리 굵기 대신 고리 안의 점 개수로 운행 수를 보여 준다. */
@Composable
private fun RingDiagram(reach: ReachData, modifier: Modifier) {
    val dots = remember(reach.rings) {
        reach.rings.flatMapIndexed { i, r -> List(r.trips.coerceAtMost(24)) { k -> Triple(i, k, r.trips.coerceAtMost(24)) } }
    }
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val step = size.minDimension / 2 / reach.rings.size
        for (i in reach.rings.indices.reversed()) {
            drawCircle(ringColor(i).copy(alpha = 0.10f + 0.04f * (reach.rings.size - i)), step * (i + 1), c)
            drawCircle(ringColor(i).copy(alpha = 0.45f), step * (i + 1), c, style = Stroke(2f))
        }
        // 보통 거리가 들어 있는 고리를 굵게
        reach.typicalKm?.let { t ->
            val i = reach.rings.indexOfFirst { it.toKm == null || t < it.toKm }
            if (i >= 0) drawCircle(Ink.copy(alpha = 0.7f), step * (i + 1), c, style = Stroke(4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
        }
        dots.forEach { (ring, k, n) ->
            val rad = step * (ring + 0.5f)
            val a = (k.toFloat() / n) * 2 * PI.toFloat() + ring * 0.7f
            drawCircle(Color.White, 6.5f, Offset(c.x + rad * cos(a), c.y + rad * kotlin.math.sin(a)))
            drawCircle(ringColor(ring), 4.5f, Offset(c.x + rad * cos(a), c.y + rad * kotlin.math.sin(a)))
        }
        drawCircle(Color.White, 15f, c)
        drawCircle(Ink, 9f, c)
    }
}

@Composable
private fun ConsentCard(consent: Boolean?, onConsent: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().background(LilacSoft, RoundedCornerShape(18.dp)).padding(18.dp)) {
        Text("📍 위치를 켜면 다녀온 동네를 지도로 보여 줘요", fontSize = 19.sp, color = Ink, fontWeight = FontWeight.Bold)
        Text(
            "출발·도착한 동네 이름(행정동)만 이 차 안에 저장해요. 좌표와 경로는 저장하지 않고, 밖으로 보내지 않아요. 끄면 동네 기록도 지워요." +
                if (consent == false) " (지금은 꺼져 있어요)" else "",
            fontSize = 15.sp, color = Muted, modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            "위치 켜기",
            fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp).background(Lilac, RoundedCornerShape(50)).clickable { onConsent(true) }.padding(horizontal = 24.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun PlaceSection(reach: ReachData, prevLabel: String, atlas: KoreaAtlas?, onConsent: (Boolean) -> Unit) {
    val home = reach.home ?: return
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.weight(1.1f).height(380.dp).clip(RoundedCornerShape(18.dp)).background(Paper, RoundedCornerShape(18.dp)).border(1.dp, Line, RoundedCornerShape(18.dp))) {
            if (atlas == null) Text("지도를 준비하고 있어요…", fontSize = 15.sp, color = Muted, modifier = Modifier.align(Alignment.Center))
            else PlaceMap(reach, home, atlas, Modifier.fillMaxWidth().height(380.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReachStat("활동 반경", reach.radiusKm?.let(ReachRules::km) ?: "—", reach.prevRadiusKm?.let { "$prevLabel ${ReachRules.km(it)}" }, Modifier.weight(1f))
                ReachStat("다녀온 동네", "${reach.dongCount}곳", if (reach.newPlaces.isNotEmpty()) "새 동네 ${reach.newPlaces.size}곳" else null, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReachStat("집 동네(추정)", home.name, null, Modifier.weight(1f))
                ReachStat("가장 먼 곳", reach.farthest?.place?.name ?: "—", reach.farthest?.let { ReachRules.km(it.fromHomeKm) }, Modifier.weight(1f))
            }
            Text("자주 간 곳", fontSize = 16.sp, color = Muted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            val maxV = (reach.visits.maxOfOrNull { it.visits } ?: 1).coerceAtLeast(1)
            reach.visits.take(5).forEachIndexed { i, v ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.background(Mint, CircleShape).padding(horizontal = 8.dp, vertical = 1.dp))
                    Text(v.place.name, fontSize = 16.sp, color = Ink, maxLines = 1, modifier = Modifier.padding(start = 8.dp).width(118.dp))
                    Box(Modifier.weight(1f).height(14.dp).background(Color.White, RoundedCornerShape(7.dp))) {
                        Box(Modifier.fillMaxWidth(v.visits.toFloat() / maxV).height(14.dp).background(MintSoft.copy(alpha = 1f), RoundedCornerShape(7.dp)).border(1.dp, Mint, RoundedCornerShape(7.dp)))
                    }
                    Text("${v.visits}회 · ${ReachRules.km(v.fromHomeKm)}", fontSize = 14.sp, color = Muted, modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (reach.newPlaces.isNotEmpty()) Text("✨ 처음 가 본 동네: " + reach.newPlaces.take(4).joinToString(", ") { it.name }, fontSize = 15.sp, color = Ink)
            Text("거리는 동네 중심끼리 잰 값이에요.", fontSize = 13.sp, color = Faint)
            Text("위치 끄기", fontSize = 14.sp, color = Muted, modifier = Modifier.clickable { onConsent(false) }.padding(vertical = 4.dp))
        }
    }
}

/** 집 동네를 가운데 둔 동네 지도. 다녀온 동네는 민트(많이 갈수록 진하게), 집은 라일락, 반경은 점선 원. */
@Composable
private fun PlaceMap(reach: ReachData, home: Place, atlas: KoreaAtlas, modifier: Modifier) {
    val kx = cos(home.lat * PI / 180) * 111.32
    val ky = 110.57
    val extent = max(3.0, max((reach.radiusKm ?: 0.0) * 1.35, (reach.farthest?.fromHomeKm ?: 0.0) * 1.15))
    val visits = remember(reach) { reach.visits.associate { it.place.code to it.visits } }
    val maxV = (visits.values.maxOrNull() ?: 1).coerceAtLeast(1)
    // 지도 범위 안의 동네만 km 좌표 경로로 미리 만든다
    val shapes = remember(atlas, home.code, extent) {
        val dLng = extent / kx
        val dLat = extent / ky
        fun inBox(r: AdminRegion) = r.maxLng >= home.lng - dLng && r.minLng <= home.lng + dLng && r.maxLat >= home.lat - dLat && r.minLat <= home.lat + dLat
        fun path(r: AdminRegion) = Path().apply {
            r.rings.forEach { ring ->
                for (i in ring.indices step 2) {
                    val x = ((ring[i] - home.lng) * kx).toFloat()
                    val y = (-(ring[i + 1] - home.lat) * ky).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        }
        atlas.dongs.filter(::inBox).map { it.code to path(it) } to atlas.municipalities.filter(::inBox).map { path(it) }
    }
    val markers = remember(reach) {
        (reach.visits.map { it.place to it.visits } + (home to 0)).map { (p, v) ->
            Triple(((p.lng - home.lng) * kx).toFloat(), (-(p.lat - home.lat) * ky).toFloat(), v)
        }
    }
    val labels = remember(reach) {
        (listOf(home to "🏠 ${home.name}") + (listOfNotNull(reach.farthest) + reach.visits.take(4)).distinct().map { it.place to it.place.name }).map { (p, n) ->
            Triple(((p.lng - home.lng) * kx).toFloat(), (-(p.lat - home.lat) * ky).toFloat(), n)
        }
    }
    Canvas(modifier) {
        val scale = (size.minDimension / 2 / extent).toFloat()
        val c = Offset(size.width / 2, size.height / 2)
        withTransform({ translate(c.x, c.y); scale(scale, scale, Offset.Zero) }) {
            val hair = 1.2f / scale
            val fine = extent <= 8.0
            shapes.first.forEach { (code, p) ->
                val v = visits[code]
                val fill = when {
                    code == home.code -> Lilac.copy(alpha = 0.75f)
                    v != null -> Mint.copy(alpha = 0.30f + 0.55f * v / maxV)
                    else -> Color.White
                }
                // 넓게 볼 땐 동 경계를 그리지 않고 다녀온 동네만 칠한다(시군구 경계가 밑그림)
                if (fine || fill != Color.White) drawPath(p, fill)
                if (fine) drawPath(p, Line, style = Stroke(hair))
            }
            shapes.second.forEach { drawPath(it, Faint, style = Stroke(hair * 2.2f)) }
            reach.radiusKm?.let { drawCircle(Ink.copy(alpha = 0.6f), it.toFloat(), Offset.Zero, style = Stroke(hair * 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f / scale, 8f / scale)))) }
        }
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true; textSize = 26f; color = Ink.toArgb(); typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(6f, 0f, 0f, android.graphics.Color.WHITE)
        }
        // 동네 점: 많이 갈수록 크게. 집은 라일락 큰 점
        markers.forEach { (x, y, v) ->
            val o = Offset(c.x + x * scale, c.y + y * scale)
            val r = if (v == 0) 13f else 7f + 7f * v / maxV
            drawCircle(Color.White, r + 3f, o)
            drawCircle(if (v == 0) Lilac else Mint, r, o)
        }
        // 이름표: 겹치면 건너뛴다(집이 먼저)
        val placed = mutableListOf<android.graphics.RectF>()
        labels.forEach { (x, y, n) ->
            val w = paint.measureText(n)
            val left = (c.x + x * scale - w / 2).coerceIn(6f, size.width - w - 6f)
            val base = (c.y + y * scale - 18f).coerceIn(30f, size.height - 8f)
            val box = android.graphics.RectF(left - 4f, base - 26f, left + w + 4f, base + 6f)
            if (placed.none { android.graphics.RectF.intersects(it, box) }) {
                placed += box
                drawContext.canvas.nativeCanvas.drawText(n, left, base, paint)
            }
        }
        reach.radiusKm?.let { r ->
            val t = "반경 ${ReachRules.km(r)}"
            val sp = android.graphics.Paint(paint).apply { textSize = 22f; color = Muted.toArgb() }
            drawContext.canvas.nativeCanvas.drawText(t, (c.x + (r * scale).toFloat() * 0.7f + 6f).coerceAtMost(size.width - sp.measureText(t) - 6f), (c.y - (r * scale).toFloat() * 0.7f).coerceAtLeast(26f), sp)
        }
    }
}
