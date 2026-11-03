package work.integro.carling.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import work.integro.carling.data.Scene
import work.integro.carling.game.nightnessAt
import java.time.LocalTime
import kotlin.random.Random

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

@Composable
fun SceneBackground(scene: Scene, nightness: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) { drawScene(scene, nightness) }
}

private fun DrawScope.drawScene(scene: Scene, night: Float) {
    drawSky(scene, night)
    when (scene) {
        Scene.CITY -> drawCity(night)
        Scene.COAST -> drawCoast(night)
        Scene.CLEAR_SKY -> drawClouds(night)
        Scene.FOREST -> drawForest(night)
    }
}

private fun DrawScope.drawSky(scene: Scene, night: Float) {
    val dayTop = when (scene) {
        Scene.CITY -> Color(0xFF6DAEDB)
        Scene.COAST -> Color(0xFF45A6E5)
        Scene.CLEAR_SKY -> Color(0xFF3F9BE8)
        Scene.FOREST -> Color(0xFF8CC8D8)
    }
    val dayBottom = when (scene) {
        Scene.CITY -> Color(0xFFD5E8F2)
        Scene.COAST -> Color(0xFFCDEEF7)
        Scene.CLEAR_SKY -> Color(0xFFCFEAFB)
        Scene.FOREST -> Color(0xFFE4F2D8)
    }
    drawRect(
        Brush.verticalGradient(
            listOf(lerp(dayTop, NightDeep, night), lerp(dayBottom, Color(0xFF161C4E), night)),
        ),
    )
    // 해 뜨고 질 때의 노을
    val dusk = 4f * night * (1f - night)
    if (dusk > 0f) {
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, Color(0xFFFF9A62).copy(alpha = 0.7f * dusk)),
                startY = size.height * 0.2f,
                endY = size.height * 0.75f,
            ),
        )
    }
    // 별
    if (night > 0f) {
        val rnd = Random(7)
        repeat(60) {
            val x = rnd.nextFloat()
            val y = rnd.nextFloat() * 0.7f
            val s = rnd.nextFloat()
            drawCircle(Color.White.copy(alpha = (0.15f + s * 0.45f) * night), 1f + s * 2.2f, Offset(x * size.width, y * size.height))
        }
    }
    // 해와 달은 낮밤에 따라 서로 교대한다
    val at = Offset(size.width * 0.42f, size.height * 0.16f)
    val r = size.minDimension * 0.055f
    val sunAlpha = (1f - night * 2f).coerceIn(0f, 1f)
    val moonAlpha = (night * 2f - 1f).coerceIn(0f, 1f)
    if (sunAlpha > 0f) {
        val alpha = sunAlpha
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF3B0).copy(alpha = 0.7f * alpha), Color.Transparent), at, r * 3f), r * 3f, at)
        drawCircle(Color(0xFFFFE680).copy(alpha = alpha), r, at)
    }
    if (moonAlpha > 0f) {
        drawCircle(Brush.radialGradient(listOf(Color(0xFFDDE4FF).copy(alpha = 0.3f * moonAlpha), Color.Transparent), at, r * 2.6f), r * 2.6f, at)
        drawCircle(Color(0xFFF2F0DC).copy(alpha = moonAlpha), r * 0.8f, at)
        drawCircle(lerp(Color(0xFF161C4E), NightDeep, 0.5f).copy(alpha = moonAlpha), r * 0.68f, at + Offset(r * 0.32f, -r * 0.12f))
    }
}

private fun DrawScope.drawCloud(center: Offset, w: Float, color: Color) {
    drawOval(color, center - Offset(w * 0.5f, w * 0.1f), Size(w, w * 0.24f))
    drawCircle(color, w * 0.17f, center + Offset(-w * 0.17f, -w * 0.05f))
    drawCircle(color, w * 0.23f, center + Offset(w * 0.06f, -w * 0.1f))
    drawCircle(color, w * 0.13f, center + Offset(w * 0.3f, -w * 0.01f))
}

private fun DrawScope.drawClouds(night: Float) {
    val w = size.width
    val h = size.height
    val color = lerp(Color.White, Color(0xFF39417A), night).copy(alpha = 0.9f)
    drawCloud(Offset(w * 0.13f, h * 0.2f), w * 0.2f, color)
    drawCloud(Offset(w * 0.68f, h * 0.13f), w * 0.15f, color)
    drawCloud(Offset(w * 0.3f, h * 0.55f), w * 0.24f, color)
    drawCloud(Offset(w * 0.87f, h * 0.5f), w * 0.2f, color)
    // 아래쪽 구름 바다
    val bank = lerp(Color.White, Color(0xFF2B3268), night)
    val rnd = Random(11)
    repeat(14) { i ->
        val x = w * (i / 13f)
        val r = h * (0.09f + rnd.nextFloat() * 0.07f)
        drawCircle(bank, r, Offset(x, h * 0.98f))
    }
    drawRect(bank, Offset(0f, h * 0.98f), Size(w, h * 0.02f))
}

