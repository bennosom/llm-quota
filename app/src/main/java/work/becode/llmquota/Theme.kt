package work.becode.llmquota

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/** The Matrix look: black field, poison-green text, monospace type. */
@Composable
fun LlmQuotaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = matrixColors(), typography = monoTypography(Typography())) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize(), content = content)
    }
}

private fun matrixColors() = darkColorScheme(
    background = Color.Black,
    surface = Color(0xFF050D06),
    primary = Poison,
    onPrimary = Color.Black,
    onBackground = Poison,
    onSurface = Poison,
    onSurfaceVariant = PoisonDim,
    outline = PoisonDim,
    error = Color(0xFFFF4D4D),
)

private fun monoTypography(base: Typography): Typography {
    val mono = FontFamily.Monospace
    fun TextStyle.mono() = copy(fontFamily = mono)
    return base.copy(
        bodyLarge = base.bodyLarge.mono(),
        bodyMedium = base.bodyMedium.mono(),
        bodySmall = base.bodySmall.mono(),
        titleLarge = base.titleLarge.mono(),
        titleSmall = base.titleSmall.mono(),
        headlineSmall = base.headlineSmall.mono(),
        labelLarge = base.labelLarge.mono(),
    )
}
