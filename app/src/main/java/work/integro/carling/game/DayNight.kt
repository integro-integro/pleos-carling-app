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
