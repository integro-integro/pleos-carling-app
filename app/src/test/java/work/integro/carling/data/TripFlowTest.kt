package work.integro.carling.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import work.integro.carling.game.CardState
import work.integro.carling.game.Clock
import work.integro.carling.game.Rules
import work.integro.carling.game.Stage
import work.integro.carling.game.TimeBucket
import work.integro.carling.game.TripPurpose
import work.integro.carling.game.TripStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

class FakeClock(var t: Long) : Clock {
    override fun now() = t
    override fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    override fun hourOf(millis: Long): Int = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).hour
    override fun skipMillis(millis: Long) { t += millis }
    override fun reset() = Unit
    fun advance(min: Double) { t += (min * 60_000).toLong() }
}

class MemStorage : Storage {
    var value: String? = null
    override fun read() = value
    override fun write(value: String, sync: Boolean) { this.value = value }
}

/** PRD 4.2.1 병합 테스트 T1~T10과 보상 1회 보장. */
class TripFlowTest {
    private val t0 = LocalDate.of(2026, 11, 10).atTime(8, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val clock = FakeClock(t0)
    private val storage = MemStorage()
    private var store = CarlingStore(storage, clock, Random(1))

    private fun drive(min: Double) = clock.advance(min)
    private fun start() = store.startTrip(clock.now(), 0f)
    private fun park(km: Float, movingMin: Int) = store.park(clock.now(), km, movingMin * 60L, 0)
    private fun restart() { store = CarlingStore(storage, clock, Random(1)) }

    @Test
    fun t1_shortStopMergesIntoOneTrip() {
        val id = start(); drive(20.0); park(10f, 20)
        drive(4 + 59 / 60.0)
        assertTrue(store.resume(clock.now()))
        drive(10.0); park(15f, 30)
        drive(5.0); store.settle()
        assertEquals(1, store.data.trips.size)
        assertEquals(id, store.data.trips.single().tripId)
        assertEquals(15f, store.data.trips.single().distanceKm)
        assertEquals(TripStatus.CONFIRMED, store.data.trips.single().status)
        assertEquals(1, store.data.cards.size)
    }

    @Test
    fun t2_fiveMinutesConfirmsAndNextDriveIsNewTrip() {
        val first = start(); drive(20.0); park(10f, 20)
        drive(5.0)
        assertFalse(store.resume(clock.now()))
        store.settle()
        assertEquals(TripStatus.CONFIRMED, store.data.trip(first)!!.status)
        val second = start()
        assertNotEquals(first, second)
    }

    @Test
    fun t4_repeatedShortStopsKeepOneCard() {
        start()
        repeat(3) { i ->
            drive(10.0); park(5f * (i + 1), 10 * (i + 1))
            drive(2.0)
            if (i < 2) assertTrue(store.resume(clock.now()))
        }
        assertEquals(1, store.data.trips.size)
        assertEquals(1, store.data.cards.size)
        assertEquals(15f, store.data.trips.single().distanceKm)
    }

    @Test
    fun t5_completeBeforeMergeKeepsPurposeAndPaysOnceOnFinalNumbers() {
        val id = start(); drive(20.0); park(10f, 20)
        store.complete(id, TripPurpose.TO_WORK)
        assertTrue(store.data.rewards.isEmpty()) // 확정 전에는 지급하지 않는다
        drive(3.0); assertTrue(store.resume(clock.now()))
        drive(10.0); park(15f, 30)
        drive(5.0); store.settle()
        assertEquals(1, store.data.rewards.size)
        assertEquals(Rules.growthPoints(15f, 30 * 60L), store.data.rewards[id]!!.gp)
        assertEquals(CardState.COMPLETED, store.data.card(id)!!.state)
        assertEquals(TripPurpose.TO_WORK, store.data.card(id)!!.purpose)
    }

    @Test
    fun t6_restartAndReprocessDoesNotPayTwice() {
        val id = start(); drive(20.0); park(10f, 20)
        store.complete(id, TripPurpose.WORK)
        drive(5.0); store.settle()
        val gp = store.data.totalGp
        restart()
        store.settle(); store.confirm(id, clock.now()); store.complete(id, TripPurpose.WORK)
        assertEquals(1, store.data.rewards.size)
        assertEquals(gp, store.data.totalGp)
    }

    @Test
    fun t7_laterThenCompleteAfterConfirmPaysAtCompletion() {
        val id = start(); drive(20.0); park(10f, 20)
        store.later()
        drive(5.0); store.settle()
        assertTrue(store.data.rewards.isEmpty())
        store.complete(id, TripPurpose.HOBBY)
        assertEquals(1, store.data.rewards.size)
        assertEquals(1, store.data.boxes.size)
    }

    @Test
    fun t8_appKilledWhileParkedIsSettledRetroactively() {
        val id = start(); drive(20.0); park(10f, 20)
        val parkAt = clock.now()
        drive(3.0); restart(); store.settle()
        assertEquals(TripStatus.PARKED, store.data.trip(id)!!.status)
        drive(4.0); restart(); store.settle()
        assertEquals(TripStatus.CONFIRMED, store.data.trip(id)!!.status)
        assertEquals(parkAt + Rules.STOP_MERGE_MILLIS, store.data.trip(id)!!.confirmedAt)
    }

    @Test
    fun t9_elapsedIncludesStopMovingDoesNot() {
        start(); drive(20.0); park(12f, 20)
        drive(4.0); assertTrue(store.resume(clock.now()))
        drive(10.0); park(18f, 30)
        val t = store.data.trips.single()
        assertEquals(30 * 60L, t.movingSec)
        assertEquals(34 * 60L, t.elapsedSec)
    }

    @Test
    fun t10_duplicateEventsChangeNothing() {
        val id = start(); drive(20.0); park(10f, 20)
        drive(5.0); store.settle(); store.settle(); store.confirm(id, clock.now())
        store.complete(id, TripPurpose.TO_WORK); store.complete(id, TripPurpose.TO_WORK)
        assertEquals(1, store.data.rewards.size)
        assertEquals(1, store.data.boxes.size)
    }

    @Test
    fun belowMinimumLeavesRecordWithoutCard() {
        start(); clock.advance(2 / 60.0); park(0f, 0)
        drive(5.0); store.settle()
        assertEquals(1, store.data.trips.size)
        assertTrue(store.data.cards.isEmpty())
    }

    @Test
    fun draftCardIsRemindedThenExpiresButRecordStays() {
        val id = start(); drive(20.0); park(10f, 20)
        store.later(); drive(5.0); store.settle()
        clock.advance(60.0 * 24); store.markIgnition()
        val due = store.reminderCards().map { it.tripId }
        assertEquals(listOf(id), due)
        store.markReminded(due)
        clock.advance(60.0 * 23); store.expireCards()
        assertEquals(CardState.DRAFT, store.data.card(id)!!.state)
        clock.advance(60.0); store.expireCards()
        assertEquals(CardState.EXPIRED, store.data.card(id)!!.state)
        assertEquals(TripStatus.CONFIRMED, store.data.trip(id)!!.status)
        // 재안내를 다시 보여줘도 기한은 늘지 않는다
        store.markIgnition(); assertTrue(store.reminderCards().isEmpty())
    }

    @Test
    fun unremindedDraftExpiresAfterSevenDays() {
        val id = start(); drive(20.0); park(10f, 20)
        drive(5.0); store.settle()
        clock.advance(60.0 * 24 * 7); store.expireCards()
        assertEquals(CardState.EXPIRED, store.data.card(id)!!.state)
    }

    @Test
    fun firstRewardCracksEggAndStageNeverDrops() {
        assertEquals(Stage.EGG, store.data.stage)
        val id = start(); drive(20.0); park(10f, 20)
        store.complete(id, TripPurpose.TO_WORK); drive(5.0); store.settle()
        assertEquals(Stage.CRACKED, store.data.stage)
        assertEquals(Stage.CRACKED, store.data.unseenStage)
    }

    @Test
    fun luckyBoxesCappedAtFivePerDay() {
        repeat(7) { i ->
            val id = start(); drive(10.0); park(5f, 10)
            store.complete(id, TripPurpose.TO_WORK); drive(5.0); store.settle()
            drive(1.0)
            assertTrue(i < 7)
        }
        assertEquals(7, store.data.rewards.size)
        assertEquals(Rules.BOX_DAILY_MAX, store.data.boxes.size)
    }

    @Test
    fun openingBoxAddsBonusOnce() {
        val id = start(); drive(20.0); park(10f, 20)
        store.complete(id, TripPurpose.TO_WORK); drive(5.0); store.settle()
        val box = store.data.boxes.single()
        val before = store.data.totalGp
        store.openBox(box.boxId); store.openBox(box.boxId)
        assertEquals(before + box.bonusGp, store.data.totalGp)
        assertTrue(store.data.unopenedBoxes.isEmpty())
    }

    @Test
    fun openAllOpensEveryBoxOnceAndEquipsNewCarItem() {
        repeat(4) {
            val id = start(); drive(10.0); park(5f, 10)
            store.complete(id, TripPurpose.TO_WORK); drive(5.0); store.settle()
        }
        val boxes = store.data.unopenedBoxes
        val before = store.data.totalGp
        val opened = store.openAll()
        assertEquals(boxes.map { it.boxId }, opened.map { it.boxId })
        assertTrue(store.data.unopenedBoxes.isEmpty())
        assertEquals(before + boxes.sumOf { it.bonusGp }, store.data.totalGp)
        assertTrue(store.openAll().isEmpty())
        // 새 꾸미기는 비어 있던 자리에 처음 나온 것이 달린다
        boxes.mapNotNull { it.part }.distinct().groupBy { it.slot }.forEach { (slot, parts) -> assertEquals(parts.first(), store.data.equipped[slot]) }
    }

    @Test
    fun karingReportCountsCardRewardsAndBoxBonus() {
        repeat(3) {
            val id = start(); drive(20.0); park(10f, 20)
            store.complete(id, TripPurpose.DATE); drive(5.0); store.settle()
        }
        store.openAll()
        val k = KaringReportBuilder.build(work.integro.carling.game.ReportPeriod.THIS_MONTH, clock.today(), ZoneOffset.UTC, store.data, store.personalityOf())
        assertEquals(store.data.rewards.values.sumOf { it.gp }, k.cardGp)
        assertEquals(store.data.boxes.sumOf { it.bonusGp }, k.bonusGp)
        assertEquals(store.data.totalGp, k.cumulative.last().second)
        assertEquals(3, k.window.size)
        assertEquals(3, k.boxesOpened)
        assertEquals(TripPurpose.DATE, k.purposeShare.single().first)
    }

    @Test
    fun lookCanBeChosenFromReachedStagesAfterBreeze() {
        store.devAddGp(1_600)
        assertEquals(Stage.BABY, store.data.stage)
        store.chooseLook(Stage.BABY)
        assertNull(store.data.look) // 아기카링에서는 고를 수 없다
        store.devAddGp(5_000)
        assertEquals(Stage.BREEZE, store.data.stage) // 버디링은 추후 공개
        store.chooseLook(Stage.BABY)
        assertEquals(Stage.BABY, store.data.shownStage)
        store.chooseLook(Stage.EGG)
        assertEquals(Stage.BABY, store.data.shownStage) // 알은 고를 수 없다
        store.chooseLook(Stage.MYCARLING)
        assertEquals(Stage.BABY, store.data.shownStage) // 공개 전 단계는 고를 수 없다
        store.chooseLook(Stage.BREEZE)
        assertNull(store.data.look)
        assertEquals(Stage.BREEZE, store.data.shownStage)
    }

    @Test
    fun oldPurposesAreMigratedOnLoad() {
        // 예전 저장값: 출퇴근(오전·오후), 외근(FIELD), 가족과 함께
        val am = t0 // 08시
        val pm = t0 + 10 * 60 * 60_000L // 18시
        listOf(am, pm, am + 86_400_000L, pm + 86_400_000L).forEachIndexed { i, s ->
            store.devPastTrip(s, 10f, 20, 0, TripPurpose.OTHER)
        }
        val json = storage.value!!
        val ids = store.data.cards.map { it.tripId }
        var old = json
        listOf("COMMUTE", "COMMUTE", "FIELD", "FAMILY").forEachIndexed { i, p ->
            old = old.replaceFirst("\"purpose\":\"OTHER\"", "\"purpose\":\"$p\"")
        }
        storage.value = old
        restart()
        val byId = store.data.cards.associateBy { it.tripId }
        assertEquals(TripPurpose.TO_WORK, byId[ids[0]]!!.purpose)
        assertEquals(TripPurpose.FROM_WORK, byId[ids[1]]!!.purpose)
        assertEquals(TripPurpose.WORK, byId[ids[2]]!!.purpose)
        assertEquals(TripPurpose.OTHER, byId[ids[3]]!!.purpose)
        assertEquals(work.integro.carling.game.Companion.FAMILY, byId[ids[3]]!!.companion)
    }

    @Test
    fun refuelDetectedWhenLevelRisesBetweenTrips() {
        val e = { soc: Int -> work.integro.carling.vehicle.EnergyReading(soc * 770f, soc, null) }
        val id = store.startTrip(clock.now(), 0f, energy = e(60)); drive(20.0); park(15f, 20)
        store.recordEnergy(id, e(52), null)
        assertEquals(15f / ((60 - 52) * 0.77f), store.data.trip(id)!!.efficiency!!, 0.01f)
        drive(6.0); store.settle(); drive(60.0)
        store.startTrip(clock.now(), 0f, energy = e(90))
        assertEquals(1, store.data.refuels.size)
        assertEquals(52, store.data.refuels.single().fromPercent)
        assertTrue(store.data.refuels.single().ev)
    }

    @Test
    fun completeStoresCompanionAndEditKeepsIt() {
        val id = start(); drive(20.0); park(10f, 20)
        store.complete(id, TripPurpose.MEAL, emptyList(), work.integro.carling.game.Companion.FRIENDS)
        assertEquals(work.integro.carling.game.Companion.FRIENDS, store.data.card(id)!!.companion)
        store.editPurpose(id, TripPurpose.SHOPPING)
        assertEquals(TripPurpose.SHOPPING, store.data.card(id)!!.purpose)
        assertEquals(work.integro.carling.game.Companion.FRIENDS, store.data.card(id)!!.companion)
    }

    @Test
    fun eleventhCardReplacesOldestInPersonality() {
        val day = 24 * 60 * 60_000L
        // 아침 출퇴근 10장
        repeat(10) { i -> store.devPastTrip(t0 - (20 - i) * day, 30f, 40, 0, TripPurpose.TO_WORK) }
        assertEquals(TimeBucket.MORNING, store.data.personality.body)
        assertEquals(TripPurpose.TO_WORK, store.data.personality.decoration)
        val first = store.personalityOf().used.first()
        // 밤 외근 1장 → 가장 오래된 카드가 빠진다
        store.devPastTrip(t0 - 2 * day + 14 * 60 * 60_000L, 10f, 20, 0, TripPurpose.WORK)
        val used = store.personalityOf().used
        assertEquals(10, used.size)
        assertFalse(first in used)
        assertTrue(store.data.snapshots.isNotEmpty())
    }

    @Test
    fun editingPurposeRecomputesPersonality() {
        val day = 24 * 60 * 60_000L
        repeat(3) { i -> store.devPastTrip(t0 - (5 - i) * day, 20f, 30, 0, TripPurpose.DATE) }
        assertEquals(TripPurpose.DATE, store.data.personality.decoration)
        store.data.cards.take(2).forEach { store.editPurpose(it.tripId, TripPurpose.TRAVEL) }
        assertEquals(TripPurpose.TRAVEL, store.data.personality.decoration)
        assertNull(store.data.current)
    }

    @Test
    fun placesNeedConsentAndAreClearedOnOptOut() {
        val id = start(); drive(10.0); park(5f, 10)
        store.recordPlace(id, start = "1124071", end = "3102374")
        assertNull(store.data.trip(id)!!.endDong)
        store.setLocationConsent(true)
        store.recordPlace(id, start = "1124071")
        store.recordPlace(id, end = "3102374")
        assertEquals("1124071", store.data.trip(id)!!.startDong)
        assertEquals("3102374", store.data.trip(id)!!.endDong)
        restart()
        assertEquals("3102374", store.data.trip(id)!!.endDong)
        store.setLocationConsent(false)
        assertEquals(false, store.data.locationConsent)
        assertNull(store.data.trip(id)!!.startDong)
        assertNull(store.data.trip(id)!!.endDong)
    }

    @Test
    fun customPurposeOnlyForOtherAndRemembered() {
        val id = start(); drive(10.0); park(5f, 10)
        store.complete(id, TripPurpose.OTHER, custom = " 병원 ")
        assertEquals("병원", store.data.card(id)!!.customPurpose)
        assertEquals("병원", store.data.card(id)!!.purposeLabel)
        assertEquals(listOf("병원"), store.data.customPurposes)
        store.editPurpose(id, TripPurpose.MEAL)
        assertNull(store.data.card(id)!!.customPurpose)
        assertEquals("식사", store.data.card(id)!!.purposeLabel)
    }

    @Test
    fun allSixBadgesGiveAurora() {
        store.devBadgeBoxes(5)
        store.openAll()
        assertEquals(null, store.data.colors[work.integro.carling.game.CarColor.AURORA])
        store.devBadgeBoxes(1)
        store.openAll()
        assertEquals(6, store.data.badges.size)
        assertEquals(1, store.data.colors[work.integro.carling.game.CarColor.AURORA])
        assertTrue(store.data.unseenSetReward)
    }

    @Test
    fun growthCompareLearnsAndUnlocks() {
        val day = 24 * 60 * 60_000L
        // 지난달(10월) 출근 3장, 이번 달(11월) 출근 4장 — 이번 달부터 목적을 맞힌다
        val oct = java.time.LocalDate.of(2026, 10, 5).atTime(8, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        repeat(3) { store.devPastTrip(oct + it * day, 30f, 40, 0, TripPurpose.TO_WORK) }
        repeat(4) { store.devPastTrip(t0 - (8 - it) * day, 30f, 40, 0, TripPurpose.TO_WORK) }
        val g = GrowthReportBuilder.build(work.integro.carling.game.ReportPeriod.THIS_MONTH, clock.today(), ZoneOffset.UTC, store.data)
        assertEquals(4, g.accuracy.total)
        assertEquals(100, g.accuracy.percent)
        assertEquals(1, g.prevAccuracy.total) // 10월 세 번째 카드부터 추천(같은 시간대 2장)
        assertTrue(g.habits.any { it.key == "to_work" && it.detail.contains("8:00") })
        assertTrue(g.abilities.first { it.key == "suggest" }.unlocked)
        assertTrue(g.abilities.first { it.key == "talk_MILESTONE" }.unlocked.not() || store.data.stage == work.integro.carling.game.Stage.MYCARLING)
        assertTrue(g.now.cards > g.before.cards)
    }

    @Test
    fun myKaringUnequipAllAndDisplayBadge() {
        store.devPartBoxes(2); store.openAll()
        assertTrue(store.data.equipped.isNotEmpty() || store.data.parts.isNotEmpty())
        store.data.parts.keys.forEach { store.equip(it) }
        store.unequipAll()
        assertTrue(store.data.equipped.isEmpty())
        assertNull(store.data.carColor)
        store.setDisplayBadge(work.integro.carling.game.Badge.MINT_HEART)
        assertNull(store.data.displayBadge) // 없는 배지는 못 단다
        store.devBadgeBoxes(1); store.openAll()
        val b = store.data.badges.keys.first()
        store.setDisplayBadge(b)
        assertEquals(b, store.data.displayBadge)
    }

    @Test
    fun levelJellyAndShop() {
        assertEquals(1, work.integro.carling.game.LevelRules.of(0))
        assertEquals(10, work.integro.carling.game.LevelRules.of(work.integro.carling.game.LevelRules.need(10)))
        assertEquals(work.integro.carling.game.LevelRules.MAX, work.integro.carling.game.LevelRules.of(1_000_000))
        assertEquals(work.integro.carling.game.EmblemTier.GOLD, work.integro.carling.game.EmblemTier.of(35))
        // 카드 보상 하나: 젤리 15 + 20km ÷ 5 = 19
        val id = start(); drive(30.0); park(20f, 30)
        store.complete(id, TripPurpose.MEAL); drive(5.0); store.settle()
        assertEquals(19, store.data.jelly)
        // 상점: 모자라면 못 산다, 모으면 산다
        val wheel = work.integro.carling.game.ShopItem.Part(work.integro.carling.game.CarPart.WHEEL_DAISY)
        assertFalse(store.buy(wheel))
        store.devAddJelly(1_000)
        assertTrue(store.buy(wheel))
        assertEquals(1, store.data.parts[work.integro.carling.game.CarPart.WHEEL_DAISY])
        assertEquals(1_019 - 300, store.data.jelly)
        val night = work.integro.carling.game.ShopItem.Theme(TimeBucket.NIGHT)
        assertTrue(store.buy(night))
        assertEquals(TimeBucket.NIGHT, store.data.theme)
        assertFalse(store.buy(night)) // 테마는 한 번만
        store.useTheme(null)
        assertNull(store.data.theme)
        // 레벨업 축하를 보면 오른 레벨만큼 젤리 50
        val before = store.data.jelly
        val seen = store.data.levelSeen
        store.devAddGp(5_000)
        store.seenLevel()
        val up = work.integro.carling.game.LevelRules.of(store.data.totalGp) - seen
        assertTrue(up > 0)
        assertEquals(before + up * 50, store.data.jelly)
        store.setHomeAngle(-1)
        assertEquals(7, store.data.homeAngle)
    }
}