private fun DrawScope.drawCity(night: Float) {
    val w = size.width
    val h = size.height
    fun skyline(seed: Int, baseColor: Color, nightColor: Color, minH: Float, maxH: Float, windows: Boolean) {
        val rnd = Random(seed)
        val body = lerp(baseColor, nightColor, night)
        var x = -w * 0.01f
        while (x < w) {
            val bw = w * (0.045f + rnd.nextFloat() * 0.055f)
            val bh = h * (minH + rnd.nextFloat() * (maxH - minH))
            val top = h * 0.88f - bh
            drawRect(body, Offset(x, top), Size(bw, bh))
            if (windows) {
                val cell = h * 0.032f
                var wy = top + cell * 0.6f
                while (wy < h * 0.86f) {
                    var wx = x + bw * 0.12f
                    while (wx < x + bw * 0.82f) {
                        val lit = rnd.nextFloat()
                        val lightColor = lerp(Color(0xFFBBDDF0), Color(0xFFFFD98A), night)
                        val alpha = if (lit < 0.5f) 0.35f + 0.55f * night else 0.2f * (1f - night)
                        drawRect(lightColor.copy(alpha = alpha), Offset(wx, wy), Size(cell * 0.5f, cell * 0.55f))
                        wx += cell * 0.85f
                    }
                    wy += cell
                }
            }
            x += bw + w * 0.004f
        }
    }
    skyline(3, Color(0xFFB4CCDD), Color(0xFF1E2552), 0.22f, 0.5f, windows = false)
    skyline(5, Color(0xFF7D93A8), Color(0xFF12173C), 0.1f, 0.34f, windows = true)
    // 도로
    drawRect(lerp(Color(0xFF6C7682), Color(0xFF0B0E26), night), Offset(0f, h * 0.88f), Size(w, h * 0.12f))
    val dash = lerp(Color.White, Color(0xFFFFD98A), night).copy(alpha = 0.7f)
    var dx = 0f
    while (dx < w) {
        drawRect(dash, Offset(dx, h * 0.935f), Size(w * 0.04f, h * 0.008f))
        dx += w * 0.08f
    }
}

