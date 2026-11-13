package work.integro.carling.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import work.integro.carling.game.Badge
import work.integro.carling.game.JellyRules
import work.integro.carling.game.LevelRules
import work.integro.carling.game.ShopItem
import work.integro.carling.game.CarColor
import work.integro.carling.game.CarPart
import work.integro.carling.game.CarSlot
import work.integro.carling.game.CardState
import work.integro.carling.game.Clock
import work.integro.carling.game.LuckyBoxRules
import work.integro.carling.game.Personality
import work.integro.carling.game.PersonalityInput
import work.integro.carling.game.PersonalityResult
import work.integro.carling.game.PersonalityRules
import work.integro.carling.game.Rules
import work.integro.carling.game.Stage
import work.integro.carling.game.TimeBucket
import work.integro.carling.game.TripCardRules
import work.integro.carling.game.TripPurpose
import work.integro.carling.game.TripStatus
import work.integro.carling.game.eulReul
import work.integro.carling.game.iGa
import java.util.UUID
import kotlin.random.Random

private const val TAG = "CarlingStore"

/**
 * 마이카링 상태를 기기 안에 저장한다. 모든 변경은 메인 스레드에서, 상태 전체를 한 번에 바꿔 저장한다.
 *
 * 운행 확정과 카드 완성은 별개 이벤트이고, 둘 다 참이 되는 순간 [grant]가 보상을 한 번 지급한다.
 * 보상 원장(rewards)에 운행 ID가 이미 있으면 아무것도 하지 않으므로, 같은 이벤트가 다시 처리돼도 중복 지급이 없다.
 * (PRD는 Room 기본키 제약을 제안했지만, 지금은 한 번에 저장하는 JSON 상태로 같은 보장을 한다.)
 */
class CarlingStore(private val storage: Storage, val clock: Clock, private val random: Random = Random.Default, private val zone: java.time.ZoneId = java.time.ZoneId.systemDefault()) {

