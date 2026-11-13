package work.integro.carling.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import work.integro.carling.data.CarlingData
import work.integro.carling.data.LuckyBox
import work.integro.carling.game.Badge
import work.integro.carling.game.CarColor
import work.integro.carling.game.CarPart
import work.integro.carling.game.CarSlot
import work.integro.carling.game.Stage
import work.integro.carling.game.eulReul
import work.integro.carling.game.iGa
import work.integro.carling.game.ieyo
import work.integro.carling.game.waGwa
import kotlin.math.cos
import kotlin.math.sin

/** 상자 빛 색(내용물 힌트, 시안 v1): 라벤더=포인트, 금빛=배지, 민트=자동차 꾸미기. (빛, 리본) */
private fun glowOf(box: LuckyBox?): Pair<Color, Color> = when {
    box?.badge != null -> Color(0xFFFFD27A) to Color(0xFFFFC24A)
    box?.part != null || box?.color != null -> Color(0xFF8CFFD6) to Color(0xFF5FE0B4)
    else -> Color(0xFFC7B8FF) to Color(0xFF5FE0B4)
}

/** 연 상자 하나와, 그 안의 배지·아이템을 처음 받았는지. */
private class Opened(val box: LuckyBox, val isNew: Boolean)

/** 열기 전 보유 수를 기준으로 처음 받는지 계산한다(모두 받기는 앞 상자부터 차례로). */
private fun planOpen(boxes: List<LuckyBox>, data: CarlingData): List<Opened> {
    val badges = data.badges.toMutableMap()
    val cars = data.parts.toMutableMap()
    val colors = data.colors.toMutableMap()
    return boxes.map { b ->
        val isNew = when {
            b.badge != null -> (badges[b.badge] ?: 0) == 0
            b.part != null -> (cars[b.part] ?: 0) == 0
            b.color != null -> (colors[b.color] ?: 0) == 0
            else -> false
        }
        b.badge?.let { badges[it] = (badges[it] ?: 0) + 1 }
        b.part?.let { cars[it] = (cars[it] ?: 0) + 1 }
        b.color?.let { colors[it] = (colors[it] ?: 0) + 1 }
        Opened(b, isNew)
    }
}

/**
 * 럭키박스(시안 v1). 상자는 내용물에 따라 빛 색이 다르고 이따금 들썩인다.
 * 열면 뚜껑이 튀어 오르며 빛줄기·반짝이가 퍼지고, 받은 것이 한 장씩 올라온다(처음 받은 것엔 NEW).
 * 자동차 꾸미기는 카링의 차에 붙은 모습까지 보여준다. 2개 이상이면 '모두 받기'로 한 번에 열고 결과를 모아 본다.
 */
