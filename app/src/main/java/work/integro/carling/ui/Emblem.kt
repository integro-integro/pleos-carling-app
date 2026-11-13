package work.integro.carling.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import work.integro.carling.R
import work.integro.carling.game.EmblemTier

/** 엠블럼 그림(원본 생성 5장, 2026-11-12)과 가운데 원판의 세로 위치·글자색. */
private class EmblemArt(val res: Int, val centerY: Float, val ink: Color)

private fun artOf(t: EmblemTier) = when (t) {
    EmblemTier.SPROUT -> EmblemArt(R.drawable.emblem_sprout, 0.57f, Color(0xFF444441))
    EmblemTier.BRONZE -> EmblemArt(R.drawable.emblem_bronze, 0.41f, Color(0xFF712B13))
    EmblemTier.SILVER -> EmblemArt(R.drawable.emblem_silver, 0.44f, Color(0xFF0C447C))
    EmblemTier.GOLD -> EmblemArt(R.drawable.emblem_gold, 0.44f, Color(0xFF633806))
    EmblemTier.AURORA -> EmblemArt(R.drawable.emblem_aurora, 0.42f, Color(0xFF3C3489))
}

/**
 * 레벨 엠블럼(사용자 확정 11/12): 차 앞에 다는 대표 뱃지. 10레벨마다 그림이 화려해지고(새싹 → 동 → 은 → 금 → 오로라),
 * 가운데 원판에 레벨 숫자를 얹는다.
 */
@Composable
fun LevelEmblem(level: Int, size: Dp, modifier: Modifier = Modifier) {
    val art = artOf(EmblemTier.of(level))
    Box(modifier.size(size)) {
        Image(painterResource(art.res), contentDescription = "LV.$level 엠블럼", modifier = Modifier.size(size))
        Column(
            Modifier.align(Alignment.TopCenter).offset(y = size * art.centerY - size * 0.17f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val shadow = TextStyle(shadow = Shadow(Color.White, blurRadius = 6f))
            Text("LV", fontSize = (size.value * 0.11f).sp, color = art.ink, fontWeight = FontWeight.Bold, style = shadow, lineHeight = (size.value * 0.12f).sp)
            Text("$level", fontSize = (size.value * 0.22f).sp, color = art.ink, fontWeight = FontWeight.Black, style = shadow, lineHeight = (size.value * 0.24f).sp)
        }
    }
}
