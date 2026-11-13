package work.integro.carling.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReachTest {
    private val day = 24 * 60 * 60 * 1000L
    private val places = mapOf(
        "H" to Place("H", "집동", 37.50, 127.08, 2.0),
        "A" to Place("A", "가동", 37.50, 127.20, 3.0), // 동쪽 약 10.6km
        "B" to Place("B", "나동", 37.40, 127.08, 4.0), // 남쪽 약 11.1km
        "F" to Place("F", "먼동", 37.00, 127.08, 9.0), // 약 55.6km
    )
    private fun trip(d: Int, km: Float?, from: String?, to: String?, hour: Int = 9) = ReachTrip(d * day + hour * 3_600_000L, km, from, to)

    @Test fun ringsAndTypical() {
        val r = ReachRules.build(listOf(trip(1, 3f, null, null), trip(2, 8f, null, null), trip(3, 25f, null, null), trip(4, 60f, null, null)), emptyList(), emptyList(), emptyList(), { null })
        assertEquals(listOf(1, 1, 1, 0, 1), r.rings.map { it.trips })
        assertEquals("50km 이상", r.rings.last().label)
        assertEquals(60.0, r.farthestTripKm!!, 1e-6)
        assertEquals(0, r.locatedTrips)
        assertNull(r.home)
    }

    @Test fun percentileInterpolates() {
        assertEquals(4.2, ReachRules.percentile(listOf(1.0, 2.0, 3.0, 4.0, 5.0), 0.8)!!, 1e-9)
        assertNull(ReachRules.percentile(emptyList(), 0.8))
    }

    @Test fun homeNeedsTwoAndFavorsFirstOfDay() {
        assertNull(ReachRules.homeOf(listOf(trip(1, 5f, "A", "H"))))
        // A는 오후 출발 두 번(1+1), H는 하루 첫 출발 한 번(2) → 동률이면 먼저 센 쪽보다 점수 높은 쪽
        val all = listOf(trip(1, 5f, "H", "A", 8), trip(1, 5f, "A", "H", 15), trip(2, 5f, "H", "B", 8), trip(2, 5f, "B", "H", 15))
        assertEquals("H", ReachRules.homeOf(all))
    }

    @Test fun placesRadiusAndNew() {
        val earlier = listOf(trip(1, 10f, "H", "A"), trip(1, 10f, "A", "H", 18))
        val period = listOf(
            trip(10, 10f, "H", "A"), trip(10, 10f, "A", "H", 18),
            trip(11, 10f, "H", "A"), trip(11, 10f, "A", "H", 18),
            trip(12, 11f, "H", "B"), trip(12, 11f, "B", "H", 18),
            trip(13, 56f, "H", "F"), trip(13, 56f, "F", "H", 18),
        )
        val r = ReachRules.build(period, earlier, earlier, earlier + period, places::get)
        assertEquals("H", r.home?.code)
        assertEquals(listOf("A", "B", "F"), r.visits.map { it.place.code })
        assertEquals(2, r.visits.first().visits)
        assertEquals("F", r.farthest?.place?.code)
        assertTrue(r.farthest!!.fromHomeKm in 55.0..56.5)
        assertEquals(setOf("B", "F"), r.newPlaces.map { it.code }.toSet())
        assertEquals(4, r.dongCount)
        assertEquals(18.0, r.areaKm2, 1e-9)
        // 도착지 거리 [10.6, 10.6, 11.1, 55.6]의 80% → 11.1과 55.6 사이
        assertTrue(r.radiusKm!! in 11.0..30.0)
        assertTrue(r.prevRadiusKm!! in 10.0..11.0)
    }
}
