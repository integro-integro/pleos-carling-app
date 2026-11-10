package work.integro.carling.game

import kotlinx.serialization.Serializable

/**
 * 카링이 지낼 수 있는 공간. 진화 단계마다 공간의 개념이 넓어지고(알: 방 → 아기 카링: 집 → …),
 * 다음 진화까지의 레벨 사이에는 같은 개념 안의 공간이 하나씩 열린다.
 * 집 안 공간의 열리는 레벨(10·13·16·18)은 사용자가 확정했다(2026-11-10).
 */
@Serializable
enum class Space(val label: String, val icon: String, val area: String, val unlockLevel: Int, val floor: Int) {
    BEDROOM("방", "🛏️", "방", 1, 2),
    LIVING("거실", "🛋️", "집", 10, 1),
    KITCHEN("주방", "🍽️", "집", 13, 1),
    BATHROOM("화장실", "🛁", "집", 16, 1),
    STUDIO("작업실", "🎨", "집", 18, 2);

    fun isOpen(level: Int): Boolean = level >= unlockLevel

    val floorLabel: String get() = "${floor}F"

    val items: List<RoomItem> get() = RoomItem.entries.filter { it.space == this }

    companion object {
        /** 공간 메뉴가 생기는 레벨. */
        const val MENU_LEVEL = 10
    }
}

/** 아직 만들지 않은 다음 공간 개념. 메뉴에 잠금으로만 보여준다. */
data class FutureArea(val label: String, val icon: String, val evolution: String, val level: Int)

val FUTURE_AREAS = listOf(
    FutureArea("정원", "🌷", "계열 진화 1", 20),
    FutureArea("집앞 해변", "🏖️", "계열 진화 2", 30),
    FutureArea("동산·숲", "🌳", "계열 진화 3", 50),
    FutureArea("하늘 전망대", "🔭", "계열 진화 4", 70),
    FutureArea("하늘 섬", "☁️", "계열 진화 5", 99),
)

/**
 * 새 아이템이 놓일 공간: 카링이 있는 공간을 먼저 채우고, 다 채웠으면 열린 다른 공간을 순서대로 채운다.
 * 열린 공간을 모두 채웠으면 null(중복 지급).
 */
fun nextItemFor(current: Space, level: Int, placed: Collection<RoomItem>): RoomItem? {
    val spaces = listOf(current) + Space.entries.filter { it != current && it.isOpen(level) }
    for (space in spaces) {
        space.items.firstOrNull { it !in placed }?.let { return it }
    }
    return null
}

/**
 * 공간의 출입구(문·계단·아치). 거실이 집의 중심이라 계단으로 2층(방·작업실), 아치로 주방, 문으로 화장실에 간다.
 * 다른 공간은 왼쪽 벽의 문으로 거실(2층이면 다른 2층 공간)로 나간다. 위치는 방 그림 대비 비율이다.
 */
data class Exit(
    val label: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val targets: List<Space>,
    /** 카링이 걸어가 서는 자리(발). */
    val walkX: Float,
    val walkY: Float,
)

private const val DOOR_L = 0.012f
private const val DOOR_T = 0.14f
private const val DOOR_R = 0.108f
private const val DOOR_B = 0.615f

fun exitsOf(space: Space): List<Exit> = when (space) {
    Space.LIVING -> listOf(
        Exit("2층으로", 0f, 0.02f, 0.21f, 0.68f, listOf(Space.BEDROOM, Space.STUDIO), 0.2f, 0.71f),
        Exit("화장실", 0.235f, 0.17f, 0.34f, 0.54f, listOf(Space.BATHROOM), 0.29f, 0.63f),
        Exit("주방", 0.735f, 0.16f, 0.91f, 0.56f, listOf(Space.KITCHEN), 0.82f, 0.63f),
    )
    Space.BEDROOM -> listOf(Exit("복도", DOOR_L, DOOR_T, DOOR_R, DOOR_B, listOf(Space.LIVING, Space.STUDIO), 0.13f, 0.67f))
    Space.STUDIO -> listOf(Exit("복도", DOOR_L, DOOR_T, DOOR_R, DOOR_B, listOf(Space.LIVING, Space.BEDROOM), 0.13f, 0.67f))
    else -> listOf(Exit("거실로", DOOR_L, DOOR_T, DOOR_R, DOOR_B, listOf(Space.LIVING), 0.13f, 0.67f))
}

/** [from]에서 [to]로 들어올 때 카링이 나타나는 출입구. */
fun arrivalExit(to: Space, from: Space): Exit {
    val exits = exitsOf(to)
    return exits.firstOrNull { from in it.targets } ?: exits.first()
}

/** 배경에 그려진 기본 가구. 아이템처럼 누르면 카링이 가서 교감한다. */
fun fixturesOf(space: Space): List<Fixture> = when (space) {
    Space.BEDROOM -> listOf(Fixture("소파", .52f, .36f, .79f, .57f, Interaction(Action.SIT, "푹신한 소파, 좋아요~", "☺️", .655f, .66f, .655f, .535f)))
    Space.LIVING -> listOf(Fixture("소파", .36f, .36f, .73f, .57f, Interaction(Action.SIT, "소파에서 쉬는 게 최고!", "☺️", .45f, .66f, .45f, .535f)))
    Space.KITCHEN -> listOf(Fixture("개수대", .19f, .43f, .52f, .62f, Interaction(Action.PLAY, "손 깨끗이 씻었어요!", "🫧", .33f, .68f)))
    Space.BATHROOM -> listOf(Fixture("욕조", .19f, .42f, .47f, .63f, Interaction(Action.PLAY, "첨벙첨벙 목욕 시간!", "🛁", .33f, .70f)))
    Space.STUDIO -> listOf(Fixture("책상", .225f, .43f, .51f, .60f, Interaction(Action.READ, "오늘은 뭘 만들어 볼까?", "✏️", .27f, .74f)))
}

/** 카링월드의 지역. 지도 그림 대비 위치와, 열리는 진화 단계. */
enum class WorldArea(val label: String, val icon: String, val x: Float, val y: Float, val needs: String, val level: Int, val form: Form?) {
    HOME("카링의 집", "🏠", 0.12f, 0.74f, "처음부터", 1, Form.EGG),
    GARDEN("카링의 정원", "🌷", 0.38f, 0.66f, "계열 진화 1", 20, Form.EVO1),
    FOREST("동산·숲", "🌳", 0.44f, 0.3f, "계열 진화 3", 50, null),
    BEACH("집앞 해변", "🏖️", 0.72f, 0.8f, "계열 진화 2", 30, null),
    OBSERVATORY("하늘 전망대", "🔭", 0.68f, 0.24f, "계열 진화 4", 70, null),
    SKY_ISLAND("하늘 섬", "☁️", 0.9f, 0.12f, "계열 진화 5", 99, null);

    /** 진화 단계에 도달해 안개가 걷혔는지. 아직 외형이 없는 단계([form] = null)는 잠겨 있다. */
    fun isOpen(current: Form): Boolean = form != null && current >= form
}
