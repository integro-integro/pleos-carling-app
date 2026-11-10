package work.integro.carling.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import work.integro.carling.R
import work.integro.carling.game.Form
import work.integro.carling.game.GrowthRules
import work.integro.carling.game.Lineage
import work.integro.carling.game.RoomItem

/**
 * 최종 캐릭터 일러스트 한 장. 그림은 768×768이고 발이 95% 높이에 닿는다.
 * @param heart 빛의 씨앗·민트 심장의 중심(그림 한 변 대비)
 */
internal data class CarlingArt(
    @param:DrawableRes val res: Int,
    @param:DrawableRes val sleepRes: Int,
    val heart: Offset,
    val heartColor: Color,
    val heartRadius: Float,
)

private val SeedGlow = Color(0xFFFFE9A8)
private val HeartGlow = Color(0xFFB7FFE6)

private fun egg(res: Int, x: Float, y: Float) = CarlingArt(res, res, Offset(x, y), SeedGlow, 0.27f)
private fun jelly(res: Int, sleep: Int, x: Float, y: Float) = CarlingArt(res, sleep, Offset(x, y), HeartGlow, 0.16f)

private val Eggs = listOf(
    egg(R.drawable.carling_egg_1, .531f, .614f),
    egg(R.drawable.carling_egg_2, .511f, .617f),
    egg(R.drawable.carling_egg_3, .536f, .601f),
    egg(R.drawable.carling_egg_4, .500f, .553f),
)
private val Baby = jelly(R.drawable.carling_baby, R.drawable.carling_baby_sleep, .545f, .715f)

/** 지금 모습의 일러스트. 알은 레벨에 따라 금이 늘어난다. */
internal fun carlingArt(form: Form, lineage: Lineage?, level: Int): CarlingArt = when (form) {
    Form.EGG -> Eggs[GrowthRules.eggStage(level) - 1]
    Form.BABY -> Baby
    Form.EVO1 -> lineageArt(lineage ?: Lineage.WIND)
}

internal fun lineageArt(lineage: Lineage): CarlingArt = when (lineage) {
    Lineage.WIND -> jelly(R.drawable.carling_wind, R.drawable.carling_wind_sleep, .546f, .752f)
    Lineage.LIGHT_RAY -> jelly(R.drawable.carling_light, R.drawable.carling_light_sleep, .477f, .745f)
    Lineage.TREE -> jelly(R.drawable.carling_tree, R.drawable.carling_tree_sleep, .505f, .712f)
    Lineage.WATER -> jelly(R.drawable.carling_water, R.drawable.carling_water_sleep, .535f, .754f)
    Lineage.EARTH -> jelly(R.drawable.carling_earth, R.drawable.carling_earth_sleep, .541f, .743f)
}

/** 화면에 보이는 이름. 이름을 지어주면 그 이름, 아니면 단계 이름. */
fun formLabel(form: Form, lineage: Lineage?): String = when {
    form == Form.EVO1 && lineage != null -> lineage.carlingName
    else -> form.label
}

