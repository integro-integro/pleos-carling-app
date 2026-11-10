package work.integro.carling.game

import org.junit.Assert.assertEquals
import work.integro.carling.data.GeoDot
import work.integro.carling.data.MapRoute
import work.integro.carling.data.TripCard
import work.integro.carling.data.afterTrip
import work.integro.carling.data.recentDestinations
import work.integro.carling.data.roadShape
import work.integro.carling.data.roughKm
import work.integro.carling.data.simplifyTrack
import org.junit.Assert.assertNull
import org.junit.Test

class GrowthRulesTest {

    @Test
    fun lightBoundaries() {
        assertEquals(Light.STAR, Light.ofHour(3))
        assertEquals(Light.DAWN, Light.ofHour(4))
        assertEquals(Light.DAY, Light.ofHour(9))
        assertEquals(Light.SUNSET, Light.ofHour(16))
        assertEquals(Light.STAR, Light.ofHour(20))
    }

    @Test
    fun savedCardXpFollowsV05Table() {
        // 저장한 카드 1장 10 XP, 95점 이상이면 +3, 운전일마다 5장까지
        assertEquals(10, GrowthRules.cardXp(safety = 92, rewardedOnDay = 0))
        assertEquals(13, GrowthRules.cardXp(safety = 95, rewardedOnDay = 4))
        assertEquals(10, GrowthRules.cardXp(safety = null, rewardedOnDay = 0))
        assertEquals(0, GrowthRules.cardXp(safety = 95, rewardedOnDay = 5))
        // 하루 최대 50 + 15 + 6
        assertEquals(71, GrowthRules.DAILY_XP_MAX)
    }

    @Test
    fun noSpeedDataMeansNoSafetyBonus() {
        assertNull(GrowthRules.safetyScore(null))
        assertEquals(false, GrowthRules.isSafeTrip(GrowthRules.safetyScore(null)))
        assertEquals(true, GrowthRules.isSafeTrip(GrowthRules.safetyScore(0)))
        assertEquals(false, GrowthRules.isSafeTrip(GrowthRules.safetyScore(1)))
        assertEquals(72, GrowthRules.safetyScore(100))
    }

    @Test
    fun intimacyXpOncePerActionPerDay() {
        assertEquals(2, GrowthRules.intimacyXp("PET", emptySet()))
        assertEquals(0, GrowthRules.intimacyXp("PET", setOf("PET")))
        assertEquals(2, GrowthRules.intimacyXp("PLAY", setOf("PET", "FEED")))
        // 말 걸기는 이번 범위에서 뺐다
        assertEquals(0, GrowthRules.intimacyXp("TALK", emptySet()))
    }

    @Test
    fun stageNamesFollowGrowthOrder() {
        assertEquals(
            listOf("아기 카링 1단계", "아기 카링 성장", "아기 카링 2단계", "산들링", "날개링", "하늘링"),
            (0..5).map { GrowthRules.stageName(it, Lineage.WIND) },
        )
        assertEquals("반짝링", GrowthRules.stageName(3, Lineage.LIGHT_RAY))
        assertEquals("잎새링", GrowthRules.stageName(4, Lineage.TREE))
        assertEquals("바다링", GrowthRules.stageName(5, Lineage.WATER))
        assertEquals("구미링", GrowthRules.stageName(5, Lineage.EARTH))
    }

    @Test
    fun evolutionsTeachNewMoves() {
        assertNull(GrowthRules.learnedMove(2, Lineage.WIND))
        assertEquals(SpecialMove.FLIP, GrowthRules.learnedMove(3, Lineage.TREE))
        assertEquals(SpecialMove.WAVE, GrowthRules.learnedMove(4, Lineage.WATER))
        assertEquals(SpecialMove.AURA, GrowthRules.learnedMove(5, Lineage.EARTH))
        assertEquals(listOf(SpecialMove.FLIP, SpecialMove.ZIP), GrowthRules.knownMoves(4, Lineage.LIGHT_RAY))
    }

    @Test
    fun regionsFromCoordinates() {
        assertEquals(Region.JEJU, Region.of(33.50, 126.53)) // 제주시
        assertEquals(Region.GANGWON, Region.of(37.88, 127.73)) // 춘천
        assertEquals(Region.GANGWON, Region.of(37.75, 128.90)) // 강릉
        assertEquals(Region.BUSAN, Region.of(35.18, 129.07))
        assertEquals(Region.DAEGU, Region.of(35.87, 128.60))
        assertEquals(Region.JEONJU, Region.of(35.82, 127.15))
        assertNull(Region.of(37.56, 126.98)) // 서울
        assertNull(Region.of(37.49, 127.49)) // 양평
    }

    @Test
    fun onlyHungryCarlingEatsFood() {
        assertEquals(true, GrowthRules.isHungry(50))
        assertEquals(false, GrowthRules.isHungry(51))
    }

