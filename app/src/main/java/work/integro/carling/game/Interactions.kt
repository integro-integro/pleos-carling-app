package work.integro.carling.game

/** 카링이 가구 곁에서 하는 행동. 보상은 없고 감상용이다. */
enum class Action { SIT, SLEEP, WATCH, EAT, DANCE, READ, SMELL, LIGHT, PLAY, ADMIRE }

/**
 * 가구 하나와의 교감.
 * @param approach 걸어가서 멈추는 바닥 자리(방 그림 대비)
 * @param seat 소파·침대처럼 올라가는 자리. null이면 바닥에서 한다
 */
data class Interaction(
    val action: Action,
    val line: String,
    val emote: String,
    val approachX: Float,
    val approachY: Float,
    val seatX: Float? = null,
    val seatY: Float? = null,
)

/** 배경에 그려진 기본 가구(침실 소파, 거실 소파, 주방 개수대 등). 아이템처럼 누를 수 있다. */
data class Fixture(
    val label: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val interaction: Interaction,
)

private fun floorX(x: Float) = x.coerceIn(0.16f, 0.88f)

/** 가구 앞의 바닥 자리. 받침 위 소품은 받침 가구 앞에 선다. */
private fun approach(item: RoomItem): Pair<Float, Float> {
    val base = if (item.beside) maxOf(item.foot, item.depth) else 0.74f
    return floorX(item.x) to (base + 0.05f).coerceIn(0.66f, 0.9f)
}

/** 아이템을 눌렀을 때 카링이 하는 일. */
fun interactionFor(item: RoomItem): Interaction {
    val (ax, ay) = approach(item)
    fun on(action: Action, line: String, emote: String) = Interaction(action, line, emote, ax, ay)
    fun seat(line: String, sx: Float, sy: Float, action: Action = Action.SIT, emote: String = "☺️") =
        Interaction(action, line, emote, ax, ay, sx, sy)
    return when (item) {
        RoomItem.BED, RoomItem.BEDDING -> seat("하암… 잠깐 잘게요.", .865f, .68f, Action.SLEEP, "💤")
        RoomItem.CUSHION -> seat("폭신폭신, 편해요~", .36f, .75f)
        RoomItem.ARMCHAIR -> seat("푹신한 의자 최고!", .30f, .745f)
        RoomItem.DINING_CHAIRS -> seat("밥 먹을 준비 끝!", .455f, .655f)
        RoomItem.DESK_CHAIR -> seat("오늘은 뭘 만들어 볼까?", .42f, .69f)
        RoomItem.BATH_STOOL -> seat("잠깐 앉아서 쉬어요.", .45f, .705f)
        RoomItem.TV_CONSOLE -> on(Action.WATCH, "와, 재밌다! 하하!", "😆")
        RoomItem.MUG -> on(Action.EAT, "음~ 커피 맛있다!", "☕")
        RoomItem.TEAPOT -> on(Action.EAT, "따뜻한 차, 맛있어요.", "🍵")
        RoomItem.FRUIT_BOWL -> on(Action.EAT, "사과 한 입, 냠!", "🍎")
        RoomItem.POT -> on(Action.EAT, "보글보글, 맛있는 냄새~", "😋")
        RoomItem.VEG_BASKET -> on(Action.EAT, "갓 구운 빵 냄새!", "🥖")
        RoomItem.TOASTER -> on(Action.EAT, "바삭바삭 토스트!", "🍞")
        RoomItem.RADIO, RoomItem.RECORD_PLAYER, RoomItem.MUSIC_BOX -> on(Action.DANCE, "룰루랄라~", "🎵")
        RoomItem.LAMP, RoomItem.FLOOR_LAMP, RoomItem.DESK_LAMP, RoomItem.PENDANT, RoomItem.WALL_LIGHT,
        RoomItem.KITCHEN_LAMP, RoomItem.BATH_LAMP, RoomItem.STAR_LIGHTS, RoomItem.BATH_CANDLES -> on(Action.LIGHT, "톡! 따뜻한 불빛이에요.", "💡")
        RoomItem.BOOKS, RoomItem.BOOKSHELF, RoomItem.SKETCHBOOKS, RoomItem.STUDIO_BOOKCASE -> on(Action.READ, "이 책 정말 재밌어요.", "📖")
        RoomItem.SMALL_POT, RoomItem.BIG_POT, RoomItem.HANGING_PLANT, RoomItem.BIG_PLANT, RoomItem.HERB_POTS,
        RoomItem.BATH_PLANT, RoomItem.MONSTERA, RoomItem.VASE -> on(Action.SMELL, "음~ 싱그러운 냄새.", "🌿")
        RoomItem.RUBBER_DUCK -> on(Action.PLAY, "꽥꽥! 귀여워!", "🐤")
        RoomItem.GLOBE -> on(Action.PLAY, "다음엔 어디로 달려 볼까?", "🌍")
        RoomItem.EASEL, RoomItem.PAINT_JARS -> on(Action.PLAY, "쓱쓱, 그림 그리자!", "🎨")
        RoomItem.MIRROR -> on(Action.ADMIRE, "오늘도 귀엽다!", "✨")
        RoomItem.RUG, RoomItem.LIVING_RUG, RoomItem.KITCHEN_RUG, RoomItem.BATH_MAT, RoomItem.STUDIO_RUG -> on(Action.PLAY, "폭신폭신 데굴데굴!", "💫")
        else -> on(Action.ADMIRE, "${item.label}, 예쁘다!", "💕")
    }
}
