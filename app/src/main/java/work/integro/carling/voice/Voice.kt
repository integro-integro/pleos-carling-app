package work.integro.carling.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

private const val TAG = "Voice"

/**
 * 3초 이내 음성 한마디. Pleos TTS SDK가 Coming Soon이라 Android 표준 TTS를 쓴다.
 * (Pleos Connect에서는 Pleos 음성 서비스가 기본 TTS 엔진으로 등록되어 있다)
 */
class Voice(context: Context) {

    private var ready = false
    // 에뮬레이터(Pleos Connect v2.1.0)에서는 초기화가 -1(ERROR)로 실패한다 — 2026-10-31 확인.
    // Pleos TTS SDK(클라이언트 ID 필요)가 열리면 그쪽으로 교체한다. 실패해도 앱은 무음으로 동작.
    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            setKorean()
        } else {
            Log.w(TAG, "TTS 초기화 실패: $status")
        }
    }

    private fun setKorean() {
        val result = tts.setLanguage(Locale.KOREAN)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "한국어 음성 미지원: $result")
        }
    }

    fun say(text: String) {
        if (!ready) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    fun shutdown() = tts.shutdown()
}
