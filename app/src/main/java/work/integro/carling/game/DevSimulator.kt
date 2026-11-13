package work.integro.carling.game

import work.integro.carling.data.CarlingStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
        store.publishMonthReport()
    }

    /** 지난 며칠 동안 [hour]시에 출발한 운행 [count]개를 완성·확정 상태로 넣는다. 하루 두 개씩. */
    fun pastTrips(store: CarlingStore, count: Int, hour: Int, purpose: TripPurpose, km: Float, minutes: Int, harsh: Int = 0, from: String? = null, to: List<String> = emptyList()) {
        val today = store.clock.today()
        val zone = java.time.ZoneId.systemDefault()
        repeat(count) { i ->
            val day = today.minusDays((count - i + 1L) / 2 + 1)
            val start = day.atTime(hour, (i % 2) * 20 + 5).atZone(zone).toInstant().toEpochMilli() + i
            store.devPastTrip(start, km + i % 3, minutes + i % 5, harsh, purpose, from = from, to = to.getOrNull(i % to.size.coerceAtLeast(1)))
        }
    }

    // 시연용 동네(행정동 코드): 집 잠실본동, 회사 판교 삼평동
    const val HOME = "1124071"
    const val OFFICE = "3102374"

    fun morningCommutes(store: CarlingStore) = pastTrips(store, 9, 8, TripPurpose.TO_WORK, 38f, 52, from = HOME, to = listOf(OFFICE))
    fun nightFieldTrips(store: CarlingStore) = pastTrips(store, 6, 22, TripPurpose.WORK, 14f, 25, harsh = 2, from = HOME, to = listOf("1123064", "1114066", "1119054"))

    /**
     * 출근 시간 실험(리포트 '꼭 알아야 할 3가지' 시연): 최근 4주 평일 출근 16번. 7:50 출발은 40분 안팎·부드럽게,
     * 8:10 출발은 57분 안팎·급조작 2~3번. 같은 주에 가까운 장보기·식사 4번.
     */
    fun commuteExperiment(store: CarlingStore, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()) {
        val today = store.clock.today()
        var d = today.minusDays(1)
        var n = 0
        while (n < 16) {
            if (d.dayOfWeek.value <= 5) {
                val early = n % 3 == 0
                val start = d.atTime(if (early) 7 else 8, if (early) 50 else 10 + n % 3).atZone(zone).toInstant().toEpochMilli() + n
                store.devPastTrip(start, 38f + n % 3, if (early) 39 + n % 4 else 55 + n % 5, if (early) 0 else 16 + n % 3, TripPurpose.TO_WORK, from = HOME, to = OFFICE)
                n++
            }
            d = d.minusDays(1)
        }
        listOf(2L, 5L, 9L, 12L).forEachIndexed { i, ago ->
            val start = today.minusDays(ago).atTime(19, 30).atZone(zone).toInstant().toEpochMilli() + 100 + i
            store.devPastTrip(start, 4f + i, 12, 0, if (i % 2 == 0) TripPurpose.SHOPPING else TripPurpose.MEAL, from = HOME, to = "1123078")
        }
    }

    /**
     * 동네 나들이: 지난 6주 동안 여러 동네로 다녀온 운행 10개와 위치 동의.
     * 앞의 4주는 '전에 가 본 동네', 최근 2주에 처음 가는 동네가 섞여 있어 새 동네·반경 비교가 보인다.
     */
    fun neighborhoodTrips(store: CarlingStore) {
        store.setLocationConsent(true)
        val zone = java.time.ZoneId.systemDefault()
        val today = store.clock.today()
        listOf(
            Triple(40L, "1123078", 9f), Triple(36L, "1121082", 7f), Triple(33L, OFFICE, 21f), Triple(29L, "1123078", 9f),
            Triple(12L, "1114068", 19f), Triple(9L, "1104071", 11f), Triple(6L, "3101356", 37f), Triple(5L, "1121082", 7f),
            Triple(3L, "2304062", 52f), Triple(2L, "1123078", 9f),
        ).forEachIndexed { i, (ago, to, km) ->
            val start = today.minusDays(ago).atTime(10 + i % 4, 15).atZone(zone).toInstant().toEpochMilli() + i
            store.devPastTrip(start, km, (km * 1.6f).toInt() + 8, 0, if (i % 3 == 0) TripPurpose.MEAL else TripPurpose.HOBBY, from = HOME, to = to)
            // 돌아오는 길(집 도착)
            store.devPastTrip(start + 4 * 60 * Rules.MINUTE_MILLIS, km, (km * 1.6f).toInt() + 8, 0, TripPurpose.OTHER, from = to, to = HOME)
        }
    }
    fun weekendFamily(store: CarlingStore, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()) {
        var d = store.clock.today().minusDays(1)
        var n = 0
        while (n < 3) {
            if (d.dayOfWeek.value >= 6) {
                store.devPastTrip(d.atTime(13, 10).atZone(zone).toInstant().toEpochMilli(), 46f, 70, 0, if (n % 2 == 0) TripPurpose.MEAL else TripPurpose.SHOPPING, work.integro.carling.game.Companion.FAMILY, from = HOME, to = if (n % 2 == 0) "1114068" else "1104071")
                n++
            }
            d = d.minusDays(1)
        }
    }

    // ---- 60초 시연 (기획안 0장 '60초 시연 흐름') ----

    /**
     * 시연 준비: 처음부터 다시 → 시계를 **이번 달 마지막 평일** 아침 7:48로 → 지난 4주 출근 16번·장보기·주말 나들이·충전 기록 →
     * 위치 동의 → 쌓인 상자와 축하 창을 미리 정리 → 다음 한 번의 주행을 [DemoDrive]로 연출.
     * 시연 주행 뒤 [closeMonth]로 달을 넘기면 '짜잔' 월간 리포트가 나온다.
     */
    fun prepareDemo(store: CarlingStore, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()) {
        store.devReset()
        val day = demoDay(store.clock.today())
        val target = day.atTime(DemoDrive.START_HOUR, DemoDrive.START_MINUTE).atZone(zone).toInstant().toEpochMilli()
        store.clock.skipMillis(target - store.clock.now())
        commuteExperiment(store, zone)
        weekendFamily(store, zone)
        store.devRefuels()
        store.setLocationConsent(true)
        store.devTidy()
        DemoDrive.armed = true
    }

    /** 이번 달 마지막 평일. 이미 지났으면(달 끝 주말) 다음 달 마지막 평일. */
    fun demoDay(today: java.time.LocalDate): java.time.LocalDate {
        fun lastWeekday(m: java.time.YearMonth): java.time.LocalDate {
            var d = m.atEndOfMonth()
            while (d.dayOfWeek.value >= 6) d = d.minusDays(1)
            return d
        }
        val m = java.time.YearMonth.from(today)
        return lastWeekday(m).takeIf { !it.isBefore(today) } ?: lastWeekday(m.plusMonths(1))
    }

    /** 한 달 마감(시연): 다음 달 1일 아침 9시로 넘기고 지난달 리포트를 만든다('짜잔' 알림). */
    fun closeMonth(store: CarlingStore, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()) {
        val first = java.time.YearMonth.from(store.clock.today()).plusMonths(1).atDay(1)
        val target = first.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        if (target > store.clock.now()) skip(store, target - store.clock.now())
    }

    /**
     * 시연 주행의 주차: 시계를 출발 + 41분으로 옮기고 38.6km·급조작 1번으로 주차, 동네는 집 → 회사,
     * 그리고 P 5분 유지(확정)까지 건너뛴다. 카드를 완성하면 바로 보상과 럭키박스가 나온다.
     */
    fun demoPark(store: CarlingStore) {
        val trip = store.data.current ?: return
        DemoDrive.armed = false
        val parkAt = trip.startedAt + DemoDrive.minutes * Rules.MINUTE_MILLIS
        if (parkAt > store.clock.now()) store.clock.skipMillis(parkAt - store.clock.now())
        store.park(store.clock.now(), DemoDrive.km, DemoDrive.minutes * 60L, DemoDrive.harsh)
        store.recordPlace(trip.tripId, start = DemoDrive.from, end = DemoDrive.to)
        if (DemoDrive.autoConfirm) skip(store, Rules.STOP_MERGE_MILLIS)
        DemoDrive.reset()
    }
}

