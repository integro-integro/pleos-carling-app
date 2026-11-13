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

    /** 지금 에너지 상태: EV 배터리(Wh·%), 연료(%). 받지 못한 값은 null. */
    suspend fun energy(): EnergyReading? {
        if (!started) return null
        val wh = read { ok, fail -> vehicle.getEvBattery().getEvBatteryLevel(ok, fail) }
        val soc = read<Int> { ok, fail -> vehicle.getEvBattery().getEvBatteryStateOfCharge(ok, fail) }
        val fuel = read<Int> { ok, fail -> vehicle.getFuel().getFuelPercent(ok, fail) }
        if (wh == null && soc == null && fuel == null) return null
        return EnergyReading(wh, soc, fuel)
    }

    /** 차량이 계산한 이번 주행 효율: EV면 전비, 아니면 연비. 지원하지 않으면 null. */
    suspend fun efficiency(ev: Boolean): Float? {
        if (!started) return null
        return if (ev) read { ok, fail -> vehicle.getEvBattery().getCurrentEvEfficiency(ok, fail) }
        else read { ok, fail -> vehicle.getFuel().getCurrentFuelEconomy(ok, fail) }
    }

    /**
     * 지금 차량 상태(리포트 ⑨). 주차 중 리포트를 열 때 한 번 읽는다. 지원하지 않는 값은 null.
     * 타이어 공기압(psi, 4바퀴), 엔진오일·브레이크액, 워셔액 부족, 남은 주행 가능 거리, 켜진 경고등.
     */
    suspend fun health(): VehicleHealth? {
        if (!started) return null
        val tire = vehicle.getTire()
        val wheels = listOf(
            ai.pleos.playground.vehicle.constant.area.WheelValue.LEFT_FRONT to "앞 왼쪽",
            ai.pleos.playground.vehicle.constant.area.WheelValue.RIGHT_FRONT to "앞 오른쪽",
            ai.pleos.playground.vehicle.constant.area.WheelValue.LEFT_REAR to "뒤 왼쪽",
            ai.pleos.playground.vehicle.constant.area.WheelValue.RIGHT_REAR to "뒤 오른쪽",
        )
        val pressures = wheels.map { (w, label) ->
            val area = ai.pleos.playground.vehicle.api.model.area.WheelArea(w)
            TireReading(label, read { ok, fail -> tire.getTirePressure(area, ok, fail) }, read { ok, fail -> tire.getLowTirePressure(area, ok, fail) })
        }
        val safety = vehicle.getSafety()
        val oil = read<ai.pleos.playground.vehicle.constant.control.OilLevelState> { ok, fail -> safety.getEngineOilLevelState(ok, fail) }
        val brake = read<ai.pleos.playground.vehicle.constant.control.OilLevelState> { ok, fail -> safety.getBrakeFluidLevelState(ok, fail) }
        val washer = read<Boolean> { ok, fail -> safety.hasLowWasherFluidWarning(ok, fail) }
        val range = read<Float> { ok, fail -> vehicle.getOdometer().getRangeRemainingDistance(ok, fail) }
        val tells = read<ai.pleos.playground.vehicle.api.model.telltale.Telltales> { ok, fail -> safety.getTelltalesInfo(ok, fail) }
        val warnings = tells?.telltales.orEmpty()
            .filter { it.state != ai.pleos.playground.vehicle.constant.telltale.TelltaleState.OFF && it.type.name in WARNING_TELLTALES }
            .map { WARNING_TELLTALES.getValue(it.type.name) }
            .distinct()
        val energy = energy()
        return VehicleHealth(
            tires = pressures,
            engineOil = oil?.name,
            brakeFluid = brake?.name,
            lowWasher = washer,
            rangeKm = range?.takeIf { it > 0f },
            warnings = warnings,
            telltalesKnown = tells != null,
            energyPercent = energy?.percent,
            ev = energy?.isEv,
            odometerKm = odometerKm()?.takeIf { it > 0f },
        )
    }

    private suspend fun <T> read(call: (ok: (T?) -> Unit, fail: (Exception) -> Unit) -> Unit): T? =
        withTimeoutOrNull(1_500) {
            suspendCancellableCoroutine { cont ->
                runCatching {
                    call({ v -> if (cont.isActive) cont.resume(v) }, { e -> logFailure("energy", e); if (cont.isActive) cont.resume(null) })
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }

    private fun logFailure(what: String, e: Throwable) {
        Log.w(TAG, "$what failed: ${e.message}", e)
    }
}

/** 에너지 상태 한 번. [batteryWh]·[socPercent]는 전기차, [fuelPercent]는 내연기관차. */
data class EnergyReading(val batteryWh: Float?, val socPercent: Int?, val fuelPercent: Int?) {
    val isEv: Boolean get() = batteryWh != null || socPercent != null
    /** 비교에 쓰는 남은 양(%). */
    val percent: Int? get() = socPercent ?: fuelPercent
}

/** 타이어 한 바퀴: 지금 공기압과 '부족' 기준(psi). */
data class TireReading(val label: String, val psi: Float?, val lowPsi: Float?) {
    val low: Boolean? get() = if (psi == null || lowPsi == null || lowPsi <= 0f) null else psi < lowPsi
}

/** 차량 상태 한 번 읽은 값. 문자열 상태는 OilLevelState 이름(NORMAL·LOW …). */
data class VehicleHealth(
    val tires: List<TireReading>,
    val engineOil: String?,
    val brakeFluid: String?,
    val lowWasher: Boolean?,
    val rangeKm: Float?,
    /** 켜진 주의 경고등 이름(한글). */
    val warnings: List<String>,
    val telltalesKnown: Boolean,
    val energyPercent: Int?,
    val ev: Boolean?,
    val odometerKm: Float?,
) {
    /** 주의가 필요한 항목 수. */
    val issues: Int
        get() = tires.count { it.low == true } +
            listOf(engineOil, brakeFluid).count { it == "LOW" || it == "CRITICALLY_LOW" } +
            (if (lowWasher == true) 1 else 0) + warnings.size
}

/** 리포트에 보여 줄 주의 경고등(정보성 표시등은 뺀다). */
private val WARNING_TELLTALES = mapOf(
    "MASTER_WARNING" to "종합 경고",
    "TPMS" to "타이어 공기압",
    "ENGINE" to "엔진 점검",
    "ENGINE_OIL_PRESSURE" to "엔진오일 압력",
    "ENGINE_TEMPERATURE" to "냉각수 온도",
    "ABS" to "ABS",
    "ESC" to "차체 자세 제어",
    "AIRBAG" to "에어백",
    "MDPS" to "조향 보조",
    "LOW_VOLT_BATTERY" to "보조 배터리",
    "EV_SYSTEM" to "전기차 시스템",
    "LOW_EV_BATTERY" to "구동 배터리 부족",
    "POWER_DOWN" to "출력 제한",
    "RBS" to "회생 제동",
    "DOOR_OPEN" to "문 열림",
)