    constructor(context: Context, clock: Clock) : this(PrefsStorage(context), clock)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; allowStructuredMapKeys = true }

    var data by mutableStateOf(load())
        private set

    /** 이번 시동 시각. 이보다 먼저 확정된 임시 카드가 재안내 대상이다. 앱이 새로 뜨거나 새 운행이 시작될 때 바뀐다. */
    var ignitionMillis by mutableLongStateOf(clock.now())
        private set

    private fun load(): CarlingData {
        val loaded = storage.read()?.let {
            runCatching { json.decodeFromString(CarlingData.serializer(), it) }
                .onFailure { e -> Log.w(TAG, "저장 데이터 복구 실패, 처음부터 시작", e) }
                .getOrNull()
        }
        return migrate((loaded ?: CarlingData()).let { if (it.karingId.isEmpty()) it.copy(karingId = UUID.randomUUID().toString()) else it })
    }

    /**
     * 예전 목적을 새 목적으로 옮긴다(2026-11-12 사용자 결정): 출퇴근은 출발이 오전이면 출근, 아니면 퇴근,
     * 가족과 함께는 목적 '기타' + 누구랑 '가족과'. 외근은 저장 이름이 같아 '업무'로 바로 읽힌다.
     */
    private fun migrate(d0: CarlingData): CarlingData {
        // 세트 보상이 생기기 전에 이미 6종을 모았다면 지금 준다
        val set = work.integro.carling.game.BadgeRules.SET_REWARD
        var d = if (d0.badges.keys.containsAll(Badge.entries) && (d0.colors[set] ?: 0) == 0) d0.copy(colors = d0.colors + (set to 1), unseenSetReward = true) else d0
        // 젤리가 생기기 전 기록: 보상과 연 상자로 한 번 채운다
        if (!d.jellyStarted) d = d.copy(jellyStarted = true, jelly = d.jelly + d.rewards.keys.sumOf { JellyRules.forReward(d.trip(it)?.distanceKm) } + d.boxes.filter { it.openedAt != null }.sumOf { it.bonusGp })
        if (d.levelSeen == 0) d = d.copy(levelSeen = LevelRules.of(d.totalGp))
        // 버디링·소울링은 추후 공개(11/13): 이미 넘어선 기록은 열린 마지막 단계로 둔다
        if (!d.stage.open) d = d.copy(stage = Stage.lastOpen, unseenStage = null)
        if (d.look?.open == false) d = d.copy(look = null)
        // 배경 톤 시안 단계(원본)에 저장된 값은 사용자가 고른 A로 맞춘다(원본은 개발 도구에서만 잠깐 볼 수 있다)
        if (d.cabinTone == work.integro.carling.game.CabinTone.ORIGINAL) d = d.copy(cabinTone = work.integro.carling.game.CabinTone.CALM)
        // 몸 분위기(낮형·밤형)가 생기기 전 기록
        if (d.personality.vibe == null && d.rewards.size >= work.integro.carling.game.Rules.PERSONALITY_MIN) d = d.copy(personality = personalityOf(d).personality)
        if (d.cards.none { it.purpose == TripPurpose.COMMUTE || it.purpose == TripPurpose.FAMILY }) return d
        return d.copy(cards = d.cards.map { c ->
            when (c.purpose) {
                TripPurpose.COMMUTE -> c.copy(purpose = if (clock.hourOf(d.trip(c.tripId)?.startedAt ?: c.createdAt) < 12) TripPurpose.TO_WORK else TripPurpose.FROM_WORK)
                TripPurpose.FAMILY -> c.copy(purpose = TripPurpose.OTHER, companion = c.companion ?: work.integro.carling.game.Companion.FAMILY)
                else -> c
            }
        })
    }

    private fun update(sync: Boolean = false, block: (CarlingData) -> CarlingData) {
        data = block(data)
        storage.write(json.encodeToString(CarlingData.serializer(), data), sync)
    }

    private fun CarlingData.log(type: String, ref: Long? = null) = copy(events = (events + EventLog(clock.now(), type, ref)).takeLast(MAX_EVENTS))

    private fun CarlingData.replaceTrip(t: TripRecord) = copy(trips = trips.map { if (it.tripId == t.tripId) t else it })
    private fun CarlingData.replaceCard(c: TripCard) = copy(cards = cards.map { if (it.tripId == c.tripId) c else it })

    // ---- 운행 ----

    fun markIgnition(now: Long = clock.now()) {
        ignitionMillis = now
    }

    /**
     * TripStarted: 새 운행 ID 발급, 상태 = 기록 중. 출발할 때 남은 양이 직전 운행의 주차 때보다
     * [Rules.REFUEL_MIN_PERCENT] 이상 늘었으면 그 사이 주유·충전한 것으로 기록한다.
     */
    fun startTrip(now: Long, odometerKm: Float?, simulated: Boolean = false, energy: work.integro.carling.vehicle.EnergyReading? = null): Long {
        update(sync = true) { d ->
            val last = d.trips.lastOrNull { it.endPercent != null }
            val now0 = energy?.percent
            val refuel = if (last?.endPercent != null && now0 != null && now0 - last.endPercent >= Rules.REFUEL_MIN_PERCENT)
                Refuel(now, energy.isEv, last.endPercent, now0, simulated) else null
            val trip = TripRecord(now, TripStatus.RECORDING, now, startOdometerKm = odometerKm, simulated = simulated, ev = energy?.isEv, startPercent = now0, startWh = energy?.batteryWh)
            var next = d.copy(trips = (d.trips + trip).takeLast(MAX_TRIPS))
            if (refuel != null) next = next.copy(refuels = (next.refuels + refuel).takeLast(500)).log(if (refuel.ev) "charged" else "refueled")
            next
        }
        return now
    }

    /** 주차할 때 에너지 상태와 차량 효율을 남긴다. 차량 효율이 없으면 EV 배터리 사용량으로 계산한다. */
    fun recordEnergy(tripId: Long, energy: work.integro.carling.vehicle.EnergyReading?, vehicleEfficiency: Float?) {
        val t = data.trip(tripId) ?: return
        val ev = energy?.isEv ?: t.ev
        val usedKwh = if (t.startWh != null && energy?.batteryWh != null) (t.startWh - energy.batteryWh) / 1000f else null
        val computed = if (usedKwh != null && usedKwh > 0.05f && (t.distanceKm ?: 0f) > 0f) t.distanceKm!! / usedKwh else null
        val eff = vehicleEfficiency?.takeIf { it > 0f && it < 100f } ?: computed
        update { it.replaceTrip(t.copy(ev = ev, endPercent = energy?.percent ?: t.endPercent, endWh = energy?.batteryWh ?: t.endWh, efficiency = eff ?: t.efficiency)) }
    }

    /** 주행 중 스냅샷. 앱이 꺼져도 여기서 이어 간다. */
    fun snapshot(now: Long, distanceKm: Float?, movingSec: Long?, harsh: Int?) {
        val t = data.current?.takeIf { it.status == TripStatus.RECORDING } ?: return
        update { it.replaceTrip(t.copy(lastSnapshotAt = now, distanceKm = distanceKm ?: t.distanceKm, movingSec = movingSec ?: t.movingSec, harsh = harsh ?: t.harsh)) }
    }

    /**
     * TripParked: 상태 = 주차됨, 주차 시각 저장. 최소 기준을 넘으면 임시 카드를 만들고(이미 있으면 수치만 갱신) 안내한다.
     * 이미 완성한 카드는 다시 묻지 않는다.
     */
    fun park(now: Long, distanceKm: Float?, movingSec: Long?, harsh: Int?) {
        val t = data.current?.takeIf { it.status == TripStatus.RECORDING } ?: return
        val parked = t.copy(status = TripStatus.PARKED, lastParkAt = now, lastSnapshotAt = now, distanceKm = distanceKm ?: t.distanceKm, movingSec = movingSec ?: t.movingSec, harsh = harsh ?: t.harsh)
        update(sync = true) { d ->
            var next = d.replaceTrip(parked)
            val card = d.card(t.tripId)
            if (card == null && Rules.meetsCardMinimum(parked.distanceKm, parked.movingSec)) {
                val number = (d.cards.maxOfOrNull { it.number } ?: 0) + 1
                next = next.copy(cards = (next.cards + TripCard(t.tripId, number, createdAt = now)).takeLast(MAX_TRIPS), unseenCards = next.unseenCards + t.tripId).log("card_shown", t.tripId)
            } else if (card?.state == CardState.DRAFT) {
                next = next.copy(unseenCards = (next.unseenCards - t.tripId) + t.tripId).log("card_shown", t.tripId)
            }
            next
        }
    }

    /** TripResumed: 주차 5분 안에 다시 출발하면 같은 운행에 계속 쌓는다. 합칠 수 없으면 false. */
    fun resume(now: Long): Boolean {
        val t = data.current?.takeIf { it.status == TripStatus.PARKED } ?: return false
        if (now - (t.lastParkAt ?: now) >= Rules.STOP_MERGE_MILLIS) return false
        update(sync = true) { it.replaceTrip(t.copy(status = TripStatus.RECORDING, lastSnapshotAt = now)).copy(unseenCards = it.unseenCards - t.tripId) }
        return true
    }

    /**
     * 주차 5분이 지난 운행을 확정한다. 앱이 꺼져 있던 동안의 5분도 여기서 소급 판정한다(확정 시각 = 주차 + 5분).
     * 기록 중인 채로 앱이 꺼졌던 운행은 [liveTripId]가 아니면 마지막 스냅샷 시각에 주차한 것으로 본다.
     */
    fun settle(now: Long = clock.now(), liveTripId: Long? = null) {
        val t = data.current ?: return
        if (t.status == TripStatus.RECORDING && t.tripId != liveTripId && now - t.lastSnapshotAt >= Rules.SNAPSHOT_MILLIS * 3) {
            Log.i(TAG, "기록 중 꺼진 운행: 마지막 스냅샷에 주차한 것으로 처리")
            update(sync = true) { it.replaceTrip(t.copy(status = TripStatus.PARKED, lastParkAt = t.lastSnapshotAt)) }
        }
        val p = data.current?.takeIf { it.status == TripStatus.PARKED } ?: return
        val parkAt = p.lastParkAt ?: return
        if (now - parkAt >= Rules.STOP_MERGE_MILLIS) confirm(p.tripId, parkAt + Rules.STOP_MERGE_MILLIS)
    }
    // ---- 월간 리포트 ----

    /**
     * 지난달이 꽉 찼으면 그달 리포트를 한 번 만들어 저장하고 '짜잔' 알림을 띄운다(11/13 사용자 결정).
     * 그달에 확정 운행이 없거나 이미 만들었으면 아무것도 하지 않는다. 지난 몇 달을 거슬러 만들지는 않는다.
     */
    fun publishMonthReport(today: java.time.LocalDate = clock.today()) {
        val month = java.time.YearMonth.from(today).minusMonths(1)
        val key = month.toString()
        if (data.monthReports.any { it.month == key }) return
        val inMonth = data.trips.filter { it.status == TripStatus.CONFIRMED && java.time.YearMonth.from(java.time.Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate()) == month }
        if (inMonth.isEmpty()) return
        val its = data.trips.filter { it.status == TripStatus.CONFIRMED }.map { t ->
            val c = data.card(t.tripId)?.takeIf { it.state == CardState.COMPLETED }
            work.integro.carling.game.InsightTrip(t.startedAt, t.distanceKm, t.movingSec, c?.purpose, t.startDong, t.endDong, t.score, t.harsh, t.efficiency)
        }
        val ev = inMonth.none { it.ev == false }
        val insights = work.integro.carling.game.InsightRules.build(
            work.integro.carling.game.ReportPeriod(work.integro.carling.game.PeriodUnit.MONTH), month.atEndOfMonth(), zone, its, ev,
            if (ev) data.kwhPrice else data.fuelPrice, { null }, null,
        )
        val report = MonthReport(key, clock.now(), insights, inMonth.size, inMonth.sumOf { (it.distanceKm ?: 0f).toDouble() }.toFloat(), (inMonth.sumOf { it.movingSec ?: 0L } / 60).toInt())
        update(sync = true) { it.copy(monthReports = (it.monthReports + report).takeLast(36), unseenMonthReport = key).log("month_report_created") }
    }

    fun seenMonthReport() = update { it.copy(unseenMonthReport = null).log("month_report_opened") }

    fun monthReport(month: java.time.YearMonth): MonthReport? = data.monthReports.firstOrNull { it.month == month.toString() }


    /** TripConfirmed: 거리·시간 동결. 이미 확정된 운행이면 상태 변화 없음. */
    fun confirm(tripId: Long, at: Long) {
        val t = data.trip(tripId) ?: return
        if (t.status == TripStatus.CONFIRMED) return
        update(sync = true) { d ->
            // 기준 미달로 카드가 없는 운행도 기록으로 남는다
            grant(d.replaceTrip(t.copy(status = TripStatus.CONFIRMED, confirmedAt = at)), tripId)
        }
        Log.i(TAG, "운행 확정: $tripId")
    }

    // ---- 카드 ----

    /** CardCompleted: 목적을 골라 완성. 운행이 확정돼 있으면 같은 저장에서 보상까지 지급한다. */
    fun complete(tripId: Long, purpose: TripPurpose, tags: List<String> = emptyList(), companion: work.integro.carling.game.Companion? = null, custom: String? = null) {
        expireCards()
        val card = data.card(tripId)?.takeIf { it.state == CardState.DRAFT } ?: return
        val clean = tags.mapNotNull(TripCardRules::cleanTag).distinct()
        update(sync = true) { d ->
            val own = customOf(purpose, custom)
            val done = card.copy(state = CardState.COMPLETED, completedAt = clock.now(), purpose = purpose, tags = clean, companion = companion, customPurpose = own)
            grant(
                d.replaceCard(done).copy(
                    unseenCards = d.unseenCards - tripId,
                    customTags = (d.customTags - clean.toSet() + clean).takeLast(MAX_TAGS),
                    customPurposes = rememberPurpose(d.customPurposes, own),
                ).log("card_completed", tripId),
                tripId,
            )
        }
    }

    /** 완성 후 목적 수정. 보상을 받은 카드라면 개성을 다시 계산한다. */
    fun editPurpose(tripId: Long, purpose: TripPurpose, companion: work.integro.carling.game.Companion? = data.card(tripId)?.companion, tags: List<String>? = null, custom: String? = data.card(tripId)?.customPurpose) {
        val card = data.card(tripId)?.takeIf { it.state == CardState.COMPLETED } ?: return
        val clean = tags?.mapNotNull(TripCardRules::cleanTag)?.distinct() ?: card.tags
        val own = customOf(purpose, custom)
        if (card.purpose == purpose && card.companion == companion && card.tags == clean && card.customPurpose == own) return
        update(sync = true) { d ->
            recomputePersonality(d.replaceCard(card.copy(purpose = purpose, companion = companion, tags = clean, customPurpose = own)))
                .copy(customPurposes = rememberPurpose(d.customPurposes, own)).log("card_edited", tripId)
        }
    }

    /** 직접 적은 목적은 '기타'일 때만 남긴다. */
    private fun customOf(purpose: TripPurpose, custom: String?): String? =
        if (purpose == TripPurpose.OTHER) custom?.let(TripCardRules::cleanTag) else null

    private fun rememberPurpose(list: List<String>, own: String?): List<String> =
        if (own == null) list else (list - own + own).takeLast(MAX_TAGS)

    /** 절약 금액 단가(원). */
    fun setPrice(ev: Boolean, won: Int) = update { if (ev) it.copy(kwhPrice = won.coerceIn(50, 2_000)) else it.copy(fuelPrice = won.coerceIn(500, 4_000)) }

    /** 운행일지 받을 메일 주소. */
    fun setLogEmail(email: String?) = update { it.copy(logEmail = email?.trim()?.takeIf { e -> e.isNotEmpty() }) }

    /** 임시 카드를 모두 완성한다. 목적은 추천값, 없으면 '기타'. */
    fun completeAllDrafts() {
        expireCards()
        data.draftCards.forEach { c -> complete(c.tripId, suggestPurpose(c.tripId) ?: TripPurpose.OTHER, c.tags, c.companion, c.customPurpose) }
    }

    /** '나중에': 안내만 닫는다. */
    fun later() = update { d -> d.unseenCards.fold(d.copy(unseenCards = emptyList())) { acc, id -> acc.log("card_later", id) } }

    /** 재안내를 보여준 순간부터 24시간 기한이 시작된다. 이미 재안내한 카드는 바뀌지 않는다. */
    fun markReminded(ids: Collection<Long>, now: Long = clock.now()) {
        if (ids.isEmpty()) return
        update(sync = true) { d ->
            ids.fold(d.copy(cards = d.cards.map { if (it.tripId in ids && it.state == CardState.DRAFT && it.remindedAt == null) it.copy(remindedAt = now) else it })) { acc, id -> acc.log("card_reprompted", id) }
        }
    }

    /** 기한이 지난 임시 카드를 만료한다. 운행 기록은 그대로 남는다. */
    fun expireCards(now: Long = clock.now()) {
        val d0 = data
        val expired = d0.cards.filter { TripCardRules.isExpired(it.clock(d0.trip(it.tripId)), now) }.map { it.tripId }.toSet()
        if (expired.isEmpty()) return
        update(sync = true) { d ->
            expired.fold(
                d.copy(
                    cards = d.cards.map { if (it.tripId in expired) it.copy(state = CardState.EXPIRED, expiredAt = now) else it },
                    unseenCards = d.unseenCards - expired,
                ),
            ) { acc, id -> acc.log("card_expired", id) }
        }
    }

    /** 재안내할 카드(최신 순). */
    fun reminderCards(now: Long = clock.now()): List<TripCard> =
        data.cards.filter { TripCardRules.needsReminder(it.clock(data.trip(it.tripId)), ignitionMillis, now) && it.tripId !in data.unseenCards }.asReversed()

    /** 위치 없이, 비슷한 시간대에 고른 목적을 기본값으로 제안한다. */
    fun suggestPurpose(tripId: Long): TripPurpose? {
        val trip = data.trip(tripId) ?: return null
        val history = data.cards.filter { it.state == CardState.COMPLETED && it.purpose != null && it.tripId != tripId }
            .mapNotNull { c -> data.trip(c.tripId)?.let { Triple(clock.hourOf(it.startedAt), isWeekend(it.startedAt), c.purpose!!) } }
        return TripCardRules.suggestPurpose(clock.hourOf(trip.startedAt), isWeekend(trip.startedAt), history)
    }

    fun isWeekend(millis: Long): Boolean = clock.dateOf(millis).dayOfWeek.value >= 6

    // ---- 보상 ----

    /**
     * RewardGranted: 운행 = 확정, 카드 = 완성, 원장에 운행 ID 없음 — 셋이 모두 참일 때만 한 번.
     * 원장 기록 → 포인트 가산 → 단계 판정 → 개성 스냅샷 → 럭키박스 생성을 한 번에 반영한다.
     */
    private fun grant(d: CarlingData, tripId: Long): CarlingData {
        val trip = d.trip(tripId) ?: return d
        val card = d.card(tripId) ?: return d
        if (trip.status != TripStatus.CONFIRMED || card.state != CardState.COMPLETED || tripId in d.rewards) return d
        val now = clock.now()
        val gp = Rules.growthPoints(trip.distanceKm, trip.movingSec)
        val day = clock.dateOf(trip.confirmedAt ?: now).toString()
        val boxesToday = d.dailyBoxes[day] ?: 0
        val box = if (boxesToday < Rules.BOX_DAILY_MAX) {
            val bucket = work.integro.carling.game.TimeBucket.of(clock.hourOf(trip.startedAt))
            val same = if (card.purpose == null) 0 else d.cards.count { it.state == CardState.COMPLETED && it.purpose == card.purpose }
            val fits = work.integro.carling.game.BadgeRules.eligible(bucket, isWeekend(trip.startedAt), card.purpose, same, trip.score)
            val c = LuckyBoxRules.draw(fits) { random.nextDouble() }
            LuckyBox((d.boxes.maxOfOrNull { it.boxId } ?: 0) + 1, tripId, c.bonusGp, c.badge, c.part, now, color = c.color)
        } else null
        val next = d.copy(
            jelly = d.jelly + JellyRules.forReward(trip.distanceKm),
            rewards = d.rewards + (tripId to Reward(now, gp, box?.boxId)),
            totalGp = d.totalGp + gp,
            boxes = if (box != null) (d.boxes + box).takeLast(MAX_TRIPS) else d.boxes,
            dailyBoxes = if (box != null) (d.dailyBoxes + (day to boxesToday + 1)).toList().takeLast(60).toMap() else d.dailyBoxes,
        ).log("reward_granted", tripId)
        return recomputePersonality(updateStage(next))
    }

    private fun updateStage(d: CarlingData): CarlingData {
        val target = Stage.of(d.totalGp, d.rewards.size)
        if (target.ordinal <= d.stage.ordinal) return d // 단계는 내려가지 않는다
        return d.copy(stage = target, unseenStage = target, stageHistory = d.stageHistory + StageChange(clock.now(), d.stage, target)).log("stage_changed")
    }

    /** 보상이 지급된 운행 중 최근 10장(출발 순)으로 개성을 다시 정하고, 바뀌었으면 근거를 남긴다. */
    private fun recomputePersonality(d: CarlingData): CarlingData {
        val result = personalityOf(d)
        if (result.personality == d.personality) return d
        // 같은 분위기 안에서 시간대만 바뀐 것은 보이는 변화가 없어 조용히 바꾼다
        if (result.personality.copy(body = d.personality.body) == d.personality) return d.copy(personality = result.personality)
        val id = (d.snapshots.maxOfOrNull { it.id } ?: 0) + 1
        val snap = PersonalitySnapshot(
            id = id,
            createdAt = clock.now(),
            tripIds = result.used,
            bucketCounts = result.bucketCounts,
            purposeCounts = result.purposeCounts,
            avgScore = result.avgScore,
            before = d.personality,
            after = result.personality,
            line = changeLine(d.personality, result),
        )
        return d.copy(personality = result.personality, snapshots = (d.snapshots + snap).takeLast(30), unseenPersonality = id).log("personality_changed")
    }

    fun personalityOf(d: CarlingData = data): PersonalityResult {
        val inputs = d.rewards.keys.mapNotNull { id -> d.trip(id) }.sortedBy { it.startedAt }.map { t ->
            PersonalityInput(t.tripId, TimeBucket.of(clock.hourOf(t.startedAt)), d.card(t.tripId)?.purpose, t.score)
        }
        return PersonalityRules.compute(inputs)
    }

    private fun changeLine(before: Personality, r: PersonalityResult): String {
        val after = r.personality
        val parts = mutableListOf<String>()
        if (before.vibe != after.vibe) parts += after.vibe?.let { "${it.eyewear.eulReul()} 했어요 · ${r.bodyReason()}" } ?: "장비를 내려놓았어요"
        if (before.decoration != after.decoration) {
            parts += after.decorationItem?.let { "${it.label.eulReul()} 달았어요 · ${r.decorationReason()}" } ?: "장식을 내려놓았어요"
        }
        if (before.heartBright != after.heartBright) parts += if (after.heartBright) "심장이 선명하게 반짝여요 · ${r.heartReason()}" else "심장빛이 기본으로 돌아왔어요"
        return parts.joinToString("\n")
    }

    /** 럭키박스 열기. 내용은 상자 생성 때 정해져 있다. 자동차 꾸미기는 '바로 장착'을 고를 때 단다. */
    fun openBox(boxId: Int) {
        if (data.boxes.none { it.boxId == boxId && it.openedAt == null }) return
        update(sync = true) { updateStage(openIn(it, boxId, fillEmpty = false)) }
    }

    /** 모두 받기: 열지 않은 상자를 한 번에 연다. 새 자동차 꾸미기는 비어 있는 자리에만 바로 단다. */
    fun openAll(): List<LuckyBox> {
        val boxes = data.unopenedBoxes
        if (boxes.isEmpty()) return emptyList()
        update(sync = true) { d -> updateStage(boxes.fold(d) { acc, b -> openIn(acc, b.boxId, fillEmpty = true) }) }
        return boxes
    }

    private fun openIn(d: CarlingData, boxId: Int, fillEmpty: Boolean): CarlingData {
        val box = d.boxes.firstOrNull { it.boxId == boxId && it.openedAt == null } ?: return d
        val part = box.part
        val isNew = part != null && (d.parts[part] ?: 0) == 0
        val equipped = if (fillEmpty && isNew && part != null && part.slot !in d.equipped) d.equipped + (part.slot to part) else d.equipped
        val badges = box.badge?.let { d.badges + (it to (d.badges[it] ?: 0) + 1) } ?: d.badges
        // 배지 6종을 처음 다 모으면 오로라 차체 색
        val set = work.integro.carling.game.BadgeRules.SET_REWARD
        val completesSet = badges.keys.containsAll(work.integro.carling.game.Badge.entries) && (d.colors[set] ?: 0) == 0
        val colors0 = box.color?.let { d.colors + (it to (d.colors[it] ?: 0) + 1) } ?: d.colors
        val dup = (box.badge != null && (d.badges[box.badge] ?: 0) > 0) || (part != null && !isNew) || (box.color != null && (d.colors[box.color] ?: 0) > 0)
        return withStyle(
            d.copy(
                boxes = d.boxes.map { if (it.boxId == boxId) it.copy(openedAt = clock.now()) else it },
                totalGp = d.totalGp + box.bonusGp,
                jelly = d.jelly + box.bonusGp + if (dup) JellyRules.DUPLICATE else 0,
                badges = badges,
                unseenSetReward = d.unseenSetReward || completesSet,
                parts = part?.let { d.parts + (it to (d.parts[it] ?: 0) + 1) } ?: d.parts,
                colors = if (completesSet) colors0 + (set to 1) else colors0,
                equipped = equipped,
            ).log("box_opened", box.tripId),
        )
    }

    /** 자동차 꾸미기를 그 자리에 단다(같은 자리의 아이템은 바뀐다). 가진 것만. */
    fun equip(part: CarPart) {
        if ((data.parts[part] ?: 0) == 0) return
        update { withStyle(it.copy(equipped = it.equipped + (part.slot to part))).log("part_equipped") }
    }

    /** 차체 색을 칠한다. 펄 화이트는 언제나 칠할 수 있고, 나머지는 모은 색만. */
    fun paint(color: CarColor) {
        if (color != CarColor.PEARL && (data.colors[color] ?: 0) == 0) return
        update { it.copy(carColor = if (color == CarColor.PEARL) null else color).log("car_painted") }
    }

    /** 모습 고르기: 아기링~지금 단계 중 하나. 지금 단계를 고르면 기본으로 돌아간다. */
    fun chooseLook(s: Stage) {
        if (!data.canChooseLook || s.ordinal !in Stage.BABY.ordinal..data.stage.ordinal) return
        update { it.copy(look = if (s == it.stage) null else s).log("look_changed") }
    }

    /** 위치(동네) 사용 동의. 끄면 지금까지 남긴 동네 기록도 지운다. */
    fun setLocationConsent(allowed: Boolean) = update(sync = true) { d ->
        if (allowed) d.copy(locationConsent = true).log("location_on")
        else d.copy(locationConsent = false, trips = d.trips.map { it.copy(startDong = null, endDong = null) }).log("location_off")
    }

    /** 출발·도착 동네를 남긴다(동의했을 때만). */
    fun recordPlace(tripId: Long, start: String? = null, end: String? = null) {
        if (data.locationConsent != true) return
        val t = data.trip(tripId) ?: return
        update { it.replaceTrip(t.copy(startDong = start ?: t.startDong, endDong = end ?: t.endDong)) }
    }

    /** 주행 한마디를 했다(성장 리포트에서 센다). */
    fun logTalk(t: work.integro.carling.game.Talk) = update { it.copy(talks = it.talks + (t to (it.talks[t] ?: 0) + 1)).log("talk_${t.name.lowercase()}") }

    /** 레벨업 축하를 봤다. 오른 레벨마다 젤리 50. */
    fun seenLevel() = update { d ->
        val now = LevelRules.of(d.totalGp)
        if (now <= d.levelSeen) d else d.copy(levelSeen = now, jelly = d.jelly + (now - d.levelSeen) * JellyRules.LEVEL_UP).log("level_up")
    }

    /** 홈에서 보일 방향(360° 보기에서 고른 것). null이면 기본 그림. */
    fun setHomeAngle(step: Int?) = update { it.copy(homeAngle = step?.let { s -> ((s % 8) + 8) % 8 }) }

    /** 상점에서 산다. 젤리가 모자라거나 이미 가졌으면(테마) 아무것도 하지 않고 false. */
    fun buy(item: ShopItem): Boolean {
        val d = data
        if (d.jelly < item.price) return false
        if (item is ShopItem.Theme && item.bucket in d.themes) return false
        update(sync = true) { x ->
            val paid = x.copy(jelly = x.jelly - item.price)
            when (item) {
                is ShopItem.Part -> withStyle(paid.copy(parts = paid.parts + (item.part to (paid.parts[item.part] ?: 0) + 1)))
                is ShopItem.Paint -> paid.copy(colors = paid.colors + (item.color to (paid.colors[item.color] ?: 0) + 1))
                is ShopItem.Theme -> paid.copy(themes = paid.themes + item.bucket, theme = item.bucket)
            }.log("shop_${item.key}")
        }
        return true
    }

    /** 배경 테마를 쓴다(null이면 시간대에 따라 자동). 산 것만. */
    fun useTheme(b: work.integro.carling.game.TimeBucket?) {
        if (b != null && b !in data.themes) return
        update { it.copy(theme = b) }
    }

    /** 생활반경 혜택 켜기/끄기(따로 받는 선택 동의). */
    fun setBenefitConsent(on: Boolean) = update { it.copy(benefitConsent = on).log(if (on) "benefit_on" else "benefit_off") }

    /** 혜택 카드를 닫는다: 30일 동안 다시 보이지 않는다. */
    fun snoozeBenefit(id: String) = update { it.copy(benefitSnoozed = it.benefitSnoozed + (id to clock.now())).log("benefit_dismissed") }

    /** 혜택을 열어 봤다(지표). */
    fun logBenefit(type: String) = update { it.log(type) }

    /** 배지 세트 보상 안내를 봤다. */
    fun seenSetReward() = update { it.copy(unseenSetReward = false) }

    /** 내 카링의 '차 타기 / 차 내리기'. 홈에서도 그 모습으로 보인다. */
    fun setRiding(on: Boolean) = update { it.copy(riding = on) }

    fun setCabinTone(t: work.integro.carling.game.CabinTone) = update { it.copy(cabinTone = t) }

    fun setCabin(view: work.integro.carling.game.CabinView) = update { it.copy(cabin = view) }

    /** 그 자리의 아이템을 뗀다. */
    fun unequip(slot: CarSlot) = update { it.copy(equipped = it.equipped - slot) }

    /** 내 카링 '모두 해제': 꾸미기와 차체 색을 모두 뗀다. */
    fun unequipAll() = update { it.copy(equipped = emptyMap(), carColor = null).log("unequipped_all") }

    /** 대표 배지(가진 것만). null이면 내린다. */
    fun setDisplayBadge(b: Badge?) {
        if (b != null && (data.badges[b] ?: 0) == 0) return
        update { it.copy(displayBadge = b) }
    }

    /** 장착 스타일이 완성되면 기록해 둔다. */
    private fun withStyle(d: CarlingData): CarlingData {
        val style = d.style ?: return d
        return if (style in d.styles) d else d.copy(styles = d.styles + style).log("style_completed")
    }

    /** 이 운행이 확정되면 받을 성장 포인트(완성 직후 예고용). */
    fun expectedGp(tripId: Long): Int = data.trip(tripId)?.let { Rules.growthPoints(it.distanceKm, it.movingSec) } ?: 0

    fun ackStage() = update { it.copy(unseenStage = null) }
    fun ackPersonality() = update { it.copy(unseenPersonality = null) }
    fun consent() = update { it.copy(consented = true).log("consented") }
    fun giveName(value: String) = update { it.copy(name = value.trim().take(10).ifEmpty { null }) }
    fun toggleVoice() = update { it.copy(voiceOn = !it.voiceOn) }
    fun logOpen(type: String) = update { it.log(type) }

    // ---- 개발자 도구 ----

    fun devReset() {
        clock.reset()
        update(sync = true) { CarlingData(karingId = UUID.randomUUID().toString(), consented = true, voiceOn = it.voiceOn) }
    }

    /** 시연 준비: 쌓인 상자를 모두 열고, 단계·개성·레벨·세트 축하 창을 이미 본 것으로 둔다. */
    fun devTidy() {
        openAll()
        update(sync = true) { d -> d.copy(unseenStage = null, unseenPersonality = null, unseenSetReward = false, unseenCards = emptyList(), levelSeen = LevelRules.of(d.totalGp)) }
    }

    /** 시연용 충전 기록: 지난 [days]일 동안 [every]일마다 한 번. */
    fun devRefuels(days: Int = 30, every: Int = 5) = update(sync = true) { d ->
        val now = clock.now()
        val list = (1..days / every).map { i -> Refuel(now - (i * every - 1) * Rules.DAY_MILLIS - 7 * Rules.HOUR_MILLIS, true, 18 + (i * 7) % 15, 80 + (i * 3) % 15, simulated = true) }
        d.copy(refuels = (d.refuels.filter { !it.simulated } + list).sortedBy { it.at })
    }

    /** 자동차 꾸미기가 든 럭키박스 [n]개(아직 없는 것부터). 시연용. */
    /** 시연용 젤리. */
    fun devAddJelly(n: Int) = update { it.copy(jelly = it.jelly + n) }

    /** 아직 없는 배지가 든 상자 [n]개(시연용: 진열장·세트 보상 확인). */
    fun devBadgeBoxes(n: Int = 6) = update(sync = true) { d ->
        val missing = Badge.entries.filter { (d.badges[it] ?: 0) == 0 }.ifEmpty { Badge.entries }
        val start = (d.boxes.maxOfOrNull { it.boxId } ?: 0) + 1
        d.copy(boxes = d.boxes + List(n) { i -> LuckyBox(start + i, -1L, 10 + i * 2, missing[i % missing.size], null, clock.now()) })
    }

    fun devPartBoxes(n: Int = 6) = update(sync = true) { d ->
        val missing = CarPart.entries.filter { (d.parts[it] ?: 0) == 0 }.ifEmpty { CarPart.entries }
        val start = (d.boxes.maxOfOrNull { it.boxId } ?: 0) + 1
        val now = clock.now()
        val colors = CarColor.collectible.filter { (d.colors[it] ?: 0) == 0 }.ifEmpty { CarColor.collectible }
        d.copy(boxes = d.boxes + List(n) { i ->
            if (i % 2 == 1) LuckyBox(start + i, -1L, 10 + i * 3, null, null, now, color = colors[(i / 2) % colors.size])
            else LuckyBox(start + i, -1L, 10 + i * 3, null, missing[(i * 5) % missing.size], now)
        })
    }

    fun devAddGp(gp: Int) = update(sync = true) { d -> updateStage(d.copy(totalGp = d.totalGp + gp, rewards = if (d.rewards.isEmpty()) mapOf(-1L to Reward(clock.now(), 0, null)) else d.rewards)) }

    /** 과거의 운행 하나를 확정 상태로 넣는다. [purpose]가 있으면 완성까지 해서 보상이 지급된다. */
    fun devPastTrip(startAt: Long, km: Float, movingMin: Int, harsh: Int, purpose: TripPurpose?, companion: work.integro.carling.game.Companion? = null, from: String? = null, to: String? = null) {
        if (data.trip(startAt) != null) return
        val park = startAt + movingMin * Rules.MINUTE_MILLIS
        // 시연용 전비: 출퇴근은 좋고, 짧은 업무·밤 운행은 조금 낮게, 급조작이 많으면 더 낮게
        val eff = (5.8f - (if (km < 15f) 0.5f else 0f) - harsh.coerceAtMost(4) * 0.25f + ((startAt / 7919) % 7) * 0.08f).coerceAtLeast(3.2f)
        val record = TripRecord(startAt, TripStatus.CONFIRMED, startAt, lastParkAt = park, confirmedAt = park + Rules.STOP_MERGE_MILLIS, lastSnapshotAt = park, distanceKm = km, movingSec = movingMin * 60L, harsh = harsh, simulated = true, ev = true, efficiency = eff, startDong = from, endDong = to)
        update(sync = true) { d ->
            val number = (d.cards.maxOfOrNull { it.number } ?: 0) + 1
            val card = TripCard(startAt, number, createdAt = park, state = if (purpose != null) CardState.COMPLETED else CardState.DRAFT, completedAt = if (purpose != null) park else null, purpose = purpose, companion = companion)
            val withTrip = d.copy(trips = (d.trips + record).sortedBy { it.startedAt }.takeLast(MAX_TRIPS))
            val withCard = if (Rules.meetsCardMinimum(km, movingMin * 60L, Rules.REAL_CARD_MIN_KM, Rules.REAL_CARD_MIN_MOVING_SEC)) withTrip.copy(cards = (withTrip.cards + card).takeLast(MAX_TRIPS)) else withTrip
            grant(withCard, startAt)
        }
    }

    private companion object {
        const val MAX_TRIPS = 3000
        const val MAX_TAGS = 12
        const val MAX_EVENTS = 400
    }
}

/** 상태 문자열 하나를 읽고 쓰는 저장소. */
interface Storage {
    fun read(): String?
    fun write(value: String, sync: Boolean)
}

/** 기기 안 SharedPreferences. */
class PrefsStorage(context: Context) : Storage {
    private val prefs = context.getSharedPreferences("carling", Context.MODE_PRIVATE)

    init {
        // 카링월드(방 꾸미기) 시절 저장값은 새 규칙과 맞지 않아 지운다
        LEGACY_KEYS.forEach { key -> if (prefs.contains(key)) prefs.edit().remove(key).apply() }
    }

    override fun read(): String? = prefs.getString(KEY_DATA, null)

    override fun write(value: String, sync: Boolean) {
        val edit = prefs.edit().putString(KEY_DATA, value)
        if (sync) edit.commit() else edit.apply()
    }

    private companion object {
        const val KEY_DATA = "mycarling_v1"
        val LEGACY_KEYS = listOf("data", "tracks", "world_v1")
    }
}
