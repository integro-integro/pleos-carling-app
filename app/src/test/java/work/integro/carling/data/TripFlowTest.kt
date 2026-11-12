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
        store.complete(id, TripPurpose.COMMUTE)
        assertTrue(store.data.rewards.isEmpty()) // 확정 전에는 지급하지 않는다
        drive(3.0); assertTrue(store.resume(clock.now()))
        drive(10.0); park(15f, 30)
        drive(5.0); store.settle()
        assertEquals(1, store.data.rewards.size)
        assertEquals(Rules.growthPoints(15f, 30 * 60L), store.data.rewards[id]!!.gp)
        assertEquals(CardState.COMPLETED, store.data.card(id)!!.state)
        assertEquals(TripPurpose.COMMUTE, store.data.card(id)!!.purpose)
    }

    @Test
    fun t6_restartAndReprocessDoesNotPayTwice() {
        val id = start(); drive(20.0); park(10f, 20)
        store.complete(id, TripPurpose.FIELD)
        drive(5.0); store.settle()
        val gp = store.data.totalGp
        restart()
        store.settle(); store.confirm(id, clock.now()); store.complete(id, TripPurpose.FIELD)
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
        store.complete(id, TripPurpose.COMMUTE); store.complete(id, TripPurpose.COMMUTE)
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
        store.complete(id, TripPurpose.COMMUTE); drive(5.0); store.settle()
        assertEquals(Stage.CRACKED, store.data.stage)
        assertEquals(Stage.CRACKED, store.data.unseenStage)
    }

    @Test
    fun luckyBoxesCappedAtFivePerDay() {
        repeat(7) { i ->
            val id = start(); drive(10.0); park(5f, 10)
            store.complete(id, TripPurpose.COMMUTE); drive(5.0); store.settle()
            drive(1.0)
            assertTrue(i < 7)
        }
        assertEquals(7, store.data.rewards.size)
        assertEquals(Rules.BOX_DAILY_MAX, store.data.boxes.size)
    }

    @Test
    fun openingBoxAddsBonusOnce() {
        val id = start(); drive(20.0); park(10f, 20)
        store.complete(id, TripPurpose.COMMUTE); drive(5.0); store.settle()
        val box = store.data.boxes.single()
        val before = store.data.totalGp
        store.openBox(box.boxId); store.openBox(box.boxId)
        assertEquals(before + box.bonusGp, store.data.totalGp)
        assertTrue(store.data.unopenedBoxes.isEmpty())
    }

    @Test
    fun eleventhCardReplacesOldestInPersonality() {
        val day = 24 * 60 * 60_000L
        // 아침 출퇴근 10장
        repeat(10) { i -> store.devPastTrip(t0 - (20 - i) * day, 30f, 40, 0, TripPurpose.COMMUTE) }
        assertEquals(TimeBucket.MORNING, store.data.personality.body)
        assertEquals(TripPurpose.COMMUTE, store.data.personality.decoration)
        val first = store.personalityOf().used.first()
        // 밤 외근 1장 → 가장 오래된 카드가 빠진다
        store.devPastTrip(t0 - 2 * day + 14 * 60 * 60_000L, 10f, 20, 0, TripPurpose.FIELD)
        val used = store.personalityOf().used
        assertEquals(10, used.size)
        assertFalse(first in used)
        assertTrue(store.data.snapshots.isNotEmpty())
    }

    @Test
    fun editingPurposeRecomputesPersonality() {
        val day = 24 * 60 * 60_000L
        repeat(3) { i -> store.devPastTrip(t0 - (5 - i) * day, 20f, 30, 0, TripPurpose.FAMILY) }
        assertEquals(TripPurpose.FAMILY, store.data.personality.decoration)
        store.data.cards.take(2).forEach { store.editPurpose(it.tripId, TripPurpose.TRAVEL) }
        assertEquals(TripPurpose.TRAVEL, store.data.personality.decoration)
        assertNull(store.data.current)
    }
}
