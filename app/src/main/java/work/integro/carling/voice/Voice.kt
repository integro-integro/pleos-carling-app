package work.integro.carling.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import java.util.Locale

private const val TAG = "Voice"

/**
 * 3초 이내 음성 한마디. TextToSpeech SDK를 차량 안 엔진(ON_DEVICE)으로 쓴다.
 * 네트워크와 앱 등록이 필요 없다. 실패해도 앱은 무음으로 동작한다.
 */
class Voice(context: Context) {

    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private var ready = false
    private var connecting = false
    private var speaking = false

    private val events = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() {
            speaking = true
        }

        override fun onDone() {
            speaking = false
            Log.i(TAG, "음성 재생 완료")
        }

        override fun onStop() {
            speaking = false
        }

        override fun onError(errorType: ErrorType) {
            speaking = false
            Log.w(TAG, "음성 오류: $errorType")
        }

        override fun onUpdatedRms(rms: Double) = Unit
    }

    init {
        connect()
    }

    /** 시동 직후에는 음성 서비스가 아직 뜨지 않았을 수 있어, 실패하면 다음 한마디 때 다시 연결한다. */
    private fun connect() {
        if (ready || connecting) return
        connecting = true
        runCatching {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    connecting = false
                    ready = true
                    tts.addEventListener(events)
                    tts.setLocale(Locale.KOREA)
                }

                override fun onFailure(errorType: ErrorType) {
                    connecting = false
                    Log.w(TAG, "음성 초기화 실패: $errorType")
                }
            })
        }.onFailure {
            connecting = false
            Log.w(TAG, "음성 사용 불가", it)
        }
    }

    fun say(text: String) {
        if (!ready) {
            connect()
            return
        }
        runCatching {
            if (speaking) tts.stop()
            tts.speak(text)
        }.onFailure { Log.w(TAG, "음성 요청 실패", it) }
    }

    fun shutdown() {
        runCatching {
            tts.removeEventListener(events)
            tts.release()
        }
    }
}
