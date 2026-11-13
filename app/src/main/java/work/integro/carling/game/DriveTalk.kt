package work.integro.carling.game

/*
 * 주행 중 카링 한마디(사용자 요청 11/12): 내비게이션처럼 짧게, 차량 TTS로만 말한다(화면 조작 없음).
 * 상황이 생길 때만, 한마디 사이 최소 5분, 같은 말은 한 운행에 한 번(거리 축하는 50km마다).
 * 단계가 자랄수록 할 줄 아는 말이 늘어난다 — 성장 리포트의 '새로 생긴 기능'에 쓰인다.
 */
enum class Talk(val label: String, val minStage: Stage, val line: String, val why: String) {
    SLOW_DOWN("천천히 가 줘", Stage.CRACKED, "앗, 깜짝이야! 조금만 천천히 가 줘.", "급가속·급감속을 했을 때"),
    HUNGRY("배고파, 밥 줘", Stage.CRACKED, "배고파… 밥 줘! 가까운 곳에서 충전하고 가자.", "배터리(연료)가 20% 아래일 때"),
    TOO_FAST("너무 빨라", Stage.BABY, "너무 빨라, 무서워… 속도를 조금만 줄여 줘.", "시속 110km를 넘었을 때"),
    CARSICK("멀미 나, 쉬어 가자", Stage.BABY, "멀미 날 것 같아… 다음 휴게소에서 쉬어 가자.", "2시간 넘게 달렸을 때"),
    SLEEPY("졸리면 말해 줘", Stage.BREEZE, "밤이 깊었어. 졸리면 꼭 쉬어 가자.", "밤 0~5시에 30분 넘게 달릴 때"),
    SMOOTH("부드러워서 좋아", Stage.BREEZE, "부드럽게 잘 달리고 있어. 기분 좋아!", "30분 동안 급조작이 없을 때"),
    MILESTONE("벌써 50km야", Stage.MYCARLING, "벌써 50km 왔어! 같이 오니까 금방이다.", "50km마다"),
    ;

    companion object {
        fun known(stage: Stage): List<Talk> = entries.filter { stage.ordinal >= it.minStage.ordinal }
    }
}

/** 지금 운행 상황(2초마다 갱신). 모르는 값은 null. */
data class DriveSnapshot(
    val now: Long,
    val startedAt: Long,
    val km: Float?,
    val speedKmh: Float?,
    val harsh: Int?,
    val lastHarshAt: Long?,
    val energyPercent: Int?,
    val hour: Int,
)

/** 이번 운행에서 한 말. */
data class TalkMemory(val lastAt: Long? = null, val said: Set<Talk> = emptySet(), val milestones: Int = 0)

object DriveTalkRules {
    const val GAP_MILLIS = 5 * 60_000L
    const val LOW_ENERGY = 20
    const val FAST_KMH = 110f
    const val CARSICK_MIN = 120
    const val SMOOTH_MIN = 30
    const val MILESTONE_KM = 50f

    /**
     * 지금 할 말. 없으면 null. 우선순위: 안전(천천히·너무 빨라·졸림·멀미) → 배고픔 → 칭찬 → 거리.
     * [gap]은 개발 빌드에서 줄인다.
     */
    fun next(s: DriveSnapshot, stage: Stage, mem: TalkMemory, gap: Long = GAP_MILLIS, prevHarsh: Int? = null): Talk? {
        if (mem.lastAt != null && s.now - mem.lastAt < gap) return null
        val minutes = (s.now - s.startedAt) / 60_000
        val candidates = buildList {
            if (s.harsh != null && prevHarsh != null && s.harsh > prevHarsh) add(Talk.SLOW_DOWN)
            if ((s.speedKmh ?: 0f) >= FAST_KMH) add(Talk.TOO_FAST)
            if (s.hour in 0..4 && minutes >= 30) add(Talk.SLEEPY)
            if (minutes >= CARSICK_MIN) add(Talk.CARSICK)
            if ((s.energyPercent ?: 100) < LOW_ENERGY) add(Talk.HUNGRY)
            val calmSince = s.lastHarshAt ?: s.startedAt
            if (s.harsh != null && s.now - calmSince >= SMOOTH_MIN * 60_000L) add(Talk.SMOOTH)
        }
        candidates.firstOrNull { it !in mem.said && stage.ordinal >= it.minStage.ordinal }?.let { return it }
        val km = s.km ?: return null
        if (stage.ordinal >= Talk.MILESTONE.minStage.ordinal && km >= MILESTONE_KM * (mem.milestones + 1)) return Talk.MILESTONE
        return null
    }

    fun remember(mem: TalkMemory, t: Talk, now: Long): TalkMemory =
        mem.copy(lastAt = now, said = mem.said + t, milestones = if (t == Talk.MILESTONE) mem.milestones + 1 else mem.milestones)

    /** 50km 축하 문구(100km, 150km …). */
    fun lineOf(t: Talk, mem: TalkMemory): String =
        if (t == Talk.MILESTONE) "벌써 ${((mem.milestones + 1) * MILESTONE_KM).toInt()}km 왔어! 같이 오니까 금방이다." else t.line
}
