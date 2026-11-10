package work.integro.carling.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import work.integro.carling.game.Form
import work.integro.carling.game.GameClock
import work.integro.carling.game.GrowthRules
import work.integro.carling.game.Lineage
import work.integro.carling.game.Quest
import work.integro.carling.game.QuestKind
import work.integro.carling.game.RoomItem
import work.integro.carling.game.Space
import work.integro.carling.game.nextItemFor
import java.util.UUID

private const val TAG = "CarlingStore"

/** 끝난 운행 한 번의 원재료. TripTracker가 만든다. */
data class TripResult(
    val startMillis: Long,
    val endMillis: Long,
    val distanceKm: Float?,
    val movingSeconds: Long?,
    val harshEvents: Int?,
    val simulated: Boolean = false,
)

/**
 * 카링월드 상태를 기기 안에 저장한다.
 * 운행이 끝나면 카드·XP·아이템·친밀도·퀘스트를 한 번에 저장한다. 화면을 보지 않아도 결과는 남는다.
 * 모든 변경은 메인 스레드에서 호출한다.
 */
class CarlingStore(context: Context, val clock: GameClock) {

    private val prefs = context.getSharedPreferences("carling", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var data by mutableStateOf(load())
        private set

    private fun load(): CarlingData {
        // 리뉴얼 이전 저장값은 새 규칙과 맞지 않아 지운다
        LEGACY_KEYS.forEach { key -> if (prefs.contains(key)) prefs.edit().remove(key).apply() }
        val loaded = prefs.getString(KEY_DATA, null)?.let {
            runCatching { json.decodeFromString(CarlingData.serializer(), it) }
                .onFailure { e -> Log.w(TAG, "저장 데이터 복구 실패, 처음부터 시작", e) }
                .getOrNull()
        }
        return (loaded ?: newWorld()).let { if (it.carlingId.isEmpty()) it.copy(carlingId = UUID.randomUUID().toString()) else it }
    }

    /** 새 카링은 시작 퀘스트 하나를 갖고 시작한다. */
    private fun newWorld() = CarlingData(
        carlingId = UUID.randomUUID().toString(),
        activeQuest = Quest(GrowthRules.questKind(0)),
        questSequence = 1,
    )

    private fun update(sync: Boolean = false, block: (CarlingData) -> CarlingData) {
        data = block(data)
        val edit = prefs.edit().putString(KEY_DATA, json.encodeToString(CarlingData.serializer(), data))
        if (sync) edit.commit() else edit.apply()
    }

    // ---- 운행 ----

    fun beginTrip(startMillis: Long, startOdometerKm: Float?) =
        update(sync = true) { it.copy(activeTrip = ActiveTrip(startMillis, startOdometerKm)) }

    fun saveMovingSeconds(seconds: Long) =
        update { d -> d.copy(activeTrip = d.activeTrip?.copy(movingSeconds = seconds)) }

    fun clearActiveTrip() = update(sync = true) { it.copy(activeTrip = null) }

    /** 운행 하나를 반영한다. 조건을 못 채우면 카드는 없고, 실제로 움직였다면 운전일만 남는다. */
    fun recordTrip(result: TripResult) {
        if (data.tripCards.any { it.sessionId == result.startMillis }) {
            Log.i(TAG, "이미 처리한 운행: ${result.startMillis}")
            return
        }
        update(sync = true) { applyTrip(it, result) }
    }

    private fun applyTrip(d: CarlingData, r: TripResult): CarlingData {
        val moved = (r.distanceKm ?: 0f) > 0f || (r.movingSeconds ?: 0L) > 0L
        val movedDays = if (moved) datesBetween(r.startMillis, r.endMillis) else emptySet()
        val base = d.copy(drivingDays = d.drivingDays + movedDays)
        if (!GrowthRules.isValidTrip(r.distanceKm, r.movingSeconds)) {
            Log.i(TAG, "카드 조건 미달: ${r.distanceKm}km ${r.movingSeconds}s")
            return base
        }

        val day = clock.dateOf(r.endMillis).toString()
        val dayOrder = (d.dayValidTrips[day] ?: 0) + 1
        val score = GrowthRules.safetyScore(r.harshEvents)
        val (xpBase, xpBonus) = GrowthRules.tripXp(dayOrder, score)
        val beforeLevel = GrowthRules.levelOf(d.totalXp)
        val totalXp = d.totalXp + xpBase + xpBonus
        val afterLevel = GrowthRules.levelOf(totalXp)

        // 아이템: 카링이 있는 공간부터 아직 없는 것을 순서대로 지급하고, 열린 공간을 다 채웠으면 중복 수량만 늘린다
        val nextNew = nextItemFor(d.currentSpace, afterLevel, d.placed)
        val owned = d.placed.ifEmpty { RoomItem.ORDER }
        val item = nextNew ?: owned[d.tripCards.size % owned.size]
        val isNew = nextNew != null

        // 퀘스트: 먼저 진행 중인 것에 반영하고, 이번에 새로 생긴 퀘스트는 다음 운행부터 센다
        var active = d.activeQuest?.let { q ->
            q.copy(trips = q.trips + 1, seconds = q.seconds + (r.movingSeconds ?: 0L), days = q.days + day)
        }
        var questsDone = d.questsDone
        var questLine = active?.let { "${it.kind.title} ${it.progress}/${it.kind.target}${it.kind.unit}" }
        if (active?.done == true) {
            questsDone++
            questLine = "퀘스트 완료 · ${active.kind.title}"
            active = null
        }
        var sequence = d.questSequence
        val queue = d.questQueue.toMutableList()
        repeat(afterLevel - beforeLevel) { queue += GrowthRules.questKind(sequence++) }
        val form = d.form
        if (active == null) {
            // 퀘스트가 모자라 진화가 영원히 막히지 않도록, 대기열이 비면 다음 것을 보충한다
            if (queue.isEmpty() && form.next != null) queue += GrowthRules.questKind(sequence++)
            active = queue.removeFirstOrNull()?.let { Quest(it) }
        }

        val number = (d.tripCards.lastOrNull()?.number ?: 0) + 1
        val card = TripCard(
            number = number,
            sessionId = r.startMillis,
            date = day,
            startMillis = r.startMillis,
            endMillis = r.endMillis,
            distanceKm = r.distanceKm,
            movingSeconds = r.movingSeconds,
            score = score,
            dayOrder = dayOrder,
            xpBase = xpBase,
            xpBonus = xpBonus,
            item = item,
            newPlacement = isNew,
            questLine = questLine,
            simulated = r.simulated,
        )
        return base.copy(
            totalXp = totalXp,
            dayValidTrips = (d.dayValidTrips + (day to dayOrder)).toList().takeLast(14).toMap(),
            placed = if (isNew) d.placed + item else d.placed,
            duplicates = if (isNew) d.duplicates else d.duplicates + (item to ((d.duplicates[item] ?: 0) + 1)),
            activeQuest = active,
            questQueue = queue,
            questSequence = sequence,
            questsDone = questsDone,
            tripCards = (d.tripCards + card).takeLast(MAX_CARDS),
            unseenCards = d.unseenCards + number,
            newItems = if (isNew) d.newItems + item else d.newItems,
        )
    }

    private fun datesBetween(startMillis: Long, endMillis: Long): Set<String> {
        val out = linkedSetOf<String>()
        var date = clock.dateOf(startMillis)
        val end = clock.dateOf(endMillis)
        while (!date.isAfter(end)) {
            out += date.toString()
            date = date.plusDays(1)
        }
        return out
    }

    // ---- 보여주기 확인 ----

    fun finishIntro() = update { it.copy(introSeen = true) }
    fun ackCards() = update { it.copy(unseenCards = emptyList()) }
    fun ackLevel() = update { it.copy(seenLevel = it.level) }
    fun ackItems() = update { it.copy(newItems = emptyList()) }

    // ---- 진화 ----

    /** 조건을 채웠을 때만 진화한다. 계열 진화 1에서는 [lineage]를 고른다. 레벨·친밀도·퀘스트는 차감하지 않는다. */
    fun evolve(lineage: Lineage? = null) {
        if (!data.canEvolve) return
        val next = data.form.next ?: return
        if (next == Form.EVO1 && lineage == null) return
        update(sync = true) {
            it.copy(
                form = next,
                lineage = if (next == Form.EVO1) lineage else it.lineage,
                personality = it.personality ?: Personality.entries[clock.hourOf(clock.now()) % Personality.entries.size],
            )
        }
    }

    /** 열린 공간으로 옮긴다. */
    fun moveTo(space: Space) {
        if (!space.isOpen(data.level) || space == data.currentSpace) return
        update { it.copy(currentSpace = space) }
    }

    fun giveName(value: String) = update { it.copy(name = value) }
    fun toggleVoice() = update { it.copy(voiceOn = !it.voiceOn) }

    // ---- 개발자 도구 ----

    fun devReset() {
        clock.reset()
        update(sync = true) { newWorld().copy(voiceOn = it.voiceOn, introSeen = true) }
    }

    /** 시뮬레이션용: 세 조건 중 레벨만 빠르게 올린다. */
    fun devAddXp(xp: Int) = update { it.copy(totalXp = it.totalXp + xp) }

    private companion object {
        const val KEY_DATA = "world_v1"
        /** 지난 카드는 모두 남긴다. 하루 5장씩 1년 반 정도의 안전 한도. */
        const val MAX_CARDS = 3000
        val LEGACY_KEYS = listOf("data", "tracks")
    }
}
