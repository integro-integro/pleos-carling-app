package work.integro.carling.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GrowthRulesTest {

    @Test
    fun lightBoundaries() {
        assertEquals(Light.STAR, Light.ofHour(3))
        assertEquals(Light.DAWN, Light.ofHour(4))
        assertEquals(Light.DAY, Light.ofHour(9))
        assertEquals(Light.SUNSET, Light.ofHour(16))
        assertEquals(Light.STAR, Light.ofHour(20))
    }

    @Test
    fun driveXpFollowsTable() {
        // 기본 XP(누적값) + 안전 보너스(90점 이상 트립당 +2)
        assertEquals(2, GrowthRules.driveXp(trips = 1, safeTrips = 1))
        assertEquals(14, GrowthRules.driveXp(trips = 2, safeTrips = 2))
        assertEquals(19, GrowthRules.driveXp(trips = 3, safeTrips = 3))
        assertEquals(24, GrowthRules.driveXp(trips = 4, safeTrips = 4))
        assertEquals(30, GrowthRules.driveXp(trips = 5, safeTrips = 5))
        // 예: 4회 중 3회 90점 이상 = 16 + 6 = 22
        assertEquals(22, GrowthRules.driveXp(trips = 4, safeTrips = 3))
        // 상한 30
        assertEquals(30, GrowthRules.driveXp(trips = 9, safeTrips = 9))
    }

    @Test
    fun intimacyXpCapsAtTwoActions() {
        assertEquals(0, GrowthRules.intimacyXp(0))
        assertEquals(5, GrowthRules.intimacyXp(1))
        assertEquals(10, GrowthRules.intimacyXp(2))
        assertEquals(10, GrowthRules.intimacyXp(4))
    }

    @Test
    fun itemsOpenAtFirstGrowth() {
        assertEquals(false, GrowthRules.itemsUnlocked(0))
        assertEquals(true, GrowthRules.itemsUnlocked(1))
        assertEquals(true, GrowthRules.itemsUnlocked(9))
    }

    @Test
    fun nightnessFollowsClock() {
        assertEquals(1f, nightnessAt(0, 0))
        assertEquals(1f, nightnessAt(5, 29))
        assertEquals(0.5f, nightnessAt(6, 15))
        assertEquals(0f, nightnessAt(7, 0))
        assertEquals(0f, nightnessAt(17, 59))
        assertEquals(0.5f, nightnessAt(18, 45))
        assertEquals(1f, nightnessAt(19, 30))
        assertEquals(1f, nightnessAt(23, 59))
    }

    @Test
    fun savingPaysMpOnlyForFirstTwoCardsOfTheDay() {
        assertEquals(3, GrowthRules.saveMp(newPlace = true, alreadyRewarded = false, rewardedOnDay = 0))
        assertEquals(1, GrowthRules.saveMp(newPlace = false, alreadyRewarded = false, rewardedOnDay = 1))
        assertEquals(1, GrowthRules.saveMp(newPlace = null, alreadyRewarded = false, rewardedOnDay = 0))
        assertEquals(0, GrowthRules.saveMp(newPlace = true, alreadyRewarded = false, rewardedOnDay = 2))
        // 저장을 취소했다가 다시 저장해도 더 주지 않는다
        assertEquals(0, GrowthRules.saveMp(newPlace = true, alreadyRewarded = true, rewardedOnDay = 0))
    }

    @Test
    fun stageNeedsDayXpAndMp_andDoesNotSkip() {
        // D7 성장1: 70 XP · 5 MP
        assertEquals(0, GrowthRules.stageIndex(dayNumber = 7, totalXp = 69, totalMp = 5, ownedCards = emptySet(), doneQuests = emptySet()))
        assertEquals(1, GrowthRules.stageIndex(dayNumber = 7, totalXp = 70, totalMp = 5, ownedCards = emptySet(), doneQuests = emptySet()))
        // 날짜가 모자라면 XP·MP가 넘쳐도 못 올라간다
        assertEquals(0, GrowthRules.stageIndex(dayNumber = 6, totalXp = 9999, totalMp = 9999, ownedCards = emptySet(), doneQuests = emptySet()))
    }

    @Test
    fun evolutionNeedsCardAndQuest() {
        // 진화1(D30, 450 XP, 20 MP) — 카드/퀘스트 없으면 성장2(idx2)에서 멈춘다
        assertEquals(2, GrowthRules.stageIndex(dayNumber = 30, totalXp = 450, totalMp = 20, ownedCards = emptySet(), doneQuests = emptySet()))
        assertEquals(2, GrowthRules.stageIndex(dayNumber = 30, totalXp = 450, totalMp = 20, ownedCards = setOf(1), doneQuests = emptySet()))
        assertEquals(3, GrowthRules.stageIndex(dayNumber = 30, totalXp = 450, totalMp = 20, ownedCards = setOf(1), doneQuests = setOf(1)))
    }

    @Test
    fun activeEvolutionByLotteryWindow() {
        assertNull(GrowthRules.activeEvolution(dayNumber = 19, ownedCards = emptySet()))
        assertEquals(1, GrowthRules.activeEvolution(dayNumber = 20, ownedCards = emptySet())?.evoIndex)
        // 1을 이미 가졌으면 다음은 2(추첨 시작 D80)
        assertNull(GrowthRules.activeEvolution(dayNumber = 30, ownedCards = setOf(1)))
        assertEquals(2, GrowthRules.activeEvolution(dayNumber = 80, ownedCards = setOf(1))?.evoIndex)
    }

    @Test
    fun lineageDefaultsToWindWhenNoData() {
        assertEquals(Lineage.WIND, GrowthRules.dominantLineage(emptyMap()))
        assertEquals(Lineage.EARTH, GrowthRules.dominantLineage(mapOf(Lineage.EARTH to 6f, Lineage.WIND to 2f)))
    }

    @Test
    fun scoreTripSeparatesHabits() {
        assertEquals(Lineage.WIND, GrowthRules.dominantLineage(GrowthRules.scoreTrip(28f, 0, true)))
        assertEquals(Lineage.EARTH, GrowthRules.dominantLineage(GrowthRules.scoreTrip(5f, 0, true)))
        assertEquals(Lineage.LIGHT_RAY, GrowthRules.dominantLineage(GrowthRules.scoreTrip(12f, 4, false)))
    }
}
