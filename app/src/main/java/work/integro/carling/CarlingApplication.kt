package work.integro.carling

import android.app.Application
import kotlinx.coroutines.MainScope
import work.integro.carling.data.CarlingStore
import work.integro.carling.game.GameClock
import work.integro.carling.navi.NaviPlaces
import work.integro.carling.trip.TripTracker
import work.integro.carling.vehicle.SpeedMonitor
import work.integro.carling.vehicle.VehicleSignals
import work.integro.carling.voice.Voice
import work.integro.carling.weather.WeatherClient

/**
 * 차량 신호와 주행 기록을 Activity가 아닌 앱 전체 범위에 둔다.
 * Pleos는 화면에서 사라진 앱을 백그라운드에 유지하므로, 프로세스가 살아 있는 동안은
 * 카링 화면이 가려져 있어도 주행이 기록된다.
 */
class CarlingApplication : Application() {

    private val scope = MainScope()

    lateinit var clock: GameClock
        private set
    lateinit var store: CarlingStore
        private set
    lateinit var vehicle: VehicleSignals
        private set
    lateinit var speed: SpeedMonitor
        private set
    lateinit var voice: Voice
        private set

    override fun onCreate() {
        super.onCreate()
        clock = GameClock(this)
        store = CarlingStore(this, clock)
        vehicle = VehicleSignals(this, scope)
        speed = SpeedMonitor(this)
        voice = Voice(this)
        val places = NaviPlaces(this)

        vehicle.start()
        places.start()
        TripTracker(scope, vehicle, speed, places, store, voice, WeatherClient()).start()
    }
}