@Composable
fun LuckyBoxDialog(data: CarlingData, onOpen: (LuckyBox) -> Unit, onOpenAll: () -> Unit, onEquip: (CarPart) -> Unit, onPaint: (CarColor) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var currentId by remember { mutableStateOf(data.unopenedBoxes.firstOrNull()?.boxId) }
    var opened by remember { mutableStateOf<Opened?>(null) }
    var summary by remember { mutableStateOf<List<Opened>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val lid = remember { Animatable(0f) }
    var bursts by remember { mutableStateOf(listOf<Triple<Long, Float, List<Color>>>()) }
    var raysKey by remember { mutableStateOf(0L) }
    /** 자동차 꾸미기를 받은 뒤: 0 = 물어보는 중, 1 = 장착 완료 화면, 2 = 끝(나중에 포함). */
    var equipStep by remember { mutableStateOf(0) }
    val current = data.boxes.firstOrNull { it.boxId == currentId }
    val remaining = data.unopenedBoxes.size
    val (glow, ribbon) = glowOf(opened?.box ?: current)

    fun burst(x: Float, colors: List<Color>) {
        bursts = (bursts + Triple(System.nanoTime(), x, colors)).takeLast(12)
    }

    fun openOne() {
        val box = current?.takeIf { it.openedAt == null } ?: return
        if (busy) return
        busy = true
        val plan = planOpen(listOf(box), data).single()
        val color = glowOf(box).first
        scope.launch {
            launch { lid.animateTo(1f, tween(450, easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.3f))) }
            delay(300)
            onOpen(box)
            equipStep = 0
            opened = plan
            raysKey = System.nanoTime()
            burst(0.5f, listOf(color, Color.White, Color(0xFFC7B8FF), Color(0xFF8CFFD6)))
            busy = false
        }
    }

    fun openAll() {
        if (busy) return
        val boxes = data.unopenedBoxes
        if (boxes.size < 2) return
        busy = true
        opened = null
        val plan = planOpen(boxes, data)
        scope.launch {
            lid.snapTo(0f)
            launch { lid.animateTo(1f, tween(320, easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.3f))) }
            delay(200)
            raysKey = System.nanoTime()
            // 상자가 많아도 터지는 연출은 0.6초 안에 끝낸다(11/13: 모두 받기가 너무 길다)
            val shown = plan.take(10)
            val step = (600L / shown.size).coerceIn(30L, 90L)
            shown.forEachIndexed { i, o ->
                burst(0.15f + 0.7f * i / (shown.size - 1).coerceAtLeast(1), listOf(glowOf(o.box).first, Color.White))
                delay(step)
            }
            onOpenAll()
            delay(120)
            summary = plan
            burst(0.5f, listOf(Color(0xFFFFD27A), Color(0xFF8CFFD6), Color(0xFFC7B8FF), Color.White))
            busy = false
        }
    }

    fun nextBox() {
        opened = null
        equipStep = 0
        currentId = data.unopenedBoxes.firstOrNull()?.boxId
        scope.launch { lid.snapTo(0f) }
    }

    FullScrim {
        Panel(Modifier.width(1060.dp), padding = 28.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val sum = summary
                val o = opened
                val (title, sub) = when {
                    sum != null -> "모두 받았어요!" to "럭키박스 ${sum.size}개를 한 번에 열었어요"
                    o == null -> "럭키박스가 도착했어요" to (if (remaining > 1) "상자 ${remaining}개 · 하나씩 열거나 모두 받을 수 있어요" else "상자를 눌러 열어 보세요")
                    o.box.color != null && equipStep == 1 -> "${o.box.color.label} 칠하기 완료" to "카링의 차가 ${o.box.color.label.iGa()} 되었어요"
                    o.box.color != null -> "${o.box.color.label} 색을 얻었어요!" to when {
                        data.stage.ordinal < Stage.BABY.ordinal -> "차체 색 · 아기링이 되면 칠할 수 있어요"
                        data.carColor == o.box.color -> "지금 칠한 색이에요" + if (!o.isNew) " · 하나 더 모았어요" else ""
                        else -> "차체 색 · 바로 칠해 볼까요?"
                    }
                    o.box.part != null && equipStep == 1 -> "${o.box.part.label} 장착 완료" to (data.style?.let { "${it.icon} '${it.label}' 스타일이 완성됐어요!" } ?: "${o.box.part.slot.label} 자리에 달았어요")
                    o.box.part != null -> "${o.box.part.label.eulReul()} 얻었어요!" to when {
                        data.stage.ordinal < Stage.BABY.ordinal -> "${o.box.part.slot.label} · 아기링이 되면 차에 달 수 있어요"
                        data.equipped[o.box.part.slot] == o.box.part -> "이미 달려 있는 ${o.box.part.slot.label.ieyo()}" + if (!o.isNew) " · 하나 더 모았어요" else ""
                        data.equipped[o.box.part.slot] != null -> "${o.box.part.slot.label} · 지금 달린 ${data.equipped[o.box.part.slot]!!.label.waGwa()} 바뀌어요"
                        else -> "${o.box.part.slot.label} · 바로 달아 볼까요?"
                    }
                    o.box.badge != null -> (if (o.isNew) "새 배지를 모았어요!" else "배지를 또 받았어요") to "${o.box.badge.label} · 수집 배지 ${data.badges.size}/${Badge.entries.size}"
                    else -> "성장 포인트를 받았어요" to "다음 상자엔 무엇이 들었을까요?"
                }
                Text(title, fontSize = 32.sp, color = Ink, fontWeight = FontWeight.Bold)
                Text(sub, fontSize = 19.sp, color = Muted)
                Spacer(Modifier.height(14.dp))

                // ---- 무대 ----
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .background(Brush.verticalGradient(listOf(Color(0xFFD9D3FF), Color(0xFFB6AEF0), Color(0xFF8F88DA))), RoundedCornerShape(30.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (sum != null) {
                        Summary(sum, Modifier.fillMaxSize().padding(20.dp))
                    } else {
                        Rays(raysKey, glow, Modifier.fillMaxSize())
                        val part = o?.box?.part
                        if (o != null && (part != null || o.box.color != null) && equipStep == 1) {
                            EquipDone(data, part, o.box.boxId)
                        } else if (o != null && lid.value >= 1f) {
                            BigReward(o, glow)
                        } else {
                            Halo(glow, Modifier.size(380.dp))
                            val t = rememberInfiniteTransition(label = "boxshake")
                            val shake by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Restart), label = "shake")
                            val tilt = if (o == null && !busy) wiggle(shake) else 0f
                            Box(
                                Modifier
                                    .size(300.dp)
                                    .graphicsLayer { alpha = if (o != null) (1f - (lid.value - 0.6f).coerceAtLeast(0f) * 2.5f).coerceIn(0f, 1f) else 1f }
                                    .clickable(remember { MutableInteractionSource() }, indication = null, enabled = o == null) { openOne() },
                                contentAlignment = Alignment.Center,
                            ) { GiftBox(lidOpen = lid.value, tilt = tilt, ribbon = ribbon, modifier = Modifier.size(260.dp)) }
                        }
                    }
                    bursts.forEach { (k, x, colors) ->
                        key(k) { SparkleBurst(k, colors, Modifier.fillMaxSize(), originX = x, originY = 0.48f, count = if (x == 0.5f) 70 else 30) }
                    }
                }

                // ---- 받은 것 ----
                if (o != null && sum == null) {
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        RewardTile("+${o.box.bonusGp}", "성장 포인트", "보너스", Color(0xFFE3E2F7), false, 0, o.box.boxId, big = true)
                        o.box.badge?.let { RewardTile(it.icon, it.label, "수집 배지", Color(0xFFFFE3A3), o.isNew, 1, o.box.boxId) }
                        o.box.part?.let { RewardTile("", it.label, "자동차 꾸미기 · ${it.slot.label}", Color(0xFFB9F5DD), o.isNew, 1, o.box.boxId, image = partRes(it)) }
                        o.box.color?.let { RewardTile("●", it.label, "차체 색", Color(it.light), o.isNew, 1, o.box.boxId, iconColor = Color(it.dark)) }
                    }
                }

                // ---- 버튼 ----
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    when {
                        sum != null -> Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Lilac), modifier = Modifier.height(64.dp)) { Text("카링 보러 가기", fontSize = 22.sp) }
                        o == null -> {
                            Button(onClick = ::openOne, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = Lilac), modifier = Modifier.height(64.dp)) { Text("열기", fontSize = 22.sp) }
                            if (remaining >= 2) Button(onClick = ::openAll, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = Mint), modifier = Modifier.height(64.dp)) { Text("모두 받기 (${remaining}개)", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                            OutlinedButton(onClick = onClose, modifier = Modifier.height(64.dp)) { Text("나중에 열기", fontSize = 20.sp) }
                        }
                        // 자동차 꾸미기: 나중에 / 바로 장착 → 장착 완료 → 확인
                        o.box.part != null && equipStep == 0 && data.stage.ordinal >= Stage.BABY.ordinal && data.equipped[o.box.part.slot] != o.box.part -> {
                            OutlinedButton(onClick = { equipStep = 2 }, modifier = Modifier.height(64.dp)) { Text("나중에", fontSize = 22.sp) }
                            Button(onClick = { onEquip(o.box.part); equipStep = 1 }, colors = ButtonDefaults.buttonColors(containerColor = Mint), modifier = Modifier.height(64.dp).widthIn(min = 220.dp)) { Text("바로 장착", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                        }
                        o.box.color != null && equipStep == 0 && data.stage.ordinal >= Stage.BABY.ordinal && data.carColor != o.box.color -> {
                            OutlinedButton(onClick = { equipStep = 2 }, modifier = Modifier.height(64.dp)) { Text("나중에", fontSize = 22.sp) }
                            Button(onClick = { onPaint(o.box.color); equipStep = 1 }, colors = ButtonDefaults.buttonColors(containerColor = Mint), modifier = Modifier.height(64.dp).widthIn(min = 220.dp)) { Text("바로 칠하기", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                        }
                        (o.box.part != null || o.box.color != null) && equipStep == 1 -> {
                            Button(onClick = { equipStep = 2 }, colors = ButtonDefaults.buttonColors(containerColor = Mint), modifier = Modifier.height(64.dp).widthIn(min = 220.dp)) { Text("확인", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                        }
                        else -> {
                            if (remaining >= 1) Button(onClick = ::nextBox, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = Lilac), modifier = Modifier.height(64.dp)) { Text("다음 상자 (${remaining}개)", fontSize = 22.sp) }
                            if (remaining >= 2) Button(onClick = ::openAll, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = Mint), modifier = Modifier.height(64.dp)) { Text("모두 받기 (${remaining}개)", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                            OutlinedButton(onClick = onClose, modifier = Modifier.height(64.dp)) { Text("닫기", fontSize = 20.sp) }
                        }
                    }
                }
            }
        }
    }
}

/** 들썩임: 한 주기의 60% 동안 가만히 있다가 좌우로 흔들린다. */
private fun wiggle(t: Float): Float = if (t < 0.6f) 0f else sin((t - 0.6f) / 0.4f * Math.PI.toFloat() * 6f) * 5f * (1f - (t - 0.6f) / 0.4f)

@Composable
private fun Halo(color: Color, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "halo")
    val p by t.animateFloat(0.9f, 1.08f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse")
    Canvas(modifier.graphicsLayer { scaleX = p; scaleY = p }) {
        drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0.35f), Color.Transparent)), size.minDimension / 2)
    }
}

