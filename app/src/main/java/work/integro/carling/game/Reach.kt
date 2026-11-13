package work.integro.carling.game

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * 내 활동 반경(운행 리포트 ⑤).
 * 1단계(위치 없이): 1회 거리를 5·10·30·50km 고리로 나눠 "보통 이만큼 다녀요"를 보여 준다.
 * 2단계(위치 동의): 출발·도착 동네(행정동)만으로 집 동네를 추정하고, 다녀온 동네·새 동네·반경을 계산한다.
 * 좌표는 저장하지 않으므로 거리는 동네 중심끼리 잰 값이다.
 */

/** 동네 하나(행정동). 중심 좌표는 경계에서 계산한 값이다. */
data class Place(val code: String, val name: String, val lat: Double, val lng: Double, val areaKm2: Double)

/** 반경 계산 입력: 확정 운행 한 번. */
data class ReachTrip(val startedAt: Long, val km: Float?, val startDong: String?, val endDong: String?)

/** 거리 고리 하나: [fromKm] 이상 [toKm] 미만(마지막은 null = 끝없음). */
data class ReachRing(val fromKm: Int, val toKm: Int?, val trips: Int) {
    val label: String get() = if (toKm == null) "${fromKm}km 이상" else if (fromKm == 0) "${toKm}km 안" else "$fromKm–${toKm}km"
}

/** 다녀온 동네와 횟수, 집 동네 중심에서의 거리. */
data class PlaceVisit(val place: Place, val visits: Int, val fromHomeKm: Double)

data class ReachData(
    // ---- 1단계: 거리 고리 ----
    val rings: List<ReachRing>,
    /** 보통 반경: 1회 거리 80번째 백분위수. 운행이 없으면 null. */
    val typicalKm: Double?,
    val prevTypicalKm: Double?,
    val farthestTripKm: Double?,
    // ---- 2단계: 동네 ----
    /** 동네가 남은 운행 수. 0이면 지도 대신 동의 안내를 보여 준다. */
    val locatedTrips: Int,
    val home: Place?,
    /** 이 기간 도착한 동네(집 제외), 많이 간 순. */
    val visits: List<PlaceVisit>,
    /** 이 기간 집 밖 도착지의 80% 가 들어오는 반경(km). */
    val radiusKm: Double?,
    val prevRadiusKm: Double?,
    val farthest: PlaceVisit?,
    /** 이 기간 처음 가 본 동네. */
    val newPlaces: List<Place>,
    /** 이 기간 출발·도착한 동네 수(집 포함)와 그 면적 합. */
    val dongCount: Int,
    val areaKm2: Double,
)

object ReachRules {
    /** 고리 경계(km). */
    val RING_EDGES = listOf(5, 10, 30, 50)

    /** 집 동네로 볼 최소 기록 수. */
    const val HOME_MIN = 2

    fun build(
        period: List<ReachTrip>,
        previous: List<ReachTrip>,
        /** 이 기간보다 앞선 모든 운행(새 동네 판정용). */
        earlier: List<ReachTrip>,
        /** 집 동네 추정에 쓰는 모든 운행. */
        all: List<ReachTrip>,
        lookup: (String) -> Place?,
        /** 하루를 나눌 현지 시차(밀리초). */
        zoneOffsetMillis: Long = 0,
    ): ReachData {
        val kms = period.mapNotNull { it.km?.toDouble() }
        val rings = (listOf(0) + RING_EDGES).mapIndexed { i, from ->
            val to = RING_EDGES.getOrNull(i)
            ReachRing(from, to, kms.count { it >= from && (to == null || it < to) })
        }

        val home = homeOf(all, zoneOffsetMillis)?.let(lookup)
        val located = period.filter { it.startDong != null || it.endDong != null }
        fun visitsOf(trips: List<ReachTrip>): List<PlaceVisit> {
            if (home == null) return emptyList()
            return trips.mapNotNull { it.endDong }.filter { it != home.code }
                .groupingBy { it }.eachCount()
                .mapNotNull { (code, n) -> lookup(code)?.let { PlaceVisit(it, n, distanceKm(home, it)) } }
                .sortedWith(compareByDescending<PlaceVisit> { it.visits }.thenBy { it.fromHomeKm })
        }
        fun radiusOf(visits: List<PlaceVisit>): Double? =
            percentile(visits.flatMap { v -> List(v.visits) { v.fromHomeKm } }, 0.8)

        val visits = visitsOf(period)
        val seenBefore = earlier.flatMap { listOfNotNull(it.startDong, it.endDong) }.toSet()
        val codes = located.flatMap { listOfNotNull(it.startDong, it.endDong) }.distinct()
        val places = codes.mapNotNull(lookup)
        return ReachData(
            rings = rings,
            typicalKm = percentile(kms, 0.8),
            prevTypicalKm = percentile(previous.mapNotNull { it.km?.toDouble() }, 0.8),
            farthestTripKm = kms.maxOrNull(),
            locatedTrips = located.size,
            home = home,
            visits = visits,
            radiusKm = radiusOf(visits),
            prevRadiusKm = radiusOf(visitsOf(previous)),
            farthest = visits.maxByOrNull { it.fromHomeKm },
            newPlaces = if (earlier.none { it.startDong != null || it.endDong != null }) emptyList()
                else places.filter { it.code !in seenBefore && it.code != home?.code },
            dongCount = places.size,
            areaKm2 = places.sumOf { it.areaKm2 },
        )
    }

    /**
     * 집 동네: 가장 많이 출발한 동네. 하루의 첫 운행은 대개 집에서 출발하므로 두 번 센다.
     * [HOME_MIN]번 이상 출발한 동네가 없으면 모른다.
     */
    fun homeOf(all: List<ReachTrip>, zoneOffsetMillis: Long = 0): String? {
        val firstOfDay = all.groupBy { (it.startedAt + zoneOffsetMillis) / DAY }.values.mapNotNull { day -> day.minByOrNull { it.startedAt } }.toSet()
        val score = HashMap<String, Int>()
        all.forEach { t -> t.startDong?.let { score[it] = (score[it] ?: 0) + if (t in firstOfDay) 2 else 1 } }
        val starts = all.mapNotNull { it.startDong }.groupingBy { it }.eachCount()
        return score.filterKeys { (starts[it] ?: 0) >= HOME_MIN }.maxByOrNull { it.value }?.key
    }

    /** 오름차순 정렬 뒤 [p] 위치 값(선형 보간). 비면 null. */
    fun percentile(values: List<Double>, p: Double): Double? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        val pos = (s.size - 1) * p
        val lo = pos.toInt()
        val hi = (lo + 1).coerceAtMost(s.lastIndex)
        return s[lo] + (s[hi] - s[lo]) * (pos - lo)
    }

    fun distanceKm(a: Place, b: Place): Double = haversine(a.lat, a.lng, b.lat, b.lng)

    fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = PI / 180
        val dLat = (lat2 - lat1) * r
        val dLng = (lng2 - lng1) * r
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1 * r) * cos(lat2 * r) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * 6371.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** 리포트 표시: 10km 미만은 소수 한 자리. */
    fun km(v: Double): String = if (v < 10) "%.1fkm".format(v) else "${v.roundToInt()}km"

    private const val DAY = 24 * 60 * 60 * 1000L
}
