package work.integro.carling

import android.content.pm.PackageManager
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
        // 한 번 열면 이후로는 화면을 닫아도 운행을 계속 기록 (시동 때는 BootReceiver가 시작)
        TripService.start(this)

        // 유효 운행의 '실제 이동시간'을 재려면 차속이 필요하다. 거부하면 카드가 만들어지지 않는다.
        if (checkSelfPermission(CAR_SPEED_PERMISSION) != PackageManager.PERMISSION_GRANTED) requestSpeed.launch(CAR_SPEED_PERMISSION)

        setContent {
            val vehicle by app.vehicle.state.collectAsState()
            CarlingApp(app.store, vehicle)
        }
    }
}
