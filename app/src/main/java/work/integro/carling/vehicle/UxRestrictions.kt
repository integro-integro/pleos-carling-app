package work.integro.carling.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log

private const val TAG = "UxRestrictions"

/** 플랫폼의 주행 제한 상태를 받아 게임 화면을 보류할지 판단한다. 받을 수 없으면 기어 상태만 쓴다. */
class UxRestrictions(private val context: Context, private val onChanged: (Boolean) -> Unit) {
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    fun start() {
        runCatching {
            val c = Car.createCar(context)
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            m.registerListener { state -> onChanged(state.isRequiresDistractionOptimization) }
            onChanged(m.currentCarUxRestrictions.isRequiresDistractionOptimization)
            car = c
            manager = m
        }.onFailure { Log.w(TAG, "주행 제한 상태를 받을 수 없음", it) }
    }

    fun stop() {
        runCatching {
            manager?.unregisterListener()
            car?.disconnect()
        }
        manager = null
        car = null
    }
}
