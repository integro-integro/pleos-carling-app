package work.integro.carling.game

import org.junit.Assert.assertEquals
import org.junit.Test

class KoreanTest {
    @Test
    fun particlesFollowBatchim() {
        assertEquals("데이지 휠캡을", "데이지 휠캡".eulReul())
        assertEquals("새싹 안테나를", "새싹 안테나".eulReul())
        assertEquals("라벤더가", "라벤더".iGa())
        assertEquals("살구빛이", "살구빛".iGa())
        assertEquals("여행가방과", "여행가방".waGwa())
        assertEquals("뒤쪽 소품이에요", "뒤쪽 소품".ieyo())
        assertEquals("안테나예요", "안테나".ieyo())
        assertEquals("가족과 함께였어요", "가족과 함께".ieosseoyo())
        assertEquals("출퇴근이었어요", "출퇴근".ieosseoyo())
    }
}
