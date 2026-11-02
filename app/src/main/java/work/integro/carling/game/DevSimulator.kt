package work.integro.carling.game

import work.integro.carling.data.CarlingStore
import work.integro.carling.data.TripResult
import java.time.ZoneId

/**
 * 개발·데모용: 하루를 건너뛰고 가상 주행을 기록한다.
 * 30일 진화를 시연 영상에서 바로 보여주기 위한 도구 — 출시 빌드에서는 숨긴다.
 */
class DevSimulator(private val store: CarlingStore) {

    private val clock get() = store.clock
    private var exploreSeq = 0

    /** 다음 날로 넘어가 [light] 시간대에 20분 주행한 것으로 기록. */
    fun simulateDays(count: Int, light: Light, explorer: Boolean) {
        repeat(count) {
            clock.skipDays(1)
            val start = clock.today()
                .atTime(representativeHour(light), 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val cell = if (explorer) "sim-new-${clock.today()}-${exploreSeq++}" else "sim-home"
            store.recordTrip(
                TripResult(
                    startMillis = start,
                    endMillis = start + 20 * 60 * 1000,
                    distanceKm = 12f,
                    harshEvents = 0,
                    placeCell = cell,
                    placeName = if (explorer) "새로운 동네" else "늘 가던 곳",
                ),
            )
        }
    }

    private fun representativeHour(light: Light) = when (light) {
        Light.DAWN -> 7
        Light.DAY -> 12
        Light.SUNSET -> 18
        Light.STAR -> 22
    }
}
