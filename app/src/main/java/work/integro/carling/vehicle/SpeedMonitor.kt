package work.integro.carling.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import work.integro.carling.game.Rules

private const val TAG = "SpeedMonitor"

/** AAOS 런타임 권한 이름 (android.car.Car.PERMISSION_SPEED). */
const val CAR_SPEED_PERMISSION = "android.car.permission.CAR_SPEED"

/**
 * 차속으로 급가속·급감속 횟수를 센다. 트립 안전 점수의 재료다.
 * 급조작과 실제 이동시간을 함께 잰다. Vehicle SDK에 속도가 없어 AAOS 표준 CarPropertyManager를 쓴다.
 */
class SpeedMonitor(private val context: Context) {

    private var car: Car? = null
    private var manager: CarPropertyManager? = null

    private var lastSpeed: Float? = null
    private var lastNanos: Long = 0
    private var inHarsh = false

    /** 이번 운행의 실제 이동시간(초). 공회전·정차 시간은 빠진다. */
    @Volatile
    private var moving = 0f

    /** 실제 이동시간. 차속을 구독하지 못했으면 null. */
    val movingSeconds: Long?
        get() = if (manager != null) moving.toLong() else null

    /** 지금 속도(km/h). 받지 못했으면 null. */
    val speedKmh: Float? get() = lastSpeed?.times(3.6f)

    /** 마지막 급조작 시각(System.currentTimeMillis). */
    @Volatile
    var lastHarshAt: Long? = null
        private set

    /** 현재 주행에서 감지한 급조작 횟수. 차속을 못 받으면 null. */
    @Volatile
    var harshEvents: Int? = null
        private set

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val speed = value.value as? Float ?: return
            val nanos = value.timestamp
            val prev = lastSpeed
            if (prev != null && nanos > lastNanos) {
                val seconds = (nanos - lastNanos) / 1e9f
                if (seconds >= 0.5f) {
                    val perSecond = (speed - prev) / seconds
                    // 급가속 +3.0 m/s² 이상, 급감속 −3.5 m/s² 이하 (PRD 4.4)
                    val harsh = perSecond >= Rules.HARSH_ACCEL_MPS2 || perSecond <= Rules.HARSH_BRAKE_MPS2
                    // 이어지는 급가속/급감속은 한 번으로 센다
                    if (harsh && !inHarsh) {
                        harshEvents = (harshEvents ?: 0) + 1
                        lastHarshAt = System.currentTimeMillis()
                        Log.i(TAG, "급조작 감지: %.1f m/s² (%.1f → %.1f m/s)".format(perSecond, prev, speed))
                    }
                    inHarsh = harsh
                    if (prev > Rules.MOVING_MPS || speed > Rules.MOVING_MPS) moving += seconds
                    lastSpeed = speed
                    lastNanos = nanos
                }
            } else {
                lastSpeed = speed
                lastNanos = nanos
            }
        }

        override fun onErrorEvent(propertyId: Int, zone: Int) {
            Log.w(TAG, "speed error prop=$propertyId zone=$zone")
        }
    }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(CAR_SPEED_PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (manager != null || !hasPermission()) return
        runCatching {
            val c = Car.createCar(context)
            val m = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            m.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
            car = c
            manager = m
        }.onFailure { Log.w(TAG, "차속 구독 실패", it) }
    }

    /** 주행 시작 시 카운트 초기화. */
    fun beginTrip() {
        lastSpeed = null
        inHarsh = false
        harshEvents = if (manager != null) 0 else null
        moving = 0f
    }

    /** 저장된 이동시간부터 이어 센다(앱이 다시 떴거나, 짧은 정차 뒤 같은 여정으로 다시 출발). [harsh]를 알면 급조작 수도 이어 센다. */
    fun resumeTrip(movingSeconds: Long, harsh: Int? = null) {
        lastSpeed = null
        inHarsh = false
        moving = movingSeconds.toFloat()
        if (harsh != null && manager != null) harshEvents = harsh
    }

    /** 주행 종료 후에는 다음 주행 전까지 카운트를 비워 둔다 (복구된 주행은 판정하지 않도록). */
    fun endTrip() {
        harshEvents = null
        moving = 0f
    }

    fun stop() {
        runCatching {
            manager?.unregisterCallback(callback)
            car?.disconnect()
        }
        manager = null
        car = null
    }
}
