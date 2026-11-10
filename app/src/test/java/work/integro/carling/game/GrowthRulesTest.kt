package work.integro.carling.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthRulesTest {
    private fun valid(km: Float?, s: Long?) = GrowthRules.isValidTrip(km, s, GrowthRules.REAL_MIN_KM, GrowthRules.REAL_MIN_MOVING_SECONDS)

    @Test
    fun tripNeedsBothDistanceAndMovingTime() {
        assertTrue(valid(2f, 300))
        assertFalse(valid(2f, 299))
        assertFalse(valid(1.99f, 600))
        assertFalse(valid(0f, 0))
    }

    @Test
    fun unknownDataIsNotGuessed() {
        assertFalse(valid(null, 600))
        assertFalse(valid(5f, null))
    }

    @Test
    fun onlyFirstFiveTripsPayXp() {
        assertEquals(20 to 0, GrowthRules.tripXp(1, 89))
        assertEquals(20 to 5, GrowthRules.tripXp(5, 90))
        assertEquals(20 to 0, GrowthRules.tripXp(2, null))
        assertEquals(0 to 0, GrowthRules.tripXp(6, 100))
    }

    @Test
    fun levelTableMatchesPlan() {
        assertEquals(20, GrowthRules.cumXp(2))
        assertEquals(340, GrowthRules.cumXp(10))
        assertEquals(1_140, GrowthRules.cumXp(20))
        assertEquals(2_340, GrowthRules.cumXp(30))
        assertEquals(6_340, GrowthRules.cumXp(50))
        assertEquals(12_340, GrowthRules.cumXp(70))
        assertEquals(23_940, GrowthRules.cumXp(99))
    }

    @Test
    fun firstTripReachesLevelTwoAndBonusCarriesOver() {
        assertEquals(1, GrowthRules.levelOf(19))
        assertEquals(2, GrowthRules.levelOf(20))
        assertEquals(2, GrowthRules.levelOf(25))
        assertEquals(5f / 40f, GrowthRules.levelProgress(25), 0.0001f)
        assertEquals(99, GrowthRules.levelOf(1_000_000))
    }

    @Test
    fun eggCracksByLevel() {
        assertEquals(listOf(1, 2, 2, 3, 3, 4), listOf(1, 2, 4, 5, 8, 9).map(GrowthRules::eggStage))
    }

    @Test
    fun evolutionNeedsAllThreeConditions() {
        val b = Form.BABY
        assertTrue(GrowthRules.canEvolve(Form.EGG, b.minLevel, b.intimacyGoal, b.questGoal))
        assertFalse(GrowthRules.canEvolve(Form.EGG, b.minLevel - 1, b.intimacyGoal, b.questGoal))
        assertFalse(GrowthRules.canEvolve(Form.EGG, b.minLevel, b.intimacyGoal - 1, b.questGoal))
        assertFalse(GrowthRules.canEvolve(Form.EGG, b.minLevel, b.intimacyGoal, b.questGoal - 1))
        assertFalse(GrowthRules.canEvolve(Form.EVO1, 99, 24, 100))
    }

    @Test
    fun goalsAreReachableWithTheItemCount() {
        assertTrue(Form.entries.all { it.intimacyGoal <= RoomItem.entries.size })
        assertEquals(24, Space.BEDROOM.items.size)
        assertTrue(Space.entries.drop(1).all { it.items.size == 12 })
    }

    @Test
    fun supportingFurnitureComesFirst() {
        fun before(a: RoomItem, b: RoomItem) = assertTrue("$a -> $b", RoomItem.ORDER.indexOf(a) < RoomItem.ORDER.indexOf(b))
        before(RoomItem.NIGHTSTAND, RoomItem.LAMP)
        before(RoomItem.CABINET, RoomItem.VASE)
        before(RoomItem.CABINET, RoomItem.BOOKS)
        before(RoomItem.TABLE, RoomItem.MUG)
        before(RoomItem.BED, RoomItem.BEDDING)
    }

    @Test
    fun newItemsFillTheCurrentSpaceFirst() {
        val bedroomDone = Space.BEDROOM.items
        // 침실에 있고 침실이 아직 비었으면 침실 아이템
        assertEquals(RoomItem.RUG, nextItemFor(Space.BEDROOM, 12, emptyList()))
        // 거실에 있으면 거실 아이템부터
        assertEquals(Space.LIVING.items.first(), nextItemFor(Space.LIVING, 12, emptyList()))
        // 침실을 다 채웠으면 열린 다른 공간으로 넘어간다
        assertEquals(Space.LIVING.items.first(), nextItemFor(Space.BEDROOM, 12, bedroomDone))
        // 잠긴 공간의 아이템은 나오지 않는다
        assertEquals(null, nextItemFor(Space.BEDROOM, 9, bedroomDone))
    }

    @Test
    fun spacesOpenByLevelWithinTheHouse() {
        assertFalse(Space.LIVING.isOpen(9))
        assertTrue(Space.LIVING.isOpen(10))
        assertTrue(Space.entries.drop(1).all { it.unlockLevel in 10..19 })
    }

    @Test
    fun questsFinishFromDataOnly() {
        assertTrue(Quest(QuestKind.ONE_TRIP, trips = 1).done)
        assertFalse(Quest(QuestKind.TEN_MINUTES, seconds = 599).done)
        assertTrue(Quest(QuestKind.TEN_MINUTES, seconds = 600).done)
        assertFalse(Quest(QuestKind.TWO_DAYS, days = setOf("2026-11-10")).done)
        assertTrue(Quest(QuestKind.TWO_DAYS, days = setOf("2026-11-10", "2026-11-11")).done)
    }
}
