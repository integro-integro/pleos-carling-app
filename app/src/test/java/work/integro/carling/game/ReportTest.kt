package work.integro.carling.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ReportTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 20) // 금요일

    private fun trip(day: Int, hour: Int, km: Float, min: Int, purpose: TripPurpose?, state: CardState? = if (purpose != null) CardState.COMPLETED else null, month: Int = 11) = ReportTrip(
        tripId = LocalDate.of(2026, month, day).atTime(hour, 0).toInstant(zone).toEpochMilli(),
        startedAt = LocalDate.of(2026, month, day).atTime(hour, 0).toInstant(zone).toEpochMilli(),
        distanceKm = km, movingSec = min * 60L, elapsedSec = min * 60L, score = 90, purpose = purpose,
        hasCard = state != null, cardState = state, rewardedGp = if (purpose != null) 50 else null, rewardedAt = null,
    )

    @Test
    fun totalsIncludeExpiredAndBelowMinimumRecords() {
        val trips = listOf(
            trip(3, 8, 40f, 60, TripPurpose.COMMUTE),
            trip(4, 8, 40f, 60, TripPurpose.COMMUTE),
            trip(5, 18, 10f, 20, null, CardState.EXPIRED),
            trip(6, 12, 0.5f, 2, null, null),
        )
        val r = ReportAggregator.build(ReportPeriod.THIS_MONTH, today, zone, trips, 0)
        assertEquals(4, r.totals.count)
        assertEquals(90.5, r.totals.km, 0.01)
        assertEquals(4, r.totals.days)
        assertEquals(1, r.unclassifiedExpired)
        assertEquals(1, r.belowMinimum)
        assertEquals(2, r.completedCards)
        assertEquals(100, r.gpEarned)
    }

    @Test
    fun findingsFollowRuleOrderAndMaxThree() {
        val trips = listOf(
            trip(2, 8, 40f, 60, TripPurpose.COMMUTE), trip(3, 8, 40f, 60, TripPurpose.COMMUTE), trip(4, 8, 40f, 60, TripPurpose.COMMUTE),
            trip(14, 13, 50f, 80, TripPurpose.FAMILY), // 토요일
            trip(10, 8, 30f, 50, TripPurpose.COMMUTE, month = 10),
        )
        val r = ReportAggregator.build(ReportPeriod.THIS_MONTH, today, zone, trips, 2)
        assertEquals(3, r.findings.size)
        assertTrue(r.findings[0].startsWith("이번 달 운전시간의 69%는 출퇴근"))
        assertTrue(r.findings[1].contains("지난달보다 운전시간이"))
        assertTrue(r.findings[2].contains("아침"))
        assertTrue(r.nextSteps.any { it.contains("완성하지 않은 여정이 2건") })
    }

    @Test
    fun fewTripsShowNoFindings() {
        val r = ReportAggregator.build(ReportPeriod.LAST_7_DAYS, today, zone, listOf(trip(19, 9, 5f, 10, TripPurpose.HOBBY)), 0)
        assertTrue(!r.enoughForFindings && r.findings.isEmpty())
        assertEquals(7, r.dailyKm.size)
    }

    @Test
    fun longestTripIsFallback() {
        val trips = listOf(trip(16, 9, 5f, 10, null, null), trip(17, 12, 25f, 30, null, null), trip(18, 19, 8f, 12, null, null))
        val r = ReportAggregator.build(ReportPeriod.LAST_7_DAYS, today, zone, trips, 0)
        assertTrue(r.findings.single().contains("가장 긴 여정은 11월 17일의 25.0km"))
    }
}