/** 상자가 열릴 때 퍼지는 빛줄기. */
@Composable
private fun Rays(key: Long, color: Color, modifier: Modifier) {
    if (key == 0L) return
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { a.animateTo(1f, tween(1400)) }
    if (a.value >= 1f) return
    Canvas(modifier) {
        val alpha = if (a.value < 0.3f) a.value / 0.3f else (1f - a.value) / 0.7f
        val r = size.minDimension * (0.25f + 0.6f * a.value)
        rotate(40f * a.value) {
            for (i in 0 until 12) {
                val ang = Math.toRadians(i * 30.0).toFloat()
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(center.x, center.y)
                    lineTo(center.x + cos(ang - 0.07f) * r, center.y + sin(ang - 0.07f) * r)
                    lineTo(center.x + cos(ang + 0.07f) * r, center.y + sin(ang + 0.07f) * r)
                    close()
                }
                drawPath(p, Brush.radialGradient(listOf(color.copy(alpha = 0.8f * alpha), Color.Transparent), center, r))
            }
        }
    }
}

/** 받은 것 한 장. [order]만큼 늦게 아래에서 올라온다. */
@Composable
private fun RewardTile(icon: String, name: String, kind: String, ring: Color, isNew: Boolean, order: Int, key: Int, big: Boolean = false, image: Int? = null, iconColor: Color? = null) {
    val a = remember(key, order) { Animatable(0f) }
    LaunchedEffect(key, order) {
        delay(200L + order * 160L)
        a.animateTo(1f, tween(420, easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.2f)))
    }
    Box(Modifier.graphicsLayer { alpha = a.value.coerceIn(0f, 1f); translationY = (1f - a.value) * 60f; scaleX = 0.85f + 0.15f * a.value; scaleY = scaleX }) {
        Column(
            Modifier.widthIn(min = 190.dp).background(Color.White, RoundedCornerShape(22.dp)).border(2.dp, ring, RoundedCornerShape(22.dp)).padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (image != null) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(image), contentDescription = null, modifier = Modifier.size(52.dp))
            else Text(icon, fontSize = if (big) 34.sp else 40.sp, color = iconColor ?: if (big) Color(0xFF1F9C74) else Color.Unspecified, fontWeight = FontWeight.Bold)
            Text(name, fontSize = 19.sp, color = Ink, fontWeight = FontWeight.Bold)
            Text(kind, fontSize = 15.sp, color = Muted)
        }
        if (isNew) NewMark(Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-10).dp))
    }
}

