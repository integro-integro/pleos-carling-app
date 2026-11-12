package work.integro.carling.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    private fun real(km: Float?, s: Long?) = Rules.meetsCardMinimum(km, s, Rules.REAL_CARD_MIN_KM, Rules.REAL_CARD_MIN_MOVING_SEC)

    @Test
    fun cardMinimumIsOneKmAndThreeMinutes() {
        assertTrue(real(1f, 180))
        assertFalse(real(0.99f, 600))
        assertFalse(real(5f, 179))
        assertFalse(real(null, 600))
        assertFalse(real(5f, null))
    }

    @Test
    fun growthPointsHaveFloorAndCap() {
        assertEquals(10, Rules.growthPoints(0.4f, 59))
        assertEquals(10 + 18 + 27, Rules.growthPoints(18.9f, 27 * 60 + 59))
        assertEquals(150, Rules.growthPoints(300f, 4 * 3600))
    }

    @Test
    fun drivingScoreFormula() {
        assertNull(Rules.drivingScore(null, 10f))
        assertEquals(100, Rules.drivingScore(0, 10f))
        assertEquals(95, Rules.drivingScore(1, 10f))
        assertEquals(90, Rules.drivingScore(1, 5f))
        assertEquals(40, Rules.drivingScore(30, 10f))
    }

    @Test
    fun stagesFollowPointsAndFirstReward() {
        assertEquals(Stage.EGG, Stage.of(0, 0))
        assertEquals(Stage.CRACKED, Stage.of(40, 1))
        assertEquals(Stage.BABY, Stage.of(1_500, 30))
        assertEquals(Stage.BREEZE, Stage.of(3_500, 60))
        assertEquals(Stage.MYCARLING, Stage.of(9_000, 99))
    }

    @Test
    fun timeBucketBoundaries() {
        assertEquals(TimeBucket.NIGHT, TimeBucket.of(4))
        assertEquals(TimeBucket.MORNING, TimeBucket.of(5))
        assertEquals(TimeBucket.DAY, TimeBucket.of(10))
        assertEquals(TimeBucket.EVENING, TimeBucket.of(17))
        assertEquals(TimeBucket.NIGHT, TimeBucket.of(21))
    }

    private fun card(id: Long, b: TimeBucket, p: TripPurpose?, s: Int? = 90) = PersonalityInput(id, b, p, s)

    @Test
    fun fewerThanThreeCardsKeepsDefault() {
        val r = PersonalityRules.compute(listOf(card(1, TimeBucket.MORNING, TripPurpose.COMMUTE), card(2, TimeBucket.MORNING, TripPurpose.COMMUTE)))
        assertEquals(Personality(), r.personality)
    }

    @Test
    fun personalityUsesLastTenAndRecentWinsTies() {
        val cards = List(10) { card(it.toLong(), TimeBucket.MORNING, TripPurpose.COMMUTE) } + List(10) { card(100L + it, if (it < 5) TimeBucket.NIGHT else TimeBucket.DAY, TripPurpose.FIELD, 70) }
        val r = PersonalityRules.compute(cards)
        assertEquals(10, r.sampleSize)
        assertEquals(TimeBucket.DAY, r.personality.body) // 5:5 동률 → 최근 카드의 시간대
        assertEquals(TripPurpose.FIELD, r.personality.decoration)
        assertFalse(r.personality.heartBright)
    }

    @Test
    fun otherAndUnclassifiedGiveNoDecoration() {
        val r = PersonalityRules.compute(List(4) { card(it.toLong(), TimeBucket.EVENING, if (it % 2 == 0) TripPurpose.OTHER else null) })
        assertNull(r.personality.decoration)
    }

    @Test
    fun heartBrightAtEightyFive() {
        assertTrue(PersonalityRules.compute(List(3) { card(it.toLong(), TimeBucket.DAY, TripPurpose.HOBBY, 85) }).personality.heartBright)
        assertFalse(PersonalityRules.compute(List(3) { card(it.toLong(), TimeBucket.DAY, TripPurpose.HOBBY, null) }).personality.heartBright)
    }

    @Test
    fun luckyBoxDrawIsBounded() {
        val seq = listOf(0.999, 0.1, 0.5).iterator()
        val c = LuckyBoxRules.draw { seq.next() }
        assertEquals(Rules.BOX_BONUS_MAX, c.bonusGp)
        assertTrue(c.badge != null && c.carItem == null)
        val car = listOf(0.0, 0.5, 0.99).iterator()
        val c2 = LuckyBoxRules.draw { car.next() }
        assertEquals(Rules.BOX_BONUS_MIN, c2.bonusGp)
        assertEquals(CarItem.entries.last(), c2.carItem)
    }
}
