package work.integro.carling.ui

import androidx.annotation.DrawableRes
import androidx.compose.ui.geometry.Offset
import work.integro.carling.R
import work.integro.carling.game.Lineage

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
)

internal val EggArt = CarlingArt(R.drawable.carling_egg, 1f, Offset(0.499f, 0.604f), 0.17f, Offset(0.5f, 0.81f))

/** 계열이 정해지기 전(아기, 성장 1)의 진주색 일러스트. 모든 계열이 같다. */
private val PearlArts = listOf(
    CarlingArt(R.drawable.carling_stage0, 0.94f, Offset(0.488f, 0.679f), 0.13f, Offset(0.5f, 0.83f), Offset(0.5f, 0.55f), 0.28f),
    CarlingArt(R.drawable.carling_stage1, 0.98f, Offset(0.488f, 0.668f), 0.13f, Offset(0.5f, 0.83f), Offset(0.5f, 0.54f), 0.295f),
)

/** 계열이 정해진 뒤(성장 2부터)의 바람 계열 일러스트. */
private val WindArts = listOf(
    CarlingArt(R.drawable.carling_stage2, 1f, Offset(0.495f, 0.64f), 0.13f, Offset(0.5f, 0.81f), Offset(0.5f, 0.49f), 0.28f),
    CarlingArt(R.drawable.carling_stage3, 1.02f, Offset(0.49f, 0.68f), 0.14f, Offset(0.49f, 0.86f), Offset(0.49f, 0.51f), 0.31f),
    CarlingArt(R.drawable.carling_stage4, 1.04f, Offset(0.46f, 0.65f), 0.14f, Offset(0.47f, 0.84f), Offset(0.48f, 0.51f), 0.29f),
    CarlingArt(R.drawable.carling_stage5, 1.06f, Offset(0.45f, 0.65f), 0.12f, Offset(0.46f, 0.81f), Offset(0.46f, 0.53f), 0.26f),
    CarlingArt(R.drawable.carling_stage6, 1.08f, Offset(0.32f, 0.7f), 0.08f, Offset(0.41f, 0.85f), mirrored = true),
    CarlingArt(R.drawable.carling_stage7, 1.1f, Offset(0.2f, 0.65f), 0.07f, Offset(0.33f, 0.84f), mirrored = true),
    CarlingArt(R.drawable.carling_stage8, 1.12f, Offset(0.19f, 0.62f), 0.07f, Offset(0.29f, 0.84f), mirrored = true),
    CarlingArt(R.drawable.carling_stage9, 1.14f, Offset(0.22f, 0.57f), 0.07f, null, mirrored = true),
)

/** 성장 2부터의 빛 계열 일러스트. 진화 2부터 자동차 모습이다. */
private val LightArts = listOf(
    CarlingArt(R.drawable.carling_light_stage2, 0.86f, Offset(0.479f, 0.656f), 0.138f, Offset(0.493f, 0.84f), Offset(0.486f, 0.490f), 0.331f),
    CarlingArt(R.drawable.carling_light_stage3, 1.2f, Offset(0.474f, 0.661f), 0.114f, Offset(0.489f, 0.84f), Offset(0.486f, 0.557f), 0.259f),
    CarlingArt(R.drawable.carling_light_stage4, 1.24f, Offset(0.429f, 0.656f), 0.105f, Offset(0.455f, 0.84f), Offset(0.439f, 0.547f), 0.251f),
    CarlingArt(R.drawable.carling_light_stage5, 1.3f, Offset(0.379f, 0.671f), 0.099f, Offset(0.415f, 0.84f), Offset(0.407f, 0.566f), 0.243f),
    CarlingArt(R.drawable.carling_light_stage6, 1.1f, Offset(0.581f, 0.608f), 0.086f, Offset(0.434f, 0.84f), mirrored = true),
    CarlingArt(R.drawable.carling_light_stage7, 1.1f, Offset(0.423f, 0.634f), 0.087f, Offset(0.406f, 0.84f), mirrored = true),
    CarlingArt(R.drawable.carling_light_stage8, 1.12f, Offset(0.410f, 0.639f), 0.080f, Offset(0.337f, 0.84f), mirrored = true),
    CarlingArt(R.drawable.carling_light_stage9, 1.14f, Offset(0.427f, 0.645f), 0.086f, Offset(0.339f, 0.84f), mirrored = true),
)

/** 성장 2부터의 나무 계열 일러스트. 진화 2부터 네 바퀴 달린 모습이다. */
private val TreeArts = listOf(
    CarlingArt(R.drawable.carling_tree_stage2, 0.86f, Offset(0.479f, 0.658f), 0.138f, Offset(0.490f, 0.84f), Offset(0.486f, 0.491f), 0.331f),
    CarlingArt(R.drawable.carling_tree_stage3, 1.19f, Offset(0.463f, 0.706f), 0.099f, Offset(0.470f, 0.84f), Offset(0.470f, 0.587f), 0.247f),
    CarlingArt(R.drawable.carling_tree_stage4, 1.24f, Offset(0.421f, 0.702f), 0.099f, Offset(0.436f, 0.84f), Offset(0.439f, 0.580f), 0.247f),
    CarlingArt(R.drawable.carling_tree_stage5, 1.25f, Offset(0.410f, 0.696f), 0.110f, Offset(0.422f, 0.84f), Offset(0.423f, 0.571f), 0.255f),
    CarlingArt(R.drawable.carling_tree_stage6, 1.18f, Offset(0.362f, 0.638f), 0.070f, Offset(0.481f, 0.84f), mirrored = true),
    CarlingArt(R.drawable.carling_tree_stage7, 1.18f, Offset(0.283f, 0.587f), 0.070f, Offset(0.364f, 0.84f), mirrored = true),
    CarlingArt(R.drawable.carling_tree_stage8, 1.2f, Offset(0.260f, 0.587f), 0.070f, Offset(0.354f, 0.84f), mirrored = true),
    CarlingArt(R.drawable.carling_tree_stage9, 1.2f, Offset(0.226f, 0.601f), 0.070f, Offset(0.393f, 0.84f), mirrored = true),
)

/** 해당 단계의 일러스트. 일러스트가 아직 없는 계열(물·흙)의 성장 2 이후면 null. */
internal fun carlingArt(stageIndex: Int, lineage: Lineage?): CarlingArt? {
    if (stageIndex < PearlArts.size) return PearlArts[stageIndex.coerceAtLeast(0)]
    val arts = when (lineage) {
        null, Lineage.WIND -> WindArts
        Lineage.LIGHT_RAY -> LightArts
        Lineage.TREE -> TreeArts
        Lineage.WATER, Lineage.EARTH -> return null
    }
    return arts.getOrNull(stageIndex - PearlArts.size)
}

/** 자는 모습 일러스트는 진화 1의 젤리 형태에 맞춰 그려졌다. */
internal const val SLEEP_ART_STAGE = 3

internal val SleepArtRes = R.drawable.carling_sleep

/** 이 단계·계열의 모습에 아이템이 보이는지. 일러스트가 없는 계열은 벡터 그림이라 보인다. */
internal fun canWearItem(stageIndex: Int, lineage: Lineage?): Boolean =
    carlingArt(stageIndex, lineage)?.let { it.bodyCenter != null } ?: true
