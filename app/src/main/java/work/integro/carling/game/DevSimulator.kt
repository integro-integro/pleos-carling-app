package work.integro.carling.game

import work.integro.carling.data.CarlingStore
import work.integro.carling.data.TripResult
import java.time.ZoneId

/**
 * 개발·데모용: 하루를 건너뛰며 '계열에 맞는 하루'(트립 2회 + 교감 + 충전 + 기억 저장)를
 * 기록해 XP·MP·단계를 빠르게 쌓는다. 출시 빌드에서는 숨긴다.
 */
class DevSimulator(private val store: CarlingStore) {

    private val clock get() = store.clock
    private var seq = 0

    /** [count]일을 건너뛰며 [lineage] 습관의 하루를 반복 기록한다. */
    fun simulateDays(count: Int, lineage: Lineage) {
        repeat(count) { simulateOneDay(lineage) }
    }

    /** 목표 단계 인덱스에 도달할 때까지(최대 220일) 시뮬레이트. 진화 단계는 카드·퀘스트까지 채운다. */
    fun advanceToStage(targetIndex: Int, lineage: Lineage) {
        var guard = 0
        while (store.data.reachedStage < targetIndex && guard++ < 220) {
            simulateOneDay(lineage)
            grantPendingEvolution()
        }
    }

    private fun simulateOneDay(lineage: Lineage) {
        clock.skipDays(1)
        val hour = 18
        val (dist, harsh) = pattern(lineage)
        // 바람·흙·나무는 새 길(탐험) 위주, 빛·물은 늘 가던 길 위주 — 계열 판정이 또렷하게
        val explore = lineage == Lineage.WIND || lineage == Lineage.EARTH || lineage == Lineage.TREE
        repeat(2) {
            val start = clock.today().atTime(hour, it * 2).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val cell = if (explore) "sim-${lineage.name}-${clock.today()}-${seq++}" else "sim-usual-${lineage.name}"
            store.recordTrip(
                TripResult(
                    startMillis = start,
                    endMillis = start + 22 * 60 * 1000,
                    distanceKm = dist,
                    harshEvents = harsh,
                    placeCell = cell,
                    placeName = if (explore) "새로운 길" else "늘 가던 길",
                ),
            )
            store.data.pendingCard?.let { store.toggleTripSaved(it) }
            store.dismissTripCard()
        }
        store.onCharging()      // 연료·충전 XP +10
        store.pet()             // 친밀도 행동 1
        store.interact("PLAY")  // 친밀도 행동 2 → 하루 친밀도 XP 10
    }

    /** 추첨이 시작된 진화 단계의 카드·퀘스트를 매일 채워 다음 단계로 넘어가게 한다(데모). */
    private fun grantPendingEvolution() {
        val dayNum = store.data.dayNumber(clock.today())
        GrowthRules.EVOLUTIONS.values
            .filter { dayNum >= it.lotteryStartDay }
            .forEach { evo ->
                if (evo.evoIndex !in store.data.ownedCards) store.devGrantCard(evo.evoIndex)
                if (store.canCompleteQuest(evo.evoIndex)) store.completeQuest(evo.evoIndex)
            }
    }

    /** 계열별 거리·급조작 패턴. */
    private fun pattern(lineage: Lineage): Pair<Float, Int> = when (lineage) {
        Lineage.WIND -> 28f to 0      // 긴 거리·고속
        Lineage.LIGHT_RAY -> 12f to 4 // 도심 가다서다
        Lineage.TREE -> 14f to 0      // 산길·숲길(탐험)
        Lineage.WATER -> 16f to 0     // 중간 거리
        Lineage.EARTH -> 5f to 1      // 10km 미만 짧은 트립
    }
}
