package work.integro.carling.vehicle

import android.content.Context
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.EvBatteryLevelListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.EvChargePortOpenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "VehicleSignals"

/** 카링이 반응하는 차량 상태 한 묶음. */
data class VehicleState(
    val connected: Boolean = false,
    val gear: VehicleGear = VehicleGear.GEAR_UNKNOWN,
    val chargePortOpen: Boolean = false,
    val chargeSwitchOn: Boolean = false,
    val stateOfCharge: Int? = null,
    val socRising: Boolean = false,
    val batteryLevel: Float? = null,
) {
    /** P단(또는 알 수 없음)이 아니면 주행 중으로 보고 터치를 막는다. */
    val isDriving: Boolean
        get() = gear != VehicleGear.GEAR_PARK && gear != VehicleGear.GEAR_UNKNOWN

    /** 충전구가 열려 있고, 충전 스위치가 켜졌거나 SOC가 오르는 중. */
    val isCharging: Boolean
        get() = chargePortOpen && (chargeSwitchOn || socRising)
}

/**
 * Pleos Vehicle SDK 신호를 StateFlow 하나로 모아 앱 전체에 넘긴다.
 * SDK 호출은 IPC라서 결과가 콜백으로 비동기 도착한다.
 */
class VehicleSignals(context: Context, private val scope: CoroutineScope) {

    private val vehicle = Vehicle(context.applicationContext)
    private val _state = MutableStateFlow(VehicleState())
    val state: StateFlow<VehicleState> = _state.asStateFlow()

    private var started = false
    private var chargeSwitchPoll: Job? = null

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            _state.update { it.copy(connected = true, gear = gear ?: VehicleGear.GEAR_UNKNOWN) }
        }

        override fun onFailed(e: Exception) = logFailure("gear", e)
    }

    private val chargePortListener = object : EvChargePortOpenListener {
        override fun onEvChargePortOpenUpdated(open: Boolean?) = onChargePort(open == true)

        override fun onFailed(e: Exception) = logFailure("chargePort", e)
    }

    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(soc: Int?) {
            _state.update {
                val rising = soc != null && it.stateOfCharge != null && soc > it.stateOfCharge
                it.copy(stateOfCharge = soc, socRising = it.chargePortOpen && (rising || it.socRising))
            }
        }

        override fun onFailed(e: Exception) = logFailure("soc", e)
    }

    private val batteryListener = object : EvBatteryLevelListener {
        override fun onEvBatteryLevelUpdated(level: Float?) {
            _state.update { it.copy(batteryLevel = level) }
        }

        override fun onFailed(e: Exception) = logFailure("battery", e)
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

        val ev = vehicle.getEvBattery()
        ev.isEvChargePortOpen({ open -> onChargePort(open == true) }, { e -> logFailure("chargePort get", e) })
        ev.registerEvChargePortOpen(chargePortListener)
        ev.registerEvBatteryStateOfCharge(socListener)
        ev.registerEvBatteryLevel(batteryListener)
    }

    fun stop() {
        if (!started) return
        started = false
        chargeSwitchPoll?.cancel()
        runCatching {
            vehicle.getGear().unregisterGearSelection(gearListener)
            vehicle.getEvBattery().unregisterEvChargePortOpen(chargePortListener)
            vehicle.getEvBattery().unregisterEvBatteryStateOfCharge(socListener)
            vehicle.getEvBattery().unregisterEvBatteryLevel(batteryListener)
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

    /** 충전 스위치는 리스너가 없어서, 충전구가 열려 있는 동안만 가볍게 조회한다. */
    private fun onChargePort(open: Boolean) {
        _state.update {
            if (open) it.copy(chargePortOpen = true)
            else it.copy(chargePortOpen = false, chargeSwitchOn = false, socRising = false)
        }
        chargeSwitchPoll?.cancel()
        if (!open) return
        chargeSwitchPoll = scope.launch {
            while (isActive) {
                vehicle.getEvBattery().getEvChargeSwitchState(
                    { on -> _state.update { it.copy(chargeSwitchOn = on == true) } },
                    { e -> logFailure("chargeSwitch", e) },
                )
                delay(CHARGE_SWITCH_POLL_MS)
            }
        }
    }

    private fun logFailure(what: String, e: Throwable) {
        Log.w(TAG, "$what failed: ${e.message}", e)
    }

    private companion object {
        const val CHARGE_SWITCH_POLL_MS = 5_000L
    }
}
