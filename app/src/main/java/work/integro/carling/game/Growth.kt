package work.integro.carling.game

import kotlinx.serialization.Serializable

/** 빛 — 언제 함께 달렸나 (기획안 4장 축 1). 시동 시각만 쓴다. */
@Serializable
enum class Light(val label: String, val color: Long) {
    // 설계서 '축 1 · 빛' 색
    DAWN("새벽빛", 0xFF9DB8FF),
    DAY("햇살빛", 0xFFFFE08A),
    SUNSET("노을빛", 0xFFFFA07A),
    STAR("별빛", 0xFFB39DFF);

    /** 질문 2용 시간대 설명. */
    val hours: String
        get() = when (this) {
            DAWN -> "04~09시"
            DAY -> "09~16시"
            SUNSET -> "16~20시"
            STAR -> "20~04시"
        }

    companion object {
        /** 04~09 새벽 / 09~16 햇살 / 16~20 노을 / 20~04 별빛 */
        fun ofHour(hour: Int): Light = when (hour) {
            in 4..8 -> DAWN
            in 9..15 -> DAY
            in 16..19 -> SUNSET
            else -> STAR
        }
    }
}

/** 길 — 어떻게 달렸나 (기획안 4장 축 2). */
@Serializable
enum class Path(val label: String) {
    REGULAR("단골"),
    EXPLORER("탐험"),
}

/** 성장 단계. 거리 아닌 '함께한 날' 기준. */
enum class Stage(val label: String) {
    EGG("알"),
    BABY("아기 카링"),
    GROWN("성장한 카링"),
    EVOLVED("나만의 카링"),
}

@Serializable
data class Evolution(val light: Light, val path: Path, val reason: String)

object GrowthRules {
    const val GROWN_DAYS = 7
    const val EVOLVE_DAYS = 30

    /** 하루에 보상받는 주행 수 상한 — 많이 달린 사람이 유리하지 않게. */
    const val DAILY_TRIP_REWARDS = 3
    const val TRIP_INTIMACY = 5
    const val GREET_INTIMACY = 3

    /** 쓰다듬기(터치) 보상 — 하루 상한이 있어 연타로 키울 수 없다. */
    const val PET_INTIMACY = 1
    const val DAILY_PETS = 5
    const val DAILY_SMOOTH_PRAISE = 3
    const val FEED_FULLNESS = 20
    const val CHARGE_FULLNESS = 30
    const val DAILY_FULLNESS_DECAY = 10

    /** 펫은 죽거나 퇴화하지 않는다 — 포만감은 이 아래로 내려가지 않는다. */
    const val MIN_FULLNESS = 20

    /** 이보다 짧은 P→D→P 전환은 주행으로 치지 않는다 (주차 중 기어 조작). */
    const val MIN_TRIP_SECONDS = 5L

    fun stage(hatched: Boolean, togetherDays: Int): Stage = when {
        !hatched -> Stage.EGG
        togetherDays >= EVOLVE_DAYS -> Stage.EVOLVED
        togetherDays >= GROWN_DAYS -> Stage.GROWN
        else -> Stage.BABY
    }

    /** 30일간 함께한 주행 시간 비중이 가장 큰 시간대. 동률이면 enum 순서. */
    fun dominantLight(minutes: Map<Light, Int>): Light =
        Light.entries.maxBy { minutes[it] ?: 0 }

    /** 처음 간 곳이 다시 찾은 곳보다 많으면 탐험. 위치 데이터가 없으면 단골. */
    fun path(newPlaces: Int, revisits: Int): Path =
        if (newPlaces > revisits) Path.EXPLORER else Path.REGULAR

    /** 보상에는 이유를 보여준다 (게임 정책 5). */
    fun evolutionReason(light: Light, lightTrips: Int, path: Path, newPlaces: Int, revisits: Int): String {
        val lightPart = "${light.label} 시간에 ${lightTrips}번이나 같이 달려서 제 몸에 ${light.label}이 번졌어요!"
        val pathPart = when (path) {
            Path.REGULAR ->
                if (revisits > 0) "다시 찾은 곳이 ${revisits}번이라 포근한 머플러가 생겼어요."
                else "늘 같은 길을 함께해서 포근한 머플러가 생겼어요."
            Path.EXPLORER -> "처음 간 곳이 ${newPlaces}곳이라 별 지도 배낭이 생겼어요."
        }
        return "$lightPart $pathPart"
    }

    fun evolve(
        lightMinutes: Map<Light, Int>,
        lightTrips: Map<Light, Int>,
        newPlaces: Int,
        revisits: Int,
    ): Evolution {
        val light = dominantLight(lightMinutes)
        val path = path(newPlaces, revisits)
        val reason = evolutionReason(light, lightTrips[light] ?: 0, path, newPlaces, revisits)
        return Evolution(light, path, reason)
    }

    /** 급가속·급감속 판정: 1초 안에 속도가 이만큼(m/s) 바뀌면 급조작. 실차에서 재보정 필요. */
    const val HARSH_DELTA_MPS = 3.0f
}
