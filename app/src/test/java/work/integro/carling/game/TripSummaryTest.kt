package work.integro.carling.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class TripSummaryTest {
    private val zone = ZoneOffset.UTC
    private fun t(day: Int, hour: Int, p: TripPurpose?, to: String?, who: Companion? = null, km: Float = 10f) =
        SumTrip(LocalDate.of(2026, 11, day).atTime(hour, 0).toInstant(zone).toEpochMilli(), km, 1200, p, null, who, "H", to, CardTier.BASIC)

    @Test
    fun routinesHeadlineAndPlaces() {
        // 11/2·3·4·5 월~목 아침 출근 → 판교, 11/7·8·14 주말 낮 가족 식사 → 합정
        val trips = listOf(2, 3, 4, 5).map { t(it, 8, TripPurpose.TO_WORK, "P") } +
            listOf(7, 8, 14).map { t(it, 12, TripPurpose.MEAL, "M", Companion.FAMILY, km = 30f) } +
            t(10, 20, null, null)
        val names = mapOf("H" to "잠실본동", "P" to "삼평동", "M" to "합정동")
        val s = TripSummaryRules.build(trips, zone, "H", names::get, 25.0, 1)
        assertEquals(8, s.cards)
        assertEquals(TripPurpose.TO_WORK, s.purposes.first().first)
        assertEquals(null, s.purposes.last().first)
        assertEquals(listOf("삼평동" to 4, "합정동" to 3), s.places)
        assertEquals(2, s.routines.size)
        assertEquals("평일 아침 출근 → 삼평동", s.routines.first().label)
        assertTrue(s.headline.contains("평일 아침엔 잠실본동 → 삼평동 출근"))
        assertTrue(s.headline.contains("주말 낮엔 가족과 잠실본동 → 합정동 식사"))
        assertTrue(s.headline.contains("생활반경 25km, 새 동네 1곳"))
        assertEquals(6 to TimeBucket.DAY, s.peak) // 토요일 낮 2번(11/7·14)
        assertEquals(30f, s.farthestKm)
        assertEquals(30.0, TripSummaryRules.sameAverage(trips, TripPurpose.MEAL)!!.first, 0.01)
    }
}
