package work.integro.carling.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import work.integro.carling.R
import work.integro.carling.data.CarlingData
import work.integro.carling.game.WorldArea

/** 지도 그림의 가로/세로 비율. */
private const val MAP_RATIO = 2688f / 1152f

/**
 * 카링월드 지도. 지금 있는 곳(집)은 밝게 빛나고, 아직 열리지 않은 지역은 안개에 덮여 있다.
 * 진화 단계마다 다음 지역의 안개가 걷힌다.
 */
@Composable
fun WorldMap(data: CarlingData, onClose: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "here").animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF9FB7F2), Color(0xFFD9C9F5), Color(0xFFF6E3F0)))),
        contentAlignment = Alignment.Center,
    ) {
        val mapW = min(maxWidth, maxHeight * MAP_RATIO)
        val mapH = mapW / MAP_RATIO
        Box(Modifier.requiredSize(mapW, mapH)) {
            Image(painterResource(R.drawable.world_map), contentDescription = "카링월드 지도", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            // 안개: 지도 전체를 두꺼운 구름으로 덮고, 열린 지역만 동그랗게 걷어 낸다. 잠긴 곳은 전혀 보이지 않는다.
            val fog = ImageBitmap.imageResource(R.drawable.world_fog)
            Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                drawImage(fog, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
                WorldArea.entries.filter { it.isOpen(data.form) }.forEach { a ->
                    val c = Offset(a.x * size.width, a.y * size.height)
                    val r = size.width * 0.15f
                    drawCircle(Brush.radialGradient(0.55f to Color.Black, 1f to Color.Transparent, center = c, radius = r), r, c, blendMode = BlendMode.DstOut)
                }
            }
            WorldArea.entries.forEach { a ->
                val open = a.isOpen(data.form)
                val here = a == WorldArea.HOME
                Column(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(mapW * a.x - 95.dp, mapH * a.y - 26.dp)
                        .requiredSize(190.dp, 74.dp)
                        .graphicsLayer {
                            if (here) {
                                scaleX = pulse
                                scaleY = pulse
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        (if (here) "📍 " else if (open) "${a.icon} " else "🔒 ") + a.label,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (open) Color.White else Ink,
                        modifier = Modifier
                            .background(if (here) RoseDeep else if (open) Color(0xCC4A3A5A) else Color.White.copy(alpha = 0.9f), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                    Text(
                        when {
                            here -> "지금 여기"
                            open -> "곧 열려요"
                            else -> "${a.needs} · Lv.${a.level}"
                        },
                        fontSize = 15.sp,
                        color = Ink,
                        modifier = Modifier.padding(top = 2.dp).background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Column(Modifier.align(Alignment.TopStart).padding(20.dp)) {
            Text("카링월드", fontSize = 34.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Text("함께 달린 만큼, 우리의 세계가 자라요.", fontSize = 19.sp, color = Color.White)
        }
        Box(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Speech("구름 너머에 아직 모르는 곳들이 있어요. 진화할 때마다 구름이 걷혀요!", maxWidth = 560.dp)
        }
        Button(onClick = onClose, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).size(width = 220.dp, height = 68.dp)) {
            Text("🏠 집으로", fontSize = 24.sp)
        }
    }
}
