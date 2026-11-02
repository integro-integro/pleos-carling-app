package work.integro.carling.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.data.CarlingData
import work.integro.carling.data.CarlingStore
import work.integro.carling.data.Personality
import work.integro.carling.vehicle.VehicleState

@Composable
fun CarlingApp(store: CarlingStore, vehicle: VehicleState, onLocationAllowed: () -> Unit = {}) {
    CarlingTheme {
        // 주행 중에는 돌보기 화면을 아예 그리지 않는다 (안전 원칙: 주행 중 터치 없음)
        if (vehicle.isDriving) {
            DrivingMode(store.data)
        } else {
            ParkedMode(store, vehicle, onLocationAllowed)
        }
    }
}

@Composable
private fun ParkedMode(store: CarlingStore, vehicle: VehicleState, onLocationAllowed: () -> Unit) {
    val data = store.data
    val personality = data.personality
    val name = data.name
    var questionsDone by rememberSaveable { mutableStateOf(data.name != null) }
    when {
        !data.hatched -> EggScreen(onHatch = store::hatch)
        personality == null -> PersonalityScreen(onChoose = store::choosePersonality)
        name == null && !questionsDone -> QuestionsScreen(
            personality = personality,
            routine = data.routine,
            usualLight = data.usualLight,
            onRoutine = store::answerRoutine,
            onLight = store::answerUsualLight,
            onNext = { questionsDone = true },
        )
        name == null -> NameScreen(personality, onName = store::giveName)
        !data.guideSeen -> GuideScreen(name, personality, data.usualLight, onDone = store::finishGuide)
        data.locationConsent == null -> LocationConsentScreen(personality, onAnswer = { allowed ->
            store.setLocationConsent(allowed)
            if (allowed) onLocationAllowed()
        })
        else -> HomeScreen(store, data, personality, name, vehicle, onLocationAllowed)
    }
}

/** 주행 중: 구석의 작은 위젯과 한 줄만. 입력은 받지 않는다. */
@Composable
private fun DrivingMode(data: CarlingData) {
    val personality = data.personality ?: Personality.LUMI
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(32.dp)
                .background(Color(0x40FFFFFF), RoundedCornerShape(48.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!data.hatched) {
                EggDrawing(96.dp)
            } else {
                CarlingDrawing(
                    size = 120.dp,
                    stage = data.stage,
                    glow = data.glowColor(),
                    path = data.evolution?.path,
                    hat = data.wearingHat,
                    mood = personality,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                if (!data.hatched) "도착하면 깨워주세요" else personality.drivingLine,
                fontSize = 28.sp,
                color = Ink,
            )
        }
    }
}
