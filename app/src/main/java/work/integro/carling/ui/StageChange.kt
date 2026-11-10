package work.integro.carling.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import work.integro.carling.game.GrowthRules
import work.integro.carling.game.Lineage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** 성장 연출 길이(빛 모으기 → 실루엣 → 반짝 → 새 모습). 끝나면 축하 카드가 잠시 남는다. */
private const val EVOLUTION_MS = 4_400

/** 축하 카드가 남아 있는 시간(ms). 누르면 바로 닫힌다. */
private const val CARD_HOLD_MS = 5_000L

/** 성장 알림이 떠 있는 시간(ms). */
private const val GROWTH_NOTICE_MS = 2_800L

/** 성장 알림. 화면을 가리지 않고 위에 잠깐 떴다가 사라진다. 카링의 '짠'은 홈 화면에서 함께 재생한다. */
@Composable
fun GrowthNotice(to: Int, lineage: Lineage?, onDone: () -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(260))
        delay(GROWTH_NOTICE_MS)
        appear.animateTo(0f, tween(220))
        onDone()
    }
    Box(Modifier.fillMaxSize().padding(top = 28.dp), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier
                .graphicsLayer {
                    alpha = appear.value
                    translationY = (appear.value - 1f) * 40f * density
                }
                .background(Night, RoundedCornerShape(28.dp))
                .border(2.dp, Gold, RoundedCornerShape(28.dp))
                .padding(horizontal = 28.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🌱", fontSize = 32.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("성장 알림", fontSize = 17.sp, color = Gold, fontWeight = FontWeight.Bold)
                Text("${GrowthRules.stageName(to, lineage)}로 자랐어요", fontSize = 24.sp, color = Ink, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * 성장·색 변화·진화에 공통으로 쓰는 변화 장면(서비스 시각화 V3, 약 4초). 화면을 누르면 바로 끝난다.
 * 모으기(옛 모습과 새 모습의 실루엣이 번갈아 나타나고 빛줄기가 돎) → 번쩍 → 새 모습 등장 →
 * 축하 카드(새 이름, 바뀐 이유, 새로 배운 동작, 새 배경) 순서로 진행한다.
 */
@Composable
fun EvolutionOverlay(from: Int, to: Int, lineage: Lineage?, newScenes: List<String> = emptyList(), gift: String? = null, onDone: () -> Unit) {
    val oldArt = carlingArt(from, lineage)
    val newArt = carlingArt(to, lineage)
    val glow = lineage?.let { Color(it.color) } ?: HeartGlow
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(EVOLUTION_MS, easing = LinearEasing))
        delay(CARD_HOLD_MS)
        onDone()
    }

    val p = progress.value
    val chargeEnd = 0.48f
    val flashEnd = chargeEnd + 0.08f
    val revealEnd = flashEnd + 0.14f
    val charge = (p / chargeEnd).coerceIn(0f, 1f)
    val flash = ((p - chargeEnd) / (flashEnd - chargeEnd)).coerceIn(0f, 1f)
    val reveal = ((p - flashEnd) / (revealEnd - flashEnd)).coerceIn(0f, 1f)
    val caption = ((p - revealEnd) / 0.1f).coerceIn(0f, 1f)
    val spec = GrowthRules.STAGES[to.coerceIn(0, GrowthRules.MAX_STAGE)]
    val name = GrowthRules.stageName(to, lineage)
    val headline = when (spec.kind) {
        GrowthRules.Kind.EVOLUTION -> if (to == GrowthRules.MAX_STAGE) "${name}, 완성형이 되었어!" else "${name}으로 진화했어!"
        GrowthRules.Kind.COLOR -> "${lineage?.label ?: "바람"} 계열 색이 입혀졌어!"
        else -> "몸이 한 단계 커졌어!"
    }
    val reason = when (spec.kind) {
        GrowthRules.Kind.EVOLUTION, GrowthRules.Kind.COLOR -> lineage?.reason
        else -> "일주일 동안 같이 달려 줘서 고마워."
    }
    val banner = when (spec.kind) {
        GrowthRules.Kind.EVOLUTION -> "진화 알림"
        GrowthRules.Kind.COLOR -> "색 변화 알림"
        else -> "성장 알림"
    }
    val revealed = flash >= 0.5f
    // 진화는 끝으로 갈수록 빠르게 옛 모습과 새 모습을 오간다
    val flickerToNew = charge > 0.4f && sin(charge * charge * 70f) > 0f

    val art = if (revealed || flickerToNew) newArt else oldArt
    val whiteness = when {
        revealed -> 1f - reveal
        flickerToNew -> 1f
        else -> charge * charge
    }
    val shake = if (revealed) 0f else sin(p * 260f) * (1.5f + charge * 6f)
    val scale = if (revealed) easeOutBack(reveal) * 0.45f + 0.55f else 1f + sin(p * 70f) * 0.05f * charge
    val size = if (LocalCompact.current) 300.dp else 440.dp

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF0060A24))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // 크기를 고정해 그림보다 큰 빛이 아래 글자를 밀어내지 않게 한다
            Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                ChangeEffects(size, glow, p, charge, reveal, revealed)
                if (art != null) {
                    Box(
                        Modifier.size(size).graphicsLayer {
                            scaleX = scale * art.scale * if (art.mirrored) -1f else 1f
                            scaleY = scale * art.scale
                            rotationZ = shake
                        },
                    ) {
                        Image(painterResource(art.res), contentDescription = null, modifier = Modifier.fillMaxSize())
                        if (whiteness > 0f) {
                            Image(
                                painterResource(art.res),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(Color.White, BlendMode.SrcIn),
                                modifier = Modifier.fillMaxSize().alpha(whiteness),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .alpha(caption)
                    .background(Night, RoundedCornerShape(28.dp))
                    .border(2.dp, glow.copy(alpha = 0.7f), RoundedCornerShape(28.dp))
                    .padding(horizontal = 36.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(headline, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Gold)
                reason?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 22.sp, color = Ink)
                }
                GrowthRules.learnedMove(to, lineage)?.let { move ->
                    Spacer(Modifier.height(6.dp))
                    Text("✨ 새로운 동작을 배웠어요: ${move.label}", fontSize = 22.sp, color = Ink)
                }
                gift?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("🚗 $it", fontSize = 22.sp, color = Ink)
                }
                if (newScenes.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("🖼 새 배경을 얻었어요: ${newScenes.joinToString(", ")}", fontSize = 22.sp, color = Ink)
                }
            }
        }
        // 진화 알림
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
                .background(Night, RoundedCornerShape(28.dp))
                .border(2.dp, Gold, RoundedCornerShape(28.dp))
                .padding(horizontal = 28.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🌟", fontSize = 28.sp)
            Spacer(Modifier.width(10.dp))
            Text(banner, fontSize = 24.sp, color = Gold, fontWeight = FontWeight.Bold)
        }
        // 번쩍
        val white = sin(flash * PI.toFloat())
        if (white > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = white)))
    }
}

