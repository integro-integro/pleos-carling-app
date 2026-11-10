package work.integro.carling.game

/**
 * 시각으로 정하는 밤의 정도. 0 = 낮, 1 = 밤.
 * 일출(05:30~07:00)과 일몰(18:00~19:30) 동안 서서히 바뀐다.
 * 계절별 일출·일몰은 반영하지 않는다.
 */
fun nightnessAt(hour: Int, minute: Int): Float {
    val t = hour * 60 + minute
    return when {
        t < SUNRISE_START -> 1f
        t < SUNRISE_END -> 1f - (t - SUNRISE_START) / (SUNRISE_END - SUNRISE_START).toFloat()
        t < SUNSET_START -> 0f
        t < SUNSET_END -> (t - SUNSET_START) / (SUNSET_END - SUNSET_START).toFloat()
        else -> 1f
    }
}

private const val SUNRISE_START = 5 * 60 + 30
private const val SUNRISE_END = 7 * 60
private const val SUNSET_START = 18 * 60
private const val SUNSET_END = 19 * 60 + 30

/** 배경 스킨의 네 시간대. 그림은 시간대마다 한 장씩 있다. */
enum class DayPart(val label: String, val startMinute: Int) {
    MORNING("아침", 5 * 60),
    DAY("낮", 9 * 60),
    DUSK("노을", 16 * 60),
    NIGHT("밤", 19 * 60);

    val previous: DayPart get() = entries[(ordinal + entries.size - 1) % entries.size]
}

/** 시간대가 바뀔 때 앞 그림에서 새 그림으로 넘어가는 시간(분). */
private const val DAY_PART_FADE_MINUTES = 30

/**
 * 지금 시간대와, 앞 시간대에서 넘어온 정도(0 = 앞 시간대 그림, 1 = 지금 그림).
 * 각 시간대가 시작되고 30분 동안 서서히 바뀐다.
 */
fun dayPartAt(hour: Int, minute: Int): Pair<DayPart, Float> {
    val t = hour * 60 + minute
    val part = DayPart.entries.lastOrNull { t >= it.startMinute } ?: DayPart.NIGHT
    val since = (t - part.startMinute + 24 * 60) % (24 * 60)
    return part to (since / DAY_PART_FADE_MINUTES.toFloat()).coerceIn(0f, 1f)
}
