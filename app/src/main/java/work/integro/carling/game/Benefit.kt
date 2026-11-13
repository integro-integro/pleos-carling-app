package work.integro.carling.game

import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/*
 * 생활반경 혜택(기획안 4.15, 11/13 사용자 요청으로 에뮬레이터 적용).
 * 내 동네와 운행 습관으로 차 관련 혜택(충전·주유·주차·세차·정비)을 먼저, 그다음 생활 혜택을 고른다.
 * 매칭은 차 안에서만 한다. 지금 혜택 목록은 모두 '예시 제휴'(가상)이며 실제 가게·브랜드가 아니다.
 */

enum class BenefitKind(val label: String, val icon: String) {
    CHARGE("충전", "⚡"), FUEL("주유", "⛽"), PARKING("주차", "🅿️"), WASH("세차", "🧽"), SERVICE("정비", "🔧"), LIFE("생활", "🏷️")
}

/** 혜택 하나. [dongs]가 비면 어느 동네든. */
data class BenefitOffer(
    val id: String,
    val kind: BenefitKind,
    val dongs: Set<String>,
    val title: String,
    val detail: String,
    val partner: String,
    /** 생활 혜택이 어떤 목적과 이어지는지. */
    val purpose: TripPurpose? = null,
)

/** 고른 혜택과 그 근거(내 기록 한 줄), 할 일 하나. */
data class BenefitMatch(val offer: BenefitOffer, val reason: String, val action: String, val dong: String?, val priority: Int)

/** 혜택 고르기 입력: 확정 운행 한 번. */
data class BenefitTrip(val startedAt: Long, val km: Float?, val purpose: TripPurpose?, val startDong: String?, val endDong: String?)

object BenefitRules {
    /** 닫은 혜택을 다시 보이지 않는 기간. */
    const val SNOOZE_MILLIS = 30L * 24 * 60 * 60 * 1000

    /** 예시 제휴 목록(가상). 시연용 동네: 잠실본동·청담동·삼성동·삼평동·합정동·교문1동. */
    val SAMPLE: List<BenefitOffer> = listOf(
        BenefitOffer("charge-jamsil", BenefitKind.CHARGE, setOf("1124071"), "동네 급속 충전 kWh당 50원 할인", "평일 밤 10시~아침 7시 충전 시", "예시 충전소"),
        BenefitOffer("charge-any", BenefitKind.CHARGE, emptySet(), "첫 충전 3,000원 할인", "제휴 충전소 어디서나 1회", "예시 충전 네트워크"),
        BenefitOffer("fuel-any", BenefitKind.FUEL, emptySet(), "리터당 60원 할인", "제휴 주유소 월 2회", "예시 주유소"),
        BenefitOffer("park-cheongdam", BenefitKind.PARKING, setOf("1123078"), "근처 공영주차장 2시간 무료", "식사·쇼핑 영수증 제시", "예시 주차장"),
        BenefitOffer("park-samsung", BenefitKind.PARKING, setOf("1121082"), "주차 1시간 무료", "주말 오후", "예시 주차장"),
        BenefitOffer("park-pangyo", BenefitKind.PARKING, setOf("3102374"), "업무 지구 월 정기주차 10% 할인", "첫 달", "예시 주차장"),
        BenefitOffer("park-any", BenefitKind.PARKING, emptySet(), "주차 30분 무료", "제휴 주차장 어디서나", "예시 주차 앱"),
        BenefitOffer("wash-jamsil", BenefitKind.WASH, setOf("1124071"), "손세차 20% 할인", "집 근처, 평일", "예시 세차장"),
        BenefitOffer("wash-any", BenefitKind.WASH, emptySet(), "자동 세차 3,000원 할인", "제휴 세차장 어디서나", "예시 세차장"),
        BenefitOffer("service-any", BenefitKind.SERVICE, emptySet(), "타이어·오일 무료 점검", "내 동선 근처 제휴 정비소", "예시 정비소"),
        BenefitOffer("gym-cheongdam", BenefitKind.LIFE, setOf("1123078"), "헬스장 첫 달 30% 할인", "운동하러 자주 가는 동네", "예시 피트니스", TripPurpose.EXERCISE),
        BenefitOffer("gym-any", BenefitKind.LIFE, emptySet(), "웰니스 체험 1회 무료", "제휴 요가·필라테스", "예시 웰니스", TripPurpose.EXERCISE),
        BenefitOffer("food-hapjeong", BenefitKind.LIFE, setOf("1114068"), "맛집 음료 1잔 무료", "2인 이상 식사 시", "예시 식당", TripPurpose.MEAL),
        BenefitOffer("food-any", BenefitKind.LIFE, emptySet(), "제휴 식당 10% 할인", "주차 가능한 식당", "예시 식당", TripPurpose.MEAL),
    )

    /**
     * 지금 보여 줄 혜택 하나. 우선순위: 정비(차량 경고) > 충전·주유 > 주차 > 세차 > 생활.
     * [warnings]는 차량 상태에서 살펴볼 곳(예: "앞 왼쪽 타이어 공기압"). [snoozed]는 닫은 시각.
     */
    fun pick(
        trips: List<BenefitTrip>,
        refuels: List<Long>,
        ev: Boolean,
        warnings: List<String>,
        now: Long,
        zone: ZoneId,
        name: (String) -> String?,
        snoozed: Map<String, Long>,
        catalog: List<BenefitOffer> = SAMPLE,
    ): BenefitMatch? = candidates(trips, refuels, ev, warnings, now, zone, name, catalog)
        .filter { m -> snoozed[m.offer.id]?.let { now - it >= SNOOZE_MILLIS } ?: true }
        .maxByOrNull { it.priority }

