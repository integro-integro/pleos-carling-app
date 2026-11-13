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
        // 버디링·소울링은 추후 공개: 포인트가 넘쳐도 꼬마링에서 멈춘다
        assertEquals(Stage.BREEZE, Stage.of(9_000, 99))
        assertEquals(Stage.BREEZE, Stage.of(50_000, 200))
        assertEquals(Stage.BREEZE, Stage.lastOpen)
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
        val r = PersonalityRules.compute(listOf(card(1, TimeBucket.MORNING, TripPurpose.TO_WORK), card(2, TimeBucket.MORNING, TripPurpose.TO_WORK)))
        assertEquals(Personality(), r.personality)
    }

    @Test
    fun personalityUsesLastTenAndRecentWinsTies() {
        val cards = List(10) { card(it.toLong(), TimeBucket.MORNING, TripPurpose.TO_WORK) } + List(10) { card(100L + it, if (it < 5) TimeBucket.NIGHT else TimeBucket.DAY, TripPurpose.WORK, 70) }
        val r = PersonalityRules.compute(cards)
        assertEquals(10, r.sampleSize)
        assertEquals(TimeBucket.DAY, r.personality.body) // 5:5 동률 → 최근 카드의 시간대
        assertEquals(TripPurpose.WORK, r.personality.decoration)
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
    fun stylesNeedAllThreeParts() {
        val full = mapOf(CarSlot.ANTENNA to CarPart.ANTENNA_SPROUT, CarSlot.WHEEL to CarPart.WHEEL_CLOVER, CarSlot.REAR to CarPart.REAR_PICNIC)
        assertEquals(CarStyle.SPROUT_DRIVE, CarStyle.of(full))
        assertNull(CarStyle.of(full - CarSlot.REAR))
        assertNull(CarStyle.of(full + (CarSlot.REAR to CarPart.REAR_SUITCASE)))
        // 모든 아이템은 정확히 한 스타일에 들어간다
        assertEquals(CarPart.entries.toSet(), CarStyle.entries.flatMap { it.parts }.toSet())
        assertEquals(CarPart.entries.size, CarStyle.entries.sumOf { it.parts.size })
    }

    @Test
    fun cardTiers() {
        assertEquals(CardTier.SPECIAL, CardTier.of(1, 3f, 70))
        assertEquals(CardTier.SPECIAL, CardTier.of(9, 100f, null))
        assertEquals(CardTier.SHINE, CardTier.of(9, 20f, 85))
        assertEquals(CardTier.BASIC, CardTier.of(9, 99.9f, 84))
        assertEquals(CardTier.BASIC, CardTier.of(9, 20f, null))
    }

    @Test
    fun luckyBoxDrawIsBounded() {
        val seq = listOf(0.999, 0.1, 0.5).iterator()
        val c = LuckyBoxRules.draw { seq.next() }
        assertEquals(Rules.BOX_BONUS_MAX, c.bonusGp)
        assertTrue(c.badge != null && c.part == null)
        val car = listOf(0.0, 0.5, 0.99).iterator()
        val c2 = LuckyBoxRules.draw { car.next() }
        assertEquals(Rules.BOX_BONUS_MIN, c2.bonusGp)
        assertEquals(CarColor.collectible.last(), c2.color)
        val part = listOf(0.0, 0.5, 0.0).iterator()
        assertEquals(CarPart.entries.first(), LuckyBoxRules.draw { part.next() }.part)
    }

    @Test
    fun vibeFollowsMorningDayVersusEveningNight() {
        fun c(id: Long, b: TimeBucket) = PersonalityInput(id, b, TripPurpose.TO_WORK, 90)
        val sunny = PersonalityRules.compute(listOf(c(1, TimeBucket.NIGHT), c(2, TimeBucket.MORNING), c(3, TimeBucket.DAY), c(4, TimeBucket.MORNING)))
        assertEquals(Vibe.SUNNY, sunny.personality.vibe)
        val neon = PersonalityRules.compute(listOf(c(1, TimeBucket.MORNING), c(2, TimeBucket.EVENING), c(3, TimeBucket.NIGHT), c(4, TimeBucket.EVENING)))
        assertEquals(Vibe.NEON, neon.personality.vibe)
        assertTrue(neon.bodyReason().contains("헤드램프"))
        // 3장 미만이면 정해지지 않는다
        assertEquals(null, PersonalityRules.compute(listOf(c(1, TimeBucket.MORNING))).personality.vibe)
    }
}
