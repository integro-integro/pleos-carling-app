package work.integro.carling.game

import kotlinx.serialization.Serializable

/**
 * 차량 돌봄 — 차가 아프면 카링도 아프다. 차량 신호가 이상 상태로 바뀌면 돌봄 퀘스트가 생기고,
 * 신호가 정상으로 돌아오면 자동으로 완료된다(버튼으로 완료하지 않는다).
 * [priority]가 작을수록 급하다. 여러 개면 가장 급한 것 하나만 카링이 표현한다.
 */
@Serializable
enum class CareIssue(
    val priority: Int,
    val icon: String,
    val chip: String,
    val line: String,
    val drivingLine: String,
    val quest: String,
    val how: String,
    val doneLine: String,
) {
    CAR_FAULT(
        0, "🤒", "엔진오일 점검 필요",
        "몸이 아파요… 엔진오일에 문제가 있대요. 정비소에서 고쳐 줄래요?",
        "몸이 좀 아파요… 도착하면 정비소에 가 볼래요?",
        "엔진오일 점검받기",
        "가까운 정비소에서 엔진오일을 점검·교환하면, 차량 신호가 정상으로 바뀌는 순간 퀘스트가 완료돼요.",
        "다 나았어요! 고쳐 줘서 고마워요.",
    ),
    TIRE(
        1, "🛞", "타이어 공기압 낮음",
        "발이 시려요… 타이어 바람이 빠졌대요. 공기압을 채워 줄래요?",
        "타이어 바람이 빠졌대요. 천천히 조심히 가요!",
        "타이어 공기압 채우기",
        "주유소나 정비소에서 공기압을 채우면, 네 바퀴가 모두 정상으로 돌아오는 순간 퀘스트가 완료돼요.",
        "이제 똑바로 걸을 수 있어요! 고마워요.",
    ),
    FUEL(
        2, "🍙", "연료 부족",
        "배고파요… 연료가 얼마 안 남았대요. 주유소(충전소)에 들러 줄래요?",
        "배가 꼬르륵… 주유소(충전소)에 들러 줄래요?",
        "주유하기 · 충전하기",
        "주유하거나 충전해서 연료(배터리)가 넉넉해지면 퀘스트가 완료돼요.",
        "배불러요! 든든해졌어요.",
    ),
}

/** 정비 쿠폰 — 친밀도가 넘쳐 쌓인 마음 포인트로 카링이 주는 선물. 지금은 시연용이다. */
@Serializable
enum class CouponKind(val icon: String, val title: String, val detail: String) {
    OIL_CHANGE("🛢", "엔진오일 교환 할인", "엔진오일 교환 공임 10% 할인"),
    TIRE_CHECK("🛞", "타이어 무상 점검", "공기압·마모 무상 점검"),
    WASHER("💧", "워셔액 무료 보충", "워셔액 1회 무료 보충"),
    AC_FILTER("🌬", "에어컨 필터 교환 할인", "에어컨 필터 교환 20% 할인"),
}

@Serializable
data class Coupon(val id: Long, val kind: CouponKind, val date: String)

object CareRules {
    /** 돌봄 퀘스트를 마치면 오르는 친밀도. 100을 넘는 만큼은 마음 포인트로 쌓인다. */
    const val CARE_INTIMACY = 20
    /** 마음 포인트가 이만큼 모이면 카링이 정비 쿠폰을 하나 선물한다. */
    const val GIFT_HEART = 40

    const val LOW_FUEL_PERCENT = 15
    const val LOW_EV_PERCENT = 20

    /** 타이어 공기압이 낮은지. 값이 100보다 크면 kPa(207kPa ≈ 30psi), 아니면 psi로 본다. */
    fun isLowTire(pressure: Float): Boolean = if (pressure > 100f) pressure < 207f else pressure < 30f

    /** 엔진오일 상태 이름이 이상인지(LOW, CRITICALLY_LOW, ERROR). */
    fun isOilFault(state: String): Boolean = state == "LOW" || state == "CRITICALLY_LOW" || state == "ERROR"
}
