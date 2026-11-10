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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.BuildConfig
import work.integro.carling.data.CarlingData
import work.integro.carling.data.CarlingStore
import work.integro.carling.data.Personality
import work.integro.carling.data.TripResult
import work.integro.carling.vehicle.VehicleState

@Composable
fun CarlingApp(store: CarlingStore, vehicle: VehicleState, onLocationAllowed: () -> Unit = {}) {
    // 처음에는 Dr.Pab의 심장 연구소에서 시작한다. 시간대는 차량 시계를 따른다.
    // 차량 상태가 바뀔 때마다 돌봄 퀘스트를 맞춘다
    LaunchedEffect(vehicle, store.devCare, store.data.hatched) {
        val (issues, known) = vehicle.careStatus()
        store.syncCare(issues, known)
    }
    CarlingTheme(store.data.scene, dayPartOverride = store.devDayPart) {
        // 주행 중에는 돌보기 화면을 아예 그리지 않는다 (안전 원칙: 주행 중 터치 없음)
        if (vehicle.isDriving) {
            DrivingMode(store.data, store.driveGreeting)
        } else {
            ParkedMode(store, vehicle, onLocationAllowed)
        }
    }
}

@Composable
private fun ParkedMode(store: CarlingStore, vehicle: VehicleState, onLocationAllowed: () -> Unit) {
    val data = store.data
    val name = data.name
    // 성격은 부화 때 정해진다. 부화 전/옛 저장 호환을 위해 기본값을 둔다.
    val personality = data.personality ?: Personality.LUMI
    when {
        // 1 · 당신에게 도착한 알 — 첫 운전이 알을 깨운다
        !data.firstTripDone && !data.hatched -> EggArrivalScreen(
            onDevFirstDrive = if (BuildConfig.DEBUG) {
                {
                    val now = System.currentTimeMillis()
                    store.recordTrip(TripResult(now - 20 * 60 * 1000, now, 8f, 0, null, "집 근처"))
                }
            } else null,
        )
        // 2 · 트립카드 #001 → 알을 톡 터치해 깨우기
        !data.hatched -> AwakenScreen(
            data,
            xpIfSaved = data.tripCards.firstOrNull()?.let(store::saveXpFor) ?: 0,
            onSave = { data.tripCards.firstOrNull()?.let { store.saveTripCard(it.number, force = true) } },
            onWake = store::hatch,
        )
        // 3 · 이름 지어주기
        name == null -> NameScreen(personality, onName = store::giveName, onBack = store::onboardingBack)
        // 4 · 첫 교감 — 쓰다듬기
        !data.firstPetDone -> FirstPetScreen(
            name, personality,
            onPet = store::pet, onFeed = store::feed, onPlay = store::play,
            onDone = store::completeFirstPet, onBack = store::onboardingBack,
        )
        // 5 · 지도의 첫 불빛, 내일의 약속
        !data.guideSeen -> FirstMapScreen(name, personality, onDone = store::finishGuide, onBack = store::onboardingBack)
        data.locationConsent == null || data.needsLocationReconsent -> LocationConsentScreen(personality, onAnswer = { allowed ->
            store.setLocationConsent(allowed)
            if (allowed) onLocationAllowed()
        }, onBack = if (data.locationConsent == null) store::onboardingBack else null)
        else -> HomeScreen(store, data, personality, name, vehicle, onLocationAllowed)
    }
}

/** 주행 중: 구석의 작은 위젯과 한 줄만. 입력은 받지 않는다. */
@Composable
private fun DrivingMode(data: CarlingData, greeting: String?) {
    val personality = data.personality ?: Personality.LUMI
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(32.dp)
                .background(Color(0xA6060A24), RoundedCornerShape(48.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!data.hatched) {
                EggDrawing(96.dp)
            } else {
                CarlingDrawing(
                    size = 120.dp,
                    stageIndex = data.reachedStage,
                    glow = data.glowColor(),
                    item = data.equippedItem,
                    mood = personality,
                    lineage = data.lineage,
                    vehicle = data.ridingVehicle,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                if (!data.hatched) "(알 속에서 작은 두근거림이 느껴진다…)" else data.careQuests.minByOrNull { it.priority }?.drivingLine ?: greeting ?: personality.drivingLine,
                fontSize = 28.sp,
                color = Ink,
            )
        }
    }
}
