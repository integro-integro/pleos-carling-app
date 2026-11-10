package work.integro.carling.data

import android.content.Context
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot

/*
 * 대한민국 행정구역 경계(통계청 2013 경계를 단순화한 공공데이터). 시·도 17, 시군구 251, 읍면동 3,482.
 * 앱 안(assets/geo)에 들어 있어 인터넷 없이 지역을 판정하고 지도를 그린다.
 * 코드는 시·도 2자리 → 시군구 5자리 → 읍면동 7자리로, 앞자리가 위 단계 코드다.
 */

/** 경계 하나. [rings]는 바깥 경계들이고 각 배열은 lng, lat을 번갈아 담는다. */
class AdminRegion(val code: String, val name: String, val rings: List<FloatArray>) {
    val minLng: Float; val maxLng: Float; val minLat: Float; val maxLat: Float

    init {
        var a = Float.MAX_VALUE; var b = -Float.MAX_VALUE; var c = Float.MAX_VALUE; var d = -Float.MAX_VALUE
        rings.forEach { r -> for (i in r.indices step 2) { a = minOf(a, r[i]); b = maxOf(b, r[i]); c = minOf(c, r[i + 1]); d = maxOf(d, r[i + 1]) } }
        minLng = a; maxLng = b; minLat = c; maxLat = d
    }

    val centerLng get() = (minLng + maxLng) / 2
    val centerLat get() = (minLat + maxLat) / 2

    /** 면적(km²). */
    val areaKm2: Double by lazy {
        rings.sumOf { r ->
            var s = 0.0
            val k = cos(centerLat * PI / 180) * 111.32
            for (i in r.indices step 2) {
                val j = (i + 2) % r.size
                s += (r[i] * k) * (r[j + 1] * 110.57) - (r[j] * k) * (r[i + 1] * 110.57)
            }
            kotlin.math.abs(s) / 2
        }
    }

    fun contains(lng: Float, lat: Float): Boolean {
        if (lng < minLng || lng > maxLng || lat < minLat || lat > maxLat) return false
        return rings.any { r ->
            var inside = false
            var j = r.size - 2
            for (i in r.indices step 2) {
                val xi = r[i]; val yi = r[i + 1]; val xj = r[j]; val yj = r[j + 1]
                if ((yi > lat) != (yj > lat) && lng < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
            inside
        }
    }

    /** 지도에 쓰는 짧은 이름: 서울특별시 → 서울, 부산광역시 → 부산. 도 이름은 그대로 둔다. */
    val shortName: String
        get() = name.removeSuffix("특별자치시").removeSuffix("특별자치도").removeSuffix("특별시").removeSuffix("광역시")
            .let { if (it == "제주") "제주도" else it }
}

class KoreaAtlas(
    val provinces: List<AdminRegion>,
    val municipalities: List<AdminRegion>,
    val dongs: List<AdminRegion>,
) {
    private val byCode = (provinces + municipalities + dongs).associateBy { it.code }

    fun region(code: String): AdminRegion? = byCode[code]

    /** 이 위치의 가장 작은 행정구역 코드(읍면동, 없으면 시군구, 시·도). 바다면 null. */
    fun locate(lng: Float, lat: Float): String? {
        val p = provinces.firstOrNull { it.contains(lng, lat) } ?: return null
        val m = municipalities.firstOrNull { it.code.startsWith(p.code) && it.contains(lng, lat) } ?: return p.code
        return dongs.firstOrNull { it.code.startsWith(m.code) && it.contains(lng, lat) }?.code ?: m.code
    }

    fun children(code: String?): List<AdminRegion> = when (code?.length) {
        null -> provinces
        2 -> municipalities.filter { it.code.startsWith(code) }
        5 -> dongs.filter { it.code.startsWith(code) }
        else -> emptyList()
    }

    companion object {
        @Volatile private var cached: KoreaAtlas? = null

        /** 처음 한 번만 읽는다. 무거우니 메인 스레드 밖에서 부른다. */
        fun load(context: Context): KoreaAtlas = cached ?: synchronized(this) {
            cached ?: KoreaAtlas(read(context, "provinces"), read(context, "municipalities"), read(context, "dongs")).also { cached = it }
        }

        private fun read(context: Context, name: String): List<AdminRegion> =
            context.assets.open("geo/$name.txt").bufferedReader().readLines().mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 3) return@mapNotNull null
                val rings = parts[2].split('|').map { ring ->
                    val pts = ring.split(' ')
                    FloatArray(pts.size * 2).also { arr ->
                        pts.forEachIndexed { i, p -> val c = p.indexOf(','); arr[i * 2] = p.substring(0, c).toFloat(); arr[i * 2 + 1] = p.substring(c + 1).toFloat() }
                    }
                }
                AdminRegion(parts[0], parts[1], rings)
            }
    }
}

/** 밝혀진 땅을 재는 격자(약 0.55km). */
const val FOG_CELL_LAT = 0.005
const val FOG_CELL_LNG = 0.0062
const val FOG_CELL_KM2 = 0.55 * 0.55

/** 달린 길에서 앞뒤·좌우로 밝혀지는 거리(km). */
const val FOG_REVEAL_KM = 1.0

fun fogCell(lat: Double, lng: Double): Long =
    (floor(lat / FOG_CELL_LAT).toLong() shl 32) or (floor(lng / FOG_CELL_LNG).toLong() and 0xffffffffL)

fun fogCellCenter(cell: Long): Pair<Double, Double> {
    val la = (cell shr 32).toInt(); val ln = cell.toInt()
    return (la + 0.5) * FOG_CELL_LAT to (ln + 0.5) * FOG_CELL_LNG
}

/** 길을 약 300m 간격으로 촘촘히 만든다. */
fun densify(track: List<GeoDot>): List<GeoDot> {
    if (track.size < 2) return track
    val out = ArrayList<GeoDot>()
    for (i in 0 until track.size - 1) {
        val a = track[i]; val b = track[i + 1]
        val km = hypot((b.lat - a.lat) * 110.57, (b.lng - a.lng) * 89.0)
        val n = (km / 0.3).toInt().coerceIn(1, 2000)
        for (k in 0 until n) out += GeoDot(a.lat + (b.lat - a.lat) * k / n, a.lng + (b.lng - a.lng) * k / n)
    }
    out += track.last()
    return out
}

/** 길 주변 [FOG_REVEAL_KM] 안의 격자 칸. */
fun revealedCells(tracks: List<List<GeoDot>>): Set<Long> {
    val cells = HashSet<Long>()
    val rLat = (FOG_REVEAL_KM / 110.57 / FOG_CELL_LAT).toInt() + 1
    val rLng = (FOG_REVEAL_KM / 89.0 / FOG_CELL_LNG).toInt() + 1
    tracks.forEach { t ->
        densify(t).forEach { p ->
            val c = fogCell(p.lat, p.lng)
            val la = (c shr 32).toInt(); val ln = c.toInt()
            for (dy in -rLat..rLat) for (dx in -rLng..rLng) {
                val km = hypot(dy * FOG_CELL_LAT * 110.57, dx * FOG_CELL_LNG * 89.0)
                if (km <= FOG_REVEAL_KM) cells += ((la + dy).toLong() shl 32) or ((ln + dx).toLong() and 0xffffffffL)
            }
        }
    }
    return cells
}
