package work.integro.carling.navi

import android.content.Context
import android.util.Log
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import kotlinx.coroutines.CompletableDeferred
import work.integro.carling.game.Region
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import kotlin.math.floor

private const val TAG = "NaviPlaces"

/** 주행이 끝난 곳. 정밀 좌표는 밖으로 내보내지 않고 격자 해시만 남긴다. */
data class Place(val cell: String, val name: String?, val region: Region? = null, val lat: Double? = null, val lng: Double? = null)

/**
 * 주행의 위치와 장소. 좌표는 FusedLocation SDK([VehicleLocation])로 받고,
 * 장소 이름은 NaviHelper SDK의 주소·목적지 도착으로 받는다. 좌표를 받지 못하면 NaviHelper 위치로 대신한다.
 */
class NaviPlaces(context: Context) {

    private val navi = NaviHelper(context.applicationContext)
    private val location = VehicleLocation(context)
    private var ready = false
    private var pendingLocation: CompletableDeferred<CurrentLocationInfo?>? = null

    /** 최근 목적지 도착 이름 — 다음 주행 종료 때 장소 이름으로 쓴다. */
    @Volatile
    private var lastArrivedName: String? = null

    private val listener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
            pendingLocation?.complete(info)
        }

        override fun onDestinationArrived(info: DestinationArrivedInfo) {
            lastArrivedName = info.name.takeIf { it.isNotBlank() }
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "NaviHelper 오류: $errorCode")
            pendingLocation?.complete(null)
        }
    }

    fun start() {
        location.start()
        runCatching {
            navi.initialize(
                {
                    ready = true
                    navi.addListener(listener)
                },
                { e -> Log.w(TAG, "NaviHelper 초기화 실패", e) },
            )
        }.onFailure { Log.w(TAG, "NaviHelper 사용 불가", it) }
    }

    /** 지금 있는 곳. 내비가 없거나 응답이 없으면 null. */
    suspend fun currentPlace(): Place? {
        val coordinates = location.current()
        val info = locationInfo()
        val (lat, lng) = coordinates ?: info?.let { it.latitude to it.longitude } ?: return null
        val name = lastArrivedName ?: info?.address?.takeIf { it.isNotBlank() }
        lastArrivedName = null
        return Place(cellOf(lat, lng), name, Region.of(lat, lng), lat, lng)
    }

    /** 지금 좌표(위도, 경도). */
    suspend fun currentCoordinates(): Pair<Double, Double>? =
        location.current() ?: locationInfo()?.let { it.latitude to it.longitude }

    /** 주행 중 위치 갱신을 받기 시작한다. */
    fun beginTracking() = location.beginTracking()

    fun endTracking() = location.endTracking()

    private suspend fun locationInfo(): CurrentLocationInfo? {
        if (!ready) return null
        val deferred = CompletableDeferred<CurrentLocationInfo?>()
        pendingLocation = deferred
        runCatching { navi.getCurrentLocationInfo() }.onFailure { deferred.complete(null) }
        val info = withTimeoutOrNull(3_000) { deferred.await() }
        pendingLocation = null
        return info?.takeUnless { it.latitude == 0.0 && it.longitude == 0.0 }
    }

    fun stop() {
        location.stop()
        runCatching {
            navi.removeListener(listener)
            navi.release()
        }
        ready = false
    }

    companion object {
        /** 약 200m 격자 (위도 0.002° ≈ 220m). 격자 번호를 해시해서 원래 좌표를 되돌릴 수 없게 한다. */
        private const val CELL_DEGREES = 0.002

        fun cellOf(lat: Double, lng: Double): String {
            val key = "${floor(lat / CELL_DEGREES).toLong()}:${floor(lng / CELL_DEGREES).toLong()}"
            val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            return digest.take(8).joinToString("") { "%02x".format(it) }
        }
    }
}
