package work.integro.carling.trip

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import work.integro.carling.data.CarlingStore
import work.integro.carling.data.GeoDot
import work.integro.carling.data.TripResult
import work.integro.carling.navi.NaviPlaces
import work.integro.carling.vehicle.SpeedMonitor
import work.integro.carling.vehicle.VehicleSignals
import work.integro.carling.game.WeatherRules
import work.integro.carling.voice.Babble
import work.integro.carling.voice.Voice
import work.integro.carling.weather.WeatherClient

private const val TAG = "TripTracker"

/** 주행 위젯에 날씨 인사를 보여주는 시간. 운전에 방해되지 않게 잠깐만 띄운다. */
private const val GREETING_SHOWN_MS = 12_000L

/** 주행 중 위치를 기록하는 간격. */
private const val TRACK_SAMPLE_MS = 10_000L

/**
 * 기어 P→주행→P 를 '함께한 주행 한 번'으로 묶어 기록한다.
 * 공식 시동 이벤트 API가 없어 기어 전환을 주행 시작/종료로 쓴다.
 * 진행 중인 주행은 저장해 두므로, 주행 도중 프로세스가 정리됐다가 다시 떠도 이어서 기록한다.
 * 충전 세션도 여기서 감지해 포만감을 채운다.
 */
class TripTracker(
    private val scope: CoroutineScope,
    private val vehicle: VehicleSignals,
    private val speed: SpeedMonitor,
    private val places: NaviPlaces,
    private val store: CarlingStore,
    private val voice: Voice,
    private val weather: WeatherClient,
) {
    /** 이번 주행의 실제 경로. 메모리에만 두었다가 주행이 끝나면 저장한다. 앱이 도중에 꺼지면 그 트립은 도착지 불빛으로만 남는다. */
    private val track = mutableListOf<GeoDot>()
    private var trackJob: Job? = null

    fun start() {
        scope.launch {
            vehicle.state.map { it.connected to it.isDriving }.distinctUntilChanged().collect { (connected, driving) ->
                if (!connected) return@collect
                if (driving) onDriveStart() else onDriveEnd()
            }
        }
        scope.launch {
            vehicle.state.map { it.isCharging }.distinctUntilChanged().collect { charging ->
                if (charging && store.data.hatched) {
                    store.onCharging()
                    store.data.personality?.let { say(it.mealLine) }
                }
            }
        }
    }

    private suspend fun onDriveStart() {
        speed.start()
        if (store.data.activeTrip != null) {
            // 주행 도중 앱이 다시 떴다 — 저장된 시작 시각으로 이어간다. 급조작 수는 이어 셀 수 없다.
            Log.i(TAG, "진행 중 주행 복구")
            return
        }
        speed.beginTrip()
        store.beginTrip(store.clock.now(), vehicle.odometerKm())
        val greet = store.takeDailyGreeting()
        if (!greet) store.data.personality?.let { say(it.drivingLine) }
        // 위치 요청은 한 번에 하나만 처리되므로 출발지와 날씨가 같은 좌표를 함께 쓴다
        track.clear()
        trackJob = scope.launch {
            if (store.usesLocation) places.beginTracking()
            val start = if (store.usesLocation) places.currentCoordinates() else null
            start?.let { (lat, lng) ->
                store.setTripStart(lat, lng)
                track += GeoDot(lat, lng)
            }
            if (greet) launch { greetWithWeather(start) }
            if (!store.usesLocation) return@launch
            // SDK에 지나온 경로를 주는 기능이 없어 현재 위치를 모아 실제 경로를 만든다
            while (true) {
                delay(TRACK_SAMPLE_MS)
                // 주행 중에 위치 사용을 끄면 그때부터 기록하지 않는다
                if (!store.usesLocation) {
                    places.endTracking()
                    return@launch
                }
                places.currentCoordinates()?.let { (lat, lng) -> track += GeoDot(lat, lng) }
            }
        }
        Log.i(TAG, "주행 시작")
    }

    /** 날짜가 바뀐 뒤 첫 운전의 인사. 오전에는 오늘 예보, 오후에는 지금 날씨를 말한다. */
    private suspend fun greetWithWeather(start: Pair<Double, Double>?) {
        val coordinates = start.takeIf { store.usesWeather }
        val report = coordinates?.let { (lat, lng) -> weather.fetch(lat, lng) }
        if (!vehicle.state.value.isDriving) return
        val hour = store.devGreetingHour ?: store.clock.hourOf(store.clock.now())
        val line = WeatherRules.greeting(hour, report)
        Log.i(TAG, "날씨 인사: hour=$hour report=$report line=$line")
        store.driveGreeting = line
        say(line)
        delay(GREETING_SHOWN_MS)
        if (store.driveGreeting == line) store.driveGreeting = null
    }

    private suspend fun onDriveEnd() {
        store.driveGreeting = null
        val trip = store.data.activeTrip ?: return
        val end = store.clock.now()
        val distance = vehicle.odometerKm()?.let { now -> trip.startOdometerKm?.let { (now - it).coerceAtLeast(0f) } }
        trackJob?.cancel()
        val place = if (store.usesLocation) places.currentPlace() else null
        places.endTracking()
        val path = track.toList() + listOfNotNull(place?.lat?.let { lat -> place.lng?.let { lng -> GeoDot(lat, lng) } })
        track.clear()
        store.clearActiveTrip()
        store.recordTrip(
            TripResult(
                startMillis = trip.startMillis,
                endMillis = end,
                distanceKm = distance,
                harshEvents = speed.harshEvents,
                placeCell = place?.cell,
                placeName = place?.name,
                region = place?.region,
                lat = place?.lat,
                lng = place?.lng,
                startLat = trip.startLat,
                startLng = trip.startLng,
                track = path,
            ),
        )
        speed.endTrip()
        val card = store.data.pendingCard?.let { number -> store.data.tripCards.firstOrNull { it.number == number } }
        val personality = store.data.personality
        if (card != null && personality != null) {
            say("${personality.arrivalLine} ${card.minutes}분 같이 달렸어요.")
        }
        Log.i(TAG, "주행 종료: ${(end - trip.startMillis) / 1000}s, place=${place != null}")
    }

    private fun say(text: String) {
        if (!store.data.voiceOn) return
        // 주행 중에도 카링이 말하는 느낌이 나도록 "뿅" 하고 나서 또렷한 음성으로 말한다
        Babble.stage = store.data.reachedStage
        Babble.lineage = store.data.lineage
        Babble.enabled = true
        Babble.chirp()
        voice.say(text, delayMillis = 450)
    }
}
