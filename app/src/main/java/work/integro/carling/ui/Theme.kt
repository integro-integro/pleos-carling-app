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
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import work.integro.carling.data.Scene

// 설계서의 밤하늘 톤
val Night = Color(0xFF0B1030)
val NightDeep = Color(0xFF070A22)
val Panel = Color(0xFF1A2052)
val PanelLight = Color(0xFF252C6B)
val Ink = Color(0xFFF2F4FF)
val Muted = Color(0xFFA9B0D9)
val Accent = Color(0xFF8E9BFF)
val Gold = Color(0xFFFFD98A)

private val CarlingColors = darkColorScheme(
    primary = Accent,
    onPrimary = NightDeep,
    secondary = Gold,
    surface = Panel,
    onSurface = Ink,
    background = Night,
    onBackground = Ink,
    outline = Color(0xFF4A528F),
    secondaryContainer = PanelLight,
    onSecondaryContainer = Ink,
    surfaceVariant = PanelLight,
    onSurfaceVariant = Muted,
)

/** 창이 좁거나 세로일 때 true. Pleos는 앱 두 개를 띄우면 각 앱을 세로 창으로 보여준다. */
val LocalCompact = staticCompositionLocalOf { false }

@Composable
fun CarlingTheme(scene: Scene, showScene: Boolean, nightnessOverride: Float? = null, content: @Composable () -> Unit) {
    val nightness = nightnessOverride ?: rememberRealNightness()
    MaterialTheme(colorScheme = CarlingColors) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxWidth < 1100.dp || maxWidth < maxHeight
            CompositionLocalProvider(LocalCompact provides compact, LocalNightness provides nightness) {
                if (showScene) SceneBackground(scene, nightness) else NightSky()
                content()
            }
        }
    }
}

@Composable
fun Speech(text: String, modifier: Modifier = Modifier, maxWidth: Dp = 760.dp) {
    val compact = LocalCompact.current
    Text(
        text,
        fontSize = if (compact) 24.sp else 30.sp,
        lineHeight = if (compact) 34.sp else 42.sp,
        color = Ink,
        textAlign = TextAlign.Center,
        modifier = modifier
            .widthIn(max = maxWidth)
            .background(PanelLight.copy(alpha = 0.9f), RoundedCornerShape(28.dp))
            .border(1.dp, Accent.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
            .padding(horizontal = 28.dp, vertical = 16.dp),
    )
}

@Composable
fun PanelBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .background(Panel.copy(alpha = 0.92f), RoundedCornerShape(28.dp))
            .border(1.dp, Accent.copy(alpha = 0.25f), RoundedCornerShape(28.dp))
            .padding(24.dp),
    ) { content() }
}
