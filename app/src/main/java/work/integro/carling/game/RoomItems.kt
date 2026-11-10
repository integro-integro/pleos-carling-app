package work.integro.carling.game

import kotlinx.serialization.Serializable

/**
 * 운행으로 얻어 공간에 자동으로 놓이는 아이템. 방 24종(기획안 기준안) + 집의 공간마다 12종.
 * 위치 값은 방 그림 대비 비율이다. 사용자가 옮기는 기능은 없다.
 * @param x 가운데 가로 위치
 * @param foot 바닥(아래 끝) 세로 위치
 * @param width 폭
 * @param aspect 세로/가로 비율
 * @param beside 카링과 앞뒤로 겹칠 수 있는 바닥 가구인지. 벽 장식과 러그는 언제나 카링 뒤에 있다
 * @param depth 그리는 순서. 받침 가구 위의 소품은 받침보다 조금 크다
 */
@Serializable
enum class RoomItem(
    val label: String,
    val category: String,
    val x: Float,
    val foot: Float,
    val width: Float,
    val aspect: Float,
    val beside: Boolean,
    val depth: Float,
    val space: Space = Space.BEDROOM,
) {
    RUG("러그", "가구·바닥", 0.5f, 0.8f, 0.4f, .2985f, false, 0.55f),
    SMALL_POT("작은 화분", "식물", 0.335f, 0.405f, 0.05f, 1.3021f, false, 0.22f),
    BED("침대", "가구·바닥", 0.865f, 0.86f, 0.21f, 1.4449f, true, 0.86f),
    NIGHTSTAND("협탁", "가구·바닥", 0.7f, 0.745f, 0.085f, .9426f, true, 0.745f),
    LAMP("스탠드", "조명", 0.7f, 0.668f, 0.055f, 1.4255f, true, 0.746f),
    PICTURE("풍경 액자", "벽·창문", 0.175f, 0.36f, 0.095f, .7960f, false, 0.1f),
    TABLE("낮은 테이블", "가구·바닥", 0.55f, 0.775f, 0.15f, .5743f, true, 0.775f),
    MUG("머그컵", "생활 소품", 0.555f, 0.7f, 0.03f, 1.3550f, true, 0.776f),
    CURTAINS("커튼", "벽·창문", 0.345f, 0.405f, 0.285f, .8611f, false, 0.2f),
    CUSHION("바닥 쿠션", "가구·바닥", 0.36f, 0.8f, 0.085f, .8197f, true, 0.8f),
    CABINET("수납장", "가구·바닥", 0.18f, 0.605f, 0.14f, .5249f, true, 0.605f),
    VASE("꽃병", "식물", 0.15f, 0.52f, 0.035f, 1.7178f, true, 0.606f),
    RADIO("작은 라디오", "생활 소품", 0.72f, 0.235f, 0.075f, .8796f, false, 0.1f),
    BIG_POT("큰 화분", "식물", 0.49f, 0.585f, 0.05f, 2.0878f, true, 0.585f),
    CLOCK("벽시계", "벽·창문", 0.86f, 0.3f, 0.06f, 1.0354f, false, 0.1f),
    BEDDING("침구 세트", "생활 소품", 0.865f, 0.86f, 0.211f, 1.4385f, true, 0.861f),
    TEDDY("곰 인형", "생활 소품", 0.6f, 0.515f, 0.05f, 1.3262f, false, 0.5f),
    PENDANT("펜던트 조명", "조명", 0.52f, 0.18f, 0.07f, 1.8196f, false, 0.3f),
    BOOKS("책 묶음", "생활 소품", 0.215f, 0.52f, 0.05f, .6411f, true, 0.607f),
    WALL_DECOR("벽걸이 장식", "벽·창문", 0.615f, 0.31f, 0.16f, .2893f, false, 0.1f),
    WALL_LIGHT("벽 조명", "조명", 0.955f, 0.4f, 0.045f, 1.6268f, false, 0.1f),
    HANGING_PLANT("행잉 플랜트", "식물", 0.935f, 0.25f, 0.06f, 2.2806f, false, 0.1f),
    STAR_LIGHTS("별 전구줄", "조명", 0.345f, 0.165f, 0.3f, .1808f, false, 0.205f),
    WINDOW_DECOR("창문 장식", "벽·창문", 0.345f, 0.41f, 0.2f, 1.0824f, false, 0.21f),

    // ---- 집: 거실·주방·화장실·작업실 (공간마다 12종) ----
    LIVING_RUG("거실 러그", "가구·바닥", 0.52f, 0.83f, 0.42f, 0.2854f, false, 0.55f, Space.LIVING),
    COFFEE_TABLE("커피 테이블", "가구·바닥", 0.54f, 0.75f, 0.17f, 0.4049f, true, 0.75f, Space.LIVING),
    ARMCHAIR("안락의자", "가구·바닥", 0.31f, 0.8f, 0.13f, 0.8876f, true, 0.8f, Space.LIVING),
    BIG_PLANT("떡갈고무나무", "식물", 0.355f, 0.585f, 0.06f, 2.1391f, true, 0.585f, Space.LIVING),
    FRUIT_BOWL("과일 그릇", "생활 소품", 0.545f, 0.695f, 0.045f, 0.8021f, true, 0.751f, Space.LIVING),
    FLOOR_LAMP("플로어 스탠드", "조명", 0.745f, 0.585f, 0.05f, 3.5729f, true, 0.585f, Space.LIVING),
    TV_CONSOLE("TV 장식장", "가구·바닥", 0.82f, 0.77f, 0.19f, 0.7418f, true, 0.77f, Space.LIVING),
    WALL_ART("꽃 액자", "벽·창문", 0.655f, 0.25f, 0.12f, 0.4652f, false, 0.1f, Space.LIVING),
    SIDE_TABLE("사이드 테이블", "가구·바닥", 0.18f, 0.84f, 0.07f, 1.0149f, true, 0.84f, Space.LIVING),
    RECORD_PLAYER("레코드 플레이어", "생활 소품", 0.18f, 0.78f, 0.07f, 1.2313f, true, 0.841f, Space.LIVING),
    BOOKSHELF("책장", "가구·바닥", 0.955f, 0.66f, 0.075f, 1.9306f, true, 0.66f, Space.LIVING),
    LIVING_CURTAINS("거실 커튼", "벽·창문", 0.49f, 0.37f, 0.22f, 0.9218f, false, 0.2f, Space.LIVING),
    HERB_POTS("허브 화분", "식물", 0.41f, 0.395f, 0.08f, 0.4183f, false, 0.21f, Space.KITCHEN),
    TOASTER("토스터", "주방 소품", 0.46f, 0.455f, 0.05f, 1.0208f, true, 0.62f, Space.KITCHEN),
    STOVE("오븐 레인지", "가전", 0.66f, 0.62f, 0.13f, 1.4056f, true, 0.62f, Space.KITCHEN),
    POT("냄비", "주방 소품", 0.66f, 0.5f, 0.05f, 0.7500f, true, 0.621f, Space.KITCHEN),
    DINING_TABLE("식탁", "가구·바닥", 0.63f, 0.8f, 0.19f, 0.8626f, true, 0.8f, Space.KITCHEN),
    TEAPOT("티포트 세트", "주방 소품", 0.63f, 0.725f, 0.05f, 0.7188f, true, 0.801f, Space.KITCHEN),
    DINING_CHAIRS("식탁 의자", "가구·바닥", 0.63f, 0.735f, 0.27f, 0.7452f, true, 0.735f, Space.KITCHEN),
    FRIDGE("냉장고", "가전", 0.88f, 0.64f, 0.11f, 1.7299f, true, 0.64f, Space.KITCHEN),
    KITCHEN_RUG("주방 러그", "가구·바닥", 0.6f, 0.855f, 0.42f, 0.1203f, false, 0.55f, Space.KITCHEN),
    VEG_BASKET("채소 바구니", "주방 소품", 0.24f, 0.79f, 0.075f, 1.0069f, true, 0.79f, Space.KITCHEN),
    KITCHEN_SHELF("그릇 선반", "벽·창문", 0.7f, 0.3f, 0.19f, 0.6099f, false, 0.1f, Space.KITCHEN),
    KITCHEN_LAMP("주방 펜던트", "조명", 0.52f, 0.2f, 0.07f, 1.9104f, false, 0.3f, Space.KITCHEN),
    RUBBER_DUCK("고무 오리", "생활 소품", 0.4f, 0.505f, 0.035f, 1.1979f, false, 0.3f, Space.BATHROOM),
    BATH_CANDLES("향초 세트", "조명", 0.53f, 0.68f, 0.075f, 0.6944f, true, 0.68f, Space.BATHROOM),
    WASHBASIN("세면대", "욕실 가구", 0.66f, 0.63f, 0.085f, 1.6626f, true, 0.63f, Space.BATHROOM),
    SOAP_SET("세면 도구", "생활 소품", 0.645f, 0.505f, 0.04f, 0.6979f, true, 0.631f, Space.BATHROOM),
    MIRROR("거울", "벽·창문", 0.66f, 0.36f, 0.085f, 1.0429f, false, 0.1f, Space.BATHROOM),
    BATH_MAT("욕실 매트", "가구·바닥", 0.5f, 0.84f, 0.3f, 0.2760f, false, 0.55f, Space.BATHROOM),
    TOWEL_RACK("수건걸이", "욕실 가구", 0.86f, 0.66f, 0.085f, 1.4110f, true, 0.66f, Space.BATHROOM),
    BATH_PLANT("욕실 화분", "식물", 0.21f, 0.8f, 0.06f, 1.2000f, true, 0.8f, Space.BATHROOM),
    BATH_STOOL("욕실 의자", "욕실 가구", 0.45f, 0.74f, 0.055f, 0.9905f, true, 0.74f, Space.BATHROOM),
    LAUNDRY_BASKET("빨래 바구니", "생활 소품", 0.8f, 0.8f, 0.075f, 0.8958f, true, 0.8f, Space.BATHROOM),
    BATH_SHELF("욕실 선반", "벽·창문", 0.83f, 0.3f, 0.15f, 0.5139f, false, 0.1f, Space.BATHROOM),
    BATH_LAMP("욕실 벽등", "조명", 0.555f, 0.3f, 0.04f, 1.4167f, false, 0.1f, Space.BATHROOM),
    DESK_CHAIR("책상 의자", "가구·바닥", 0.37f, 0.7f, 0.08f, 1.6863f, true, 0.7f, Space.STUDIO),
    MUSIC_BOX("오르골", "생활 소품", 0.37f, 0.445f, 0.045f, 1.4896f, false, 0.3f, Space.STUDIO),
    DESK_LAMP("책상 스탠드", "조명", 0.27f, 0.445f, 0.05f, 1.4583f, false, 0.3f, Space.STUDIO),
    SKETCHBOOKS("스케치북", "생활 소품", 0.46f, 0.445f, 0.055f, 0.6000f, false, 0.3f, Space.STUDIO),
    STUDIO_RUG("작업실 러그", "가구·바닥", 0.5f, 0.83f, 0.38f, 0.2689f, false, 0.55f, Space.STUDIO),
    EASEL("이젤", "작업 도구", 0.66f, 0.7f, 0.1f, 1.8125f, true, 0.7f, Space.STUDIO),
    STUDIO_BOOKCASE("수납 책장", "가구·바닥", 0.83f, 0.64f, 0.21f, 0.3921f, true, 0.64f, Space.STUDIO),
    PAINT_JARS("물감 세트", "작업 도구", 0.79f, 0.537f, 0.05f, 0.8542f, true, 0.641f, Space.STUDIO),
    GLOBE("지구본", "생활 소품", 0.88f, 0.537f, 0.045f, 1.6354f, true, 0.642f, Space.STUDIO),
    MONSTERA("몬스테라", "식물", 0.2f, 0.8f, 0.08f, 1.2745f, true, 0.8f, Space.STUDIO),
    CORKBOARD("메모판", "벽·창문", 0.66f, 0.33f, 0.13f, 0.7831f, false, 0.1f, Space.STUDIO),
    CRAFT_SHELF("공예 선반", "벽·창문", 0.87f, 0.28f, 0.16f, 0.4397f, false, 0.1f, Space.STUDIO);

    companion object {
        /** 공간마다 이 순서로 지급한다. 받침 가구가 소품보다 먼저 온다(enum 순서가 곧 지급 순서). */
        val ORDER: List<RoomItem> = entries
    }
}
