package work.integro.carling

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import android.content.pm.PackageManager
import work.integro.carling.navi.FUSED_LOCATION_PERMISSION
import work.integro.carling.trip.TripService
import work.integro.carling.ui.CarlingApp
import work.integro.carling.vehicle.CAR_SPEED_PERMISSION

class MainActivity : ComponentActivity() {

    private val requestVehicleData = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val app = application as CarlingApplication
        if (granted[CAR_SPEED_PERMISSION] == true) app.speed.start()
        app.lightFirstTripRoad()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as CarlingApplication
        // 한 번 열면 이후로는 화면을 닫아도 주행을 계속 기록 (시동 때는 BootReceiver가 시작)
        TripService.start(this)

        // 차량 위치와 차속 권한은 앱 시작 때가 아니라, 카링의 위치 동의 화면에서 '허용'을 고른 뒤에만 묻는다.
        val requestVehicleDataIfNeeded = {
            val missing = listOf(FUSED_LOCATION_PERMISSION, CAR_SPEED_PERMISSION)
                .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isEmpty()) app.lightFirstTripRoad() else requestVehicleData.launch(missing.toTypedArray())
        }
        if (app.store.data.locationConsent == true) requestVehicleDataIfNeeded()

        setContent {
            val vehicle by app.vehicle.state.collectAsState()
            CarlingApp(app.store, vehicle, onLocationAllowed = {
                requestVehicleDataIfNeeded()
            })
        }
    }
}