private val HeartGlow = Color(0xFFB7FFE6)

private fun easeOutBack(x: Float): Float {
    val t = x - 1f
    return 1f + 2.7f * t.pow(3) + 1.7f * t.pow(2)
}

/** 뒤쪽 빛 — 모일수록 커지는 후광, 도는 빛줄기, 등장 때 퍼지는 별. */
@Composable
private fun ChangeEffects(size: Dp, glow: Color, p: Float, charge: Float, reveal: Float, revealed: Boolean) {
    val stars = remember {
        val rnd = Random(13)
        List(30) { Triple(rnd.nextFloat() * 2f * PI.toFloat(), 0.55f + rnd.nextFloat() * 0.6f, 0.5f + rnd.nextFloat()) }
    }
    Canvas(Modifier.requiredSize(size * 2.6f)) {
        val base = this.size.minDimension / 2f
        val aura = if (revealed) 1f - reveal * 0.45f else charge
        val auraR = base * (0.35f + 0.5f * aura)
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.85f * aura), Color.Transparent), center, auraR), auraR, center)

        val rays = if (revealed) 1f - reveal * 0.3f else charge
        rotate(p * 300f) {
            repeat(12) { i ->
                val a = i * (2f * PI.toFloat() / 12f)
                val spread = 0.11f
                val ray = Path().apply {
                    moveTo(center.x, center.y)
                    lineTo(center.x + cos(a - spread) * base, center.y + sin(a - spread) * base)
                    lineTo(center.x + cos(a + spread) * base, center.y + sin(a + spread) * base)
                    close()
                }
                drawPath(ray, Brush.radialGradient(listOf(Color.White.copy(alpha = 0.5f * rays), Color.Transparent), center, base))
            }
        }

        if (revealed) {
            val fade = 1f - reveal * reveal
            stars.forEach { (angle, dist, s) ->
                val d = base * dist * (0.25f + 0.75f * easeOutBack(reveal))
                val at = center + Offset(cos(angle) * d, sin(angle) * d)
                val r = base * 0.022f * s
                val star = Path().apply {
                    moveTo(at.x, at.y - r * 2f)
                    quadraticTo(at.x, at.y, at.x + r * 2f, at.y)
                    quadraticTo(at.x, at.y, at.x, at.y + r * 2f)
                    quadraticTo(at.x, at.y, at.x - r * 2f, at.y)
                    quadraticTo(at.x, at.y, at.x, at.y - r * 2f)
                    close()
                }
                drawPath(star, Color.White.copy(alpha = fade.coerceIn(0f, 1f)))
            }
        }
    }
}
