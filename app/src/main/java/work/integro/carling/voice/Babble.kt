package work.integro.carling.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import work.integro.carling.game.Form
import work.integro.carling.game.Lineage
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 카링의 목소리. 말풍선 글자 수만큼 "뾰로롱" 하는 높은 옹알이를 즉석에서 만들어 낸다.
 * 녹음 파일 없이 소리를 합성하므로 용량이 들지 않고, 단계·계열마다 목소리가 조금씩 다르다.
 * - 어릴수록 높고, 자랄수록 조금 낮아진다.
 * - 계열마다 음색이 다르다 (바람은 숨소리, 빛은 반짝, 나무는 맑게, 물은 방울방울, 흙은 포근하게).
 * - 물음표로 끝나면 끝음이 올라가고, 느낌표면 통 튄다.
 * 소리를 못 내도 앱은 그대로 동작한다.
 */
object Babble {
    private const val TAG = "Babble"
    private const val RATE = 22_050
    private const val MAX_SYLLABLES = 14
    private const val VOLUME = 0.42f

    /** 꺼져 있으면 아무 소리도 내지 않는다 (홈 화면의 음성 켜짐/꺼짐과 같이 움직인다). */
    @Volatile var enabled = true
    /** 목소리 단계 (-1 = 알, 소리 없음). */
    @Volatile var stage = 0
    @Volatile var lineage: Lineage? = null

    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var current: AudioTrack? = null

    /** 진화 단계를 목소리 단계로 바꾼다. 알은 말하지 않는다. */
    fun stageOf(form: Form): Int = when (form) {
        Form.EGG -> -1
        Form.BABY -> 0
        Form.EVO1 -> 3
    }

    /** 단계별 기본 음높이(Hz). 사람 말소리(200Hz 안팎)보다 훨씬 높게 해서 작은 생물처럼 들리게 한다. */
    internal fun basePitch(stage: Int, lineage: Lineage?): Double {
        val byStage = when {
            stage <= 0 -> 860.0
            stage == 1 -> 800.0
            stage == 2 -> 740.0
            stage == 3 -> 690.0
            stage == 4 -> 650.0
            else -> 620.0
        }
        return byStage * when (lineage) {
            Lineage.EARTH -> 0.9
            Lineage.LIGHT_RAY -> 1.06
            Lineage.WIND -> 1.03
            else -> 1.0
        }
    }

    /** 말풍선 한 줄을 옹알이로 말한다. */
    fun say(text: String) {
        if (!enabled || stage < 0) return
        play { synthLine(text, basePitch(stage, lineage), lineage) }
    }

    /** 말 없이 감정만 보일 때 짧게 "뿅". */
    fun chirp() {
        if (!enabled || stage < 0) return
        play { synthChirp(basePitch(stage, lineage), lineage) }
    }

    private fun play(make: () -> ShortArray) {
        worker.execute {
            runCatching {
                val pcm = make()
                if (pcm.isEmpty()) return@runCatching
                current?.let { old -> runCatching { old.stop(); old.release() } }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.setVolume(VOLUME)
                track.play()
                current = track
                // 다 재생되면 정리한다 (다음 소리가 먼저 오면 그쪽에서 정리)
                Thread.sleep(pcm.size * 1000L / RATE + 120)
                if (current === track) {
                    runCatching { track.stop(); track.release() }
                    current = null
                }
            }.onFailure { Log.w(TAG, "카링 목소리 재생 실패", it) }
        }
    }

    // ---- 합성 ----

    private class Syllable(val vowel: Int, val pause: Boolean)

    /** 한글 음절은 모음(중성)으로 음색을 고르고, 띄어쓰기는 짧은 쉼이 된다. */
    private fun syllables(text: String): List<Syllable> {
        val out = ArrayList<Syllable>()
        for (ch in text) {
            when {
                ch in '가'..'힣' -> out += Syllable(((ch - '가') % 588) / 28, pause = false)
                ch.isLetterOrDigit() -> out += Syllable(ch.code % 21, pause = false)
                ch == ' ' && out.isNotEmpty() && !out.last().pause -> out += Syllable(0, pause = true)
            }
            if (out.count { !it.pause } >= MAX_SYLLABLES) break
        }
        while (out.isNotEmpty() && out.last().pause) out.removeAt(out.lastIndex)
        return out
    }

