package work.integro.carling.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import work.integro.carling.R
import work.integro.carling.game.RoomItem
import work.integro.carling.game.Exit
import work.integro.carling.game.Fixture
import work.integro.carling.game.Space
import work.integro.carling.game.exitsOf
import work.integro.carling.game.fixturesOf

/** 방 그림의 가로/세로 비율. */
private const val ROOM_RATIO = 2336f / 1744f

/** 확대·축소 배율. 확대하면 왼쪽 벽의 문이 화면 밖으로 나간다. */
const val ZOOM_IN = 1.25f
const val ZOOM_OUT = 1f
private const val ZOOM_PIVOT_Y = 0.55f

// 침실 왼쪽 벽 문 그림의 자리(방 그림 대비).
private const val DOOR_LEFT = 0.012f
private const val DOOR_RIGHT = 0.108f
private const val DOOR_TOP = 0.14f
private const val DOOR_BOTTOM = 0.615f

/**
 * 작은 방. 고정된 2.5D 장면 위에 얻은 아이템이 정해진 자리에 놓인다.
 * 바닥 가구는 카링의 발 위치와 비교해 앞뒤가 바뀌어 깊이감이 생긴다.
 * 사용자가 가구를 옮기는 기능은 없다.
 */
@Composable
fun RoomWorld(
    space: Space,
    placed: List<RoomItem>,
    newItems: Set<RoomItem>,
    motion: CarlingMotion,
    zoom: Float,
    onExit: (Exit) -> Unit,
    onItem: (RoomItem) -> Unit,
    onFixture: (Fixture) -> Unit,
    modifier: Modifier = Modifier,
    actor: @Composable (roomW: Dp, roomH: Dp) -> Unit,
) {
    // 확대하면 방 가장자리(문)가 화면 밖으로 나가고, 축소하면 방 전체와 문이 보인다
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                transformOrigin = TransformOrigin(0.5f, ZOOM_PIVOT_Y)
            },
        contentAlignment = Alignment.Center,
    ) {
        val roomW = max(maxWidth, maxHeight * ROOM_RATIO)
        val roomH = roomW / ROOM_RATIO
        val left = (maxWidth - roomW) / 2
        val top = (maxHeight - roomH) / 2

        Image(
            painterResource(spaceRes(space)),
            contentDescription = space.label,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.requiredSize(roomW, roomH),
        )

        // 침실의 문은 그림으로 얹고, 다른 공간은 배경에 문·계단·아치가 그려져 있다
        if (space == Space.BEDROOM) {
            Image(
                painterResource(R.drawable.room_door),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(left + roomW * DOOR_LEFT, top + roomH * DOOR_TOP)
                    .size(roomW * (DOOR_RIGHT - DOOR_LEFT), roomH * (DOOR_BOTTOM - DOOR_TOP)),
            )
        }
        exitsOf(space).forEach { exit -> ExitSpot(exit, zoom < 1.05f, roomW, roomH, left, top) { onExit(exit) } }
        fixturesOf(space).forEach { f ->
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(left + roomW * f.left, top + roomH * f.top)
                    .size(roomW * (f.right - f.left), roomH * (f.bottom - f.top))
                    .zIndex(0.5f)
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onFixture(f) },
            )
        }

        val hasBedding = RoomItem.BEDDING in placed
        placed
            .filter { it.space == space }
            .filterNot { it == RoomItem.BED && hasBedding } // 침구 세트 그림은 이불을 덮은 침대다
            .sortedBy { it.depth }
            .forEach { item ->
                key(item) {
                    PlacedItem(item, item in newItems, roomW, roomH, left, top, motion) { onItem(item) }
                }
            }

        if (RoomItem.LAMP in placed) LampGlow(motion.lampOn, roomW, roomH, left, top)

        Box(Modifier.fillMaxSize().zIndex(2f)) { actor(roomW, roomH) }
    }
}

@Composable
private fun BoxScope.PlacedItem(item: RoomItem, isNew: Boolean, roomW: Dp, roomH: Dp, left: Dp, top: Dp, motion: CarlingMotion, onTap: () -> Unit) {
    val w = roomW * item.width
    val h = w * item.aspect
    val inFront by remember(item) { derivedStateOf { item.beside && !motion.onFurniture && motion.footFraction < item.foot } }
    val pop = remember(item) { Animatable(if (isNew) 0.4f else 1f) }
    LaunchedEffect(isNew) { if (isNew) pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 260f)) }
    Box(
        Modifier
            .align(Alignment.TopStart)
            .offset(left + roomW * item.x - w / 2, top + roomH * item.foot - h)
            .size(w, h)
            .zIndex(if (inFront) 3f else 1f)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                transformOrigin = TransformOrigin(0.5f, 1f)
            },
    ) {
        Image(painterResource(roomItemRes(item)), contentDescription = item.label, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        if (isNew) Sparkles(Modifier.align(Alignment.TopCenter))
    }
}

/** 출입구(문·계단·아치). 축소하면 이름표가 보이고, 누르면 그쪽으로 걸어간다. */
@Composable
private fun BoxScope.ExitSpot(exit: Exit, showLabel: Boolean, roomW: Dp, roomH: Dp, left: Dp, top: Dp, onTap: () -> Unit) {
    Box(
        Modifier
            .align(Alignment.TopStart)
            .offset(left + roomW * exit.left, top + roomH * exit.top)
            .size(roomW * (exit.right - exit.left), roomH * (exit.bottom - exit.top))
            .zIndex(0.6f)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap),
    ) {
        if (showLabel) {
            val pulse by rememberInfiniteTransition(label = "door").animateFloat(0.9f, 1.08f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
            Text(
                "🚪 ${exit.label}",
                fontSize = 20.sp,
                color = Ink,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { scaleX = pulse; scaleY = pulse }
                    .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

/** 새로 놓인 아이템을 알아보게 하는 반짝임. */
@Composable
private fun Sparkles(modifier: Modifier) {
    val pulse by rememberInfiniteTransition(label = "new").animateFloat(0.6f, 1.2f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "pulse")
    Text(
        "✨",
        fontSize = 40.sp,
        modifier = modifier.offset(y = (-22).dp).graphicsLayer {
            scaleX = pulse
            scaleY = pulse
        },
    )
}


/** 게임 속 스탠드의 불빛. 실제 차량 실내등과는 관계없다. */
@Composable
private fun BoxScope.LampGlow(on: Boolean, roomW: Dp, roomH: Dp, left: Dp, top: Dp) {
    val amount by animateFloatAsState(if (on) 1f else 0f, tween(420), label = "lamp")
    if (amount <= 0f) return
    val r = roomW * 0.17f
    val centerX = left + roomW * RoomItem.LAMP.x
    val centerY = top + roomH * 0.49f
    Canvas(
        Modifier
            .align(Alignment.TopStart)
            .offset(centerX - r, centerY - r)
            .size(r * 2)
            .zIndex(1.5f),
    ) {
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(
            Brush.radialGradient(listOf(Color(0xCCFFE9A8).copy(alpha = 0.85f * amount), Color(0x66FFD27A).copy(alpha = 0.4f * amount), Color.Transparent), c, size.width / 2),
            size.width / 2,
            c,
        )
    }
}
