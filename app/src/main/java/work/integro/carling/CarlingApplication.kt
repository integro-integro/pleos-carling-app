package work.integro.carling

import android.app.Application
import kotlinx.coroutines.MainScope
import work.integro.carling.data.CarlingStore
import work.integro.carling.game.GameClock
import work.integro.carling.geo.KoreaAtlas
import work.integro.carling.geo.VehicleLocation
import work.integro.carling.trip.TripTracker
import work.integro.carling.vehicle.SpeedMonitor
import work.integro.carling.vehicle.UxRestrictions
import work.integro.carling.vehicle.VehicleSignals
import work.integro.carling.voice.Voice

/**
 * 차량 신호와 운행 기록을 Activity가 아닌 앱 전체 범위에 둔다.
 * Pleos는 화면에서 사라진 앱을 백그라운드에 유지하므로, 프로세스가 살아 있는 동안은
 * 카링 화면이 가려져 있어도 운행이 기록된다.
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
    lateinit var tracker: TripTracker
        private set
    lateinit var location: VehicleLocation
        private set
    private lateinit var restrictions: UxRestrictions

    /** 지금 동네(행정동 코드). 동의·권한이 없거나 위치를 모르면 null. 좌표는 판정에만 쓰고 버린다. */
    private suspend fun currentDong(): String? {
        if (store.data.locationConsent != true) return null
        if (checkSelfPermission(work.integro.carling.geo.FUSED_LOCATION_PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return null
        location.start()
        val (lat, lng) = location.current() ?: return null
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching { KoreaAtlas.load(this@CarlingApplication).locate(lng.toFloat(), lat.toFloat()) }.getOrNull()
        }
    }

    override fun onCreate() {
        super.onCreate()
        clock = GameClock(this)
        store = CarlingStore(this, clock)
        vehicle = VehicleSignals(this)
        speed = SpeedMonitor(this)
        voice = Voice(this)
        restrictions = UxRestrictions(this, vehicle::setUxRestricted)
        location = VehicleLocation(this)
        if (store.data.locationConsent == true) location.start()

        vehicle.start()
        restrictions.start()
        tracker = TripTracker(scope, vehicle, speed, store, voice, place = ::currentDong).also { it.start() }
        if (BuildConfig.DEBUG) DevCommands.register(this, store)
    }
}
