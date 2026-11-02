package work.integro.carling.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthRulesTest {

    @Test
    fun lightBoundaries() {
        assertEquals(Light.STAR, Light.ofHour(3))
        assertEquals(Light.DAWN, Light.ofHour(4))
        assertEquals(Light.DAWN, Light.ofHour(8))
        assertEquals(Light.DAY, Light.ofHour(9))
        assertEquals(Light.DAY, Light.ofHour(15))
        assertEquals(Light.SUNSET, Light.ofHour(16))
        assertEquals(Light.SUNSET, Light.ofHour(19))
        assertEquals(Light.STAR, Light.ofHour(20))
        assertEquals(Light.STAR, Light.ofHour(0))
    }

    @Test
    fun stageByTogetherDays() {
        assertEquals(Stage.EGG, GrowthRules.stage(hatched = false, togetherDays = 40))
        assertEquals(Stage.BABY, GrowthRules.stage(hatched = true, togetherDays = 0))
        assertEquals(Stage.BABY, GrowthRules.stage(hatched = true, togetherDays = 6))
        assertEquals(Stage.GROWN, GrowthRules.stage(hatched = true, togetherDays = 7))
        assertEquals(Stage.GROWN, GrowthRules.stage(hatched = true, togetherDays = 29))
        assertEquals(Stage.EVOLVED, GrowthRules.stage(hatched = true, togetherDays = 30))
    }

    @Test
    fun dominantLightUsesMinutesNotTrips() {
        val minutes = mapOf(Light.DAWN to 100, Light.SUNSET to 240)
        assertEquals(Light.SUNSET, GrowthRules.dominantLight(minutes))
    }

    @Test
    fun pathIsRegularWithoutLocationData() {
        assertEquals(Path.REGULAR, GrowthRules.path(newPlaces = 0, revisits = 0))
        assertEquals(Path.REGULAR, GrowthRules.path(newPlaces = 3, revisits = 3))
        assertEquals(Path.EXPLORER, GrowthRules.path(newPlaces = 4, revisits = 3))
    }

    @Test
    fun evolutionExplainsWhy() {
        val evo = GrowthRules.evolve(
            lightMinutes = mapOf(Light.SUNSET to 300, Light.DAY to 40),
            lightTrips = mapOf(Light.SUNSET to 12, Light.DAY to 2),
            newPlaces = 1,
            revisits = 13,
        )
        assertEquals(Light.SUNSET, evo.light)
        assertEquals(Path.REGULAR, evo.path)
        assertTrue(evo.reason.contains("노을빛 시간에 12번"))
        assertTrue(evo.reason.contains("머플러"))
    }
}