private fun DrawScope.drawCoast(night: Float) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.56f
    // 먼 섬
    val island = lerp(Color(0xFF7FB39A), Color(0xFF12173C), night)
    drawPath(
        Path().apply {
            moveTo(w * 0.02f, horizon)
            quadraticTo(w * 0.1f, horizon - h * 0.09f, w * 0.2f, horizon)
            close()
        },
        island,
    )
    drawPath(
        Path().apply {
            moveTo(w * 0.64f, horizon)
            quadraticTo(w * 0.72f, horizon - h * 0.06f, w * 0.82f, horizon)
            close()
        },
        island,
    )
    // 바다
    drawRect(
        Brush.verticalGradient(
            listOf(lerp(Color(0xFF58BFE3), Color(0xFF1B2A63), night), lerp(Color(0xFF1C86C8), Color(0xFF0D1540), night)),
            startY = horizon,
            endY = h * 0.8f,
        ),
        Offset(0f, horizon),
        Size(w, h * 0.27f),
    )
    // 물결과 해·달빛
    val rnd = Random(9)
    repeat(26) {
        val y = horizon + h * 0.015f + rnd.nextFloat() * h * 0.2f
        val x = rnd.nextFloat() * w
        drawRect(Color.White.copy(alpha = 0.3f - night * 0.12f), Offset(x, y), Size(w * (0.015f + rnd.nextFloat() * 0.035f), 2.5f))
    }
    repeat(9) { i ->
        val y = horizon + h * 0.018f * (i + 1)
        val half = w * (0.012f + i * 0.004f)
        drawRect(Color(0xFFFFF0B8).copy(alpha = 0.55f - i * 0.04f), Offset(w * 0.42f - half, y), Size(half * 2, 3f))
    }
    // 모래사장 — 파도 치는 곡선
    val sand = lerp(Color(0xFFF4DFA9), Color(0xFF2E3158), night)
    val shore = Path().apply {
        moveTo(0f, h * 0.84f)
        cubicTo(w * 0.25f, h * 0.76f, w * 0.5f, h * 0.87f, w * 0.75f, h * 0.8f)
        quadraticTo(w * 0.9f, h * 0.76f, w, h * 0.8f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    translate(top = -h * 0.012f) { drawPath(shore, Color.White.copy(alpha = 0.55f - night * 0.35f)) }
    drawPath(shore, sand)
    // 야자수
    drawPalm(Offset(w * 0.06f, h * 0.9f), h * 0.42f, night)
}

private fun DrawScope.drawPalm(base: Offset, height: Float, night: Float) {
    val trunk = lerp(Color(0xFF8A6A45), Color(0xFF12122E), night)
    val leaf = lerp(Color(0xFF3D9A5A), Color(0xFF10183E), night)
    val top = base + Offset(height * 0.12f, -height)
    drawPath(
        Path().apply {
            moveTo(base.x - height * 0.03f, base.y)
            quadraticTo(base.x, base.y - height * 0.6f, top.x, top.y)
            quadraticTo(base.x + height * 0.05f, base.y - height * 0.6f, base.x + height * 0.04f, base.y)
            close()
        },
        trunk,
    )
    listOf(-1f to 0.05f, -1f to -0.3f, 1f to 0.05f, 1f to -0.3f, -0.45f to -0.42f, 0.45f to -0.42f).forEach { (dx, dy) ->
        val tip = top + Offset(dx * height * 0.42f, dy * height * 0.42f + height * 0.1f)
        drawPath(
            Path().apply {
                moveTo(top.x, top.y)
                quadraticTo((top.x + tip.x) / 2, top.y - height * 0.13f, tip.x, tip.y)
                quadraticTo((top.x + tip.x) / 2, top.y - height * 0.03f, top.x, top.y + height * 0.02f)
                close()
            },
            leaf,
        )
    }
}

private fun DrawScope.drawForest(night: Float) {
    val w = size.width
    val h = size.height
    fun treeLine(seed: Int, day: Color, dark: Color, base: Float, minH: Float, maxH: Float) {
        val rnd = Random(seed)
        val color = lerp(day, dark, night)
        drawRect(color, Offset(0f, h * base), Size(w, h * (1f - base)))
        var x = -w * 0.02f
        while (x < w * 1.02f) {
            val tw = w * (0.035f + rnd.nextFloat() * 0.035f)
            val th = h * (minH + rnd.nextFloat() * (maxH - minH))
            val y = h * base
            // 세 겹으로 쌓은 침엽수
            repeat(3) { tier ->
                val tierTop = y - th * (1f - tier * 0.26f)
                val tierBottom = y - th * (0.5f - tier * 0.22f)
                val half = tw * (0.62f + tier * 0.22f)
                drawPath(
                    Path().apply {
                        moveTo(x, tierTop)
                        lineTo(x + half, tierBottom)
                        lineTo(x - half, tierBottom)
                        close()
                    },
                    color,
                )
            }
            x += tw * 0.95f
        }
    }
    treeLine(21, Color(0xFFA9CFB0), Color(0xFF1C2453), 0.62f, 0.16f, 0.3f)
    treeLine(23, Color(0xFF6DAE7E), Color(0xFF141B44), 0.72f, 0.18f, 0.32f)
    treeLine(25, Color(0xFF3C8256), Color(0xFF0C1233), 0.84f, 0.2f, 0.36f)
    // 풀밭
    drawRect(
        Brush.verticalGradient(
            listOf(lerp(Color(0xFF5FA463), Color(0xFF0B1A2C), night), lerp(Color(0xFF417F49), Color(0xFF070E22), night)),
            startY = h * 0.88f,
            endY = h,
        ),
        Offset(0f, h * 0.88f),
        Size(w, h * 0.12f),
    )
    // 밤의 반딧불이
    if (night > 0f) {
        val rnd = Random(29)
        repeat(26) {
            val p = Offset(rnd.nextFloat() * w, h * (0.5f + rnd.nextFloat() * 0.45f))
            val r = h * (0.004f + rnd.nextFloat() * 0.004f)
            drawCircle(Color(0xFFE9FF9E).copy(alpha = 0.25f * night), r * 3.5f, p)
            drawCircle(Color(0xFFF5FFC4).copy(alpha = 0.9f * night), r, p)
        }
    }
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
                SceneBackground(scene, night)
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
