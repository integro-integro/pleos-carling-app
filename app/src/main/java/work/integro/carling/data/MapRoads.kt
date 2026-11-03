package work.integro.carling.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.sin
import kotlin.random.Random

/** 지도 위의 점(위도, 경도). */
data class GeoDot(val lat: Double, val lng: Double)

/** 트립이 끝난 뒤 지도에 일어난 변화. [newRoute]면 새 길이고, 아니면 달렸던 길이 더 밝아졌거나 위치를 몰라 변화가 없다. */
data class MapChange(val routes: List<MapRoute>, val newRoute: Boolean, val lit: Boolean)

/** 위치를 약 1km 단위로 어림한다. */
fun coarseKm(degree: Double): Double = round(degree * 100.0) / 100.0

/**
 * 끝난 트립을 지도에 반영한다. 위치를 알면 트립마다 길이 켜진다.
 * 처음 이어지는 출발지·도착지면 새 길을 더하고, 달렸던 길이면 방문 수를 올려 더 밝게 한다.
 * 출발지를 모르거나 1km 안에서 돌아온 트립은 도착지를 지나는 고리 길로 친다.
 */
fun List<MapRoute>.afterTrip(
    fromLat: Double?,
    fromLng: Double?,
    toLat: Double?,
    toLng: Double?,
    name: String?,
    distanceKm: Float?,
    date: String,
): MapChange {
    if (toLat == null || toLng == null) return MapChange(this, newRoute = false, lit = false)
    val tLat = coarseKm(toLat)
    val tLng = coarseKm(toLng)
    val fLat = fromLat?.let(::coarseKm)
    val fLng = fromLng?.let(::coarseKm)
    val moved = fLat != null && fLng != null && (fLat != tLat || fLng != tLng)
    val known = indexOfLast {
        if (moved) it.connects(fLat!!, fLng!!, tLat, tLng)
        else it.fromLat == null && it.toLat == tLat && it.toLng == tLng
    }
    if (known >= 0) {
        val updated = toMutableList()
        val old = updated[known]
        updated[known] = old.copy(visits = old.visits + 1, name = name ?: old.name)
        return MapChange(updated, newRoute = false, lit = true)
    }
    val route = MapRoute(
        toLat = tLat,
        toLng = tLng,
        fromLat = if (moved) fLat else null,
        fromLng = if (moved) fLng else null,
        date = date,
        name = name,
        distanceKm = distanceKm,
    )
    return MapChange(this + route, newRoute = true, lit = true)
}

/**
 * 실제 경로를 기록하지 않으므로 길 모양은 지어낸다. 직선이 아니라 도로처럼 굽은 선을 만들고,
 * 같은 길은 언제나 같은 모양이 되도록 좌표로 정한다.
 */
fun roadShape(route: MapRoute): List<GeoDot> {
    val fromLat = route.fromLat
    val fromLng = route.fromLng
    val to = GeoDot(route.toLat, route.toLng)
    return if (fromLat != null && fromLng != null) winding(GeoDot(fromLat, fromLng), to) else loop(to, route.distanceKm)
}

private const val LNG_PER_LAT = 1.23 // 위도 36° 부근 경도 1°는 위도 1°의 약 0.81배 길이
private const val WINDING_LEVELS = 5
private const val WINDING_AMOUNT = 0.17
private const val MIN_LOOP_KM = 0.5
private const val MAX_LOOP_KM = 6.0
private const val KM_PER_DEGREE = 111.0

private fun seedOf(a: GeoDot, b: GeoDot) = listOf(a.lat, a.lng, b.lat, b.lng).hashCode()

/** 두 점 사이를 중간점을 옆으로 밀어 가며 굽힌다. */
private fun winding(from: GeoDot, to: GeoDot): List<GeoDot> {
    // 왕복이 같은 모양이 되도록 좌표 순서를 고정해 씨앗을 정한다
    val (a, b) = if (from.lat < to.lat || (from.lat == to.lat && from.lng < to.lng)) from to to else to to from
    val rnd = Random(seedOf(a, b))
    var points = listOf(a, b)
    var amount = WINDING_AMOUNT
    repeat(WINDING_LEVELS) {
        val next = ArrayList<GeoDot>(points.size * 2)
        for (i in 0 until points.size - 1) {
            val p = points[i]
            val q = points[i + 1]
            next += p
            val dx = (q.lng - p.lng) / LNG_PER_LAT
            val dy = q.lat - p.lat
            val push = (rnd.nextDouble() * 2 - 1) * amount
            next += GeoDot((p.lat + q.lat) / 2 + dx * push, (p.lng + q.lng) / 2 - dy * push * LNG_PER_LAT)
        }
        next += points.last()
        points = next
        amount *= 0.72
    }
    return if (a == from) points else points.asReversed()
}

/** 도착지에서 나갔다 돌아오는 고리 길. 크기는 달린 거리로 정한다. */
private fun loop(at: GeoDot, distanceKm: Float?): List<GeoDot> {
    val rnd = Random(seedOf(at, at))
    val lengthKm = (distanceKm?.toDouble() ?: 0.0).coerceIn(MIN_LOOP_KM * 2 * PI, MAX_LOOP_KM * 2 * PI)
    val radius = lengthKm / (2 * PI) / KM_PER_DEGREE
    val heading = rnd.nextDouble() * 2 * PI
    val wobbleA = rnd.nextDouble() * 2 * PI
    val wobbleB = rnd.nextDouble() * 2 * PI
    val centerLat = at.lat - sin(heading) * radius
    val centerLng = at.lng - cos(heading) * radius * LNG_PER_LAT
    val count = 36
    return List(count + 1) { i ->
        val t = i.toDouble() / count
        val angle = heading + t * 2 * PI
        // 도착지에서는 흔들리지 않아야 고리가 닫힌다
        val fade = sin(t * PI)
        val r = radius * (1 + fade * (0.32 * sin(3 * angle + wobbleA) + 0.16 * sin(7 * angle + wobbleB)))
        GeoDot(centerLat + sin(angle) * r, centerLng + cos(angle) * r * LNG_PER_LAT)
    }
}

/** 두 점 사이의 어림 거리(km). */
internal fun roughKm(a: GeoDot, b: GeoDot): Double =
    hypot(a.lat - b.lat, (a.lng - b.lng) / LNG_PER_LAT) * KM_PER_DEGREE
