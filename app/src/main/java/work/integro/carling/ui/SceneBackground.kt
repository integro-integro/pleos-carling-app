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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import work.integro.carling.game.DayPart
import work.integro.carling.game.GrowthRules
import work.integro.carling.game.dayPartAt
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.style.TextAlign
import java.time.LocalTime
import kotlin.random.Random

/** 지금 시간대와 넘어온 정도. 배경 고르기 화면의 미리보기도 같은 값을 쓴다. */
val LocalDayPart = compositionLocalOf { DayPart.DAY to 1f }

/** 기기 시계로 시간대를 구해 1분마다 갱신한다. 게임 날짜를 건너뛰어도 실제 시각을 따른다. */
@Composable
fun rememberRealDayPart(): Pair<DayPart, Float> {
    var part by remember { mutableStateOf(currentDayPart()) }
    LaunchedEffect(Unit) {
        while (true) {
            part = currentDayPart()
            delay(60_000)
        }
    }
    return part
}

private fun currentDayPart(): Pair<DayPart, Float> = LocalTime.now().let { dayPartAt(it.hour, it.minute) }

/** 알이 깨어나기 전의 별이 흩어진 밤하늘. 시간과 고른 배경에 상관없이 같다. */
@Composable
fun NightSky() {
    val stars = remember {
        val rnd = Random(7)
        List(60) { Triple(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()) }
    }
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(NightDeep, Night, Color(0xFF161C4E))))
        stars.forEach { (x, y, s) ->
            drawCircle(Color.White.copy(alpha = 0.15f + s * 0.45f), 1f + s * 2.2f, Offset(x * size.width, y * size.height))
        }
    }
}

/** 장면별 아침·낮·노을·밤 일러스트. 같은 구도로 그려져 있어 겹쳐서 시간대를 전환한다. */
@DrawableRes
private fun Scene.art(part: DayPart): Int = when (this) {
    Scene.LAB -> when (part) { DayPart.MORNING -> R.drawable.scene_lab_morning; DayPart.DAY -> R.drawable.scene_lab_day; DayPart.DUSK -> R.drawable.scene_lab_dusk; DayPart.NIGHT -> R.drawable.scene_lab_night }
    Scene.ATTIC -> when (part) { DayPart.MORNING -> R.drawable.scene_attic_morning; DayPart.DAY -> R.drawable.scene_attic_day; DayPart.DUSK -> R.drawable.scene_attic_dusk; DayPart.NIGHT -> R.drawable.scene_attic_night }
    Scene.CITY -> when (part) { DayPart.MORNING -> R.drawable.scene_city_morning; DayPart.DAY -> R.drawable.scene_city_day; DayPart.DUSK -> R.drawable.scene_city_dusk; DayPart.NIGHT -> R.drawable.scene_city_night }
    Scene.COAST -> when (part) { DayPart.MORNING -> R.drawable.scene_coast_morning; DayPart.DAY -> R.drawable.scene_coast_day; DayPart.DUSK -> R.drawable.scene_coast_dusk; DayPart.NIGHT -> R.drawable.scene_coast_night }
    Scene.FOREST -> when (part) { DayPart.MORNING -> R.drawable.scene_forest_morning; DayPart.DAY -> R.drawable.scene_forest_day; DayPart.DUSK -> R.drawable.scene_forest_dusk; DayPart.NIGHT -> R.drawable.scene_forest_night }
    Scene.REST_STOP -> when (part) { DayPart.MORNING -> R.drawable.scene_reststop_morning; DayPart.DAY -> R.drawable.scene_reststop_day; DayPart.DUSK -> R.drawable.scene_reststop_dusk; DayPart.NIGHT -> R.drawable.scene_reststop_night }
    Scene.CLEAR_SKY -> when (part) { DayPart.MORNING -> R.drawable.scene_sky_morning; DayPart.DAY -> R.drawable.scene_sky_day; DayPart.DUSK -> R.drawable.scene_sky_dusk; DayPart.NIGHT -> R.drawable.scene_sky_night }
    Scene.OBSERVATORY -> when (part) { DayPart.MORNING -> R.drawable.scene_observatory_morning; DayPart.DAY -> R.drawable.scene_observatory_day; DayPart.DUSK -> R.drawable.scene_observatory_dusk; DayPart.NIGHT -> R.drawable.scene_observatory_night }
}

@Composable
fun SceneBackground(
    scene: Scene,
    dayPart: Pair<DayPart, Float>,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.BottomCenter,
) {
    val (part, blend) = dayPart
    Box(modifier.fillMaxSize().background(Night)) {
        // 다 보이는 쪽 한 장만 그려 메모리를 아낀다. 두 장을 함께 쓰는 건 시간대가 바뀌는 30분뿐이다.
        if (blend < 1f) SceneImage(scene.art(part.previous), 1f, alignment)
        SceneImage(scene.art(part), blend, alignment)
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

/** 배경 고르기 — 얻은 배경만 고를 수 있고, 아직 못 얻은 배경은 언제 열리는지 보여준다. 시간대는 시계를 따른다. */
@Composable
fun ScenePickerDialog(selected: Scene, owned: List<Scene>, onSelect: (Scene) -> Unit, onClose: () -> Unit) {
    val part = LocalDayPart.current
    Scrim {
        Text("배경 고르기", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "지금은 ${part.first.label}이에요. 아침·낮·노을·밤은 차량 시계에 맞춰 저절로 바뀌어요. 배경은 자랄 때마다 하나씩 얻어요 (${owned.size}/${Scene.entries.size}).",
            fontSize = 18.sp,
            color = Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Scene.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                row.forEach { scene ->
                    SceneTile(scene, scene == selected, scene in owned, part, onClick = { onSelect(scene) }, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Button(onClick = onClose, modifier = Modifier.height(60.dp).width(200.dp)) { Text("닫기", fontSize = 24.sp) }
    }
}

/** 이 배경이 열리는 때. */
private fun unlockLabel(scene: Scene): String = when {
    scene.unlockStage < 0 -> "처음부터"
    scene.unlockStage == 0 -> "알이 깨어나면"
    else -> GrowthRules.STAGES[scene.unlockStage].let { "${it.label} (${it.drivingDays}운전일)" }
}

@Composable
private fun SceneTile(scene: Scene, selected: Boolean, owned: Boolean, part: Pair<DayPart, Float>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Surface(onClick = onClick, enabled = owned, shape = shape, color = Panel, modifier = modifier) {
        Column(Modifier.border(if (selected) 4.dp else 1.dp, if (selected) Gold else Accent.copy(alpha = 0.35f), shape)) {
            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)), contentAlignment = Alignment.Center) {
                SceneBackground(scene, part, alignment = Alignment.Center)
                if (!owned) {
                    Box(Modifier.fillMaxSize().background(Color(0xB3060A24)))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔒", fontSize = 30.sp)
                        Text(unlockLabel(scene), fontSize = 17.sp, color = Ink)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (selected) Text("✓ ", fontSize = 21.sp, color = Gold, fontWeight = FontWeight.Bold)
                Text(scene.label, fontSize = 21.sp, color = if (owned) Ink else Muted, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** 배경 위에 바로 놓이는 글자의 받침. 밝은 낮 배경에서도 읽히게 한다. */
fun Modifier.onScene(): Modifier =
    background(Color(0xA6060A24), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 6.dp)
