package work.integro.carling.navi

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "VehicleLocation"
private const val LOCATION_TIMEOUT_MS = 3_000L

/** FusedLocation SDK 런타임 권한. */
const val FUSED_LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"

/**
 * FusedLocation SDK로 받는 차량 위치. 주행 중에는 위치 갱신을 구독해 마지막 값을 들고 있고,
 * 그 밖에는 마지막으로 알려진 위치를 한 번 묻는다.
 * 지원하지 않는 환경이거나 권한이 없으면 null을 돌려준다.
 */
class VehicleLocation(context: Context) {

    private val fused = FusedLocation(context.applicationContext)
    private var ready = false
    private var tracking = false

    @Volatile
    private var latest: Pair<Double, Double>? = null

    private val updates = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            location?.coordinates()?.let { latest = it }
        }

        override fun onError(exception: Exception) {
            Log.w(TAG, "위치 갱신 오류: ${exception.message}")
        }
    }

    fun start() {
        runCatching {
            if (fused.isSupported()) {
                fused.initialize()
                ready = true
            } else {
                Log.i(TAG, "FusedLocation 미지원 환경")
            }
        }.onFailure { Log.w(TAG, "FusedLocation 사용 불가", it) }
    }

    fun beginTracking() {
        if (!ready || tracking) return
        runCatching { fused.registerFusedLocationCallback(updates) }
            .onSuccess { tracking = true }
            .onFailure { Log.w(TAG, "위치 구독 실패: ${it.message}") }
    }

    fun endTracking() {
        if (tracking) runCatching { fused.unregisterFusedLocationCallback(updates) }
        tracking = false
        latest = null
    }

    /** 지금 좌표(위도, 경도). */
    suspend fun current(): Pair<Double, Double>? {
        if (!ready) return null
        latest?.let { return it }
        val deferred = CompletableDeferred<Pair<Double, Double>?>()
        val once = object : FusedLocationListener {
            override fun onChanged(location: LocationData?) {
                deferred.complete(location?.coordinates())
            }

            override fun onError(exception: Exception) {
                Log.w(TAG, "위치 조회 오류: ${exception.message}")
                deferred.complete(null)
            }
        }
        runCatching { fused.getLastKnownLocation(once) }.onFailure {
            Log.w(TAG, "위치 조회 실패: ${it.message}")
            deferred.complete(null)
        }
        return withTimeoutOrNull(LOCATION_TIMEOUT_MS) { deferred.await() }
    }

    fun stop() {
        endTracking()
        if (ready) runCatching { fused.release() }
        ready = false
    }

    private fun LocationData.coordinates(): Pair<Double, Double>? =
        (latitude to longitude).takeUnless { latitude == 0.0 && longitude == 0.0 }
}
