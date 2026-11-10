package work.integro.carling.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.R
import work.integro.carling.data.CarlingData
import work.integro.carling.game.FUTURE_AREAS
import work.integro.carling.game.Space

@DrawableRes
internal fun spaceRes(space: Space): Int = when (space) {
    Space.BEDROOM -> R.drawable.room_base
    Space.LIVING -> R.drawable.space_living
    Space.KITCHEN -> R.drawable.space_kitchen
    Space.BATHROOM -> R.drawable.space_bathroom
    Space.STUDIO -> R.drawable.space_studio
}

/**
 * 공간 이동. 진화 단계마다 공간의 개념이 넓어지고, 그 사이 레벨마다 같은 개념 안의 공간이 열린다.
 * 잠긴 공간은 열리는 레벨을 보여준다.
 */
@Composable
fun SpacePicker(data: CarlingData, onPick: (Space) -> Unit, onClose: () -> Unit) {
    val level = data.level
    FullScrim {
        PanelBox(Modifier.widthIn(max = 1240.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("공간", fontSize = 30.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(14.dp))
                    Text("1층: 거실·주방·화장실 / 2층: 방·작업실. 새 아이템은 카링이 있는 공간부터 채워져요.", fontSize = 19.sp, color = Muted)
                }
                Space.entries.groupBy { it.area }.forEach { (area, spaces) ->
                    Spacer(Modifier.height(14.dp))
                    Text(area, fontSize = 22.sp, color = RoseDeep, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        spaces.forEach { s -> SpaceCard(s, data, level) { onPick(s) } }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("다음 공간", fontSize = 22.sp, color = RoseDeep, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FUTURE_AREAS.forEach { f ->
                        Column(
                            Modifier.width(190.dp).background(Color(0xFFF3E6EA), RoundedCornerShape(20.dp)).padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("🔒 ${f.icon}", fontSize = 28.sp)
                            Text(f.label, fontSize = 21.sp, color = Ink, fontWeight = FontWeight.Bold)
                            Text("${f.evolution} · Lv.${f.level}", fontSize = 16.sp, color = Muted)
                            Text("추후 공개", fontSize = 16.sp, color = Muted)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = onClose, modifier = Modifier.align(Alignment.End).height(64.dp)) { Text("닫기", fontSize = 24.sp) }
            }
        }
    }
}

@Composable
private fun SpaceCard(space: Space, data: CarlingData, level: Int, onClick: () -> Unit) {
    val open = space.isOpen(level)
    val here = data.currentSpace == space
    val got = space.items.count { it in data.placed }
    Column(
        Modifier
            .width(250.dp)
            .background(if (here) Blush else Color.White, RoundedCornerShape(22.dp))
            .border(if (here) 4.dp else 2.dp, if (here) Rose else Color(0x33EF7F9E), RoundedCornerShape(22.dp))
            .clickable(enabled = open && !here, onClick = onClick)
            .padding(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(16.dp))) {
            Image(
                painterResource(spaceRes(space)),
                contentDescription = space.label,
                contentScale = ContentScale.Crop,
                colorFilter = if (open) null else ColorFilter.tint(Color(0x99FFFFFF), BlendMode.SrcAtop),
                modifier = Modifier.fillMaxSize(),
            )
            if (!open) Text("🔒", fontSize = 48.sp, modifier = Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${space.icon} ${space.label} ${space.floorLabel}", fontSize = 22.sp, color = Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    here -> "지금 여기"
                    open -> "아이템 $got/${space.items.size}"
                    else -> "Lv.${space.unlockLevel}에 열려요"
                },
                fontSize = 16.sp,
                color = if (open) RoseDeep else Muted,
            )
        }
    }
}

/** 아이템 도감: 공간마다 얻을 수 있는 아이템 수와 얻은 수. 아직 없는 아이템은 실루엣으로 보인다. */
@Composable
fun ItemDex(data: CarlingData, onClose: () -> Unit) {
    var tab by remember { mutableStateOf(data.currentSpace) }
    val total = Space.entries.sumOf { it.items.size }
    val got = data.placed.size
    FullScrim {
        PanelBox(Modifier.widthIn(max = 1240.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("아이템 도감", fontSize = 30.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(14.dp))
                    Text("전체 $got / $total", fontSize = 22.sp, color = RoseDeep, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Space.entries.forEach { s ->
                        val on = s == tab
                        val n = s.items.count { it in data.placed }
                        Text(
                            "${s.icon} ${s.label}  $n/${s.items.size}" + if (s.isOpen(data.level)) "" else " 🔒",
                            fontSize = 20.sp,
                            color = if (on) Color.White else Ink,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .background(if (on) Rose else Color.White, RoundedCornerShape(50))
                                .clickable { tab = s }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                val open = tab.isOpen(data.level)
                Text(
                    if (open) "운행할 때마다 이 공간의 아이템이 하나씩 놓여요." else "Lv.${tab.unlockLevel}에 열리는 공간이에요.",
                    fontSize = 18.sp,
                    color = Muted,
                )
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.height(430.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(tab.items, key = { it.name }) { item ->
                        val owned = item in data.placed
                        Column(
                            Modifier.background(if (owned) Color.White else Color(0xFFF6ECEF), RoundedCornerShape(18.dp)).padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Image(
                                painterResource(roomItemRes(item)),
                                contentDescription = item.label,
                                colorFilter = if (owned) null else ColorFilter.tint(Color(0x66806070), BlendMode.SrcIn),
                                modifier = Modifier.size(104.dp),
                            )
                            Text(if (owned) item.label else "???", fontSize = 18.sp, color = if (owned) Ink else Muted)
                            val dup = data.duplicates[item] ?: 0
                            if (owned && dup > 0) Text("+${dup}개 더", fontSize = 14.sp, color = Muted)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onClose, modifier = Modifier.align(Alignment.End).height(64.dp)) { Text("닫기", fontSize = 24.sp) }
            }
        }
    }
}

/** 여러 공간으로 이어지는 출입구(계단·복도)를 눌렀을 때 갈 곳을 고른다. */
@Composable
fun ExitChooser(exit: work.integro.carling.game.Exit, level: Int, onPick: (Space) -> Unit, onClose: () -> Unit) {
    FullScrim {
        PanelBox(Modifier.widthIn(max = 760.dp)) {
            Column {
                Text("🚪 ${exit.label}", fontSize = 28.sp, color = Ink, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    exit.targets.forEach { s ->
                        val open = s.isOpen(level)
                        Column(
                            Modifier
                                .width(220.dp)
                                .background(Color.White, RoundedCornerShape(22.dp))
                                .clickable(enabled = open) { onPick(s) }
                                .padding(10.dp),
                        ) {
                            Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp))) {
                                Image(
                                    painterResource(spaceRes(s)),
                                    contentDescription = s.label,
                                    contentScale = ContentScale.Crop,
                                    colorFilter = if (open) null else ColorFilter.tint(Color(0x99FFFFFF), BlendMode.SrcAtop),
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (!open) Text("🔒", fontSize = 40.sp, modifier = Modifier.align(Alignment.Center))
                            }
                            Text("${s.icon} ${s.label} · ${s.floorLabel}", fontSize = 21.sp, color = Ink, fontWeight = FontWeight.Bold)
                            Text(if (open) "가기" else "Lv.${s.unlockLevel}에 열려요", fontSize = 16.sp, color = if (open) RoseDeep else Muted)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Button(onClick = onClose, modifier = Modifier.align(Alignment.End).height(60.dp)) { Text("닫기", fontSize = 22.sp) }
            }
        }
    }
}
