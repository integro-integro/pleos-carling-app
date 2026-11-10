package work.integro.carling.game

import kotlin.random.Random

/** 트립카드 희귀도. [weight]는 저장할 때 이 희귀도가 나올 상대 확률이다. */
enum class CarRarity(val label: String, val color: Long, val weight: Int) {
    COMMON("일반", 0xFF9FB3C8, 60),
    RARE("레어", 0xFF5AA9FF, 28),
    EPIC("에픽", 0xFFB57BFF, 10),
    LEGEND("전설", 0xFFFFC94A, 2),
}

/** 카드 앞면 미니카의 생김새. 그림을 받기 전까지 이 모양으로 그린다. */
enum class CarBody { HATCH, SEDAN, SUV, SPORTS, VAN, BOX, CONCEPT }

/**
 * 트립카드 앞면에 나오는 자동차(모두 100대). 1세대 포니부터 지금, 그리고 미래 콘셉트까지 현대·기아·제네시스 차를 모은다.
 * 희귀도는 '길에서 만나기 드문 정도'로 매겼다(예: GV60 마그마는 타는 사람이 적어 전설).
 */
data class CarModel(
    val id: String,
    val name: String,
    val year: Int,
    val rarity: CarRarity,
    val body: CarBody,
    val color: Long,
    val era: String,
)

