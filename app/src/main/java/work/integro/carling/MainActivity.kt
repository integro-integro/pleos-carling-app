package work.integro.carling

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import work.integro.carling.trip.TripService
import work.integro.carling.ui.CarlingApp
import work.integro.carling.vehicle.CAR_SPEED_PERMISSION

class MainActivity : ComponentActivity() {

    private val requestSpeed = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) (application as CarlingApplication).speed.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as CarlingApplication
        // 한 번 열면 이후로는 화면을 닫아도 주행을 계속 기록 (시동 때는 BootReceiver가 시작)
        TripService.start(this)

        // 차속 권한(부드러운 운전 칭찬)은 시스템에서 '위치' 권한으로 묶여 보인다.
        // 그래서 앱 시작 때가 아니라, 카링의 위치 동의 화면에서 '허용'을 고른 뒤에만 묻는다.
        val requestSpeedIfNeeded = {
            if (!app.speed.hasPermission()) requestSpeed.launch(CAR_SPEED_PERMISSION)
        }
        if (app.store.data.locationConsent == true) requestSpeedIfNeeded()

        setContent {
            val vehicle by app.vehicle.state.collectAsState()
            CarlingApp(app.store, vehicle, onLocationAllowed = {
                requestSpeedIfNeeded()
                app.lightFirstTripRoad()
            })
        }
    }
}
