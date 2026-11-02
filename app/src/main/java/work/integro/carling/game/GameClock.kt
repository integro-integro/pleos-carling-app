package work.integro.carling.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 게임 시계. 데모·테스트를 위해 '며칠 뒤'로 건너뛸 수 있다.
 * 출시 빌드에서는 offset을 쓰지 않는다.
 */
class GameClock(context: Context) {

    private val prefs = context.getSharedPreferences("carling_clock", Context.MODE_PRIVATE)
    private val zone: ZoneId = ZoneId.systemDefault()

    var offsetMillis by mutableLongStateOf(prefs.getLong(KEY_OFFSET, 0L))
        private set

    fun now(): Long = System.currentTimeMillis() + offsetMillis

    fun today(): LocalDate = dateOf(now())

    fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun hourOf(millis: Long): Int = Instant.ofEpochMilli(millis).atZone(zone).hour

    fun skipDays(days: Int) {
        offsetMillis += days * DAY_MILLIS
        prefs.edit().putLong(KEY_OFFSET, offsetMillis).apply()
    }

    fun reset() {
        offsetMillis = 0L
        prefs.edit().clear().apply()
    }

    companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        private const val KEY_OFFSET = "offset"
    }
}
