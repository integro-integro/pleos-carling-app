package work.integro.carling.game

import work.integro.carling.data.CarlingStore
import work.integro.carling.data.TripResult

/** 개발자 도구. 여기서 만든 카드는 '시뮬레이션 데이터'로 표시되고 실차 기록과 구분된다. */
object DevSimulator {
    private var sequence = 0L

    /** 유효 운행 1회. [harshEvents]가 0이면 점수 95(보너스 대상). */
    fun validTrip(store: CarlingStore, harshEvents: Int = 0) = trip(store, 8.4f, 22L * 60, harshEvents)

    /** 조건 미달 운행(거리 1.99km). 개발 빌드는 기준이 낮아 실제로는 카드가 생기므로 시뮬레이션에서는 이동시간을 0으로 둔다. */
    fun shortTrip(store: CarlingStore) {
        val end = store.clock.now()
        store.recordTrip(TripResult(end - 60_000L - sequence++, end, 0f, 0L, 0, simulated = true))
    }

    /** 다음 날로 넘겨 그날 [trips]번 달린다. */
    fun nextDay(store: CarlingStore, trips: Int = 2) {
        store.clock.skipDays(1)
        repeat(trips) { validTrip(store) }
    }

    /** 다음 진화 조건이 다 찰 때까지 날을 넘겨 달린다. 진화 자체는 화면에서 누른다. */
    fun fillNextEvolution(store: CarlingStore) {
        var guard = 0
        while (!store.data.canEvolve && store.data.form.next != null && guard++ < 200) nextDay(store)
    }

    private fun trip(store: CarlingStore, km: Float, movingSeconds: Long, harshEvents: Int) {
        val end = store.clock.now()
        store.recordTrip(TripResult(end - movingSeconds * 1000 - sequence++, end, km, movingSeconds, harshEvents, simulated = true))
    }
}
