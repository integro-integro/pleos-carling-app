package work.integro.carling.trip

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import work.integro.carling.BuildConfig
import work.integro.carling.data.CarlingStore
import work.integro.carling.data.TripResult
import work.integro.carling.vehicle.SpeedMonitor
import work.integro.carling.vehicle.VehicleSignals
import work.integro.carling.voice.Babble
import work.integro.carling.voice.Voice

private const val TAG = "TripTracker"

/** 앱이 도중에 꺼져도 이어 세도록 이동시간을 저장하는 간격. */
private const val SAVE_MOVING_MS = 20_000L

/**
 * 기어 P→주행→P 를 운행 한 번으로 묶어 기록한다.
 * 시동 이벤트 API가 없어 기어 전환을 시작/종료로 쓰고, 진행 중인 운행은 저장해 앱이 다시 떠도 이어 간다.
 * 경로와 위치는 기록하지 않는다.
 */
class TripTracker(
    private val scope: CoroutineScope,
    private val vehicle: VehicleSignals,
    private val speed: SpeedMonitor,
    private val store: CarlingStore,
    private val voice: Voice,
) {
    private var saveJob: Job? = null

    fun start() {
        scope.launch {
            vehicle.state.map { it.connected to it.isDriving }.distinctUntilChanged().collect { (connected, driving) ->
                if (!connected) return@collect
                if (driving) onDriveStart() else onDriveEnd()
            }
        }
    }

    private suspend fun onDriveStart() {
        speed.start()
        val running = store.data.activeTrip
        if (running != null) {
            // 운행 도중 앱이 다시 떴다 — 저장된 값에서 이어 간다. 급조작 수는 이어 셀 수 없다.
            Log.i(TAG, "진행 중 운행 복구")
            speed.resumeTrip(running.movingSeconds)
        } else {
            speed.beginTrip()
            store.beginTrip(store.clock.now(), vehicle.odometerKm())
            store.data.personality?.let { say(it.drivingLine) }
            Log.i(TAG, "운행 시작")
        }
        saveJob?.cancel()
        saveJob = scope.launch {
            while (isActive) {
                delay(SAVE_MOVING_MS)
                speed.movingSeconds?.let(store::saveMovingSeconds)
            }
        }
    }

    private suspend fun onDriveEnd() {
        val trip = store.data.activeTrip ?: return
        saveJob?.cancel()
        val end = store.clock.now()
        val distance = vehicle.odometerKm()?.let { now -> trip.startOdometerKm?.let { (now - it).coerceAtLeast(0f) } }
        val before = store.data.tripCards.size
        // 개발 빌드: 에뮬레이터는 기어만 바꾸고 속도가 0이라 경과 시간을 이동시간으로 친다
        val moving = if (BuildConfig.DEBUG) (end - trip.startMillis) / 1000 else speed.movingSeconds
        val harsh = speed.harshEvents
        store.clearActiveTrip()
        store.recordTrip(TripResult(trip.startMillis, end, distance, moving, harsh))
        speed.endTrip()
        val personality = store.data.personality
        if (store.data.tripCards.size > before && personality != null) say(personality.arrivalLine)
        Log.i(TAG, "운행 종료: ${(end - trip.startMillis) / 1000}s, ${distance}km, 이동 ${moving}s")
    }

    private fun say(text: String) {
        if (!store.data.voiceOn) return
        // 주행 중에도 카링이 말하는 느낌이 나도록 "뿅" 하고 나서 또렷한 음성으로 말한다
        Babble.stage = Babble.stageOf(store.data.form)
        Babble.lineage = store.data.lineage
        Babble.enabled = true
        Babble.chirp()
        voice.say(text, delayMillis = 450)
    }
}
