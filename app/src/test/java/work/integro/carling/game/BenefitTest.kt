package work.integro.carling.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class BenefitTest {
    private val zone = ZoneOffset.UTC
    private val now = LocalDate.of(2026, 11, 27).atTime(12, 0).toInstant(zone).toEpochMilli()
    private val day = 86_400_000L
    private val names = mapOf("1124071" to "잠실본동", "1123078" to "청담동")
    private fun t(ago: Int, km: Float, p: TripPurpose?, from: String? = "1124071", to: String? = "1123078", hour: Int = 9) =
        BenefitTrip(now - ago * day - (12 - hour) * 3_600_000L, km, p, from, to)

    @Test
    fun priorityServiceThenChargeThenParking() {
        val trips = (1..4).map { t(it, 9f, TripPurpose.MEAL, hour = 8) }
        val refuels = listOf(now - 15 * day, now - 10 * day, now - 5 * day)
        val withWarn = BenefitRules.pick(trips, refuels, true, listOf("앞 왼쪽 타이어 공기압"), now, zone, names::get, emptyMap())
        assertEquals(BenefitKind.SERVICE, withWarn!!.offer.kind)
        val charge = BenefitRules.pick(trips, refuels, true, emptyList(), now, zone, names::get, emptyMap())!!
        assertEquals("charge-jamsil", charge.offer.id)
        assertTrue(charge.reason.contains("약 5일마다 충전해요, 주로 잠실본동"))
        // 충전을 닫으면 30일 동안 주차(청담동 식사 4번)
        val parking = BenefitRules.pick(trips, refuels, true, emptyList(), now, zone, names::get, mapOf("charge-jamsil" to now - day))!!
        assertEquals("park-cheongdam", parking.offer.id)
        assertTrue(parking.reason.contains("청담동에 식사(으)로 4번"))
        // 30일 지나면 다시
        assertEquals("charge-jamsil", BenefitRules.pick(trips, refuels, true, emptyList(), now, zone, names::get, mapOf("charge-jamsil" to now - 31 * day))!!.offer.id)
    }

    @Test
    fun washAfterLongTripAndLifeAndNothing() {
        val wash = BenefitRules.pick(listOf(t(3, 120f, TripPurpose.TRAVEL)), emptyList(), true, emptyList(), now, zone, names::get, emptyMap())!!
        assertEquals(BenefitKind.WASH, wash.offer.kind)
        val gym = BenefitRules.pick((1..3).map { t(it, 5f, TripPurpose.EXERCISE) }, emptyList(), true, emptyList(), now, zone, names::get, emptyMap())!!
        assertEquals("gym-cheongdam", gym.offer.id)
        assertNull(BenefitRules.pick(listOf(t(1, 5f, TripPurpose.TO_WORK)), emptyList(), true, emptyList(), now, zone, names::get, emptyMap()))
        // 내연기관은 주유, 위치가 없으면 어디서나 혜택
        val fuel = BenefitRules.pick(listOf(t(1, 5f, null, null, null)), listOf(now - 9 * day, now - 2 * day), false, emptyList(), now, zone, names::get, emptyMap())!!
        assertEquals("fuel-any", fuel.offer.id)
        assertEquals("park-cheongdam", BenefitRules.forDong("1123078")!!.id)
    }
}