@Composable
private fun NewMark(modifier: Modifier) =
    Text("NEW", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = modifier.background(Coral, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp))

/** 연 상자에서 나온 대표 보상을 무대 가운데에 크게: 배지·자동차 아이템이면 그 아이콘, 아니면 포인트. */
@Composable
private fun BigReward(o: Opened, glow: Color) {
    val a = remember(o.box.boxId) { Animatable(0f) }
    LaunchedEffect(o.box.boxId) { a.animateTo(1f, tween(520, easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.35f))) }
    Box(Modifier.graphicsLayer { alpha = a.value.coerceIn(0f, 1f); scaleX = 0.5f + 0.5f * a.value; scaleY = scaleX; translationY = (1f - a.value) * 80f }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(320.dp)) { drawCircle(Brush.radialGradient(listOf(Color.White, glow.copy(alpha = 0.8f), Color.Transparent)), size.minDimension / 2) }
        val item = o.box.badge?.icon
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (o.box.color != null) {
                Box(Modifier.size(170.dp).background(Brush.linearGradient(listOf(Color(o.box.color.light), Color(o.box.color.dark))), CircleShape).border(6.dp, Color.White, CircleShape))
                Spacer(Modifier.height(8.dp))
                Text(o.box.color.label, fontSize = 28.sp, color = Ink, fontWeight = FontWeight.Bold)
            } else if (o.box.part != null) {
                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(partRes(o.box.part)), contentDescription = o.box.part.label, modifier = Modifier.size(200.dp))
                Text(o.box.part.label, fontSize = 28.sp, color = Ink, fontWeight = FontWeight.Bold)
            } else if (item != null) {
                Text(item, fontSize = 110.sp)
                Text(o.box.badge?.label ?: "", fontSize = 28.sp, color = Ink, fontWeight = FontWeight.Bold)
            } else {
                Text("+${o.box.bonusGp}", fontSize = 96.sp, color = Color(0xFF1F9C74), fontWeight = FontWeight.Bold)
                Text("성장 포인트", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold)
            }
        }
        if (o.isNew) NewMark(Modifier.align(Alignment.TopEnd).offset(x = (-30).dp, y = 30.dp))
    }
}

