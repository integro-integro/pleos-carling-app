package work.integro.carling.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.data.CarlingData
import work.integro.carling.data.Personality
import work.integro.carling.game.GrowthRules

/*
 * 서비스 시각화 v1 · 알과의 첫 만남 (FIRST MEETING)
 * 알은 모두에게 하나, 고르는 것은 없다. 첫 운전이 알을 깨우고, 이름을 지어주는 순간 나만의 카링이 된다.
 *   1 탑승·정차   : 당신에게 도착한 알 — 설정도 선택도 없이, 그냥 출발하면 된다
 *   (첫 운전 주행 중: 알이 은은하게 빛날 뿐)
 *   2 도착·주차 후 : 트립카드 #001 → 알을 톡 터치해 깨우기
 *   3 이름 지어주기
 *   4 첫 교감 — 쓰다듬기 (친밀도 퀘스트를 처음 배운다)
 *   5 지도의 첫 불빛, 내일의 약속
 */

@Composable
private fun Step(onBack: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { content() }
        if (onBack != null) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
                Text("‹ 뒤로", fontSize = 22.sp, color = Ink, modifier = Modifier.onScene())
            }
        }
    }
}

/** 1 · 당신에게 도착한 알 — 첫 운전이 알을 깨운다. 누른다고 깨지 않는다. */
@Composable
fun EggArrivalScreen(onDevFirstDrive: (() -> Unit)? = null) = Step {
    val compact = LocalCompact.current
    Spacer(Modifier.height(if (compact) 40.dp else 60.dp))
    EggDrawing(size = if (compact) 280.dp else 360.dp)
    Spacer(Modifier.height(28.dp))
    Text(GrowthRules.EGG_NAME, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.onScene())
    Spacer(Modifier.height(12.dp))
    Speech("미래에서 온 작은 친구가 당신을 기다리고 있어요.")
    Spacer(Modifier.height(14.dp))
    Text(
        "고르는 것도, 설정도 없어요.\n그냥 출발하면, 함께 달린 길이 이 알을 깨워요.",
        fontSize = if (compact) 18.sp else 22.sp,
        color = Ink,
        lineHeight = 32.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.onScene(),
    )
    if (onDevFirstDrive != null) {
        Spacer(Modifier.height(28.dp))
        TextButton(onClick = onDevFirstDrive) { Text("🛠 첫 운전 시뮬레이트", fontSize = 16.sp, color = Ink, modifier = Modifier.onScene()) }
    }
}

/** 2 · 트립카드 #001 — 알을 눌러 금이 가게 하고, 네 번째에 깨어난다. */
@Composable
fun AwakenScreen(data: CarlingData, onWake: () -> Unit) = Step {
    val compact = LocalCompact.current
    var taps by rememberSaveable { mutableIntStateOf(0) }
    Text("TRIP CARD #001", fontSize = 18.sp, color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.onScene())
    Spacer(Modifier.height(12.dp))
    PanelBox(Modifier.widthIn(max = 640.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            data.firstTripPlaceName?.let { TripCardLine("여정", it) }
            val dist = data.firstTripDistanceKm?.takeIf { it > 0.05f }?.let { " · %.1fkm".format(it) } ?: ""
            TripCardLine("함께한 시간", "${data.firstTripMinutes}분$dist")
            TripCardLine("안전운전", "${data.firstTripSafety}점")
            TripCardLine("지도", "새로운 길 1개가 켜졌어요")
        }
    }
    Spacer(Modifier.height(24.dp))
    // 글 길이가 달라도 줄 수가 같아야 알 위치가 움직이지 않아 연달아 누를 수 있다
    Speech(
        when (taps) {
            0 -> "첫 트립카드가 되었어요. 알을 눌러 깨워줄래요?"
            1 -> "어? 알에 금이 갔어요!"
            2 -> "안에서 무언가 움직여요!"
            3 -> "조금만 더! 곧 나올 것 같아요!"
            else -> "톡!"
        },
    )
    Spacer(Modifier.height(16.dp))
    HatchingEgg(
        size = if (compact) 240.dp else 300.dp,
        taps = taps,
        onTap = { taps += 1 },
        onHatched = onWake,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        "${GrowthRules.EGG_NAME}을 눌러 주세요 · 남은 횟수 ${(EGG_TAPS_TO_HATCH - taps).coerceAtLeast(0)}번",
        fontSize = 18.sp,
        color = Ink,
        modifier = Modifier.onScene(),
    )
}

@Composable
private fun TripCardLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 20.sp, color = Muted, modifier = Modifier.width(140.dp))
        Text(value, fontSize = 20.sp, color = Ink, fontWeight = FontWeight.Medium)
    }
}

