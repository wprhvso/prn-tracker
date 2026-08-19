package ru.murasya.prn.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

val MED_COLORS: List<Int> =
    listOf(
        0xFFE45B5B,
        0xFFE8863C,
        0xFFD9A72B,
        0xFF8FB33A,
        0xFF43B36B,
        0xFF2FAE9E,
        0xFF3B93E0,
        0xFF5C6BE8,
        0xFF8A5BE0,
        0xFFC44FC7,
        0xFFE05590,
        0xFF7A8494,
        0xFFFFFFFF,
        0xFFB4B8C0,
        0xFF5A5F68,
        0xFF15171C,
    ).map { it.toInt() }

private val PRN_SHAPES =
    Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(36.dp),
    )

@Composable
fun PrnTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme =
        if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    CompositionLocalProvider(LocalReducedMotion provides rememberReducedMotion()) {
        MaterialTheme(colorScheme = scheme, shapes = PRN_SHAPES, content = content)
    }
}

fun <T> springySpatial(): SpringSpec<T> = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)

fun <T> calmEffects(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)

fun onSwatch(swatch: Color): Color = if (swatch.luminance() > 0.5f) Color.Black else Color.White

fun nextColor(used: List<Int>): Int = MED_COLORS.firstOrNull { it !in used } ?: MED_COLORS[used.size % MED_COLORS.size]
