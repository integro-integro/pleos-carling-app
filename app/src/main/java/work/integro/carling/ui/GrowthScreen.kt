package work.integro.carling.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.data.CarlingData
import work.integro.carling.game.FUTURE_EVOLUTIONS
import work.integro.carling.game.Form
import work.integro.carling.game.GrowthRules
import work.integro.carling.game.Lineage
import work.integro.carling.voice.Babble
import kotlin.math.pow
import kotlin.math.sin

/** 굵은 진행 바. */
@Composable
fun SoftBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.height(18.dp).background(Color(0xFFF3DDE3), RoundedCornerShape(9.dp))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(color, RoundedCornerShape(9.dp)))
    }
}

/** 성장과 진화 조건을 한 화면에 모았다. 세 조건을 다 채우면 진화 버튼이 켜진다. */
@Composable
fun GrowthScreen(data: CarlingData, onEvolve: () -> Unit, onClose: () -> Unit) {
    val next = data.form.next
    val level = data.level
    FullScrim {
        PanelBox(Modifier.widthIn(max = 1180.dp)) {
            Row(Modifier.verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                Column(Modifier.width(360.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CarlingPicture(carlingArt(data.form, data.lineage, level), 300.dp)
                    Text(data.name ?: formLabel(data.form, data.lineage), fontSize = 32.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Text("${formLabel(data.form, data.lineage)} · Lv.$level", fontSize = 22.sp, color = RoseDeep)
                    Spacer(Modifier.height(12.dp))
                    Text("함께 달린 날 ${data.drivingDayCount}일", fontSize = 20.sp, color = Muted)
                    Text("모은 아이템 ${data.placed.size} / 24", fontSize = 20.sp, color = Muted)
                }
                Column(Modifier.weight(1f)) {
                    ConditionRow("레벨 XP", if (level >= GrowthRules.MAX_LEVEL) "최고 레벨" else "${data.totalXp - GrowthRules.cumXp(level)} / ${GrowthRules.xpToNext(level)}", GrowthRules.levelProgress(data.totalXp), Honey)
                    Spacer(Modifier.height(18.dp))
                    if (next != null) {
                        Text("다음: ${next.label}", fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        ConditionRow("최소 레벨 Lv.${next.minLevel}", "Lv.$level", level.toFloat() / next.minLevel, Sky)
                        ConditionRow("친밀도", "${data.intimacy} / ${next.intimacyGoal}", data.intimacy.toFloat() / next.intimacyGoal, Rose)
                        ConditionRow("퀘스트", "${data.questsDone} / ${next.questGoal}", data.questsDone.toFloat() / next.questGoal, Mint)
                        Spacer(Modifier.height(8.dp))
                        Text("친밀도는 새 아이템이 방에 놓일 때 올라요.", fontSize = 18.sp, color = Muted)
                    } else {
                        Text("다음 모습은 추후 공개될 거예요.", fontSize = 24.sp, color = Ink, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(14.dp))
                    data.activeQuest?.let { q ->
                        Column(Modifier.fillMaxWidth().background(Blush, RoundedCornerShape(20.dp)).padding(16.dp)) {
                            Text("진행 중인 퀘스트", fontSize = 16.sp, color = Muted)
                            Text("${q.kind.title}  ${q.progress}/${q.kind.target}${q.kind.unit}", fontSize = 24.sp, color = Ink, fontWeight = FontWeight.Bold)
                            Text(q.kind.detail + " · 자동으로 완료돼요", fontSize = 18.sp, color = Muted)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FUTURE_EVOLUTIONS.forEach { f ->
                            Text("${f.label}\nLv.${f.minLevel} 추후 공개", fontSize = 15.sp, color = Muted, lineHeight = 20.sp, modifier = Modifier.background(Color(0xFFF7EAEE), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = onEvolve, enabled = data.canEvolve, modifier = Modifier.height(72.dp)) {
                            Text(when {
                                !data.canEvolve -> "조건을 채우면 진화할 수 있어요"
                                next == Form.EVO1 -> "계열을 골라 진화하기"
                                else -> "알에서 깨어나기"
                            }, fontSize = 24.sp)
                        }
                        Button(onClick = onClose, modifier = Modifier.height(72.dp)) { Text("닫기", fontSize = 24.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConditionRow(label: String, value: String, progress: Float, color: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row {
            Text(label, fontSize = 20.sp, color = Ink)
            Spacer(Modifier.weight(1f))
            Text(value, fontSize = 20.sp, color = Ink, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        SoftBar(progress, color, Modifier.fillMaxWidth())
    }
}

/** 계열 진화 1에서 다섯 계열 중 하나를 고른다. 성능 차이는 없고 한 번만 고른다. */
@Composable
fun LineagePicker(onPick: (Lineage) -> Unit, onCancel: () -> Unit) {
    var selected by remember { mutableStateOf<Lineage?>(null) }
    FullScrim {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("어떤 모습으로 자랄까요?", fontSize = 32.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Text("한 번 고르면 바꿀 수 없어요. 모습만 다르고 능력 차이는 없어요.", fontSize = 20.sp, color = Color.White)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Lineage.entries.forEach { l ->
                    val on = selected == l
                    Column(
                        Modifier
                            .width(216.dp)
                            .background(if (on) Blush else Cream, RoundedCornerShape(26.dp))
                            .border(if (on) 5.dp else 2.dp, if (on) Rose else Color(0x55EF7F9E), RoundedCornerShape(26.dp))
                            .clickable { selected = l }
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CarlingPicture(lineageArt(l), 180.dp, shadow = false)
                        Text(l.carlingName, fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold)
                        Text("${l.label} 계열", fontSize = 18.sp, color = Muted)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            selected?.let { Speech(it.tagline) }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { selected?.let(onPick) }, enabled = selected != null, colors = ButtonDefaults.buttonColors(disabledContainerColor = Blush, disabledContentColor = Muted), modifier = Modifier.height(72.dp)) { Text("이 모습으로 진화", fontSize = 24.sp) }
                Button(onClick = onCancel, modifier = Modifier.height(72.dp)) { Text("다음에", fontSize = 24.sp) }
            }
        }
    }
}

/** 진화 연출: 빛이 모여 터지고 새 모습이 나타난다. 언제 닫아도 진화 결과는 이미 저장돼 있다. */
@Composable
internal fun EvolutionOverlay(from: CarlingArt, to: CarlingArt, title: String, line: String, onDone: () -> Unit) {
    val t = remember { Animatable(0f) }
    var finished by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(3_800, easing = LinearEasing))
        Babble.chirp()
        finished = true
    }
    val p = t.value
    Box(Modifier.fillMaxSize().background(Scrim), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val glow = sin(p * Math.PI.toFloat()).coerceAtLeast(0f)
            val r = size.minDimension * (0.25f + 0.75f * p)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = glow), Color(0xFFFFE9A8).copy(alpha = 0.55f * glow), Color.Transparent), center, r), r, center)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                if (p < 0.6f) {
                    val shake = sin(p * 80f) * (4f + p * 12f)
                    Image(
                        painterResource(from.res),
                        contentDescription = null,
                        modifier = Modifier.size(380.dp).graphicsLayer {
                            rotationZ = shake
                            val s = 1f + p * 0.1f
                            scaleX = s
                            scaleY = s
                            alpha = (1f - ((p - 0.5f) / 0.1f)).coerceIn(0f, 1f)
                        },
                    )
                } else {
                    val q = ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)
                    val scale = 1f + 2.70158f * (q - 1f).pow(3) + 1.70158f * (q - 1f).pow(2)
                    CarlingPicture(to, 380.dp, Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
                }
            }
            if (finished) {
                Spacer(Modifier.height(10.dp))
                Text(title, fontSize = 36.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Speech(line)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDone, modifier = Modifier.height(72.dp)) { Text("좋아요", fontSize = 24.sp) }
            }
        }
    }
}

/** 처음 깨어난 카링에게 이름을 지어준다. */
@Composable
internal fun NameScreen(art: CarlingArt, onName: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    FullScrim {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CarlingPicture(art, 300.dp)
            Speech("안녕!! 깨워줘서 고마워. 나와 함께 가줄래? 내 이름을 지어줄래?")
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(10) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, color = Ink),
                    placeholder = { Text("이름 (최대 10자)", fontSize = 24.sp) },
                    modifier = Modifier.background(Color.White).width(460.dp),
                )
                Spacer(Modifier.width(18.dp))
                Button(onClick = { onName(text.trim()) }, enabled = text.isNotBlank(), colors = ButtonDefaults.buttonColors(disabledContainerColor = Blush, disabledContentColor = Muted), modifier = Modifier.height(72.dp)) { Text("정했어요", fontSize = 26.sp) }
            }
        }
    }
}
