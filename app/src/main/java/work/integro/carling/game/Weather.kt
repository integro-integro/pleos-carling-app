package work.integro.carling.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.round

enum class Sky { CLEAR, CLOUDY, RAIN, SNOW, FOG }

/** 오늘 날씨. [forecast]는 하루 예보, [now]는 지금 하늘. */
data class WeatherReport(val forecast: Sky, val now: Sky)

object WeatherRules {
    /** 비 올 확률이 이 이상이면 예보 코드가 흐림이어도 비 소식으로 본다. */
    const val RAIN_CHANCE_PERCENT = 60

    /** 날씨 서비스로 보내는 위치의 단위(도). 약 10km. */
    private const val COARSE_DEGREES = 0.1

    fun coarse(degree: Double): Double = round(degree / COARSE_DEGREES) * COARSE_DEGREES

    /** WMO 날씨 코드를 카링이 말하는 하늘로 묶는다. */
    fun skyOf(wmoCode: Int): Sky = when (wmoCode) {
        0, 1 -> Sky.CLEAR
        45, 48 -> Sky.FOG
        in 51..67, in 80..82, in 95..99 -> Sky.RAIN
        in 71..77, 85, 86 -> Sky.SNOW
        else -> Sky.CLOUDY
    }

    /** Open-Meteo 응답을 읽는다. 형식이 다르면 null. */
    fun parse(body: String): WeatherReport? = runCatching {
        val root = Json.parseToJsonElement(body).jsonObject
        val daily = root.getValue("daily").jsonObject
        val code = daily.getValue("weather_code").jsonArray[0].jsonPrimitive.int
        val chance = daily["precipitation_probability_max"]?.jsonArray?.getOrNull(0)?.jsonPrimitive?.intOrNull ?: 0
        val now = root.getValue("current").jsonObject.getValue("weather_code").jsonPrimitive.int
        var forecast = skyOf(code)
        if (chance >= RAIN_CHANCE_PERCENT && (forecast == Sky.CLEAR || forecast == Sky.CLOUDY)) forecast = Sky.RAIN
        WeatherReport(forecast, skyOf(now))
    }.getOrNull()

    /** 하루 첫 운전의 인사. 오전에는 오늘 예보, 오후에는 지금 날씨를 말한다. 날씨를 모르면 날씨 이야기를 하지 않는다. */
    fun greeting(hour: Int, report: WeatherReport?): String {
        if (report == null) return "오늘도 함께 안전운전해요!"
        return if (hour < 12) {
            when (report.forecast) {
                Sky.CLEAR -> "오늘은 화창해요! 운전을 즐겨 봐요."
                Sky.CLOUDY -> "오늘은 흐리대요. 제가 즐겁게 해줄게요!"
                Sky.RAIN -> "오늘 비 소식이 있어요. 조심히 운전해요."
                Sky.SNOW -> "오늘 눈 소식이 있어요. 천천히 조심히 가요."
                Sky.FOG -> "오늘 안개 소식이 있어요. 앞을 잘 살펴요."
            }
        } else {
            when (report.now) {
                Sky.CLEAR -> "지금은 맑아요. 오늘도 안전운전해요!"
                Sky.CLOUDY -> "지금은 흐려요. 안전운전해요, 제가 곁에 있을게요!"
                Sky.RAIN -> "지금 비가 와요. 천천히 안전운전해요."
                Sky.SNOW -> "지금 눈이 와요. 천천히 안전운전해요."
                Sky.FOG -> "지금 안개가 꼈어요. 앞을 잘 살피며 안전운전해요."
            }
        }
    }
}