@DrawableRes
internal fun roomItemRes(item: RoomItem): Int = when (item) {
    RoomItem.RUG -> R.drawable.room_rug
    RoomItem.SMALL_POT -> R.drawable.room_small_pot
    RoomItem.BED -> R.drawable.room_bed
    RoomItem.NIGHTSTAND -> R.drawable.room_nightstand
    RoomItem.LAMP -> R.drawable.room_lamp
    RoomItem.PICTURE -> R.drawable.room_picture
    RoomItem.TABLE -> R.drawable.room_table
    RoomItem.MUG -> R.drawable.room_mug
    RoomItem.CURTAINS -> R.drawable.room_curtains
    RoomItem.CUSHION -> R.drawable.room_cushion
    RoomItem.CABINET -> R.drawable.room_cabinet
    RoomItem.VASE -> R.drawable.room_vase
    RoomItem.RADIO -> R.drawable.room_radio
    RoomItem.BIG_POT -> R.drawable.room_big_pot
    RoomItem.CLOCK -> R.drawable.room_clock
    RoomItem.BEDDING -> R.drawable.room_bedding
    RoomItem.TEDDY -> R.drawable.room_teddy
    RoomItem.PENDANT -> R.drawable.room_pendant
    RoomItem.BOOKS -> R.drawable.room_books
    RoomItem.WALL_DECOR -> R.drawable.room_wall_decor
    RoomItem.WALL_LIGHT -> R.drawable.room_wall_light
    RoomItem.HANGING_PLANT -> R.drawable.room_hanging_plant
    RoomItem.STAR_LIGHTS -> R.drawable.room_star_lights
    RoomItem.WINDOW_DECOR -> R.drawable.room_window_decor
    RoomItem.LIVING_RUG -> R.drawable.item_living_rug
    RoomItem.COFFEE_TABLE -> R.drawable.item_coffee_table
    RoomItem.ARMCHAIR -> R.drawable.item_armchair
    RoomItem.BIG_PLANT -> R.drawable.item_big_plant
    RoomItem.FRUIT_BOWL -> R.drawable.item_fruit_bowl
    RoomItem.FLOOR_LAMP -> R.drawable.item_floor_lamp
    RoomItem.TV_CONSOLE -> R.drawable.item_tv_console
    RoomItem.WALL_ART -> R.drawable.item_wall_art
    RoomItem.SIDE_TABLE -> R.drawable.item_side_table
    RoomItem.RECORD_PLAYER -> R.drawable.item_record_player
    RoomItem.BOOKSHELF -> R.drawable.item_bookshelf
    RoomItem.LIVING_CURTAINS -> R.drawable.item_living_curtains
    RoomItem.HERB_POTS -> R.drawable.item_herb_pots
    RoomItem.TOASTER -> R.drawable.item_toaster
    RoomItem.STOVE -> R.drawable.item_stove
    RoomItem.POT -> R.drawable.item_pot
    RoomItem.DINING_TABLE -> R.drawable.item_dining_table
    RoomItem.TEAPOT -> R.drawable.item_teapot
    RoomItem.DINING_CHAIRS -> R.drawable.item_dining_chairs
    RoomItem.FRIDGE -> R.drawable.item_fridge
    RoomItem.KITCHEN_RUG -> R.drawable.item_kitchen_rug
    RoomItem.VEG_BASKET -> R.drawable.item_veg_basket
    RoomItem.KITCHEN_SHELF -> R.drawable.item_kitchen_shelf
    RoomItem.KITCHEN_LAMP -> R.drawable.item_kitchen_lamp
    RoomItem.RUBBER_DUCK -> R.drawable.item_rubber_duck
    RoomItem.BATH_CANDLES -> R.drawable.item_bath_candles
    RoomItem.WASHBASIN -> R.drawable.item_washbasin
    RoomItem.SOAP_SET -> R.drawable.item_soap_set
    RoomItem.MIRROR -> R.drawable.item_mirror
    RoomItem.BATH_MAT -> R.drawable.item_bath_mat
    RoomItem.TOWEL_RACK -> R.drawable.item_towel_rack
    RoomItem.BATH_PLANT -> R.drawable.item_bath_plant
    RoomItem.BATH_STOOL -> R.drawable.item_bath_stool
    RoomItem.LAUNDRY_BASKET -> R.drawable.item_laundry_basket
    RoomItem.BATH_SHELF -> R.drawable.item_bath_shelf
    RoomItem.BATH_LAMP -> R.drawable.item_bath_lamp
    RoomItem.DESK_CHAIR -> R.drawable.item_desk_chair
    RoomItem.MUSIC_BOX -> R.drawable.item_music_box
    RoomItem.DESK_LAMP -> R.drawable.item_desk_lamp
    RoomItem.SKETCHBOOKS -> R.drawable.item_sketchbooks
    RoomItem.STUDIO_RUG -> R.drawable.item_studio_rug
    RoomItem.EASEL -> R.drawable.item_easel
    RoomItem.STUDIO_BOOKCASE -> R.drawable.item_studio_bookcase
    RoomItem.PAINT_JARS -> R.drawable.item_paint_jars
    RoomItem.GLOBE -> R.drawable.item_globe
    RoomItem.MONSTERA -> R.drawable.item_monstera
    RoomItem.CORKBOARD -> R.drawable.item_corkboard
    RoomItem.CRAFT_SHELF -> R.drawable.item_craft_shelf
}

/** 일러스트 한 장에 숨쉬기, 심장 박동, 바닥 그림자를 입혀 그린다. */
@Composable
internal fun CarlingPicture(
    art: CarlingArt,
    size: Dp,
    modifier: Modifier = Modifier,
    asleep: Boolean = false,
    rockDegrees: Float = 0f,
    shadow: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "art")
    val breath by transition.animateFloat(0.985f, 1.015f, infiniteRepeatable(tween(if (asleep) 2400 else 1600), RepeatMode.Reverse), label = "breath")
    val beat by transition.animateFloat(0.82f, 1.18f, infiniteRepeatable(tween(820), RepeatMode.Reverse), label = "beat")
    val rock by transition.animateFloat(-1f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "rock")
    Box(modifier.size(size)) {
        if (shadow) {
            Canvas(Modifier.fillMaxSize()) {
                val s = this.size.minDimension
                drawOval(Color(0x33000000), Offset(s * 0.2f, s * 0.915f), Size(s * 0.6f, s * 0.07f))
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleY = breath
                    rotationZ = rock * rockDegrees
                    transformOrigin = TransformOrigin(0.5f, 0.95f)
                },
        ) {
            Image(painterResource(if (asleep) art.sleepRes else art.res), contentDescription = null, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val s = this.size.minDimension
                val at = Offset(art.heart.x * s, art.heart.y * s)
                val glow = art.heartRadius * s * beat
                drawCircle(Brush.radialGradient(listOf(art.heartColor.copy(alpha = 0.55f), Color.Transparent), at, glow), glow, at)
            }
        }
    }
}