    @Test
    fun skyFromWeatherCode() {
        assertEquals(Sky.CLEAR, WeatherRules.skyOf(1))
        assertEquals(Sky.CLOUDY, WeatherRules.skyOf(3))
        assertEquals(Sky.FOG, WeatherRules.skyOf(45))
        assertEquals(Sky.RAIN, WeatherRules.skyOf(63))
        assertEquals(Sky.RAIN, WeatherRules.skyOf(95))
        assertEquals(Sky.SNOW, WeatherRules.skyOf(73))
    }

    @Test
    fun parsesForecastAndRainChance() {
        fun body(today: Int, chance: Int, now: Int) =
            """{"current":{"weather_code":$now},"daily":{"weather_code":[$today],"precipitation_probability_max":[$chance]}}"""
        assertEquals(WeatherReport(Sky.CLEAR, Sky.CLOUDY), WeatherRules.parse(body(1, 10, 3)))
        // 예보가 흐림이어도 비 올 확률이 높으면 비 소식으로 말한다
        assertEquals(WeatherReport(Sky.RAIN, Sky.CLEAR), WeatherRules.parse(body(3, 60, 0)))
        // 눈 예보는 확률 때문에 비로 바뀌지 않는다
        assertEquals(WeatherReport(Sky.SNOW, Sky.SNOW), WeatherRules.parse(body(73, 90, 71)))
        assertNull(WeatherRules.parse("not json"))
    }

    @Test
    fun morningSaysForecastAndAfternoonSaysCurrentWeather() {
        val report = WeatherReport(forecast = Sky.RAIN, now = Sky.CLEAR)
        assertEquals("오늘 비 소식이 있어요. 조심히 운전해요.", WeatherRules.greeting(11, report))
        assertEquals("지금은 맑아요. 오늘도 안전운전해요!", WeatherRules.greeting(12, report))
        assertEquals("오늘은 흐리대요. 제가 즐겁게 해줄게요!", WeatherRules.greeting(8, WeatherReport(Sky.CLOUDY, Sky.CLOUDY)))
        assertEquals("오늘은 화창해요! 운전을 즐겨 봐요.", WeatherRules.greeting(8, WeatherReport(Sky.CLEAR, Sky.CLEAR)))
        // 날씨를 모르면 날씨 이야기를 지어내지 않는다
        assertEquals("오늘도 함께 안전운전해요!", WeatherRules.greeting(8, null))
    }

    @Test
    fun weatherLocationIsCoarse() {
        assertEquals(37.6, WeatherRules.coarse(37.5665), 1e-9)
        assertEquals(127.0, WeatherRules.coarse(126.978), 1e-9)
    }

    private fun card(number: Int, cell: String?) = TripCard(
        number = number, date = "2026-11-03", startMillis = 0L, minutes = 10, distanceKm = null,
        light = Light.DAY, safety = null, placeName = null, newPlace = null, dayNumber = 1, placeCell = cell,
    )

    @Test
    fun revisitedDestinationMovesToTop() {
        val recent = listOf(card(1, "home"), card(2, "cafe"), card(3, "home")).recentDestinations()
        assertEquals(listOf(3, 2), recent.map { it.card.number })
        assertEquals(listOf(2, 1), recent.map { it.visits })
    }

    @Test
    fun keepsOnlyRecentTwentyDestinations() {
        val cards = (1..25).map { card(it, "place$it") }
        val recent = cards.recentDestinations()
        assertEquals(20, recent.size)
        assertEquals(25, recent.first().card.number)
        assertEquals(6, recent.last().card.number)
    }

    @Test
    fun tripsWithoutLocationAreNotGrouped() {
        assertEquals(2, listOf(card(1, null), card(2, null)).recentDestinations().size)
    }

    private fun List<MapRoute>.drive(fromLat: Double?, fromLng: Double?, toLat: Double?, toLng: Double?) =
        afterTrip(fromLat, fromLng, toLat, toLng, null, null, "d")

    @Test
    fun everyTripLightsTheMap() {
        val first = emptyList<MapRoute>().drive(37.5, 127.0, 37.6, 127.1)
        assertEquals(true, first.newRoute)
        assertEquals(1, first.routes.size)
        // 같은 길을 반대로 달리면 새 길이 아니라 더 밝아진다
        val back = first.routes.drive(37.6, 127.1, 37.5, 127.0)
        assertEquals(false, back.newRoute)
        assertEquals(true, back.lit)
        assertEquals(2, back.routes.single().visits)
        // 아는 목적지라도 다른 곳에서 출발하면 새 길이다
        val other = first.routes.drive(37.9, 127.5, 37.6, 127.1)
        assertEquals(true, other.newRoute)
        assertEquals(2, other.routes.size)
        // 위치를 모르면 지도에 아무 일도 없다
        assertEquals(false, first.routes.drive(null, null, null, null).lit)
        // 제자리로 돌아온 트립도 길이 켜진다
        val still = emptyList<MapRoute>().drive(37.5, 127.0, 37.5, 127.0)
        assertEquals(true, still.newRoute)
        assertEquals(2, still.routes.drive(37.5, 127.0, 37.5, 127.0).routes.single().visits)
    }

