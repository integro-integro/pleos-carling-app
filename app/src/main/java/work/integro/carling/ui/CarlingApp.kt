package work.integro.carling.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.data.CarlingData
import work.integro.carling.data.CarlingStore
import work.integro.carling.game.TimeBucket
import work.integro.carling.trip.LiveTrip
import work.integro.carling.vehicle.VehicleState

@Composable
fun CarlingApp(store: CarlingStore, vehicle: VehicleState, live: LiveTrip?, onLocation: (Boolean) -> Unit = store::setLocationConsent) {
    CarlingTheme {
      androidx.compose.runtime.CompositionLocalProvider(LocalCabinTone provides store.data.cabinTone) {
        val bucket = TimeBucket.of(store.clock.hourOf(store.clock.now()))
        when {
            // 주차가 확인되고 주행 제한이 없을 때만 조작 화면을 그린다
            vehicle.canShowWorld -> HomeScreen(store, onLocation)
            vehicle.isDriving -> DriveScreen(store.data, bucket, live)
            else -> HoldScreen(store.data, bucket)
        }
      }
    }
}

/**
 * 주행(D) 화면: 달리는 카링과 숫자 두 개(누적 거리·이동시간)만. 터치 요소가 없고, 배경과 카링은 움직이지 않는다.
 * (주행 중 애니메이션 허용 범위가 확인되기 전까지 정지 이미지로 둔다 — PRD 리스크 대응)
 */
@Composable
private fun DriveScreen(data: CarlingData, bucket: TimeBucket, live: LiveTrip?) {
    // 주차 화면과 같은 차 안 배경. 카링은 달리는 모습으로 대시보드 위를 동동 떠다닌다(터치 없음)
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "float")
    val bob by t.animateFloat(-1f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1_400, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "bob")
    val drift by t.animateFloat(-1f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(4_200, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "drift")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        CabinBackdrop(data.cabin, data.theme ?: bucket, Modifier.fillMaxSize())
        val g = cabinGeometry(maxWidth, maxHeight, data.cabin)
        val art = artFor(data.shownStage, Pose.DRIVE)
        val karW = g.karH * art.aspect
        Box(
            Modifier
                .offset(x = g.anchorX - karW / 2 + g.wander * 0.5f * drift, y = g.anchorY - g.karH * 1.0f)
                .graphicsLayer {
                    translationY = bob * g.karH.toPx() * 0.05f - g.karH.toPx() * 0.06f
                    rotationZ = bob * 1.5f
                },
        ) {
            KaringView(data.shownStage, Pose.DRIVE, data.personality, data.equipped, height = g.karH, carColor = data.carColor)
        }
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
                .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(50))
                .border(1.dp, Line, RoundedCornerShape(50))
                .padding(horizontal = 30.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("D", fontSize = 26.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.background(Mint, CircleShape).padding(horizontal = 14.dp, vertical = 2.dp))
            Spacer(Modifier.width(14.dp))
            Text("함께 달리는 중", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text(" · ${live?.distanceKm?.let { "%.1fkm".format(it) } ?: "—"} · ${live?.minutes ?: 0}분", fontSize = 26.sp, color = Ink)
        }
        // 주행 한마디: 말하는 동안만 카링 위에 말풍선(누를 것 없음)
        live?.talk?.let { line ->
            Text(
                line,
                fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(x = (g.anchorX - 260.dp).coerceAtLeast(16.dp), y = (g.anchorY - g.karH * 1.35f).coerceAtLeast(100.dp))
                    .background(Color.White.copy(alpha = 0.96f), RoundedCornerShape(26.dp)).border(2.dp, Lilac, RoundedCornerShape(26.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }
    }
}

/** 주차를 확인할 수 없을 때: 터치도, 게임 연출도 없다. */
@Composable
private fun HoldScreen(data: CarlingData, bucket: TimeBucket) {
    Box(Modifier.fillMaxSize()) {
        Backdrop(bucket, still = true, modifier = Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            KaringView(data.shownStage, Pose.STAND, data.personality, emptyMap(), height = 300.dp, still = true)
            Spacer(Modifier.height(12.dp))
            Text("차량 상태를 확인하고 있어요.", fontSize = 26.sp, color = Ink)
        }
    }
}
