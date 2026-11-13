package work.integro.carling.voice

import org.junit.Assert.assertTrue
import org.junit.Test

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BabbleTest {
    @Test
    fun youngerIsHigherAndLinesHaveSound() {
        assertTrue(Babble.basePitch(0, null) > Babble.basePitch(5, null))
        val pcm = Babble.synthLine("안녕! 나는 카링이에요?", Babble.basePitch(1, null), null)
        assertTrue(pcm.size > 22_050 / 2)
        assertTrue(pcm.any { it > 10_000 })
        assertTrue(Babble.synthLine("...", 700.0, null).isNotEmpty()) // 글자가 없으면 "뿅"
    }

    /** 들어보기용 샘플: -Pbabble.out=폴더 를 주면 그 폴더에 wav를 쓴다. */
    @Test
    fun writeSamples() {
        val dir = System.getProperty("babble.out")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        dir.mkdirs()
        // 카링 목소리 후보 3가지(2026-11-12 사용자 요청): 같은 문장을 음색만 바꿔 들어본다
        val samples = listOf(
            Triple("A_mallang_babble", 0 to null, "안녕! 오늘도 같이 달려요!"),
            Triple("B_bell_bright", 1 to Timbre.LIGHT, "안녕! 오늘도 같이 달려요!"),
            Triple("C_breeze_whisper", 3 to Timbre.WIND, "안녕~ 오늘도 같이 달려요?"),
        )
        for ((name, look, text) in samples) {
            val (stage, lineage) = look
            writeWav(File(dir, "$name.wav"), Babble.synthLine(text, Babble.basePitch(stage, lineage), lineage))
        }
        writeWav(File(dir, "7_chirp.wav"), Babble.synthChirp(Babble.basePitch(1, null), null))
    }

    private fun writeWav(f: File, pcm: ShortArray) {
        val rate = 22_050
        val b = ByteBuffer.allocate(44 + pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(pcm.size * 2)
        pcm.forEach { b.putShort(it) }
        f.writeBytes(b.array())
    }
}
