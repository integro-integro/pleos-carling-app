package work.integro.carling.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveTalkTest {
    private val min = 60_000L
    private fun snap(atMin: Long, km: Float = 5f, speed: Float = 60f, harsh: Int = 0, energy: Int = 80, hour: Int = 14, lastHarshAtMin: Long? = null) =
        DriveSnapshot(atMin * min, 0L, km, speed, harsh, lastHarshAtMin?.times(min), energy, hour)

    @Test
    fun saysSituationalLinesWithGapAndOncePerTrip() {
        var mem = TalkMemory()
        // 급조작 → 천천히
        assertEquals(Talk.SLOW_DOWN, DriveTalkRules.next(snap(3, harsh = 1, lastHarshAtMin = 3), Stage.BABY, mem, prevHarsh = 0))
        mem = DriveTalkRules.remember(mem, Talk.SLOW_DOWN, 3 * min)
        // 5분 안엔 조용
        assertNull(DriveTalkRules.next(snap(6, energy = 10), Stage.BABY, mem))
        // 5분 지나면 배고파
        assertEquals(Talk.HUNGRY, DriveTalkRules.next(snap(9, energy = 10), Stage.BABY, mem))
        // 같은 급조작 말은 한 운행에 한 번
        mem = DriveTalkRules.remember(mem, Talk.HUNGRY, 9 * min)
        assertNull(DriveTalkRules.next(snap(20, harsh = 2, lastHarshAtMin = 20), Stage.BABY, mem, prevHarsh = 1))
    }

    @Test
    fun stageGatesLinesAndMilestones() {
        // 깨어난 알은 '너무 빨라'를 아직 모른다
        assertNull(DriveTalkRules.next(snap(10, speed = 130f, lastHarshAtMin = 9), Stage.CRACKED, TalkMemory()))
        assertEquals(Talk.TOO_FAST, DriveTalkRules.next(snap(10, speed = 130f, lastHarshAtMin = 9), Stage.BABY, TalkMemory()))
        assertEquals(Talk.CARSICK, DriveTalkRules.next(snap(125, lastHarshAtMin = 120), Stage.BABY, TalkMemory()))
        assertEquals(Talk.SMOOTH, DriveTalkRules.next(snap(40), Stage.BREEZE, TalkMemory()))
        var mem = TalkMemory(said = setOf(Talk.SMOOTH))
        assertEquals(Talk.MILESTONE, DriveTalkRules.next(snap(40, km = 51f), Stage.MYCARLING, mem))
        mem = DriveTalkRules.remember(mem, Talk.MILESTONE, 40 * min)
        assertEquals("벌써 100km 왔어! 같이 오니까 금방이다.", DriveTalkRules.lineOf(Talk.MILESTONE, mem))
        assertEquals(2, Talk.known(Stage.CRACKED).size)
        assertEquals(Talk.entries.size, Talk.known(Stage.MYCARLING).size)
    }
}