    fun candidates(
        trips: List<BenefitTrip>, refuels: List<Long>, ev: Boolean, warnings: List<String>, now: Long, zone: ZoneId,
        name: (String) -> String?, catalog: List<BenefitOffer> = SAMPLE,
    ): List<BenefitMatch> {
        val day = 86_400_000L
        val recent = trips.filter { now - it.startedAt in 0..60 * day }
        fun offer(kind: BenefitKind, dong: String?, purpose: TripPurpose? = null): BenefitOffer? {
            val list = catalog.filter { it.kind == kind && (purpose == null || it.purpose == purpose) }
            return list.firstOrNull { dong != null && dong in it.dongs } ?: list.firstOrNull { it.dongs.isEmpty() }
        }
        fun place(d: String?) = d?.let(name)
        val home = ReachRules.homeOf(recent.map { ReachTrip(it.startedAt, it.km, it.startDong, it.endDong) }, zone.rules.getOffset(Instant.ofEpochMilli(now)).totalSeconds * 1000L)
        val out = mutableListOf<BenefitMatch>()

        // 정비: 차량 상태에 살펴볼 곳이 있으면
        if (warnings.isNotEmpty()) offer(BenefitKind.SERVICE, home)?.let {
            out += BenefitMatch(it, "${warnings.first()}${if (warnings.size > 1) " 외 ${warnings.size - 1}곳" else ""}을 살펴봐야 해요", "다음 주차 때 가까운 곳에서 점검받아요", home, 100)
        }
        // 충전·주유: 기록이 2번 이상
        val recentRefuels = refuels.filter { now - it in 0..60 * day }.sorted()
        if (recentRefuels.size >= 2) {
            val every = recentRefuels.zipWithNext { a, b -> (b - a) / day.toDouble() }.average().roundToInt().coerceAtLeast(1)
            val kind = if (ev) BenefitKind.CHARGE else BenefitKind.FUEL
            offer(kind, home)?.let {
                out += BenefitMatch(it, "약 ${every}일마다 ${kind.label}해요" + (place(home)?.let { h -> ", 주로 $h" } ?: ""), "다음 ${kind.label}은 이 혜택으로", home, 90)
            }
        }
        // 주차: 같은 동네로 식사·장보기·업무 3번 이상
        val errands = setOf(TripPurpose.MEAL, TripPurpose.SHOPPING, TripPurpose.WORK, TripPurpose.DATE)
        recent.filter { it.purpose in errands && it.endDong != null && it.endDong != home }
            .groupBy { it.endDong!! }.filter { it.value.size >= 3 }.maxByOrNull { it.value.size }?.let { (dong, list) ->
                offer(BenefitKind.PARKING, dong)?.let {
                    val what = list.mapNotNull { t -> t.purpose?.label }.distinct().take(2).joinToString("·")
                    out += BenefitMatch(it, "${place(dong) ?: "같은 동네"}에 ${what}(으)로 ${list.size}번 갔어요", "다음에 갈 때 주차를 아껴요", dong, 80)
                }
            }
        // 세차: 최근 2주 장거리(100km+) 또는 주말 합계 150km 이상
        val twoWeeks = trips.filter { now - it.startedAt in 0..14 * day }
        val longest = twoWeeks.maxByOrNull { it.km ?: 0f }
        val weekendKm = twoWeeks.filter { Instant.ofEpochMilli(it.startedAt).atZone(zone).dayOfWeek.value >= 6 }.sumOf { (it.km ?: 0f).toDouble() }
        if ((longest?.km ?: 0f) >= 100f || weekendKm >= 150) offer(BenefitKind.WASH, home)?.let {
            out += BenefitMatch(it, if ((longest?.km ?: 0f) >= 100f) "최근 ${ReportAggregator.km((longest!!.km ?: 0f).toDouble())}를 달렸어요" else "최근 주말에 ${ReportAggregator.km(weekendKm)}를 달렸어요", "먼 길 다녀온 차, 한 번 씻겨 줘요", home, 70)
        }
        // 생활: 운동·식사 카드 3장 이상(같은 동네가 있으면 그 동네)
        listOf(TripPurpose.EXERCISE, TripPurpose.MEAL).forEach { p ->
            val list = recent.filter { it.purpose == p }
            if (list.size < 3) return@forEach
            val dong = list.mapNotNull { it.endDong }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            offer(BenefitKind.LIFE, dong, p)?.let {
                out += BenefitMatch(it, "${p.label} 카드 ${list.size}장" + (place(dong)?.let { d -> ", 주로 $d" } ?: ""), if (p == TripPurpose.EXERCISE) "운동 가는 길에 들러 보세요" else "다음 식사 때 써 보세요", dong, 40 + list.size)
            }
        }
        return out
    }

    /** 트립카드 상세: 이 도착 동네에 딱 맞는(동네 지정) 혜택이 있으면 하나. */
    fun forDong(dong: String?, catalog: List<BenefitOffer> = SAMPLE): BenefitOffer? =
        dong?.let { d -> catalog.firstOrNull { d in it.dongs } }
}
