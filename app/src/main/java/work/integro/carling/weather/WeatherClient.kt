package work.integro.carling.weather

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import work.integro.carling.game.WeatherReport
import work.integro.carling.game.WeatherRules
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private const val TAG = "WeatherClient"
private const val TIMEOUT_MS = 4_000

/** Open-Meteo에서 오늘 날씨를 받는다. 키가 필요 없다. 위치는 약 10km 단위로 어림해서 보낸다. */
class WeatherClient {

    /** 실패하면 null. */
    suspend fun fetch(lat: Double, lng: Double): WeatherReport? = withContext(Dispatchers.IO) {
        runCatching {
            val query = String.format(
                Locale.US,
                "latitude=%.1f&longitude=%.1f&current=weather_code&daily=weather_code,precipitation_probability_max&forecast_days=1&timezone=auto",
                WeatherRules.coarse(lat),
                WeatherRules.coarse(lng),
            )
            val connection = URL("https://api.open-meteo.com/v1/forecast?$query").openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            try {
                connection.inputStream.bufferedReader().use { WeatherRules.parse(it.readText()) }
            } finally {
                connection.disconnect()
            }
        }.onFailure { Log.w(TAG, "날씨를 받지 못함: ${it.message}") }.getOrNull()
    }
}