    internal fun synthLine(text: String, base: Double, lineage: Lineage?): ShortArray {
        val sy = syllables(text)
        if (sy.isEmpty()) return synthChirp(base, lineage)
        val trimmed = text.trimEnd()
        val asking = trimmed.endsWith("?")
        val excited = trimmed.endsWith("!") || trimmed.endsWith("~")
        // 글이 같으면 늘 같은 가락이 나오도록 글에서 씨앗을 만든다
        val rnd = Random(text.hashCode())
        val buf = ArrayList<Float>()
        val voiced = sy.count { !it.pause }
        var n = 0
        for (s in sy) {
            if (s.pause) {
                repeat((RATE * 0.045).toInt()) { buf += 0f }
                continue
            }
            n++
            val last = n == voiced
            // 모음에 따라 살짝 높낮이가 달라 말하는 것처럼 들린다 (ㅣ·ㅔ는 높게, ㅗ·ㅜ는 낮게)
            val vowelLift = VOWEL_LIFT[s.vowel.coerceIn(0, VOWEL_LIFT.lastIndex)]
            var pitch = base * vowelLift * (0.94 + rnd.nextDouble() * 0.14)
            var endRise = 1.0
            var dur = 0.068 + rnd.nextDouble() * 0.022
            if (last && asking) { endRise = 1.38; dur = 0.13 }
            if (last && excited) { pitch *= 1.18; dur = 0.085 }
            synthBlip(buf, pitch, dur, endRise, bright = VOWEL_BRIGHT[s.vowel.coerceIn(0, VOWEL_BRIGHT.lastIndex)], lineage, rnd)
            repeat((RATE * 0.014).toInt()) { buf += 0f }
        }
        return toPcm(buf)
    }

    internal fun synthChirp(base: Double, lineage: Lineage?): ShortArray {
        val buf = ArrayList<Float>()
        val rnd = Random(7)
        synthBlip(buf, base * 0.95, 0.07, 1.3, bright = 0.3, lineage, rnd)
        repeat((RATE * 0.02).toInt()) { buf += 0f }
        synthBlip(buf, base * 1.25, 0.09, 1.15, bright = 0.4, lineage, rnd)
        return toPcm(buf)
    }

    /**
     * 음절 하나. 시작은 살짝 높았다가 내려앉는 "뽀" 모양(귀여운 느낌의 핵심)이고,
     * [endRise]가 1보다 크면 끝에서 다시 올라간다(질문).
     */
    private fun synthBlip(buf: MutableList<Float>, pitch: Double, dur: Double, endRise: Double, bright: Double, lineage: Lineage?, rnd: Random) {
        val len = (RATE * dur).toInt()
        val attack = (RATE * 0.006).toInt()
        val vibRate = if (lineage == Lineage.WATER) 22.0 else 9.0
        val vibDepth = if (lineage == Lineage.WATER) 0.035 else 0.012
        val h2 = 0.18 + bright * 0.35 + if (lineage == Lineage.LIGHT_RAY) 0.15 else 0.0
        val h3 = if (lineage == Lineage.TREE) 0.02 else 0.06 + bright * 0.12 + if (lineage == Lineage.EARTH) 0.12 else 0.0
        val breath = if (lineage == Lineage.WIND) 0.10 else 0.0
        var phase = 0.0
        for (i in 0 until len) {
            val t = i.toDouble() / len
            // 처음 1.18배에서 빠르게 내려와 제 음에 앉고, 질문이면 뒤 40%에서 올라간다
            val slide = 1.0 + 0.18 * exp(-t * 9.0)
            val rise = if (endRise > 1.0 && t > 0.6) 1.0 + (endRise - 1.0) * ((t - 0.6) / 0.4) else 1.0
            val f = pitch * slide * rise * (1.0 + vibDepth * sin(2 * PI * vibRate * i / RATE))
            phase += 2 * PI * f / RATE
            var v = sin(phase) + h2 * sin(2 * phase) + h3 * sin(3 * phase)
            if (breath > 0) v += breath * (rnd.nextDouble() * 2 - 1)
            val env = min(1.0, i.toDouble() / attack) * exp(-t * 2.6) * (1.0 - t * t * t)
            buf += (v * env / (1.0 + h2 + h3)).toFloat()
        }
    }

    private fun toPcm(buf: List<Float>): ShortArray {
        // 앞뒤로 아주 짧게 비워 클릭 소리를 막는다
        val pad = (RATE * 0.01).toInt()
        val out = ShortArray(buf.size + pad * 2)
        for (i in buf.indices) out[i + pad] = (buf[i].coerceIn(-1f, 1f) * 30_000).toInt().toShort()
        return out
    }

    // 중성 21개: ㅏ ㅐ ㅑ ㅒ ㅓ ㅔ ㅕ ㅖ ㅗ ㅘ ㅙ ㅚ ㅛ ㅜ ㅝ ㅞ ㅟ ㅠ ㅡ ㅢ ㅣ
    private val VOWEL_LIFT = doubleArrayOf(
        1.00, 1.06, 1.04, 1.08, 0.95, 1.08, 1.00, 1.08, 0.92, 0.97, 1.02, 1.02, 0.95, 0.90, 0.94, 1.00, 1.06, 0.93, 0.96, 1.05, 1.12,
    )
    private val VOWEL_BRIGHT = doubleArrayOf(
        0.6, 0.8, 0.7, 0.8, 0.4, 0.8, 0.5, 0.8, 0.2, 0.4, 0.6, 0.6, 0.3, 0.1, 0.3, 0.6, 0.7, 0.2, 0.3, 0.7, 1.0,
    )
}