/**
 * 장착 완료: 왼쪽엔 아이템을 단 카링의 차, 오른쪽엔 그 자리를 확대한 원.
 * 차가 떠오르고 아이템이 '톡' 달린 뒤 확대 원이 나타난다.
 */
@Composable
private fun EquipDone(data: CarlingData, part: CarPart?, key: Int) {
    val a = remember(key) { Animatable(0f) }
    val zoom = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        a.animateTo(1f, tween(450))
        zoom.animateTo(1f, tween(420, easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.3f)))
    }
    val art = artFor(data.shownStage, Pose.CAR)
    val slots = art.slots ?: return
    val focus = when (part?.slot) {
        null -> slots.body.let { b -> Offset(b.map { it.x }.average().toFloat(), b.map { it.y }.average().toFloat()) }
        CarSlot.WHEEL -> slots.wheels.first().let { Offset(it.cx, it.cy) }
        CarSlot.ANTENNA -> Offset(slots.antenna.x, slots.antenna.y - slots.antennaH * 0.55f)
        CarSlot.REAR -> Offset(slots.rear.x, slots.rear.y - slots.rearH * 0.5f)
    }
    val carH = 320.dp
    val carW = carH * art.aspect
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Box(Modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 40f }) {
            KaringView(data.shownStage, Pose.CAR, data.personality, data.equipped, height = carH, carColor = data.carColor)
        }
        // 확대 원: 같은 그림을 2.4배로 키워 그 자리를 가운데에 둔다
        val d = 240.dp
        val scale = 2.4f
        Box(
            Modifier
                .size(d)
                .graphicsLayer { alpha = zoom.value; scaleX = 0.6f + 0.4f * zoom.value; scaleY = scaleX }
                .clip(CircleShape)
                .background(Color(0xFFE9E5FF))
                .border(6.dp, Color.White, CircleShape),
        ) {
            Box(
                Modifier
                    .requiredSize(carW, carH)
                    .graphicsLayer {
                        translationX = (d.toPx() / 2 - carW.toPx() * focus.x) * 1f + (carW.toPx() - d.toPx()) / 2
                        translationY = (d.toPx() / 2 - carH.toPx() * focus.y) * 1f + (carH.toPx() - d.toPx()) / 2
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(focus.x, focus.y)
                    },
            ) { KaringView(data.shownStage, Pose.CAR, data.personality, data.equipped, height = carH, still = true, carColor = data.carColor) }
        }
    }
}

