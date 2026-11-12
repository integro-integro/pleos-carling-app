package work.integro.carling.game

import java.time.LocalDate

/** 시간 규칙(5분·24시간·7일)을 시험하고 데모에서 시간을 건너뛸 수 있게 주입하는 시계. */
interface Clock {
    fun now(): Long
    fun dateOf(millis: Long): LocalDate
    fun hourOf(millis: Long): Int
    fun today(): LocalDate = dateOf(now())
    fun skipMillis(millis: Long)
    fun reset()
}
