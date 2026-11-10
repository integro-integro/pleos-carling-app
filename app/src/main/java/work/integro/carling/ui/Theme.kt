package work.integro.carling.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 카링월드의 파스텔 톤
val Cream = Color(0xFFFFF7F1)
val Blush = Color(0xFFFFE3E8)
val Ink = Color(0xFF5B3A44)
val Muted = Color(0xFF9B7C86)
val Rose = Color(0xFFEF7F9E)
val RoseDeep = Color(0xFFD9607F)
val Honey = Color(0xFFE9A23B)
val Mint = Color(0xFF5ED6A5)
val Sky = Color(0xFF7CC4F2)
val Scrim = Color(0xB03A2A3A)

private val CarlingColors = lightColorScheme(
    primary = Rose,
    onPrimary = Color.White,
    secondary = Honey,
    surface = Cream,
    onSurface = Ink,
    background = Cream,
    onBackground = Ink,
    outline = Color(0xFFE7C4CE),
    secondaryContainer = Blush,
    onSecondaryContainer = Ink,
    surfaceVariant = Blush,
    onSurfaceVariant = Muted,
)

/** 창이 좁거나 세로일 때 true. Pleos는 앱 두 개를 띄우면 각 앱을 세로 창으로 보여준다. */
val LocalCompact = staticCompositionLocalOf { false }

@Composable
fun CarlingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CarlingColors) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Cream)) {
            val compact = maxWidth < 1100.dp || maxWidth < maxHeight
            CompositionLocalProvider(LocalCompact provides compact) { content() }
        }
    }
}

/** 카링의 말풍선. */
@Composable
fun Speech(text: String, modifier: Modifier = Modifier, maxWidth: Dp = 760.dp) {
    val compact = LocalCompact.current
    Text(
        text,
        fontSize = if (compact) 22.sp else 26.sp,
        lineHeight = if (compact) 32.sp else 36.sp,
        color = Ink,
        textAlign = TextAlign.Center,
        modifier = modifier
            .widthIn(max = maxWidth)
            .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(26.dp))
            .border(2.dp, Rose.copy(alpha = 0.45f), RoundedCornerShape(26.dp))
            .padding(horizontal = 26.dp, vertical = 12.dp),
    )
}

@Composable
fun PanelBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .background(Cream, RoundedCornerShape(30.dp))
            .border(2.dp, Rose.copy(alpha = 0.35f), RoundedCornerShape(30.dp))
            .padding(26.dp),
    ) { content() }
}
