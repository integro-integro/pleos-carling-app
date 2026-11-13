package work.integro.carling.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import work.integro.carling.data.CarlingStore
import work.integro.carling.data.FakeClock
import work.integro.carling.data.MemStorage
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

/** 60초 시연(기획안 0장): 준비 → D → P → '출근' 완성 → 럭키박스 → 리포트 출발 시각 인사이트. */
class DemoDriveTest {
    private val zone = ZoneOffset.UTC
    // 2026-11-14(토)에 준비하면 이번 달 마지막 평일 11/30(월) 아침으로 간다
    private val clock = FakeClock(LocalDate.of(2026, 11, 14).atTime(15, 0).toInstant(zone).toEpochMilli())
    private val store = CarlingStore(MemStorage(), clock, Random(7), zone)

    @After fun disarm() { DemoDrive.armed = false }

    @Test fun `시연 준비는 이번 달 마지막 평일 아침 7시 48분, 한 달 기록, 정리된 화면으로 시작한다`() {
        DevSimulator.prepareDemo(store, zone)
        val d = store.data
        assertEquals(LocalDate.of(2026, 11, 30), clock.today())
        assertEquals(7, clock.hourOf(clock.now()))
        assertTrue(d.cards.count { it.purpose == TripPurpose.TO_WORK } >= 16)
        assertTrue(d.refuels.isNotEmpty())
        assertEquals(true, d.locationConsent)
        assertTrue(d.unopenedBoxes.isEmpty())
        assertNull(d.unseenStage)
        assertNull(d.unseenPersonality)
        assertFalse(d.unseenSetReward)
        assertEquals(LevelRules.of(d.totalGp), d.levelSeen)
        assertTrue(DemoDrive.armed)
    }

    @Test fun `시연 주행은 출근 카드 한 장으로 끝나고 완성하면 바로 보상이 온다`() {
        DevSimulator.prepareDemo(store, zone)
        val start = clock.now()
        store.startTrip(start, null)
        clock.t += 20_000 // 실제로는 20초만 달린다
        DevSimulator.demoPark(store)

        assertFalse(DemoDrive.armed)
        val trip = store.data.trip(start)!!
        assertEquals(TripStatus.CONFIRMED, trip.status)
        assertEquals(DemoDrive.KM, trip.distanceKm!!, 0.01f)
        assertEquals(DemoDrive.MINUTES * 60L, trip.movingSec)
        assertEquals(DevSimulator.HOME, trip.startDong)
        assertEquals(DevSimulator.OFFICE, trip.endDong)
        assertEquals(CardState.DRAFT, store.data.card(start)!!.state)
        assertTrue(start in store.data.unseenCards)
        assertEquals(TripPurpose.TO_WORK, store.suggestPurpose(start))

        store.complete(start, TripPurpose.TO_WORK)
        val reward = store.data.rewards[start]
        assertNotNull(reward)
        assertNotNull(reward!!.boxId)
        assertEquals(1, store.data.unopenedBoxes.size)
    }

    @Test fun `시연 기록이면 리포트 맨 위에 7시 50분 출발 인사이트가 나온다`() {
        DevSimulator.prepareDemo(store, zone)
        val d = store.data
        val trips = d.trips.filter { it.status == TripStatus.CONFIRMED }.map { t ->
            InsightTrip(t.startedAt, t.distanceKm, t.movingSec, d.card(t.startedAt)?.purpose, t.startDong, t.endDong, t.score, t.harsh, t.efficiency)
        }
        val ins = InsightRules.depart(trips, zone) { null }
        assertNotNull(ins)
        assertTrue(ins!!.title, ins.title.contains("7:50"))
    }

    @Test fun `D 화면 배속 숫자는 41분 38점6km를 넘지 않는다`() {
        assertEquals(5f, DemoDrive.liveKm(10), 0.01f)
        assertEquals(DemoDrive.KM, DemoDrive.liveKm(10_000), 0.01f)
        assertEquals(DemoDrive.MINUTES * 60L, DemoDrive.liveSeconds(DemoDrive.KM))
    }

    @Test fun `한 달 마감하면 지난달 리포트가 한 번 만들어지고 짜잔 알림이 뜬다`() {
        DevSimulator.prepareDemo(store, zone)
        assertTrue(store.data.monthReports.isEmpty())
        DevSimulator.closeMonth(store, zone)
        assertEquals(LocalDate.of(2026, 12, 1), clock.today())
        val r = store.data.monthReports.single()
        assertEquals("2026-11", r.month)
        assertEquals("2026-11", store.data.unseenMonthReport)
        assertTrue(r.trips >= 16)
        assertTrue(r.insights.any { it.kind == InsightKind.DEPART })
        store.seenMonthReport()
        store.publishMonthReport()
        assertEquals(1, store.data.monthReports.size)
        assertNull(store.data.unseenMonthReport)
    }

    @Test fun `마지막 평일은 달 끝 주말을 건너뛴다`() {
        assertEquals(LocalDate.of(2026, 10, 30), DevSimulator.demoDay(LocalDate.of(2026, 10, 2)))
        assertEquals(LocalDate.of(2026, 11, 30), DevSimulator.demoDay(LocalDate.of(2026, 10, 31)))
    }
}
