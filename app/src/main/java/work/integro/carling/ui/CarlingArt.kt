package work.integro.carling.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.geometry.Offset
import work.integro.carling.R
import work.integro.carling.game.CarlingItem
import work.integro.carling.game.Lineage
import work.integro.carling.game.Vehicle

/**
 * 단계별 일러스트. 위치 값은 그림 한 변을 1로 본 비율이다.
 * @param scale 화면에서의 크기 — 자랄수록 커진다
 * @param heart 뛰는 심장의 중심
 * @param heartRadius 심장 빛의 반지름
 * @param shadowAt 바닥 그림자 중심. 나는 모습이면 null
 * @param bodyCenter 모자를 얹을 젤리 몸의 중심. 모자가 어울리지 않는 모습이면 null
 * @param mirrored 원본이 왼쪽을 보고 있어 오른쪽을 보도록 뒤집는 그림
 */
internal data class CarlingArt(
    @param:DrawableRes val res: Int,
    val scale: Float,
    val heart: Offset,
    val heartRadius: Float,
    val shadowAt: Offset?,
    val bodyCenter: Offset? = null,
    val bodyRadius: Float = 0f,
    val mirrored: Boolean = false,
    /** 두 눈의 가운데. 선글라스 같은 얼굴 아이템의 기준이다. */
    val eyes: Offset? = null,
    /** 가운데에서 한쪽 눈까지의 거리. */
    val eyeGap: Float = 0f,
)

internal val EggArt = CarlingArt(R.drawable.carling_egg, 0.82f, Offset(0.500f, 0.541f), 0.2f, Offset(0.5f, 0.94f))

/*
 * 서비스 시각화 V3의 최종 캐릭터(개별 원본 22장, 2026-11-06 전달).
 * 그림은 768×768, 아래쪽 5%를 비우고 바닥에 맞춰 놓았다.
 * 단계 인덱스는 [GrowthRules.STAGES]를 따른다.
 */

/** 아기 몸 위에 모자를 얹는 기준: 두 눈 가운데에서 조금 아래, 반지름은 눈 간격에 비례한다. */
private fun jelly(res: Int, scale: Float, heart: Offset, heartR: Float, footX: Float, eyes: Offset, gap: Float) =
    CarlingArt(res, scale, heart, heartR, Offset(footX, 0.94f), Offset(eyes.x, eyes.y + 0.05f), gap * 2.4f, eyes = eyes, eyeGap = gap)

/** 일어선 모습(진화 2·3). 머리가 몸보다 작아 머리 반지름을 눈 간격으로 어림한다. */
private fun standing(res: Int, scale: Float, heart: Offset, heartR: Float, footX: Float, eyes: Offset, gap: Float) =
    CarlingArt(res, scale, heart, heartR, Offset(footX, 0.94f), Offset(eyes.x, eyes.y + 0.03f), gap * 2.3f, eyes = eyes, eyeGap = gap)

/** 계열이 정해지기 전(아기 카링 1단계·성장)의 진주색 모습. 성장하면 같은 그림이 조금 커진다. */
private val PearlBaby = jelly(R.drawable.carling_baby, 0.8f, Offset(0.490f, 0.728f), 0.15f, 0.49f, Offset(0.484f, 0.466f), 0.137f)
private val PearlArts = listOf(PearlBaby, PearlBaby.copy(scale = 0.9f))

private val WindArts = listOf(
    jelly(R.drawable.carling_wind_stage2, 0.95f, Offset(0.497f, 0.745f), 0.15f, 0.49f, Offset(0.496f, 0.493f), 0.135f),
    jelly(R.drawable.carling_wind_stage3, 1.02f, Offset(0.462f, 0.784f), 0.13f, 0.46f, Offset(0.461f, 0.589f), 0.113f),
    standing(R.drawable.carling_wind_stage4, 1.08f, Offset(0.470f, 0.773f), 0.11f, 0.48f, Offset(0.477f, 0.497f), 0.106f),
    standing(R.drawable.carling_wind_stage5, 1.25f, Offset(0.44f, 0.75f), 0.08f, 0.57f, Offset(0.440f, 0.603f), 0.055f),
)

private val LightArts = listOf(
    jelly(R.drawable.carling_light_stage2, 0.95f, Offset(0.494f, 0.746f), 0.15f, 0.49f, Offset(0.495f, 0.482f), 0.139f),
    jelly(R.drawable.carling_light_stage3, 1.02f, Offset(0.492f, 0.752f), 0.15f, 0.49f, Offset(0.491f, 0.512f), 0.134f),
    standing(R.drawable.carling_light_stage4, 1.08f, Offset(0.481f, 0.739f), 0.12f, 0.48f, Offset(0.485f, 0.432f), 0.115f),
    standing(R.drawable.carling_light_stage5, 1.15f, Offset(0.388f, 0.744f), 0.11f, 0.41f, Offset(0.396f, 0.495f), 0.088f),
)

private val TreeArts = listOf(
    jelly(R.drawable.carling_tree_stage2, 0.95f, Offset(0.496f, 0.747f), 0.15f, 0.50f, Offset(0.500f, 0.493f), 0.139f),
    jelly(R.drawable.carling_tree_stage3, 1.02f, Offset(0.468f, 0.780f), 0.13f, 0.48f, Offset(0.466f, 0.560f), 0.121f),
    standing(R.drawable.carling_tree_stage4, 1.08f, Offset(0.462f, 0.744f), 0.11f, 0.47f, Offset(0.464f, 0.461f), 0.112f),
    standing(R.drawable.carling_tree_stage5, 1.2f, Offset(0.391f, 0.734f), 0.09f, 0.44f, Offset(0.398f, 0.527f), 0.072f),
)

