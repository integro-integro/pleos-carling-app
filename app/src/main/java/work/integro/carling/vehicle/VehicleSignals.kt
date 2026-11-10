package work.integro.carling.vehicle

import android.content.Context
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "VehicleSignals"

/** 카링이 쓰는 차량 상태. 기어로 운행 시작·종료를 판단한다. */
data class VehicleState(
    val connected: Boolean = false,
    val gear: VehicleGear = VehicleGear.GEAR_UNKNOWN,
    /** 플랫폼이 주행 제한을 걸었는지. 받지 못했으면 null. */
    val uxRestricted: Boolean? = null,
) {
    /** 운행 기록용: P단(또는 알 수 없음)이 아니면 운행 중이다. */
    val isDriving: Boolean
        get() = gear != VehicleGear.GEAR_PARK && gear != VehicleGear.GEAR_UNKNOWN

    /** 게임 화면을 보여도 되는지. 주차가 확인되지 않거나 주행 제한 중이면 보류한다. */
    val canShowWorld: Boolean
        get() = connected && gear == VehicleGear.GEAR_PARK && uxRestricted != true
}

/**
 * Pleos Vehicle SDK 신호를 StateFlow 하나로 모아 앱 전체에 넘긴다.
 * SDK 호출은 IPC라서 결과가 콜백으로 비동기 도착한다.
 */
class VehicleSignals(context: Context) {

    private val vehicle = Vehicle(context.applicationContext)
    private val _state = MutableStateFlow(VehicleState())
    val state: StateFlow<VehicleState> = _state.asStateFlow()

    private var started = false

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            _state.update { it.copy(connected = true, gear = gear ?: VehicleGear.GEAR_UNKNOWN) }
        }

        override fun onFailed(e: Exception) = logFailure("gear", e)
    }

    fun start() {
        if (started) return
        try {
            vehicle.initialize()
        } catch (e: Exception) {
            // 일반 폰/기본 에뮬레이터처럼 Vehicle Service가 없으면 여기로 온다.
            logFailure("initialize", e)
            return
        }
        started = true

        val gear = vehicle.getGear()
        gear.getCurrentGearSelectionState(
            { current ->
                _state.update { it.copy(connected = true, gear = current ?: VehicleGear.GEAR_UNKNOWN) }
            },
            { e -> logFailure("gear get", e) },
        )
        gear.registerGearSelection(gearListener)
    }

    fun setUxRestricted(restricted: Boolean) = _state.update { it.copy(uxRestricted = restricted) }

    fun stop() {
        if (!started) return
        started = false
        runCatching {
            vehicle.getGear().unregisterGearSelection(gearListener)
            vehicle.release()
        }.onFailure { logFailure("stop", it) }
    }

    /** 누적 주행거리(km). 지원하지 않으면 null. */
    suspend fun odometerKm(): Float? {
        if (!started) return null
        return withTimeoutOrNull(2_000) {
            suspendCancellableCoroutine { cont ->
                vehicle.getOdometer().getCurrentDistanceDriven(
                    { km -> if (cont.isActive) cont.resume(km) },
                    { e ->
                        logFailure("odometer", e)
                        if (cont.isActive) cont.resume(null)
                    },
                )
            }
        }
    }

    private fun logFailure(what: String, e: Throwable) {
        Log.w(TAG, "$what failed: ${e.message}", e)
    }
}
