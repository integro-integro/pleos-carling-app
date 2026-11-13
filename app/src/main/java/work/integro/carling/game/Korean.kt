package work.integro.carling.game

/** 한글 조사 고르기: 마지막 글자에 받침이 있는지로 '을/를', '이/가', '와/과', '이에요/예요', '이었/였'을 정한다. */
fun hasBatchim(word: String): Boolean {
    val c = word.trimEnd().lastOrNull() ?: return false
    if (c !in '가'..'힣') return false
    return (c.code - 0xAC00) % 28 != 0
}

/** "휠캡을", "안테나를" */
fun String.eulReul(): String = this + if (hasBatchim(this)) "을" else "를"

/** "살구빛이", "라벤더가" */
fun String.iGa(): String = this + if (hasBatchim(this)) "이" else "가"

/** "여행가방과", "안테나와" */
fun String.waGwa(): String = this + if (hasBatchim(this)) "과" else "와"

/** "휠캡이에요", "안테나예요" */
fun String.ieyo(): String = this + if (hasBatchim(this)) "이에요" else "예요"

/** "출퇴근이었어요", "취미였어요" */
fun String.ieosseoyo(): String = this + if (hasBatchim(this)) "이었어요" else "였어요"
