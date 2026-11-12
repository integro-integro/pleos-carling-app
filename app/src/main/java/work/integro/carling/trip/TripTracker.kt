package work.integro.carling.trip

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import work.integro.carling.BuildConfig
import work.integro.carling.data.CarlingStore
import work.integro.carling.game.Rules
import work.integro.carling.game.TripStatus
import work.integro.carling.vehicle.SpeedMonitor
import work.integro.carling.vehicle.VehicleSignals
import work.integro.carling.voice.Babble
import work.integro.carling.voice.Voice

private const val TAG = "TripTracker"

/** D 화면에 보여줄 지금 운행의 거리·시간. */
data class LiveTrip(val distanceKm: Float?, val minutes: Int)

/**
 * 기어로 운행 상태를 나눈다(PRD 4.2).
 * - P가 아닌 기어: 기록 중. 주차 5분 안이면 같은 운행에 계속 쌓는다(TripResumed), 아니면 새 운행(TripStarted).
 * - P: 주차됨(TripParked). 임시 카드가 바로 뜨고, 5분이 지나면 확정(TripConfirmed).
 * 시동 이벤트는 쓰지 않는다. 앱이 꺼진 동안의 5분은 다음 실행 때 [CarlingStore.settle]이 소급 판정한다.
 * 경로와 위치는 기록하지 않는다.
 */
class TripTracker(
    private val scope: CoroutineScope,
    private val vehicle: VehicleSignals,
    private val speed: SpeedMonitor,
    private val store: CarlingStore,
    private val voice: Voice,
) {
    private var snapshotJob: Job? = null
    private var confirmJob: Job? = null
    /** 이 프로세스에서 직접 기록 중인 운행. 아니면 앱이 꺼졌다 뜬 것이다. */
    private var liveTripId: Long? = null

    private val _live = MutableStateFlow<LiveTrip?>(null)
    val live: StateFlow<LiveTrip?> = _live.asStateFlow()

    fun start() {
        // 앱이 꺼져 있던 동안 지난 5분을 먼저 정리한다
        store.settle()
        scope.launch {
            vehicle.state.map { it.connected to it.isDriving }.distinctUntilChanged().collect { (connected, driving) ->
                if (!connected) return@collect
                if (driving) onDrive() else onPark()
            }
        }
    }

    private suspend fun onDrive() {
        confirmJob?.cancel()
        speed.start()
        val now = store.clock.now()
        store.settle(now, liveTripId)
        val current = store.data.current
        when {
            current?.status == TripStatus.PARKED && store.resume(now) -> {
                Log.i(TAG, "짧은 정차 뒤 같은 운행으로 이어 감")
                speed.resumeTrip(current.movingSec ?: 0, current.harsh)
            }
            current?.status == TripStatus.RECORDING -> {
                Log.i(TAG, "진행 중 운행 복구")
                speed.resumeTrip(current.movingSec ?: 0, current.harsh)
            }
            else -> {
                speed.beginTrip()
                store.markIgnition(now)
                store.startTrip(now, vehicle.odometerKm())
                say("출발! 오늘도 안전하게 함께 가요.")
                Log.i(TAG, "운행 시작")
            }
        }
        liveTripId = store.data.current?.tripId
        snapshotJob?.cancel()
        snapshotJob = scope.launch {
            var tick = 0L
            while (isActive) {
                val (km, moving) = measure()
                _live.value = LiveTrip(km, ((moving ?: 0L) / 60).toInt())
                if (tick % (Rules.SNAPSHOT_MILLIS / LIVE_MILLIS) == 0L) store.snapshot(store.clock.now(), km, moving, speed.harshEvents)
                tick++
                delay(LIVE_MILLIS)
            }
        }
    }

    private suspend fun onPark() {
        snapshotJob?.cancel()
        _live.value = null
        val now = store.clock.now()
        store.settle(now, liveTripId)
        val current = store.data.current ?: return
        if (current.status == TripStatus.RECORDING) {
            val (km, moving) = measure()
            val before = store.data.cards.size
            store.park(now, km, moving, speed.harshEvents)
            speed.endTrip()
            if (store.data.cards.size > before) say("도착했어요! 오늘 여정을 카드로 남겨요.")
            Log.i(TAG, "주차: ${km}km, 이동 ${moving}s")
        }
        liveTripId = null
        val parkAt = store.data.current?.lastParkAt ?: return
        confirmJob?.cancel()
        confirmJob = scope.launch {
            delay((parkAt + Rules.STOP_MERGE_MILLIS - store.clock.now()).coerceAtLeast(0))
            store.settle()
        }
    }

    /** 지금 운행의 누적 거리와 이동시간. */
    private suspend fun measure(): Pair<Float?, Long?> {
        val trip = store.data.current ?: return null to null
        val km = vehicle.odometerKm()?.let { now -> trip.startOdometerKm?.let { (now - it).coerceAtLeast(0f) } } ?: trip.distanceKm
        // 개발 빌드: 에뮬레이터는 속도가 0이라 경과 시간을 이동시간으로 친다
        val moving = if (BuildConfig.DEBUG) (store.clock.now() - trip.startedAt) / 1000 else speed.movingSeconds
        return km to moving
    }

    private fun say(text: String) {
        if (!store.data.voiceOn) return
        Babble.stage = Babble.stageOf(store.data.stage)
        Babble.lineage = Babble.timbreOf(store.data.stage)
        Babble.enabled = true
        Babble.chirp()
        voice.say(text, delayMillis = 450)
    }

    private companion object {
        const val LIVE_MILLIS = 2_000L
    }
}
