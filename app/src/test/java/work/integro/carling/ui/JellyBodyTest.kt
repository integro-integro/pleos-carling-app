package work.integro.carling.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class JellyBodyTest {
    private fun maxDisp(b: JellyBody) = b.displacements(breathing = false).maxOf { abs(it) }

    @Test
    fun hopMakesVisibleWobbleThenSettles() {
        val b = JellyBody()
        // 걷기 한 번 뛸 때 정도의 위아래 가속(약 17)을 0.15초 준다
        var peak = 0f
        repeat(9) { b.step(1 / 60f, 0f, -17f); peak = maxOf(peak, maxDisp(b)) }
        repeat(30) { b.step(1 / 60f, 0f, 0f); peak = maxOf(peak, maxDisp(b)) }
        println("hop peak=$peak")
        assertTrue("눈에 보일 만큼 출렁여야 함: $peak", peak in 0.02f..0.14f)
        repeat(240) { b.step(1 / 60f, 0f, 0f) }
        assertTrue("4초 뒤에는 가라앉아야 함", b.settled)
    }

    @Test
    fun pokeDentsAndFeetStayPut() {
        val b = JellyBody()
        b.poke(0.5f, 0.3f)
        var head = 0f
        var feet = 0f
        repeat(20) {
            b.step(1 / 60f, 0f, 0f)
            val d = b.displacements(breathing = false)
            head = maxOf(head, abs(d[1 * 2 + 1]), abs(d[2 * 2 + 1]))
            feet = maxOf(feet, abs(d[13 * 2 + 1]), abs(d[14 * 2 + 1]))
        }
        println("poke head=$head feet=$feet")
        assertTrue(head > 0.03f)
        assertTrue(feet < head / 2)
    }

    @Test
    fun violentShakingStaysBounded() {
        val b = JellyBody()
        repeat(600) { i -> b.step(1 / 60f, if (i % 4 < 2) 60f else -60f, if (i % 6 < 3) 60f else -60f) }
        assertTrue(b.displacements(breathing = false).all { !it.isNaN() && abs(it) < 0.3f })
    }
}
