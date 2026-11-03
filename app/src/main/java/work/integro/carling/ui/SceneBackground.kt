package work.integro.carling.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import work.integro.carling.R
import work.integro.carling.data.Scene
import work.integro.carling.game.nightnessAt
import java.time.LocalTime

/** 지금 밤의 정도(0 = 낮, 1 = 밤). 배경 선택 화면의 미리보기도 같은 값을 쓴다. */
val LocalNightness = compositionLocalOf { 1f }

/** 기기 시계로 밤의 정도를 구해 1분마다 갱신한다. 게임 날짜를 건너뛰어도 실제 시각을 따른다. */
@Composable
fun rememberRealNightness(): Float {
    var nightness by remember { mutableFloatStateOf(currentNightness()) }
    LaunchedEffect(Unit) {
        while (true) {
            nightness = currentNightness()
            delay(60_000)
        }
    }
    return nightness
}

private fun currentNightness(): Float = LocalTime.now().let { nightnessAt(it.hour, it.minute) }

/** 장면별 낮·밤 3D 일러스트. 같은 구도로 그려져 있어 겹쳐서 낮밤을 전환한다. */
@DrawableRes
private fun Scene.dayArt(): Int = when (this) {
    Scene.CITY -> R.drawable.scene_city_day
    Scene.COAST -> R.drawable.scene_coast_day
    Scene.CLEAR_SKY -> R.drawable.scene_sky_day
    Scene.FOREST -> R.drawable.scene_forest_day
}

@DrawableRes
private fun Scene.nightArt(): Int = when (this) {
    Scene.CITY -> R.drawable.scene_city_night
    Scene.COAST -> R.drawable.scene_coast_night
    Scene.CLEAR_SKY -> R.drawable.scene_sky_night
    Scene.FOREST -> R.drawable.scene_forest_night
}

@Composable
fun SceneBackground(
    scene: Scene,
    nightness: Float,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.BottomCenter,
) {
    Box(modifier.fillMaxSize().background(Night)) {
        // 다 보이는 쪽 한 장만 그려 메모리를 아낀다. 두 장을 함께 쓰는 건 일출·일몰 때뿐이다.
        if (nightness < 1f) SceneImage(scene.dayArt(), 1f, alignment)
        if (nightness > 0f) SceneImage(scene.nightArt(), nightness, alignment)
        // 해 뜨고 질 때의 노을
        val dusk = 4f * nightness * (1f - nightness)
        if (dusk > 0f) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color(0xFFFF9A62).copy(alpha = 0.45f * dusk), Color(0xFFFF9A62).copy(alpha = 0.15f * dusk)),
                    ),
                )
            }
        }
    }
}

/** 화면에서는 땅이 보이도록 아래를 기준으로 맞춘다. 미리보기는 풍경이 보이도록 가운데를 쓴다. */
@Composable
private fun SceneImage(@DrawableRes res: Int, alpha: Float, alignment: Alignment) {
    Image(
        painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = alignment,
        alpha = alpha,
        modifier = Modifier.fillMaxSize(),
    )
}

/** 배경 고르기 — 고른 배경은 저장되고, 낮밤은 시계를 따른다. */
@Composable
fun ScenePickerDialog(selected: Scene, onSelect: (Scene) -> Unit, onClose: () -> Unit) {
    val night = LocalNightness.current
    Scrim {
        Text("배경 고르기", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "${dayPartLabel(night)} 낮과 밤은 차량 시계에 맞춰 저절로 바뀌어요.",
            fontSize = 18.sp,
            color = Muted,
        )
        Spacer(Modifier.height(18.dp))
        Scene.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                row.forEach { scene ->
                    SceneTile(scene, scene == selected, night, onClick = { onSelect(scene) }, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Button(onClick = onClose, modifier = Modifier.height(60.dp).width(200.dp)) { Text("닫기", fontSize = 24.sp) }
    }
}

private fun dayPartLabel(night: Float) = when {
    night <= 0f -> "지금은 낮이에요."
    night >= 1f -> "지금은 밤이에요."
    else -> "지금은 노을 시간이에요."
}

@Composable
private fun SceneTile(scene: Scene, selected: Boolean, night: Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Surface(onClick = onClick, shape = shape, color = Panel, modifier = modifier) {
        Column(Modifier.border(if (selected) 4.dp else 1.dp, if (selected) Gold else Accent.copy(alpha = 0.35f), shape)) {
            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))) {
                SceneBackground(scene, night, alignment = Alignment.Center)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (selected) Text("✓ ", fontSize = 21.sp, color = Gold, fontWeight = FontWeight.Bold)
                Text(scene.label, fontSize = 21.sp, color = Ink, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** 배경 위에 바로 놓이는 글자의 받침. 밝은 낮 배경에서도 읽히게 한다. */
fun Modifier.onScene(): Modifier =
    background(Color(0xA6060A24), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 6.dp)
