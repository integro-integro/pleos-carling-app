package work.integro.carling.data

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/** 첫 선택 성격. 말투와 표정만 바꾸고, 모습은 경험이 정한다(기획안 4장). [colorValue]는 선택 카드 강조색. */
@Serializable
enum class Personality(
    val label: String,
    val description: String,
    val colorValue: Long,
    val greeting: String,
    val drivingLine: String,
    val mealLine: String,
    val arrivalLine: String,
) {
    NARUN(
        label = "포근한 나른",
        description = "느긋하고 다정해요",
        colorValue = 0xFFF2B5A0,
        greeting = "왔구나… 오늘도 같이 있어서 좋아.",
        drivingLine = "천천히, 같이 가요.",
        mealLine = "냠… 따뜻하게 같이 먹자.",
        arrivalLine = "도착했다… 수고 많았어.",
    ),
    DODO(
        label = "활발한 도도",
        description = "호기심 많고 신나요",
        colorValue = 0xFFF7C948,
        greeting = "왔다! 오늘은 어디 가요?",
        drivingLine = "출발이다! 안전하게!",
        mealLine = "밥이다! 냠냠냠!",
        arrivalLine = "도착! 재밌었다!",
    ),
    LUMI(
        label = "차분한 루미",
        description = "조용히 곁을 지켜요",
        colorValue = 0xFF9DB8F2,
        greeting = "다시 만나서 반가워요.",
        drivingLine = "조용히 함께할게요.",
        mealLine = "충전하는 동안 같이 쉬어요.",
        arrivalLine = "잘 도착했어요. 고마워요.",
    );

    val color: Color get() = Color(colorValue)

    /** 눌렀을 때 하는 말. */
    val tapLines: List<String>
        get() = when (this) {
            NARUN -> listOf("으음… 간지러워.", "쓰다듬어 주는 거야? 좋아…", "조금만 더 이렇게 있자.", "하암… 반가워.")
            DODO -> listOf("헤헤, 간지러워요!", "또 해줘요! 또!", "같이 놀아요!", "오늘 기분 최고예요!")
            LUMI -> listOf("앗, 깜짝이야. 반가워요.", "고마워요. 따뜻해요.", "여기 있었어요.", "오늘도 함께해서 좋아요.")
        }
}