object CarCatalog {
    val ALL: List<CarModel> = listOf(
        // 클래식
        CarModel("pony", "현대 포니", 1975, CarRarity.RARE, CarBody.HATCH, 0xFFF2C43D, "클래식"),
        CarModel("stellar", "현대 스텔라", 1983, CarRarity.COMMON, CarBody.SEDAN, 0xFFC9D4DC, "클래식"),
        CarModel("pony_excel", "현대 포니 엑셀", 1985, CarRarity.COMMON, CarBody.HATCH, 0xFFE0563F, "클래식"),
        CarModel("grandeur1", "현대 각 그랜저", 1986, CarRarity.RARE, CarBody.SEDAN, 0xFF2B2F3A, "클래식"),
        CarModel("pride", "기아 프라이드", 1987, CarRarity.COMMON, CarBody.HATCH, 0xFF4E9BD8, "클래식"),
        CarModel("scoupe", "현대 스쿠프", 1990, CarRarity.RARE, CarBody.SPORTS, 0xFFD8333A, "클래식"),
        CarModel("galloper", "현대 갤로퍼", 1991, CarRarity.COMMON, CarBody.SUV, 0xFF3F6B4E, "클래식"),
        CarModel("tiburon", "현대 티뷰론", 1996, CarRarity.EPIC, CarBody.SPORTS, 0xFF2F6FD8, "클래식"),
        // 지금
        CarModel("avante", "현대 아반떼", 1995, CarRarity.COMMON, CarBody.SEDAN, 0xFFE9ECEF, "지금"),
        CarModel("carnival", "기아 카니발", 1998, CarRarity.COMMON, CarBody.VAN, 0xFF1F2A44, "지금"),
        CarModel("santafe", "현대 싼타페", 2000, CarRarity.COMMON, CarBody.SUV, 0xFF8A8F98, "지금"),
        CarModel("sorento", "기아 쏘렌토", 2002, CarRarity.COMMON, CarBody.SUV, 0xFF5C6470, "지금"),
        CarModel("tucson", "현대 투싼", 2004, CarRarity.COMMON, CarBody.SUV, 0xFF6E8FAF, "지금"),
        CarModel("k5", "기아 K5", 2010, CarRarity.COMMON, CarBody.SEDAN, 0xFFB8202E, "지금"),
        CarModel("ray", "기아 레이", 2011, CarRarity.COMMON, CarBody.BOX, 0xFF8CCB6A, "지금"),
        CarModel("veloster", "현대 벨로스터", 2011, CarRarity.RARE, CarBody.HATCH, 0xFFFFA62B, "지금"),
        CarModel("g80", "제네시스 G80", 2016, CarRarity.RARE, CarBody.SEDAN, 0xFF4A3A35, "지금"),
        CarModel("stinger", "기아 스팅어", 2017, CarRarity.EPIC, CarBody.SPORTS, 0xFFE8632B, "지금"),
        CarModel("nexo", "현대 넥쏘", 2018, CarRarity.RARE, CarBody.SUV, 0xFF7FC6D9, "지금"),
        CarModel("palisade", "현대 팰리세이드", 2018, CarRarity.COMMON, CarBody.SUV, 0xFF2A3348, "지금"),
        CarModel("casper", "현대 캐스퍼", 2021, CarRarity.COMMON, CarBody.BOX, 0xFFD9F15A, "지금"),
        CarModel("ioniq5", "현대 아이오닉 5", 2021, CarRarity.COMMON, CarBody.HATCH, 0xFFB9C3BE, "지금"),
        CarModel("ev6", "기아 EV6", 2021, CarRarity.COMMON, CarBody.SPORTS, 0xFF5E7F8F, "지금"),
        CarModel("gv60", "제네시스 GV60", 2021, CarRarity.RARE, CarBody.SUV, 0xFF3E8A86, "지금"),
        CarModel("ioniq6", "현대 아이오닉 6", 2022, CarRarity.RARE, CarBody.SEDAN, 0xFF7C2D3A, "지금"),
        CarModel("ev9", "기아 EV9", 2023, CarRarity.RARE, CarBody.SUV, 0xFF2D5A4A, "지금"),
        CarModel("ioniq5n", "현대 아이오닉 5 N", 2023, CarRarity.EPIC, CarBody.HATCH, 0xFF8FB8D9, "지금"),
        CarModel("gv60_magma", "제네시스 GV60 마그마", 2025, CarRarity.LEGEND, CarBody.SUV, 0xFFFF6A1A, "지금"),
        // 미래
        CarModel("prophecy", "현대 프로페시 콘셉트", 2020, CarRarity.EPIC, CarBody.CONCEPT, 0xFFBFD8E6, "미래"),
        CarModel("nvision74", "현대 N 비전 74", 2022, CarRarity.LEGEND, CarBody.SPORTS, 0xFFE6E2D6, "미래"),
        CarModel("heartcar", "카링 하트카 2030", 2030, CarRarity.LEGEND, CarBody.CONCEPT, 0xFF5FE3C0, "미래"),
        // 더 모으기 — 클래식
        CarModel("brisa", "기아 브리사", 1974, CarRarity.RARE, CarBody.SEDAN, 0xFFE8D9A8, "클래식"),
        CarModel("bongo", "기아 봉고", 1980, CarRarity.COMMON, CarBody.VAN, 0xFF6E9FC7, "클래식"),
        CarModel("pony2", "현대 포니2", 1982, CarRarity.RARE, CarBody.HATCH, 0xFFD9573B, "클래식"),
        CarModel("sonata1", "현대 쏘나타", 1985, CarRarity.RARE, CarBody.SEDAN, 0xFF9AA4AE, "클래식"),
        CarModel("grace", "현대 그레이스", 1986, CarRarity.COMMON, CarBody.VAN, 0xFFE4E7EA, "클래식"),
        CarModel("concord", "기아 콩코드", 1987, CarRarity.COMMON, CarBody.SEDAN, 0xFF7B2F3A, "클래식"),
        CarModel("capital", "기아 캐피탈", 1989, CarRarity.COMMON, CarBody.SEDAN, 0xFFB8B2A6, "클래식"),
        CarModel("elantra1", "현대 엘란트라", 1990, CarRarity.COMMON, CarBody.SEDAN, 0xFF3B5E8C, "클래식"),
        CarModel("sephia", "기아 세피아", 1992, CarRarity.COMMON, CarBody.SEDAN, 0xFFD24F3E, "클래식"),
        CarModel("sportage1", "기아 스포티지", 1993, CarRarity.RARE, CarBody.SUV, 0xFF4F7A55, "클래식"),
        CarModel("marcia", "현대 마르샤", 1995, CarRarity.RARE, CarBody.SEDAN, 0xFF2E4A6B, "클래식"),
        CarModel("elan", "기아 엘란", 1996, CarRarity.EPIC, CarBody.SPORTS, 0xFFE0B23A, "클래식"),
        CarModel("dynasty", "현대 다이너스티", 1996, CarRarity.RARE, CarBody.SEDAN, 0xFF1E2230, "클래식"),
        CarModel("atoz", "현대 아토스", 1997, CarRarity.COMMON, CarBody.BOX, 0xFF8FD0E8, "클래식"),
        CarModel("verna", "현대 베르나", 1999, CarRarity.COMMON, CarBody.SEDAN, 0xFFE9C46A, "클래식"),
        CarModel("visto", "기아 비스토", 1999, CarRarity.COMMON, CarBody.BOX, 0xFFF29E4C, "클래식"),
        CarModel("equus1", "현대 에쿠스", 1999, CarRarity.RARE, CarBody.SEDAN, 0xFF262A33, "클래식"),
        CarModel("retona", "기아 레토나", 1998, CarRarity.COMMON, CarBody.SUV, 0xFF7A8B5A, "클래식"),
        CarModel("trajet", "현대 트라제 XG", 1999, CarRarity.COMMON, CarBody.VAN, 0xFFA7B4C2, "클래식"),
        CarModel("carens", "기아 카렌스", 1999, CarRarity.COMMON, CarBody.VAN, 0xFFC9B79C, "클래식"),
        // 더 모으기 — 2000년대
        CarModel("optima", "기아 옵티마", 2000, CarRarity.COMMON, CarBody.SEDAN, 0xFF8C96A3, "지금"),
        CarModel("terracan", "현대 테라칸", 2001, CarRarity.COMMON, CarBody.SUV, 0xFF5A6B4E, "지금"),
        CarModel("tuscani", "현대 투스카니", 2001, CarRarity.RARE, CarBody.SPORTS, 0xFFF2C230, "지금"),
        CarModel("lavita", "현대 라비타", 2001, CarRarity.COMMON, CarBody.BOX, 0xFFA9D18E, "지금"),
        CarModel("click", "현대 클릭", 2002, CarRarity.COMMON, CarBody.HATCH, 0xFFEB6F92, "지금"),
        CarModel("opirus", "기아 오피러스", 2003, CarRarity.RARE, CarBody.SEDAN, 0xFF3A3F4A, "지금"),
        CarModel("morning", "기아 모닝", 2004, CarRarity.COMMON, CarBody.HATCH, 0xFF8EC7F2, "지금"),
        CarModel("grandeur_tg", "현대 그랜저 TG", 2005, CarRarity.COMMON, CarBody.SEDAN, 0xFF5C5F66, "지금"),
        CarModel("veracruz", "현대 베라크루즈", 2006, CarRarity.RARE, CarBody.SUV, 0xFF6D5A4A, "지금"),
        CarModel("i30", "현대 i30", 2007, CarRarity.COMMON, CarBody.HATCH, 0xFFE24A4A, "지금"),
        CarModel("mohave", "기아 모하비", 2008, CarRarity.RARE, CarBody.SUV, 0xFF2F3A2F, "지금"),
        CarModel("genesis_coupe", "현대 제네시스 쿠페", 2008, CarRarity.EPIC, CarBody.SPORTS, 0xFFD93A2B, "지금"),
        CarModel("soul", "기아 쏘울", 2008, CarRarity.COMMON, CarBody.BOX, 0xFF6BC4A6, "지금"),
        CarModel("forte", "기아 포르테", 2008, CarRarity.COMMON, CarBody.SEDAN, 0xFFC8CDD3, "지금"),
        CarModel("k7", "기아 K7", 2009, CarRarity.COMMON, CarBody.SEDAN, 0xFF4A4F58, "지금"),
        CarModel("equus2", "현대 에쿠스 2세대", 2009, CarRarity.RARE, CarBody.SEDAN, 0xFF1A1D24, "지금"),
        // 더 모으기 — 2010년대
        CarModel("i40", "현대 i40", 2011, CarRarity.RARE, CarBody.SEDAN, 0xFF7FA6C9, "지금"),
        CarModel("k9", "기아 K9", 2012, CarRarity.RARE, CarBody.SEDAN, 0xFF2B2E36, "지금"),
        CarModel("maxcruz", "현대 맥스크루즈", 2013, CarRarity.COMMON, CarBody.SUV, 0xFF9A8F80, "지금"),
        CarModel("g90", "제네시스 G90", 2015, CarRarity.RARE, CarBody.SEDAN, 0xFF14161C, "지금"),
        CarModel("niro", "기아 니로", 2016, CarRarity.COMMON, CarBody.SUV, 0xFF5FAF9A, "지금"),
        CarModel("kona", "현대 코나", 2017, CarRarity.COMMON, CarBody.SUV, 0xFFC4E04A, "지금"),
        CarModel("stonic", "기아 스토닉", 2017, CarRarity.COMMON, CarBody.SUV, 0xFFF07A3E, "지금"),
        CarModel("g70", "제네시스 G70", 2017, CarRarity.RARE, CarBody.SEDAN, 0xFF3D5A80, "지금"),
        CarModel("venue", "현대 베뉴", 2019, CarRarity.COMMON, CarBody.SUV, 0xFF9DD3E0, "지금"),
        CarModel("seltos", "기아 셀토스", 2019, CarRarity.COMMON, CarBody.SUV, 0xFFE8B04A, "지금"),
        CarModel("gv80", "제네시스 GV80", 2020, CarRarity.RARE, CarBody.SUV, 0xFF5B5148, "지금"),
        CarModel("gv70", "제네시스 GV70", 2020, CarRarity.RARE, CarBody.SUV, 0xFF7A3B3B, "지금"),
        // 더 모으기 — 2020년대
        CarModel("staria", "현대 스타리아", 2021, CarRarity.COMMON, CarBody.VAN, 0xFFDCE3E8, "지금"),
        CarModel("k8", "기아 K8", 2021, CarRarity.COMMON, CarBody.SEDAN, 0xFF39414E, "지금"),
        CarModel("g80e", "제네시스 G80 전동화", 2021, CarRarity.RARE, CarBody.SEDAN, 0xFF5E7C8A, "지금"),
        CarModel("ev6gt", "기아 EV6 GT", 2022, CarRarity.EPIC, CarBody.SPORTS, 0xFFC9F04A, "지금"),
        CarModel("grandeur7", "현대 디 올 뉴 그랜저", 2022, CarRarity.COMMON, CarBody.SEDAN, 0xFFB7BEC6, "지금"),
        CarModel("sonata_edge", "현대 쏘나타 디 엣지", 2023, CarRarity.COMMON, CarBody.SEDAN, 0xFF4E6E8E, "지금"),
        CarModel("santafe5", "현대 디 올 뉴 싼타페", 2023, CarRarity.COMMON, CarBody.SUV, 0xFF7C8A6A, "지금"),
        CarModel("ev3", "기아 EV3", 2024, CarRarity.COMMON, CarBody.SUV, 0xFF6FA8C9, "지금"),
        CarModel("ev5", "기아 EV5", 2024, CarRarity.COMMON, CarBody.SUV, 0xFFA3B8A0, "지금"),
        CarModel("casper_ev", "현대 캐스퍼 일렉트릭", 2024, CarRarity.COMMON, CarBody.BOX, 0xFF7FD6C1, "지금"),
        CarModel("ioniq9", "현대 아이오닉 9", 2025, CarRarity.RARE, CarBody.SUV, 0xFF3F4B5C, "지금"),
        CarModel("pv5", "기아 PV5", 2025, CarRarity.RARE, CarBody.VAN, 0xFFE6EDF2, "지금"),
        CarModel("ioniq6n", "현대 아이오닉 6 N", 2025, CarRarity.EPIC, CarBody.SEDAN, 0xFF5FA8E8, "지금"),
        CarModel("ev4", "기아 EV4", 2025, CarRarity.COMMON, CarBody.SEDAN, 0xFFB5C9D9, "지금"),
        // 더 모으기 — 미래
        CarModel("elevate", "현대 엘리베이트 워킹카", 2019, CarRarity.LEGEND, CarBody.CONCEPT, 0xFFE0E6EA, "미래"),
        CarModel("genesis_x", "제네시스 X 콘셉트", 2021, CarRarity.LEGEND, CarBody.SPORTS, 0xFF2F6B5E, "미래"),
        CarModel("rn22e", "현대 RN22e", 2022, CarRarity.EPIC, CarBody.CONCEPT, 0xFFDDE4EA, "미래"),
        CarModel("initium", "현대 이니시움", 2024, CarRarity.EPIC, CarBody.CONCEPT, 0xFFB6C9D6, "미래"),
        CarModel("ev2c", "기아 EV2 콘셉트", 2025, CarRarity.RARE, CarBody.CONCEPT, 0xFF8EC9B0, "미래"),
        CarModel("magma_gt", "제네시스 마그마 GT 콘셉트", 2025, CarRarity.LEGEND, CarBody.SPORTS, 0xFFFF7A2A, "미래"),
        CarModel("hydrogen_bus", "현대 수소 버스 2028", 2028, CarRarity.EPIC, CarBody.VAN, 0xFF5FC6E0, "미래"),
    )

    fun of(id: String?): CarModel? = ALL.firstOrNull { it.id == id }

    /**
     * 저장한 트립카드에 나올 차를 고른다. 희귀도를 먼저 뽑고, 그 안에서는 아직 없는 차가 3배 잘 나온다.
     */
    fun draw(owned: Set<String>, random: Random = Random): CarModel {
        val total = CarRarity.entries.sumOf { it.weight }
        var pick = random.nextInt(total)
        val rarity = CarRarity.entries.first { pick -= it.weight; pick < 0 }
        val pool = ALL.filter { it.rarity == rarity }
        val weights = pool.map { if (it.id in owned) 1 else 3 }
        var w = random.nextInt(weights.sum())
        return pool[weights.indices.first { w -= weights[it]; w < 0 }]
    }
}
