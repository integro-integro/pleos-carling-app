package work.integro.carling.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 마이카링 톤: 라벤더 블루 배경, 남색 글자, 심장과 차량 포인트에만 민트
val Ink = Color(0xFF262A5C)
val Muted = Color(0xFF767BA6)
val Faint = Color(0xFFB4B7D6)
val Paper = Color(0xFFF7F7FF)
val Line = Color(0xFFE3E2F7)
val Lilac = Color(0xFF8C7CF0)
val LilacSoft = Color(0xFFEDEAFF)
val Mint = Color(0xFF3CCB9A)
val MintSoft = Color(0xFFDDF8EE)
val Honey = Color(0xFFF2A93B)
val Coral = Color(0xFFF07B7B)
val Scrim = Color(0x99202450)

private val CarlingColors = lightColorScheme(
    primary = Lilac,
    onPrimary = Color.White,
    secondary = Mint,
    surface = Paper,
    onSurface = Ink,
    background = Paper,
    onBackground = Ink,
    outline = Line,
    secondaryContainer = LilacSoft,
    onSecondaryContainer = Ink,
    surfaceVariant = LilacSoft,
    onSurfaceVariant = Muted,
)

/** 창이 좁거나 세로일 때 true. Pleos는 앱 두 개를 띄우면 각 앱을 세로 창으로 보여준다. */
val LocalCompact = staticCompositionLocalOf { false }

@Composable
fun CarlingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CarlingColors) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Paper)) {
            val compact = maxWidth < 1100.dp || maxWidth < maxHeight
            CompositionLocalProvider(LocalCompact provides compact) { content() }
        }
    }
}

/** 카링의 말풍선. */
@Composable
fun Speech(text: String, modifier: Modifier = Modifier, maxWidth: Dp = 640.dp) {
    Text(
        text,
        fontSize = 24.sp,
        lineHeight = 34.sp,
        color = Ink,
        textAlign = TextAlign.Center,
        modifier = modifier
            .widthIn(max = maxWidth)
            .background(Color.White.copy(alpha = 0.94f), RoundedCornerShape(28.dp))
            .border(1.5.dp, Line, RoundedCornerShape(28.dp))
            .padding(horizontal = 26.dp, vertical = 12.dp),
    )
}

/** 흰 카드 면. */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 24.dp, content: @Composable () -> Unit) {
    Box(
        modifier
            .background(Color.White.copy(alpha = 0.96f), RoundedCornerShape(28.dp))
            .border(1.dp, Line, RoundedCornerShape(28.dp))
            .padding(padding),
    ) { content() }
}

/** 둥근 선택 칩. */
@Composable
fun Pill(label: String, on: Boolean, modifier: Modifier = Modifier, onColor: Color = Lilac, fontSize: Int = 21, onClick: () -> Unit) {
    Text(
        label,
        fontSize = fontSize.sp,
        color = if (on) Color.White else Ink,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
        modifier = modifier
            .background(if (on) onColor else Color.White, RoundedCornerShape(50))
            .border(1.5.dp, if (on) onColor else Line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
    )
}

/** 화면 위에 어둡게 깔리는 창. */
@Composable
fun FullScrim(onDismiss: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Scrim)
            .then(if (onDismiss != null) Modifier.clickable(onClick = onDismiss) else Modifier)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}