/** 모두 받기 결과: 합계 포인트, 새로 얻은 것 먼저, 같은 것은 ×n으로 묶는다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(results: List<Opened>, modifier: Modifier) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(420)) }
    Column(
        modifier
            .graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 30f }
            .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(26.dp))
            .padding(22.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("+${results.sumOf { it.box.bonusGp }}", fontSize = 46.sp, color = Color(0xFF1F9C74), fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text("성장 포인트", fontSize = 20.sp, color = Muted, modifier = Modifier.padding(bottom = 8.dp))
        }
        Group("수집 배지", results.filter { it.box.badge != null }.groupBy { it.box.badge!! }.map { (b, l) -> Triple("${b.icon} ${b.label}", l.size, l.any { it.isNew }) }, Color(0xFFFFF7E3))
        Group("자동차 꾸미기 · 새로 받은 것은 비어 있는 자리에 바로 달았어요", results.filter { it.box.part != null }.groupBy { it.box.part!! }.map { (c, l) -> Triple(c.label, l.size, l.any { it.isNew }) }, Color(0xFFE6FAF2), images = results.mapNotNull { it.box.part }.associate { it.label to partRes(it) })
        Group("차체 색 · 카링 탭에서 골라 칠할 수 있어요", results.filter { it.box.color != null }.groupBy { it.box.color!! }.map { (c, l) -> Triple(c.label, l.size, l.any { it.isNew }) }, Color(0xFFEDEAFF))
        Text("상자별", fontSize = 16.sp, color = Muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            results.forEachIndexed { i, o ->
                Text(
                    "${i + 1}번 +${o.box.bonusGp}" + (o.box.badge?.let { " · ${it.icon}" } ?: "") + (o.box.part?.let { " · ${it.label}" } ?: "") + (o.box.color?.let { " · ${it.label}" } ?: ""),
                    fontSize = 15.sp,
                    color = Muted,
                    modifier = Modifier.background(Color.White, RoundedCornerShape(12.dp)).border(1.dp, Line, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Group(title: String, items: List<Triple<String, Int, Boolean>>, fill: Color, images: Map<String, Int> = emptyMap()) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 16.sp, color = Muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.sortedByDescending { it.third }.forEach { (label, n, isNew) ->
                Row(Modifier.background(fill, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    images[label]?.let {
                        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(it), contentDescription = null, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(label, fontSize = 19.sp, color = Ink, fontWeight = FontWeight.Medium)
                    if (n > 1) Text("  ×$n", fontSize = 16.sp, color = Muted)
                    if (isNew) {
                        Spacer(Modifier.width(8.dp))
                        Text("NEW", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.background(Coral, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
}

/**
 * 럭키박스(11/13 새 디자인): 비스듬히 본 남보라 상자에 금빛 모서리, 내용물 빛 색의 리본과 큰 나비 매듭,
 * 앞면 가운데 별 자물쇠. [lidOpen] 0~1이면 뚜껑이 뒤로 젖혀지며 안에서 빛이 솟는다.
 */
