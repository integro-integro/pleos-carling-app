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

internal val EggArt = CarlingArt(R.drawable.carling_egg, 1f, Offset(0.495f, 0.63f), 0.17f, Offset(0.5f, 0.85f))

/** 성장 2까지는 모두 같은 일러스트. */
private val JellyArts = listOf(
    CarlingArt(R.drawable.carling_stage0, 0.76f, Offset(0.49f, 0.68f), 0.13f, Offset(0.5f, 0.83f), Offset(0.5f, 0.53f), 0.27f),
    CarlingArt(R.drawable.carling_stage1, 0.8f, Offset(0.49f, 0.68f), 0.13f, Offset(0.5f, 0.84f), Offset(0.5f, 0.53f), 0.27f),
    CarlingArt(R.drawable.carling_stage2, 0.84f, Offset(0.495f, 0.64f), 0.13f, Offset(0.5f, 0.81f), Offset(0.5f, 0.49f), 0.28f),
)

/** 진화 1부터의 바람 계열 일러스트. */
private val WindArts = listOf(
    CarlingArt(R.drawable.carling_stage3, 0.88f, Offset(0.49f, 0.68f), 0.14f, Offset(0.49f, 0.86f), Offset(0.49f, 0.51f), 0.31f),
    CarlingArt(R.drawable.carling_stage4, 0.94f, Offset(0.46f, 0.65f), 0.14f, Offset(0.47f, 0.84f), Offset(0.48f, 0.51f), 0.29f),
    CarlingArt(R.drawable.carling_stage5, 1f, Offset(0.45f, 0.65f), 0.12f, Offset(0.46f, 0.81f), Offset(0.46f, 0.53f), 0.26f),
    CarlingArt(R.drawable.carling_stage6, 1.04f, Offset(0.32f, 0.7f), 0.08f, Offset(0.41f, 0.85f), mirrored = true),
    CarlingArt(R.drawable.carling_stage7, 1.1f, Offset(0.2f, 0.65f), 0.07f, Offset(0.33f, 0.84f), mirrored = true),
    CarlingArt(R.drawable.carling_stage8, 1.14f, Offset(0.19f, 0.62f), 0.07f, Offset(0.29f, 0.84f), mirrored = true),
    CarlingArt(R.drawable.carling_stage9, 1.18f, Offset(0.22f, 0.57f), 0.07f, null, mirrored = true),
)

/** 해당 단계의 일러스트. 일러스트가 아직 없는 계열의 진화 단계면 null. */
internal fun carlingArt(stageIndex: Int, lineage: Lineage?): CarlingArt? = when {
    stageIndex < JellyArts.size -> JellyArts[stageIndex.coerceAtLeast(0)]
    lineage == null || lineage == Lineage.WIND -> WindArts.getOrNull(stageIndex - JellyArts.size)
    else -> null
}

/** 자는 모습 일러스트는 진화 1의 젤리 형태에 맞춰 그려졌다. */
internal const val SLEEP_ART_STAGE = 3

internal val SleepArtRes = R.drawable.carling_sleep

/** 이 단계·계열의 모습에 모자를 씌울 수 있는지. 일러스트가 없는 계열은 벡터 그림이라 씌울 수 있다. */
internal fun canWearHat(stageIndex: Int, lineage: Lineage?): Boolean =
    carlingArt(stageIndex, lineage)?.let { it.bodyCenter != null } ?: true