/**
 * 60초 시연 주행(디버그 전용). [DevSimulator.prepareDemo] 뒤 첫 D → P 한 번만 연출한다.
 * D 화면 숫자는 배속으로 올라가고(2초에 1km), 출발 [TALK_DELAY_MILLIS] 뒤 "천천히 가 줘" 한마디.
 */
object DemoDrive {
    const val START_HOUR = 7
    const val START_MINUTE = 48
    const val KM = 38.6f
    const val MINUTES = 41
    const val HARSH = 1
    const val TALK_DELAY_MILLIS = 6_000L

    @Volatile var armed = false

    // 시연 영상(adb 명령)에서 바꿔 쓰는 이번 주행 값. 기본은 60초 시연의 출근 주행
    @Volatile var km = KM
    @Volatile var minutes = MINUTES
    @Volatile var harsh = HARSH
    @Volatile var talk: Talk? = Talk.SLOW_DOWN
    @Volatile var autoConfirm = true
    @Volatile var from: String = DevSimulator.HOME
    @Volatile var to: String = DevSimulator.OFFICE

    fun reset() { km = KM; minutes = MINUTES; harsh = HARSH; talk = Talk.SLOW_DOWN; autoConfirm = true; from = DevSimulator.HOME; to = DevSimulator.OFFICE }

    /** D 화면에 보일 배속 거리: 1초에 0.5km, 최대 [km]. */
    fun liveKm(elapsedSec: Long): Float = (elapsedSec * 0.5f).coerceIn(0f, km)

    /** 배속 거리에 맞춘 이동시간(분). */
    fun liveSeconds(km: Float): Long = (km / this.km * minutes * 60).toLong()
}

/** 시연 영상 녹화 때 🛠 칩을 숨긴다(디버그 전용, adb 명령). */
object DevUi {
    var hidden by androidx.compose.runtime.mutableStateOf(false)
    /** 이름 짓기 창에 한 글자씩 입력할 이름(녹화용). */
    var typeName by androidx.compose.runtime.mutableStateOf<String?>(null)
}
