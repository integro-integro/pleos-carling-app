package work.integro.carling.game

import work.integro.carling.data.CarlingStore

/** 개발자 도구(디버그 빌드 전용). 여기서 만든 운행은 '시뮬레이션 데이터'로 표시된다. */
object DevSimulator {

    /** 지금 막 주차한 유효 운행 하나(임시 카드가 뜬다). */
    fun parkNow(store: CarlingStore, km: Float = 18.4f, minutes: Int = 27, harsh: Int = 0) {
        val now = store.clock.now()
        if (store.data.current != null) return
        store.startTrip(now - minutes * Rules.MINUTE_MILLIS, null, simulated = true)
        store.park(now, km, minutes * 60L, harsh)
    }

    /** 기준 미달 운행(0.6km, 2분): 카드 없이 기록만 남는다. */
    fun shortTrip(store: CarlingStore) {
        val now = store.clock.now()
        if (store.data.current != null) return
        store.startTrip(now - 2 * Rules.MINUTE_MILLIS, null, simulated = true)
        store.park(now, 0.6f, 120L, 0)
        skip(store, Rules.STOP_MERGE_MILLIS)
    }

    /** 시계를 앞으로 돌리고 확정·만료를 판정한다. */
    fun skip(store: CarlingStore, millis: Long) {
        store.clock.skipMillis(millis)
        store.settle()
        store.expireCards()
    }

    /** 지난 며칠 동안 [hour]시에 출발한 운행 [count]개를 완성·확정 상태로 넣는다. 하루 두 개씩. */
    fun pastTrips(store: CarlingStore, count: Int, hour: Int, purpose: TripPurpose, km: Float, minutes: Int, harsh: Int = 0) {
        val today = store.clock.today()
        val zone = java.time.ZoneId.systemDefault()
        repeat(count) { i ->
            val day = today.minusDays((count - i + 1L) / 2 + 1)
            val start = day.atTime(hour, (i % 2) * 20 + 5).atZone(zone).toInstant().toEpochMilli() + i
            store.devPastTrip(start, km + i % 3, minutes + i % 5, harsh, purpose)
        }
    }

    fun morningCommutes(store: CarlingStore) = pastTrips(store, 9, 8, TripPurpose.COMMUTE, 38f, 52)
    fun nightFieldTrips(store: CarlingStore) = pastTrips(store, 6, 22, TripPurpose.FIELD, 14f, 25, harsh = 2)
    fun weekendFamily(store: CarlingStore) {
        val zone = java.time.ZoneId.systemDefault()
        var d = store.clock.today().minusDays(1)
        var n = 0
        while (n < 3) {
            if (d.dayOfWeek.value >= 6) {
                store.devPastTrip(d.atTime(13, 10).atZone(zone).toInstant().toEpochMilli(), 46f, 70, 0, TripPurpose.FAMILY)
                n++
            }
            d = d.minusDays(1)
        }
    }
}
