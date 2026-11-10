package work.integro.carling.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.data.CarlingData
import work.integro.carling.data.CarlingStore
import work.integro.carling.vehicle.VehicleState

@Composable
fun CarlingApp(store: CarlingStore, vehicle: VehicleState) {
    CarlingTheme {
        // 주차가 확인되고 주행 제한이 없을 때만 카링월드를 그린다
        if (vehicle.canShowWorld) HomeScreen(store) else HoldScreen(store.data, vehicle.isDriving)
    }
}

/** 운행 중이거나 주차를 확인할 수 없을 때: 터치도, 게임 연출도 없다. */
@Composable
private fun HoldScreen(data: CarlingData, driving: Boolean) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CarlingPicture(carlingArt(data.form, data.lineage, data.level), 220.dp, shadow = false)
        Text(
            if (driving) "운전에 집중해요. 도착하면 보여줄게요." else "차량 상태를 확인하고 있어요.",
            fontSize = 28.sp,
            color = Ink,
        )
    }
}
