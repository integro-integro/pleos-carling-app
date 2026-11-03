package work.integro.carling.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.sin

/** 이만큼 누르면 알이 깨진다. 이전 횟수까지는 금이 하나씩 늘어난다. */
const val EGG_TAPS_TO_HATCH = 4

private val CrackDark = Color(0xFF17507F)
private val CrackLight = Color(0xCCFFFFFF)

/** 알을 가로지르는 금. 마지막에 알은 이 선을 따라 위아래로 갈라진다. 값은 그림 한 변을 1로 본 비율이다. */
private val EquatorCrack = listOf(
    Offset(0.265f, 0.49f), Offset(0.31f, 0.45f), Offset(0.37f, 0.5f), Offset(0.43f, 0.44f), Offset(0.5f, 0.49f),
    Offset(0.57f, 0.43f), Offset(0.63f, 0.48f), Offset(0.7f, 0.44f),
)

/** 누를 때마다 더해지는 금. */
private val CracksByTap = listOf(
    listOf(listOf(Offset(0.6f, 0.27f), Offset(0.57f, 0.32f), Offset(0.6f, 0.36f), Offset(0.56f, 0.41f))),
    listOf(
        listOf(Offset(0.37f, 0.33f), Offset(0.41f, 0.37f), Offset(0.38f, 0.41f), Offset(0.42f, 0.45f)),
        listOf(Offset(0.57f, 0.32f), Offset(0.62f, 0.34f)),
    ),
    listOf(EquatorCrack, listOf(Offset(0.43f, 0.44f), Offset(0.46f, 0.38f))),
)

private fun DrawScope.drawCracks(tapCount: Int, s: Float) {
    CracksByTap.take(tapCount).flatten().forEach { line ->
        val path = Path().apply {
            line.forEachIndexed { i, p -> if (i == 0) moveTo(p.x * s, p.y * s) else lineTo(p.x * s, p.y * s) }
        }
        val width = s * 0.011f
        drawPath(path, CrackLight, style = Stroke(width * 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, CrackDark, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * 눌러서 깨우는 알. 누를 때마다 흔들리며 금이 늘고, [EGG_TAPS_TO_HATCH]번째에 갈라진 뒤 [onHatched]를 부른다.
 * 알 밖이 아니라 알 자리만 누를 수 있다.
 */
@Composable
fun HatchingEgg(size: Dp, taps: Int, onTap: () -> Unit, onHatched: () -> Unit, modifier: Modifier = Modifier) {
    val shake = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    val split = remember { Animatable(0f) }
    val broken = taps >= EGG_TAPS_TO_HATCH

    LaunchedEffect(taps) {
        if (taps == 0) return@LaunchedEffect
        if (broken) {
            split.animateTo(1f, tween(750, easing = LinearOutSlowInEasing))
            onHatched()
            return@LaunchedEffect
        }
        // 금이 늘수록 더 크게 흔들린다
        val strength = 5f + taps * 3f
        pop.snapTo(1.06f)
        pop.animateTo(1f, tween(220))
        listOf(strength, -strength, strength * 0.6f, -strength * 0.6f, 0f).forEach { shake.animateTo(it, tween(55)) }
    }

    Box(
        modifier
            .size(size)
            .graphicsLayer {
                rotationZ = shake.value
                scaleX = pop.value
                scaleY = pop.value
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !broken,
                onClick = onTap,
            ),
    ) {
        if (broken) {
            BreakingEgg(split.value)
        } else {
            ArtDrawing(EggArt, size, Modifier, rockDegrees = 2f, overlay = { s -> drawCracks(taps, s) })
        }
    }
}

/** 금을 따라 위아래로 갈라지며 빛이 터진다. */
@Composable
private fun BreakingEgg(t: Float) {
    val fade = 1f - ((t - 0.55f) / 0.45f).coerceIn(0f, 1f)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val at = Offset(0.495f * s, 0.55f * s)
            val r = s * (0.2f + 0.85f * t)
            val glow = sin(t * PI.toFloat())
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = glow), Color(0xFFB7FFE6).copy(alpha = 0.6f * glow), Color.Transparent), at, r), r, at)
        }
        Image(
            painterResource(EggArt.res),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                clip = true
                shape = eggHalfShape(top = false)
                translationY = this.size.height * 0.06f * t
                rotationZ = 7f * t
                alpha = fade
            },
        )
        Image(
            painterResource(EggArt.res),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                clip = true
                shape = eggHalfShape(top = true)
                translationX = -this.size.width * 0.06f * t
                translationY = -this.size.height * 0.3f * t
                rotationZ = -24f * t
                alpha = fade
            },
        )
    }
}

private fun eggHalfShape(top: Boolean) = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(0f, EquatorCrack.first().y * h)
    EquatorCrack.forEach { lineTo(it.x * w, it.y * h) }
    lineTo(w, EquatorCrack.last().y * h)
    if (top) {
        lineTo(w, 0f)
        lineTo(0f, 0f)
    } else {
        lineTo(w, h)
        lineTo(0f, h)
    }
    close()
}