    @Test
    fun roadsAreNotStraight() {
        val road = roadShape(MapRoute(toLat = 35.18, toLng = 129.07, fromLat = 37.5, fromLng = 127.03))
        assertEquals(37.5, road.first().lat, 1e-9)
        assertEquals(129.07, road.last().lng, 1e-9)
        // 직선에서 벗어난 만큼을 길이와 비교한다
        val (a, b) = road.first() to road.last()
        val off = road.maxOf { p ->
            val t = (p.lat - a.lat) / (b.lat - a.lat)
            kotlin.math.abs(p.lng - (a.lng + (b.lng - a.lng) * t))
        }
        assertEquals(true, off > 0.03)
        // 같은 길은 언제나 같은 모양이고, 왕복도 같은 길이다
        assertEquals(road, roadShape(MapRoute(toLat = 35.18, toLng = 129.07, fromLat = 37.5, fromLng = 127.03)))
        assertEquals(road, roadShape(MapRoute(toLat = 37.5, toLng = 127.03, fromLat = 35.18, fromLng = 129.07)).asReversed())
    }

    @Test
    fun tripThatReturnsDrawsClosedLoop() {
        val at = GeoDot(37.5, 127.0)
        val road = roadShape(MapRoute(toLat = at.lat, toLng = at.lng, distanceKm = 12f))
        assertEquals(at.lat, road.first().lat, 1e-9)
        assertEquals(at.lat, road.last().lat, 1e-9)
        assertEquals(true, road.maxOf { roughKm(at, it) } > 2.0)
    }

    @Test
    fun gaugeRefillsEachStage() {
        assertEquals(0f, GrowthRules.stageProgress(100, 100, 300), 0.001f)
        assertEquals(0.5f, GrowthRules.stageProgress(200, 100, 300), 0.001f)
        assertEquals(1f, GrowthRules.stageProgress(350, 100, 300), 0.001f)
        assertEquals(0f, GrowthRules.stageProgress(50, 100, 300), 0.001f)
    }

    @Test
    fun recordedTrackDrawsActualRoad() {
        val track = listOf(GeoDot(37.5, 127.0), GeoDot(37.51, 127.01), GeoDot(37.52, 127.0))
        assertEquals(track, roadShape(MapRoute(toLat = 37.52, toLng = 127.0, fromLat = 37.5, fromLng = 127.0), track))
    }

    @Test
    fun simplifyDropsStillPointsAndLimitsSize() {
        val still = List(50) { GeoDot(37.5, 127.0) }
        assertEquals(1, simplifyTrack(still).size)
        val moving = List(1000) { GeoDot(37.0 + it * 0.001, 127.0) }
        val out = simplifyTrack(moving)
        assertEquals(300, out.size)
        assertEquals(38.0, out.last().lat, 0.01)
    }

    @Test
    fun differentWayBetweenSamePlacesIsNewRoute() {
        val north = List(10) { GeoDot(37.5 + it * 0.01, 127.0) }
        val south = List(10) { GeoDot(37.5 + it * 0.01, 127.1) }
        val first = emptyList<MapRoute>().afterTrip(37.5, 127.0, 37.59, 127.0, null, null, "d", north)
        assertEquals("r0", first.trackKey)
        val tracks = mapOf("r0" to north)
        val same = first.routes.afterTrip(37.5, 127.0, 37.59, 127.0, null, null, "d", north) { tracks[it.trackKey] }
        assertEquals(false, same.newRoute)
        assertEquals(2, same.routes.single().visits)
        val other = first.routes.afterTrip(37.5, 127.0, 37.59, 127.0, null, null, "d", south) { tracks[it.trackKey] }
        assertEquals(true, other.newRoute)
        assertEquals("r1", other.trackKey)
    }

    @Test
    fun itemsOpenAtFirstGrowth() {
        assertEquals(false, GrowthRules.itemsUnlocked(0))
        assertEquals(true, GrowthRules.itemsUnlocked(1))
        assertEquals(true, GrowthRules.itemsUnlocked(5))
    }

    @Test
    fun nightnessFollowsClock() {
        assertEquals(1f, nightnessAt(0, 0))
        assertEquals(1f, nightnessAt(5, 29))
        assertEquals(0.5f, nightnessAt(6, 15))
        assertEquals(0f, nightnessAt(7, 0))
        assertEquals(0f, nightnessAt(17, 59))
        assertEquals(0.5f, nightnessAt(18, 45))
        assertEquals(1f, nightnessAt(19, 30))
        assertEquals(1f, nightnessAt(23, 59))
    }