private val WaterArts = listOf(
    jelly(R.drawable.carling_water_stage2, 0.95f, Offset(0.494f, 0.739f), 0.16f, 0.49f, Offset(0.492f, 0.487f), 0.141f),
    jelly(R.drawable.carling_water_stage3, 1.02f, Offset(0.451f, 0.804f), 0.14f, 0.45f, Offset(0.452f, 0.622f), 0.109f),
    standing(R.drawable.carling_water_stage4, 1.08f, Offset(0.472f, 0.769f), 0.12f, 0.48f, Offset(0.475f, 0.509f), 0.106f),
    standing(R.drawable.carling_water_stage5, 1.2f, Offset(0.340f, 0.756f), 0.10f, 0.36f, Offset(0.339f, 0.583f), 0.072f),
)

private val EarthArts = listOf(
    jelly(R.drawable.carling_earth_stage2, 0.95f, Offset(0.499f, 0.753f), 0.15f, 0.50f, Offset(0.499f, 0.508f), 0.144f),
    jelly(R.drawable.carling_earth_stage3, 1.02f, Offset(0.388f, 0.772f), 0.14f, 0.39f, Offset(0.393f, 0.546f), 0.130f),
    standing(R.drawable.carling_earth_stage4, 1.08f, Offset(0.409f, 0.728f), 0.11f, 0.42f, Offset(0.431f, 0.421f), 0.126f),
    standing(R.drawable.carling_earth_stage5, 1.22f, Offset(0.484f, 0.817f), 0.07f, 0.51f, Offset(0.489f, 0.675f), 0.056f),
)

/** 해당 단계의 일러스트. 계열이 정해지기 전이면 진주색 아기 카링이다. */
internal fun carlingArt(stageIndex: Int, lineage: Lineage?): CarlingArt {
    if (stageIndex < PearlArts.size || lineage == null) return PearlArts[stageIndex.coerceIn(0, PearlArts.size - 1)]
    val arts = when (lineage) {
        Lineage.WIND -> WindArts
        Lineage.LIGHT_RAY -> LightArts
        Lineage.TREE -> TreeArts
        Lineage.WATER -> WaterArts
        Lineage.EARTH -> EarthArts
    }
    return arts[(stageIndex - PearlArts.size).coerceIn(0, arts.size - 1)]
}

/** 이 단계·계열의 모습에 아이템이 보이는지. 모든 모습에 머리 기준점이 있어 지금은 언제나 보인다. */
internal fun canWearItem(stageIndex: Int, lineage: Lineage?, item: CarlingItem? = null): Boolean =
    carlingArt(stageIndex, lineage).bodyCenter != null

/**
 * 이동수단 그림. 지금은 한 장짜리 그림이라, 카링을 좌석 등받이 앞에 크게 앉혀 의자를 가리고
 * [cover] 아래쪽 차체(문·옆판)를 한 번 더 그려 카링이 차 안에 앉은 것처럼 보이게 한다.
 * 뒷면·앞면 2장 그림을 받으면 그 사이에 끼우는 방식으로 바꾼다.
 * @param seat 카링 바닥이 닿는 위치(그림 한 변 = 1). 차체에 가려지는 쿠션 높이다
 * @param cover 이 높이 아래의 차체를 카링 앞에 다시 그린다
 * @param rider 탄 카링의 크기(이동수단 그림 한 변 대비). 등받이를 다 가릴 만큼 크게 잡는다
 * @param straddle 바이크처럼 걸터앉는 이동수단. 몸을 안장 높이까지 내리고, 안장 아래로 내려가는 부분은
 *   부드럽게 사라지게 해 안장에 앉은 것처럼 보이게 한다(딱 잘린 선이 보이지 않게)
 */
internal data class VehicleArt(
    @param:DrawableRes val res: Int,
    val seat: Offset,
    val cover: Float,
    val rider: Float,
    val straddle: Boolean = false,
)

/** 걸터앉을 때 안장 아래로 내리는 높이와, 그 위에서 서서히 사라지는 띠의 높이(카링 그림 한 변 대비). */
internal const val STRADDLE_LEGS = 0.12f
internal const val STRADDLE_FADE = 0.10f

internal fun vehicleArt(vehicle: Vehicle): VehicleArt = when (vehicle) {
    Vehicle.LEAF_BUGGY -> VehicleArt(R.drawable.vehicle_leaf_buggy, Offset(0.58f, 0.68f), 0.62f, 0.42f)
    Vehicle.SHELL_CAR -> VehicleArt(R.drawable.vehicle_shell_car, Offset(0.47f, 0.68f), 0.63f, 0.44f)
    Vehicle.WING_CAR -> VehicleArt(R.drawable.vehicle_wing_car, Offset(0.465f, 0.77f), 0.72f, 0.38f)
    Vehicle.GOLD_RACER -> VehicleArt(R.drawable.vehicle_gold_racer, Offset(0.61f, 0.74f), 0.69f, 0.38f)
    Vehicle.FOX_BIKE -> VehicleArt(R.drawable.vehicle_fox_bike, Offset(0.62f, 0.575f), 0.585f, 0.44f, straddle = true)
}
