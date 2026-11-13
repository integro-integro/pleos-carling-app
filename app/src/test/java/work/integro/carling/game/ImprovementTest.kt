package work.integro.carling.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ImprovementTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 20)

    private fun trip(month: Int, day: Int, km: Float, harsh: Int, eff: Float?) = LocalDate.of(2026, month, day).atTime(9, 0).toInstant(zone).toEpochMilli().let {
        ReportTrip(it, it, km, 1800, 1800, Rules.drivingScore(harsh, km), null, false, null, null, null, harsh = harsh, efficiency = eff, ev = true)
    }

    @Test
    fun monthlyTrailComparesLastTwoPeriods() {
        val trips = listOf(
            trip(10, 5, 50f, 4, 5.0f), trip(10, 9, 50f, 2, 5.0f), // 10월: 100km 급조작 6
            trip(11, 3, 50f, 0, 6.0f), trip(11, 10, 50f, 1, 6.0f), // 11월: 100km 급조작 1
        )
        val stats = ImprovementRules.stats(ReportPeriod.THIS_MONTH, today, zone, trips, count = 3)
        assertEquals(listOf("9월", "10월", "11월"), stats.map { it.short })
        assertEquals(0, stats[0].trips)
        assertEquals(6.0, stats[1].harshPer100!!, 0.01)
        assertEquals(1.0, stats[2].harshPer100!!, 0.01)
        val v = ImprovementRules.verdicts(stats)
        assertTrue(v.first { it.metric == Metric.HARSH }.better)
        assertTrue(v.first { it.metric == Metric.EFFICIENCY }.better)
        assertEquals(20, v.first { it.metric == Metric.EFFICIENCY }.changePct)
        assertTrue(ImprovementRules.headline(v).contains("좋아"))
    }

    @Test
    fun savingsAgainstPreviousEfficiency() {
        // 120km: 6.0이면 20kWh, 5.0이었다면 24kWh → 4kWh × 324원
        val s = ImprovementRules.savings(120.0, 6.0f, 5.0f, 324)!!
        assertEquals(20.0, s.energyNow, 1e-9)
        assertEquals(1296, s.won)
        assertTrue(s.saved)
        assertFalse(ImprovementRules.savings(120.0, 4.0f, 5.0f, 324)!!.saved)
        assertNull(ImprovementRules.savings(120.0, null, 5.0f, 324))
    }

    @Test
    fun badgeFitsTheTrip() {
        val b = BadgeRules.eligible(TimeBucket.MORNING, weekend = false, purpose = TripPurpose.TO_WORK, samePurposeCount = 3, score = 90)
        assertEquals(listOf(Badge.MORNING_SUN, Badge.COMMUTE_COFFEE, Badge.REGULAR_ROAD, Badge.MINT_HEART), b)
        // 배지가 나오는 굴림이면 맞는 배지 중에서만 고른다
        val rolls = ArrayDeque(listOf(0.0, 0.0, 0.99))
        val c = LuckyBoxRules.draw(listOf(Badge.NIGHT_STAR, Badge.WEEKEND_OUTING)) { rolls.removeFirst() }
        assertEquals(Badge.WEEKEND_OUTING, c.badge)
        assertEquals(BadgeTier.SILVER, BadgeTier.of(3))
        assertNull(BadgeTier.of(0))
        assertFalse(CarColor.AURORA in CarColor.collectible)
    }
}
