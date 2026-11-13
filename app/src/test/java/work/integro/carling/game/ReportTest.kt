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
            trip(3, 8, 40f, 60, TripPurpose.TO_WORK),
            trip(4, 8, 40f, 60, TripPurpose.TO_WORK),
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
            trip(2, 8, 40f, 60, TripPurpose.TO_WORK), trip(3, 8, 40f, 60, TripPurpose.TO_WORK), trip(4, 8, 40f, 60, TripPurpose.TO_WORK),
            trip(14, 13, 50f, 80, TripPurpose.DATE), // 토요일
            trip(10, 8, 30f, 50, TripPurpose.TO_WORK, month = 10),
        )
        val r = ReportAggregator.build(ReportPeriod.THIS_MONTH, today, zone, trips, 2)
        assertEquals(3, r.findings.size)
        assertTrue(r.findings[0].startsWith("이번 달 운전시간의 69%는 출근이었어요"))
        assertTrue(r.findings[1].contains("지난달보다 운전시간이"))
        assertTrue(r.findings[2].contains("아침"))
        assertTrue(r.nextSteps.any { it.contains("완성하지 않은 여정이 2건") })
    }

    @Test
    fun fewTripsShowNoFindings() {
        val r = ReportAggregator.build(ReportPeriod.THIS_WEEK, today, zone, listOf(trip(19, 9, 5f, 10, TripPurpose.HOBBY)), 0)
        assertTrue(!r.enoughForFindings && r.findings.isEmpty())
        assertEquals(5, r.dailyKm.size) // 이번 주 월~금
    }

    @Test
    fun longestTripIsFallback() {
        val trips = listOf(trip(16, 9, 5f, 10, null, null), trip(17, 12, 25f, 30, null, null), trip(18, 19, 8f, 12, null, null))
        val r = ReportAggregator.build(ReportPeriod.THIS_WEEK, today, zone, trips, 0)
        assertTrue(r.findings.single().contains("가장 긴 여정은 11월 17일의 25.0km"))
    }

    @Test
    fun detailedMetrics() {
        val trips = listOf(
            trip(16, 8, 30f, 30, TripPurpose.TO_WORK).copy(elapsedSec = 40 * 60L, harsh = 1), // 월
            trip(17, 8, 30f, 30, TripPurpose.TO_WORK).copy(elapsedSec = 40 * 60L, harsh = 0), // 화
            trip(19, 22, 120f, 90, null, null).copy(elapsedSec = 100 * 60L), // 목
        )
        val r = ReportAggregator.build(ReportPeriod.THIS_WEEK, today, zone, trips, 0)
        assertEquals(180.0 / 2.5, r.avgSpeedKmh!!, 0.01)
        assertEquals(60.0, r.avgKm, 0.01)
        assertEquals(17, r.stopRatio) // (180-150)/180
        assertEquals(listOf(1, 1, 0, 1, 0, 0, 0), r.weekdayCounts)
        assertEquals(2, r.heat[0][0] + r.heat[1][0])
        assertEquals(1, r.heat[3][3])
        assertEquals(2, r.longestStreak)
        assertEquals(0, r.currentStreak) // 11/20엔 운행 없음
        assertEquals(1, r.distanceBins.first { it.first == "100km 이상" }.second)
        assertEquals(120f, r.topTrips.first().distanceKm)
        assertEquals(1, r.harshTotal)
        assertEquals(2, r.cardsCompleted)
    }

    @Test
    fun efficiencyIsDistanceWeightedAndComparedWithPrevious() {
        val trips = listOf(
            trip(16, 8, 30f, 30, TripPurpose.TO_WORK).copy(efficiency = 6f, ev = true),
            trip(17, 8, 10f, 20, TripPurpose.WORK).copy(efficiency = 4f, ev = true),
            trip(10, 8, 20f, 20, TripPurpose.TO_WORK).copy(efficiency = 5f, ev = true),
        )
        val r = ReportAggregator.build(ReportPeriod.THIS_WEEK, today, zone, trips, 0, listOf(
            RefuelPoint(LocalDate.of(2026, 11, 15).atTime(9, 0).toInstant(zone).toEpochMilli(), true, 20, 85),
            RefuelPoint(LocalDate.of(2026, 11, 18).atTime(9, 0).toInstant(zone).toEpochMilli(), true, 30, 90),
            RefuelPoint(LocalDate.of(2026, 11, 9).atTime(9, 0).toInstant(zone).toEpochMilli(), true, 25, 80),
        ))
        // 40km ÷ (30/6 + 10/4 = 7.5 kWh) = 5.33
        assertEquals(40f / 7.5f, r.avgEfficiency!!, 0.01f)
        assertEquals(5f, r.prevEfficiency!!, 0.01f)
        assertEquals(true, r.ev)
        assertEquals("전비", r.efficiencyName)
        assertEquals(1, r.refuels.size) // 11/15는 지난주
        assertEquals(2, r.prevRefuels)
        assertEquals(6f, r.bestEfficiency!!.efficiency!!, 0.01f)
        assertEquals(TripPurpose.TO_WORK.label, r.efficiencyByPurpose.first().label)
    }

    @Test
    fun periodUnitsAndLabels() {
        // 2026-11-20 금요일
        assertEquals(LocalDate.of(2026, 11, 16) to today, ReportPeriod(PeriodUnit.WEEK).range(today))
        assertEquals(LocalDate.of(2026, 11, 9) to LocalDate.of(2026, 11, 15), ReportPeriod(PeriodUnit.WEEK).previous(today))
        assertEquals(LocalDate.of(2026, 10, 1) to today, ReportPeriod(PeriodUnit.QUARTER).range(today))
        assertEquals(LocalDate.of(2026, 7, 1) to LocalDate.of(2026, 9, 30), ReportPeriod(PeriodUnit.QUARTER).previous(today))
        assertEquals(LocalDate.of(2025, 1, 1) to LocalDate.of(2025, 12, 31), ReportPeriod(PeriodUnit.YEAR, -1).range(today))
        assertEquals("지난달", ReportPeriod(PeriodUnit.MONTH, -1).label(today))
        assertEquals("2026년 2분기", ReportPeriod(PeriodUnit.QUARTER, -2).label(today))
        assertEquals("11월 1주", ReportPeriod(PeriodUnit.WEEK, -2).label(today))
        assertEquals(0, ReportPeriod(PeriodUnit.MONTH).shift(1).offset)
        assertEquals(listOf(-2, -1, 0), ReportPeriod(PeriodUnit.MONTH).trail(3).map { it.offset })
    }
}