@Composable
private fun GiftBox(lidOpen: Float, tilt: Float, ribbon: Color = Color(0xFF5FE0B4), modifier: Modifier = Modifier.size(260.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun p(x: Float, y: Float) = Offset(w * x, h * y)
        fun poly(a: Offset, b: Offset, c: Offset, d: Offset? = null): androidx.compose.ui.graphics.Path = androidx.compose.ui.graphics.Path().apply {
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); if (d != null) lineTo(d.x, d.y); close()
        }
        val gold = Color(0xFFFFD27A)
        val goldDark = Color(0xFFD99A2B)
        val dark = Color(ribbon.red * 0.72f, ribbon.green * 0.72f, ribbon.blue * 0.72f)
        rotate(tilt, Offset(w / 2, h * 0.92f)) {
            // 바닥 그림자
            drawOval(Color(0x33201A4A), p(0.16f, 0.86f), Size(w * 0.7f, h * 0.08f))
            // 몸통: 앞면(왼쪽 아래)과 옆면(오른쪽)
            val fTL = p(0.16f, 0.50f); val fTR = p(0.60f, 0.56f); val fBR = p(0.60f, 0.90f); val fBL = p(0.16f, 0.83f)
            val sTR = p(0.84f, 0.46f); val sBR = p(0.84f, 0.80f)
            drawPath(poly(fTL, fTR, fBR, fBL), Brush.verticalGradient(listOf(Color(0xFF7B6BF0), Color(0xFF4B3FB8)), startY = fTL.y, endY = fBR.y))
            drawPath(poly(fTR, sTR, sBR, fBR), Brush.verticalGradient(listOf(Color(0xFF5C4ED6), Color(0xFF362C93)), startY = sTR.y, endY = fBR.y))
            // 리본: 앞면 세로 띠와 옆면 가로 띠
            drawPath(poly(p(0.35f, 0.527f), p(0.42f, 0.537f), p(0.42f, 0.87f), p(0.35f, 0.86f)), ribbon)
            drawPath(poly(p(0.60f, 0.70f), p(0.84f, 0.635f), p(0.84f, 0.69f), p(0.60f, 0.755f)), dark)
            // 금빛 모서리
            listOf(fTL to fTR, fTR to sTR, fTR to fBR, fBL to fBR, fBR to sBR, fTL to fBL, sTR to sBR).forEach { (a, b) -> drawLine(gold, a, b, strokeWidth = w * 0.012f, cap = StrokeCap.Round) }
            // 별 자물쇠
            val lock = p(0.385f, 0.70f)
            drawCircle(goldDark, w * 0.062f, lock)
            drawCircle(gold, w * 0.052f, lock)
            drawPath(starPath(lock, w * 0.036f), Color.White)
            // 안에서 솟는 빛
            if (lidOpen > 0f) {
                val c = p(0.5f, 0.48f)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF7D6).copy(alpha = lidOpen), ribbon.copy(alpha = 0.4f * lidOpen), Color.Transparent), c, w * 0.48f), w * 0.48f, c)
                drawPath(poly(fTL, fTR, sTR, p(0.40f, 0.40f)), Color(0xFF2A2170))
            }
            // 뚜껑: 윗면 + 앞·옆 테두리, 젖혀지며 위로 들린다
            val lift = lidOpen * h * 0.30f
            rotate(-28f * lidOpen, Offset(w * 0.62f, h * 0.44f - lift)) {
                val dy = -lift
                val tFL = p(0.13f, 0.47f + dy / h); val tFR = p(0.61f, 0.535f + dy / h); val tBR = p(0.87f, 0.425f + dy / h); val tBL = p(0.40f, 0.37f + dy / h)
                val rim = h * 0.07f
                drawPath(poly(tFL, tFR, Offset(tFR.x, tFR.y + rim), Offset(tFL.x, tFL.y + rim)), Brush.verticalGradient(listOf(Color(0xFF9A8CFF), Color(0xFF6E5FE6)), startY = tFL.y, endY = tFR.y + rim))
                drawPath(poly(tFR, tBR, Offset(tBR.x, tBR.y + rim), Offset(tFR.x, tFR.y + rim)), Color(0xFF5A4CD0))
                drawPath(poly(tFL, tFR, tBR, tBL), Brush.linearGradient(listOf(Color(0xFFC9C0FF), Color(0xFF9B8DFF)), tBL, tFR))
                listOf(tFL to tFR, tFR to tBR, tBR to tBL, tBL to tFL).forEach { (a, b) -> drawLine(gold, a, b, strokeWidth = w * 0.012f, cap = StrokeCap.Round) }
                // 뚜껑 위 리본 십자
                val mid1a = Offset((tFL.x + tFR.x) / 2 - w * 0.03f, (tFL.y + tFR.y) / 2); val mid1b = Offset((tBL.x + tBR.x) / 2 - w * 0.03f, (tBL.y + tBR.y) / 2)
                drawLine(ribbon, mid1a, mid1b, strokeWidth = w * 0.07f)
                val mid2a = Offset((tFL.x + tBL.x) / 2, (tFL.y + tBL.y) / 2); val mid2b = Offset((tFR.x + tBR.x) / 2, (tFR.y + tBR.y) / 2)
                drawLine(dark, mid2a, mid2b, strokeWidth = w * 0.055f)
                // 나비 매듭
                val knot = Offset((mid1a.x + mid1b.x) / 2, (mid1a.y + mid1b.y) / 2 - h * 0.01f)
                drawPath(poly(knot, Offset(knot.x - w * 0.17f, knot.y - h * 0.12f), Offset(knot.x - w * 0.19f, knot.y + h * 0.02f)), ribbon)
                drawPath(poly(knot, Offset(knot.x + w * 0.17f, knot.y - h * 0.13f), Offset(knot.x + w * 0.18f, knot.y + h * 0.01f)), ribbon)
                drawLine(dark, knot, Offset(knot.x - w * 0.15f, knot.y - h * 0.085f), strokeWidth = w * 0.01f)
                drawLine(dark, knot, Offset(knot.x + w * 0.15f, knot.y - h * 0.095f), strokeWidth = w * 0.01f)
                drawCircle(dark, w * 0.04f, knot)
                drawCircle(ribbon, w * 0.028f, knot)
            }
        }
    }
}

private fun starPath(c: Offset, r: Float): androidx.compose.ui.graphics.Path = androidx.compose.ui.graphics.Path().apply {
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) r else r * 0.45f
        val a = Math.toRadians(i * 36.0 - 90).toFloat()
        val x = c.x + rr * kotlin.math.cos(a)
        val y = c.y + rr * kotlin.math.sin(a)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** 홈에 떠 있는 럭키박스 버튼. 다음 상자의 빛 색으로 빛난다. */
@Composable
fun LuckyBoxButton(count: Int, next: LuckyBox?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "boxbtn")
    val bob by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bob")
    val (glow, ribbon) = glowOf(next)
    Row(
        modifier
            .graphicsLayer { translationY = -8f * bob }
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(120.dp)) { drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.7f), Color.Transparent)), size.minDimension / 2) }
            GiftBox(lidOpen = 0f, tilt = 0f, ribbon = ribbon, modifier = Modifier.size(96.dp))
        }
        // 밤 배경에서도 읽히게 흰 알약 위에 쓴다
        Text(
            if (count > 1) "럭키박스 ${count}개 · 모두 받기" else "럭키박스 열기",
            fontSize = 21.sp,
            color = Ink,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}