/** 3 · 이름 지어주기 — 이름을 부르면 반응하기 시작하고, 이때부터 나만의 카링이 된다. */
@Composable
fun NameScreen(personality: Personality, onName: (String) -> Unit, onBack: (() -> Unit)? = null) = Step(onBack) {
    var text by remember { mutableStateOf("") }
    val compact = LocalCompact.current
    CarlingDrawing(if (compact) 240.dp else 300.dp, mood = personality)
    Spacer(Modifier.height(16.dp))
    Speech("안녕… 나랑 같이 가줄래? 내 이름을 지어줄래?")
    Spacer(Modifier.height(28.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(10) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, color = Ink),
            placeholder = { Text("이름 (최대 10자)", fontSize = 24.sp) },
            modifier = Modifier.background(Panel.copy(alpha = 0.92f)).width(if (compact) 360.dp else 480.dp),
        )
        Spacer(Modifier.width(20.dp))
        Button(onClick = { onName(text.trim()) }, enabled = text.isNotBlank(), modifier = Modifier.height(72.dp)) {
            Text("정했어요", fontSize = 26.sp)
        }
    }
}

/** 4 · 첫 교감 — 처음 쓰다듬으면 카링이 반응한다. 친밀도 퀘스트를 여기서 처음 배운다. */
@Composable
fun FirstPetScreen(name: String, personality: Personality, onPet: () -> Unit, onBack: (() -> Unit)? = null) = Step(onBack) {
    val compact = LocalCompact.current
    var petted by remember { mutableStateOf(false) }
    CarlingDrawing(
        size = if (compact) 240.dp else 300.dp,
        mood = personality,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { petted = true },
        ),
    )
    Spacer(Modifier.height(16.dp))
    if (!petted) {
        Speech("'$name'! 정말 마음에 들어. 한 번 쓰다듬어 줄래요?")
        Spacer(Modifier.height(12.dp))
        Text("카링을 톡 쓰다듬어 보세요", fontSize = 20.sp, color = Ink, modifier = Modifier.onScene())
    } else {
        Speech("와아, 간지러워! 이렇게 같이 지내는 거예요.")
        Spacer(Modifier.height(10.dp))
        Text("+5 XP · 오늘 첫 교감", fontSize = 22.sp, color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.onScene())
        Spacer(Modifier.height(18.dp))
        val quests = listOf("쓰다듬기" to "💗", "밥 주기" to "🍙", "놀아주기" to "🎈", "말 걸기" to "💬")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(max = 720.dp)) {
            quests.forEach { (title, icon) ->
                PanelBox(Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(icon, fontSize = 30.sp)
                        Text(title, fontSize = 18.sp, color = Ink, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPet, modifier = Modifier.height(64.dp).width(240.dp)) { Text("좋아요", fontSize = 24.sp) }
    }
}

/** 5 · 지도의 첫 불빛, 내일의 약속 — 어두웠던 지도에 오늘 달린 길 하나가 켜진다. */
@Composable
fun FirstMapScreen(name: String, personality: Personality, onDone: () -> Unit, onBack: (() -> Unit)? = null) = Step(onBack) {
    val compact = LocalCompact.current
    CarlingDrawing(if (compact) 200.dp else 240.dp, mood = personality)
    Spacer(Modifier.height(8.dp))
    Speech("어두웠던 지도에 오늘 달린 길 하나가 켜졌어요. 제가 자라는 방법을 알려줄게요.")
    Spacer(Modifier.height(24.dp))
    val tips = listOf(
        "함께 달리기" to "운전이 끝날 때마다 트립카드 한 장",
        "기억 저장" to "오늘의 길을 지도에 남기면 지도가 켜짐",
        "매일 교감" to "쓰다듬기·밥 주기·놀아주기·말 걸기",
        "천천히, 함께" to "거리가 아니라 함께한 횟수로 자라요",
    )
    val rows = if (compact) tips.chunked(2) else listOf(tips)
    rows.forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.widthIn(max = 1200.dp).padding(bottom = 16.dp)) {
            row.forEach { (title, sub) ->
                PanelBox(Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Gold, textAlign = TextAlign.Center)
                        Text(sub, fontSize = 18.sp, color = Muted, textAlign = TextAlign.Center, lineHeight = 26.sp)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Text("내일은 어떤 길을 같이 달릴까요?", fontSize = 22.sp, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.onScene())
    Spacer(Modifier.height(24.dp))
    Button(onClick = onDone, modifier = Modifier.height(64.dp).width(240.dp)) { Text("알겠어요", fontSize = 24.sp) }
}

/** 위치 사용 동의 — 기획안 데이터 원칙: 위치 활용은 이용자 동의 기반. */
@Composable
fun LocationConsentScreen(personality: Personality, onAnswer: (Boolean) -> Unit, onBack: (() -> Unit)? = null) = Step(onBack) {
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
            ConsentLine("허용하지 않으면", "카링은 똑같이 잘 자라요. 지도만 조금 덜 밝아져요.")
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
