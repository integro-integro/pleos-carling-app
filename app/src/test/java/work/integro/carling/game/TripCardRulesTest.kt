package work.integro.carling.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripCardRulesTest {
    private val h = Rules.HOUR_MILLIS
    private val confirmed = 1_000_000_000L
    private fun draft(reminded: Long? = null) = CardClock(CardState.DRAFT, confirmed, reminded)

    @Test
    fun noDeadlineBeforeConfirmation() {
        assertNull(TripCardRules.expiresAt(CardClock(CardState.DRAFT, null, null)))
    }

    @Test
    fun unremindedDraftExpiresSevenDaysAfterConfirmation() {
        assertFalse(TripCardRules.isExpired(draft(), confirmed + 7 * 24 * h - 1))
        assertTrue(TripCardRules.isExpired(draft(), confirmed + 7 * 24 * h))
    }

    @Test
    fun remindedDraftExpires24HoursAfterReminder() {
        val r = confirmed + 6 * 24 * h + 23 * h
        assertFalse(TripCardRules.isExpired(draft(r), r + 24 * h - 1))
        assertTrue(TripCardRules.isExpired(draft(r), r + 24 * h))
    }

    @Test
    fun completedAndExpiredHaveNoDeadline() {
        assertNull(TripCardRules.expiresAt(CardClock(CardState.COMPLETED, confirmed, null)))
        assertNull(TripCardRules.expiresAt(CardClock(CardState.EXPIRED, confirmed, null)))
    }

    @Test
    fun reminderOnlyOnceAndOnlyForEarlierConfirmations() {
        assertTrue(TripCardRules.needsReminder(draft(), confirmed + h, confirmed + h))
        assertFalse(TripCardRules.needsReminder(draft(), confirmed - 1, confirmed))
        assertFalse(TripCardRules.needsReminder(draft(confirmed + 1), confirmed + h, confirmed + h))
        assertFalse(TripCardRules.needsReminder(CardClock(CardState.DRAFT, null, null), confirmed, confirmed))
    }

    @Test
    fun suggestsPurposeFromSimilarTimes() {
        val history = listOf(Triple(8, false, TripPurpose.TO_WORK), Triple(9, false, TripPurpose.TO_WORK), Triple(8, true, TripPurpose.DATE), Triple(8, true, TripPurpose.DATE))
        assertEquals(TripPurpose.TO_WORK, TripCardRules.suggestPurpose(8, false, history))
        assertEquals(TripPurpose.DATE, TripCardRules.suggestPurpose(7, true, history))
        assertNull(TripCardRules.suggestPurpose(15, false, history))
        assertEquals(TripPurpose.HOBBY, TripCardRules.suggestPurpose(0, false, listOf(Triple(23, false, TripPurpose.HOBBY), Triple(0, false, TripPurpose.HOBBY))))
    }

    @Test
    fun tagsAreCleaned() {
        assertEquals("헬스장", TripCardRules.cleanTag("  #헬스장 "))
        assertNull(TripCardRules.cleanTag(" # "))
    }
}
