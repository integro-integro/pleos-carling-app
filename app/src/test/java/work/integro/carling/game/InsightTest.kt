package work.integro.carling.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class InsightTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 27) // 금요일

    private fun t(day: Int, hour: Int, min: Int, moving: Int, harsh: Int, eff: Float, p: TripPurpose? = TripPurpose.TO_WORK, km: Float = 38f) =
        InsightTrip(LocalDate.of(2026, 11, day).atTime(hour, min).toInstant(zone).toEpochMilli(), km, moving * 60L, p, "H", "O", Rules.drivingScore(harsh, km), harsh, eff)

    private val week = listOf(2, 3, 4, 5, 6, 9, 10, 11, 12, 13, 16, 17, 18, 19, 20, 23)

    @Test
    fun departTimeFindsFasterSlotAndMonthlyGain() {
        // 8:10 출발 10번(58분), 7:50 출발 6번(40분)
        val trips = week.mapIndexed { i, d -> if (i % 8 < 3) t(d, 7, 50, 40, 0, 6.2f) else t(d, 8, 10, 58, 3, 5.0f) }
        val ins = InsightRules.depart(trips, zone) { mapOf("H" to "잠실본동", "O" to "삼평동")[it] }
        assertNotNull(ins)
        assertTrue(ins!!.title.contains("7:50에 나가면 18분 빨라요"))
        assertTrue(ins.big.startsWith("한 달에 "))
        assertTrue(ins.title.contains("출근(잠실본동 → 삼평동)"))
        // 동네 이름을 모르면 목적만
        assertTrue(InsightRules.depart(trips, zone) { null }!!.title.startsWith("출근, 7:50"))
    }

    @Test
    fun habitMoneyAndCostAndTop3() {
        val trips = week.mapIndexed { i, d -> if (i % 3 == 0) t(d, 7, 50, 40, 0, 6.0f) else t(d, 8, 10, 55, 12, 5.0f) } +
            listOf(t(24, 19, 0, 12, 0, 6f, TripPurpose.SHOPPING, 5f), t(25, 19, 0, 12, 0, 6f, TripPurpose.MEAL, 4f), t(26, 19, 0, 12, 0, 6f, TripPurpose.SHOPPING, 6f))
        val all = InsightRules.build(ReportPeriod.THIS_MONTH, today, zone, trips, ev = true, price = 324, name = { null }, reach = null)
        assertEquals(3, all.size)
        val habit = all.first { it.kind == InsightKind.HABIT_MONEY }
        assertTrue(habit.title.contains("20% 좋았어요"))
        assertTrue(habit.big.startsWith("1년에 "))
        val cost = all.first { it.kind == InsightKind.COST }
        assertTrue(cost.action.contains("묶으면"))
        assertEquals(InsightKind.DEPART, all.first().kind)
    }

    @Test
    fun upcomingWhenNotEnoughData() {
        val all = InsightRules.build(ReportPeriod.THIS_MONTH, today, zone, listOf(t(20, 8, 0, 50, 0, 6f)), true, 324, { null }, null)
        assertEquals(3, all.size)
        assertTrue(all.all { it.kind == InsightKind.UPCOMING })
        assertEquals("1만원", InsightRules.won(10_000))
        assertEquals("1.5만원", InsightRules.won(15_000))
        assertEquals("1시간 20분", InsightRules.duration(80))
    }
}
