package work.integro.carling.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.data.Personality
import work.integro.carling.data.Routine
import work.integro.carling.game.Light

/*
 * 설계서 '카링과의 첫 5분' (모두 정차 중):
 * 알과의 첫 만남 → 첫 성격 고르기 + 짧은 질문 2개 → 이름 → 카링이 자라는 방법 → 위치 동의
 */

@Composable
private fun Step(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
fun EggScreen(onHatch: () -> Unit) = Step {
    val compact = LocalCompact.current
    Spacer(Modifier.height(if (compact) 60.dp else 80.dp))
    EggDrawing(
        size = if (compact) 300.dp else 380.dp,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onHatch,
        ),
    )
    Spacer(Modifier.height(24.dp))
    Speech("누군가 나를 깨워주길 기다리고 있었어요.")
    Spacer(Modifier.height(12.dp))
    Text("알을 눌러 깨워주세요", fontSize = 22.sp, color = Muted)
}

@Composable
fun PersonalityScreen(onChoose: (Personality) -> Unit) = Step {
    val compact = LocalCompact.current
    Speech("저는 어떤 친구면 좋겠어요?")
    Spacer(Modifier.height(32.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 32.dp),
        modifier = Modifier.widthIn(max = 1200.dp),
    ) {
        Personality.entries.forEach { p ->
            Surface(
                onClick = { onChoose(p) },
                shape = RoundedCornerShape(28.dp),
                color = Panel,
                modifier = Modifier
                    .weight(1f)
                    .border(2.dp, p.color.copy(alpha = 0.6f), RoundedCornerShape(28.dp)),
            ) {
                Column(Modifier.padding(if (compact) 12.dp else 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CarlingDrawing(if (compact) 150.dp else 230.dp, mood = p)
                    Text(p.label, fontSize = if (compact) 22.sp else 30.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Spacer(Modifier.height(6.dp))
                    Text(p.description, fontSize = if (compact) 16.sp else 22.sp, color = Muted, textAlign = TextAlign.Center)
                }
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    Text("성격은 말투와 표정만 정해요. 모습은 함께한 경험이 정해요.", fontSize = 20.sp, color = Muted, textAlign = TextAlign.Center)
}

/** 짧은 질문 2개 — 초기 관심사. 진화 판정에는 쓰지 않고 대화와 '예상 빛' 안내에만 쓴다. */
@Composable
fun QuestionsScreen(
    personality: Personality,
    routine: Routine?,
    usualLight: Light?,
    onRoutine: (Routine) -> Unit,
    onLight: (Light) -> Unit,
    onNext: () -> Unit,
) = Step {
    val compact = LocalCompact.current
    CarlingDrawing(if (compact) 220.dp else 260.dp, mood = personality)
    Spacer(Modifier.height(12.dp))
    Speech(routine?.reply ?: "주로 어떤 길을 달려요?")
    Spacer(Modifier.height(20.dp))
    ChoiceRow(Routine.entries, routine, { it.label }, onRoutine)
    Spacer(Modifier.height(28.dp))
    Text("언제 주로 달려요?", fontSize = 24.sp, color = Ink, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    ChoiceRow(Light.entries, usualLight, { it.hours }, onLight)
    Spacer(Modifier.height(28.dp))
    Button(onClick = onNext, enabled = routine != null && usualLight != null, modifier = Modifier.height(64.dp).width(240.dp)) {
        Text("다음", fontSize = 24.sp)
    }
}

@Composable
private fun <T> ChoiceRow(items: List<T>, selected: T?, label: (T) -> String, onPick: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick = { onPick(item) },
                label = { Text(label(item), fontSize = 20.sp, modifier = Modifier.padding(vertical = 10.dp)) },
            )
        }
    }
}

@Composable
fun NameScreen(personality: Personality, onName: (String) -> Unit) = Step {
    var text by remember { mutableStateOf("") }
    val compact = LocalCompact.current
    CarlingDrawing(if (compact) 260.dp else 320.dp, mood = personality)
    Spacer(Modifier.height(16.dp))
    Speech("제 이름을 지어줄래요?")
    Spacer(Modifier.height(28.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(10) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, color = Ink),
            placeholder = { Text("이름", fontSize = 26.sp) },
            modifier = Modifier.width(if (compact) 360.dp else 480.dp),
        )
        Spacer(Modifier.width(20.dp))
        Button(onClick = { onName(text.trim()) }, enabled = text.isNotBlank(), modifier = Modifier.height(72.dp)) {
            Text("좋아요", fontSize = 26.sp)
        }
    }
}

/** 카링이 자라는 방법 — 성장 규칙을 카링이 직접 알려준다. */
@Composable
fun GuideScreen(name: String, personality: Personality, usualLight: Light?, onDone: () -> Unit) = Step {
    val compact = LocalCompact.current
    CarlingDrawing(if (compact) 200.dp else 240.dp, mood = personality)
    Speech("'$name'! 이름이 마음에 들어요. 제가 자라는 방법을 알려줄게요.")
    Spacer(Modifier.height(24.dp))
    val tips = listOf(
        "함께한 날" to "짧은 출근길도 하루",
        "부드러운 운전" to "감점 없이, 칭찬만",
        "새로운 곳" to "처음 간 장소",
        "하루 첫 인사" to "만나면 밥 한 그릇",
    )
    val rows = if (compact) tips.chunked(2) else listOf(tips)
    rows.forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.widthIn(max = 1200.dp).padding(bottom = 16.dp)) {
            row.forEach { (title, sub) ->
                PanelBox(Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Gold)
                        Text(sub, fontSize = 20.sp, color = Muted)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    usualLight?.let {
        Text("${it.label} 시간에 자주 만나면, 제 몸에 ${it.label}이 번질지도 몰라요.", fontSize = 22.sp, color = Ink, textAlign = TextAlign.Center)
    }
    Text("멀리 가지 않아도 돼요. 매일 만나면 충분해요.", fontSize = 20.sp, color = Muted)
    Spacer(Modifier.height(24.dp))
    Button(onClick = onDone, modifier = Modifier.height(64.dp).width(240.dp)) { Text("알겠어요", fontSize = 24.sp) }
}

/** 위치 사용 동의 — 기획안 데이터 원칙: 위치 활용은 이용자 동의 기반. */
@Composable
fun LocationConsentScreen(personality: Personality, onAnswer: (Boolean) -> Unit) = Step {
    val compact = LocalCompact.current
    CarlingDrawing(if (compact) 200.dp else 240.dp, mood = personality)
    Speech("처음 가는 곳과 자주 가는 곳을 알아보고 싶어요. 위치를 써도 될까요?")
    Spacer(Modifier.height(24.dp))
    PanelBox(Modifier.widthIn(max = 820.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ConsentLine("어디에 쓰나요", "주행이 끝날 때 한 번, '처음 간 곳 / 다시 찾은 곳'을 판정하는 데만 써요.")
            ConsentLine("무엇을 남기나요", "정확한 좌표는 저장하지 않아요. 약 200m 구역을 알아볼 수 없게 바꾼 표시만 기기에 남겨요.")
            ConsentLine("어디로 보내나요", "서버로 보내지 않아요. 모든 기록은 이 차 안에만 있어요.")
            ConsentLine("언제든 바꿀 수 있어요", "홈 화면에서 끄면 그때부터 위치를 쓰지 않아요.")
            ConsentLine("허용하지 않으면", "카링은 똑같이 잘 자라요. 길 모양만 '단골'로 정해져요.")
            ConsentLine("시스템 창이 뜨면", "'허용'을 눌러 주세요. 부드러운 운전 칭찬에 쓰는 차량 속도도 시스템에서는 '위치' 권한으로 함께 묻어요.")
        }
    }
    Spacer(Modifier.height(28.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(onClick = { onAnswer(false) }, modifier = Modifier.height(64.dp).width(240.dp)) {
            Text("허용하지 않을게요", fontSize = 22.sp)
        }
        Button(onClick = { onAnswer(true) }, modifier = Modifier.height(64.dp).width(240.dp)) {
            Text("허용할게요", fontSize = 22.sp)
        }
    }
}

@Composable
private fun ConsentLine(title: String, body: String) {
    Row {
        Text(title, fontSize = 20.sp, color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.width(200.dp))
        Text(body, fontSize = 20.sp, lineHeight = 28.sp, color = Ink, modifier = Modifier.weight(1f))
    }
}
