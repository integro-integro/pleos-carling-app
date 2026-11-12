package work.integro.carling.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
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
class CarlingStore(private val storage: Storage, val clock: Clock, private val random: Random = Random.Default) {

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
        return (loaded ?: CarlingData()).let { if (it.karingId.isEmpty()) it.copy(karingId = UUID.randomUUID().toString()) else it }
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

    /** TripStarted: 새 운행 ID 발급, 상태 = 기록 중. */
    fun startTrip(now: Long, odometerKm: Float?, simulated: Boolean = false): Long {
        update(sync = true) { d ->
            d.copy(trips = (d.trips + TripRecord(now, TripStatus.RECORDING, now, startOdometerKm = odometerKm, simulated = simulated)).takeLast(MAX_TRIPS))
        }
        return now
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
    fun complete(tripId: Long, purpose: TripPurpose, tags: List<String> = emptyList()) {
        expireCards()
        val card = data.card(tripId)?.takeIf { it.state == CardState.DRAFT } ?: return
        val clean = tags.mapNotNull(TripCardRules::cleanTag).distinct()
        update(sync = true) { d ->
            val done = card.copy(state = CardState.COMPLETED, completedAt = clock.now(), purpose = purpose, tags = clean)
            grant(
                d.replaceCard(done).copy(
                    unseenCards = d.unseenCards - tripId,
                    customTags = (d.customTags - clean.toSet() + clean).takeLast(MAX_TAGS),
                ).log("card_completed", tripId),
                tripId,
            )
        }
    }

    /** 완성 후 목적 수정. 보상을 받은 카드라면 개성을 다시 계산한다. */
    fun editPurpose(tripId: Long, purpose: TripPurpose) {
        val card = data.card(tripId)?.takeIf { it.state == CardState.COMPLETED && it.purpose != purpose } ?: return
        update(sync = true) { d -> recomputePersonality(d.replaceCard(card.copy(purpose = purpose))) }
    }

    /** 임시 카드를 모두 완성한다. 목적은 추천값, 없으면 '기타'. */
    fun completeAllDrafts() {
        expireCards()
        data.draftCards.forEach { c -> complete(c.tripId, suggestPurpose(c.tripId) ?: TripPurpose.OTHER, c.tags) }
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
            val c = LuckyBoxRules.draw { random.nextDouble() }
            LuckyBox((d.boxes.maxOfOrNull { it.boxId } ?: 0) + 1, tripId, c.bonusGp, c.badge, c.carItem, now)
        } else null
        val next = d.copy(
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
        if (before.body != after.body) parts += "몸 색이 ${after.body.colorName}이 되었어요 · ${r.bodyReason()}"
        if (before.decoration != after.decoration) {
            parts += after.decorationItem?.let { "${it.label}를 달았어요 · ${r.decorationReason()}" } ?: "장식을 내려놓았어요"
        }
        if (before.heartBright != after.heartBright) parts += if (after.heartBright) "심장이 선명하게 반짝여요 · ${r.heartReason()}" else "심장빛이 기본으로 돌아왔어요"
        return parts.joinToString("\n")
    }

    /** 럭키박스 열기. 내용은 상자 생성 때 정해져 있고, 열면 보너스 포인트·배지·자동차 아이템이 들어온다. */
    fun openBox(boxId: Int) {
        val box = data.boxes.firstOrNull { it.boxId == boxId && it.openedAt == null } ?: return
        update(sync = true) { d ->
            val opened = d.copy(
                boxes = d.boxes.map { if (it.boxId == boxId) it.copy(openedAt = clock.now()) else it },
                totalGp = d.totalGp + box.bonusGp,
                badges = box.badge?.let { d.badges + (it to (d.badges[it] ?: 0) + 1) } ?: d.badges,
                carItems = box.carItem?.let { d.carItems + (it to (d.carItems[it] ?: 0) + 1) } ?: d.carItems,
                // 처음 받은 자동차 아이템은 바로 붙여 준다
                equippedCarItem = d.equippedCarItem ?: box.carItem,
            ).log("box_opened", box.tripId)
            updateStage(opened)
        }
    }

    /** 자동차 꾸미기 아이템을 붙이거나(null이면 떼기) 바꾼다. 가진 것만. */
    fun equip(item: work.integro.carling.game.CarItem?) {
        if (item != null && (data.carItems[item] ?: 0) == 0) return
        update { it.copy(equippedCarItem = item) }
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

    fun devAddGp(gp: Int) = update(sync = true) { d -> updateStage(d.copy(totalGp = d.totalGp + gp, rewards = if (d.rewards.isEmpty()) mapOf(-1L to Reward(clock.now(), 0, null)) else d.rewards)) }

    /** 과거의 운행 하나를 확정 상태로 넣는다. [purpose]가 있으면 완성까지 해서 보상이 지급된다. */
    fun devPastTrip(startAt: Long, km: Float, movingMin: Int, harsh: Int, purpose: TripPurpose?) {
        if (data.trip(startAt) != null) return
        val park = startAt + movingMin * Rules.MINUTE_MILLIS
        val record = TripRecord(startAt, TripStatus.CONFIRMED, startAt, lastParkAt = park, confirmedAt = park + Rules.STOP_MERGE_MILLIS, lastSnapshotAt = park, distanceKm = km, movingSec = movingMin * 60L, harsh = harsh, simulated = true)
        update(sync = true) { d ->
            val number = (d.cards.maxOfOrNull { it.number } ?: 0) + 1
            val card = TripCard(startAt, number, createdAt = park, state = if (purpose != null) CardState.COMPLETED else CardState.DRAFT, completedAt = if (purpose != null) park else null, purpose = purpose)
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