    @Test
    fun dayPartFollowsClockWithFade() {
        assertEquals(DayPart.NIGHT to 1f, dayPartAt(3, 0))
        assertEquals(DayPart.MORNING to 0.5f, dayPartAt(5, 15))
        assertEquals(DayPart.DAY to 1f, dayPartAt(12, 0))
        assertEquals(DayPart.DUSK to 0f, dayPartAt(16, 0))
        assertEquals(DayPart.NIGHT to 1f, dayPartAt(19, 30))
        assertEquals(DayPart.DUSK, DayPart.NIGHT.previous)
        assertEquals(DayPart.NIGHT, DayPart.MORNING.previous)
    }

    @Test
    fun stageNeedsDrivingDaysAndXp_andDoesNotSkip() {
        // 7운전일 · 175 XP
        assertEquals(0, GrowthRules.stageIndex(drivingDays = 7, totalXp = 174))
        assertEquals(1, GrowthRules.stageIndex(drivingDays = 7, totalXp = 175))
        // 운전일이 모자라면 XP가 넘쳐도 못 자란다 — 많이 달린다고 더 빨라지지 않는다
        assertEquals(0, GrowthRules.stageIndex(drivingDays = 6, totalXp = 9999))
        assertEquals(4, GrowthRules.stageIndex(drivingDays = 60, totalXp = 9999))
        assertEquals(5, GrowthRules.stageIndex(drivingDays = 90, totalXp = 2250))
    }

    @Test
    fun basicPatternFinishesInNinetyDrivingDays() {
        // 기본 패턴(경유 포함 3장 저장 = 30 XP/운전일)이면 90운전일에 완성형
        assertEquals(5, GrowthRules.stageIndex(drivingDays = 90, totalXp = 90 * 30))
        // 왕복 2장(20 XP)만 저장하면 XP가 모자라 조금 늦다
        assertEquals(4, GrowthRules.stageIndex(drivingDays = 90, totalXp = 90 * 20))
        assertEquals(5, GrowthRules.stageIndex(drivingDays = 113, totalXp = 113 * 20))
    }

    @Test
    fun lineageDefaultsToWindWhenNoData() {
        assertEquals(Lineage.WIND, GrowthRules.dominantLineage(emptyMap()))
        assertEquals(Lineage.EARTH, GrowthRules.dominantLineage(mapOf(Lineage.EARTH to 6f, Lineage.WIND to 2f)))
    }

    @Test
    fun scoreTripSeparatesHabits() {
        assertEquals(Lineage.WIND, GrowthRules.dominantLineage(GrowthRules.scoreTrip(28f, 0, true)))
        assertEquals(Lineage.EARTH, GrowthRules.dominantLineage(GrowthRules.scoreTrip(5f, 0, true)))
        assertEquals(Lineage.LIGHT_RAY, GrowthRules.dominantLineage(GrowthRules.scoreTrip(12f, 4, false)))
    }

    @Test
    fun carCatalogDrawsKnownCarsAndPrefersNewOnes() {
        assertEquals(CarCatalog.ALL.size, CarCatalog.ALL.map { it.id }.toSet().size)
        assertEquals(CarRarity.LEGEND, CarCatalog.of("gv60_magma")?.rarity)
        val rnd = kotlin.random.Random(1)
        val counts = (1..4000).map { CarCatalog.draw(emptySet(), rnd).rarity }.groupingBy { it }.eachCount()
        // 일반이 가장 흔하고 전설이 가장 드물다
        assertEquals(true, counts.getValue(CarRarity.COMMON) > counts.getValue(CarRarity.RARE))
        assertEquals(true, counts.getValue(CarRarity.EPIC) > (counts[CarRarity.LEGEND] ?: 0))
    }

    @Test
    fun careIssuesFromVehicleSignals() {
        val ok = work.integro.carling.vehicle.VehicleState(tirePressures = mapOf("LEFT_FRONT" to 240f), fuelPercent = 60, oilState = "NORMAL")
        assertEquals(emptySet<CareIssue>(), ok.careStatus().first)
        assertEquals(CareIssue.entries.toSet(), ok.careStatus().second)
        val bad = ok.copy(tirePressures = mapOf("LEFT_FRONT" to 240f, "RIGHT_REAR" to 180f), fuelPercent = 10, oilState = "LOW")
        assertEquals(CareIssue.entries.toSet(), bad.careStatus().first)
        // 신호가 없으면 판단하지 않는다
        assertEquals(emptySet<CareIssue>(), work.integro.carling.vehicle.VehicleState().careStatus().second)
        assertEquals(true, CareRules.isLowTire(28f))
        assertEquals(false, CareRules.isLowTire(33f))
    }
}
