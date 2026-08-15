package ru.murasya.prn.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay

private const val ENTRANCE_WINDOW_MS = 900L
private const val ENTRANCE_MS = 420
private const val ENTRANCE_STAGGER_MS = 40L
private const val ENTRANCE_STAGGER_CAP = 6
private const val ENTRANCE_RISE_DP = 18
private const val ENTRANCE_SCALE = 0.94f
private const val PRESS_SCALE = 0.965f
private const val BREATHE_SCALE = 1.14f
private const val BREATHE_HALF_MS = 520
private const val BREATHE_CYCLES = 3
private const val TICKER_MS = 260
private const val REVEAL_MS = 320
private const val POP_SCALE = 0.6f
private const val FULL = 1f

private val EMPHASIZED_DECELERATE = CubicBezierEasing(0.05f, 0.7f, 0.1f, FULL)
private val EMPHASIZED_ACCELERATE = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private val STANDARD = CubicBezierEasing(0.2f, 0f, 0f, FULL)

class EntranceWindow(
    private val startedAt: Long,
    private val open: Boolean,
) {
    fun waitFor(index: Int): Long? {
        if (!open || System.currentTimeMillis() - startedAt > ENTRANCE_WINDOW_MS) return null
        return index.coerceIn(0, ENTRANCE_STAGGER_CAP) * ENTRANCE_STAGGER_MS
    }
}

val LocalReducedMotion = staticCompositionLocalOf { false }

val LocalEntranceWindow = staticCompositionLocalOf { EntranceWindow(0L, false) }

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember { mutableStateOf(animationsOff(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reduced = animationsOff(context) }
    return reduced
}

@Composable
fun rememberEntranceWindow(ready: Boolean): EntranceWindow {
    val reduced = LocalReducedMotion.current
    return remember(ready, reduced) { EntranceWindow(System.currentTimeMillis(), ready && !reduced) }
}

@Composable
fun <T> spatial(): FiniteAnimationSpec<T> = if (LocalReducedMotion.current) snap() else springySpatial()

@Composable
fun <T> effects(): FiniteAnimationSpec<T> = if (LocalReducedMotion.current) snap() else calmEffects()

@Composable
fun LazyItemScope.itemPlacement(): Modifier =
    Modifier.animateItem(
        fadeInSpec = effects(),
        placementSpec = placementSpec(),
        fadeOutSpec = effects(),
    )

@Composable
fun LazyItemScope.itemMotion(index: Int): Modifier = itemPlacement().entrance(index)

@Composable
fun Modifier.entrance(index: Int = 0): Modifier {
    val window = LocalEntranceWindow.current
    val wait = remember(window) { window.waitFor(index) }
    val progress = remember(window) { Animatable(if (wait == null) FULL else 0f) }
    LaunchedEffect(window) {
        if (wait == null) return@LaunchedEffect
        delay(wait)
        progress.animateTo(FULL, tween(ENTRANCE_MS, easing = EMPHASIZED_DECELERATE))
    }
    return graphicsLayer {
        val shown = progress.value
        alpha = shown
        translationY = (FULL - shown) * ENTRANCE_RISE_DP.dp.toPx()
        scaleX = ENTRANCE_SCALE + (FULL - ENTRANCE_SCALE) * shown
        scaleY = scaleX
    }
}

@Composable
fun Modifier.pressSquish(source: InteractionSource, scale: Float = PRESS_SCALE): Modifier {
    val pressed by source.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val squish by
        animateFloatAsState(
            targetValue = if (pressed && !reduced) scale else FULL,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
            label = "pressSquish",
        )
    return graphicsLayer {
        scaleX = squish
        scaleY = squish
    }
}

@Composable
fun Modifier.breathing(active: Boolean): Modifier = if (active && !LocalReducedMotion.current) breathe() else this

@Composable
private fun Modifier.breathe(): Modifier {
    val pulse = remember { Animatable(FULL) }
    LaunchedEffect(Unit) {
        repeat(BREATHE_CYCLES) {
            pulse.animateTo(BREATHE_SCALE, tween(BREATHE_HALF_MS, easing = STANDARD))
            pulse.animateTo(FULL, tween(BREATHE_HALF_MS, easing = STANDARD))
        }
    }
    return graphicsLayer {
        scaleX = pulse.value
        scaleY = pulse.value
    }
}

@Composable
fun TickerText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = text,
        transitionSpec = { if (reduced) instant() else ticker() },
        contentAlignment = Alignment.CenterStart,
        modifier = modifier.clearAndSetSemantics { contentDescription = text },
        label = "ticker",
    ) { value ->
        Text(text = value, style = style, color = color)
    }
}

@Composable
fun Reveal(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible,
        enter = if (reduced) EnterTransition.None else grow(),
        exit = if (reduced) ExitTransition.None else collapse(),
        modifier = modifier,
    ) {
        content()
    }
}

@Composable
fun PopIn(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible,
        enter = if (reduced) EnterTransition.None else scaleIn(spatial(), POP_SCALE) + fadeIn(effects()),
        exit = if (reduced) ExitTransition.None else scaleOut(effects(), POP_SCALE) + fadeOut(effects()),
        modifier = modifier,
    ) {
        content()
    }
}

@Composable
fun sizing(): FiniteAnimationSpec<IntSize> =
    if (LocalReducedMotion.current) {
        snap()
    } else {
        spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntSize.VisibilityThreshold)
    }

@Composable
private fun placementSpec(): FiniteAnimationSpec<IntOffset> =
    if (LocalReducedMotion.current) {
        snap()
    } else {
        spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = IntOffset.VisibilityThreshold,
        )
    }

private fun grow(): EnterTransition =
    expandVertically(tween(REVEAL_MS, easing = EMPHASIZED_DECELERATE), Alignment.Top) +
        fadeIn(tween(REVEAL_MS, delayMillis = REVEAL_MS / 3))

private fun collapse(): ExitTransition =
    shrinkVertically(tween(REVEAL_MS, easing = EMPHASIZED_ACCELERATE), Alignment.Top) +
        fadeOut(tween(REVEAL_MS / 2))

private fun AnimatedContentTransitionScope<String>.ticker(): ContentTransform {
    val enter =
        slideInVertically(tween(TICKER_MS, easing = EMPHASIZED_DECELERATE)) { height -> height / 2 } +
            fadeIn(tween(TICKER_MS))
    val exit =
        slideOutVertically(tween(TICKER_MS, easing = EMPHASIZED_ACCELERATE)) { height -> -height / 2 } +
            fadeOut(tween(TICKER_MS / 2))
    return enter togetherWith exit using SizeTransform { _, _ -> tween(TICKER_MS, easing = STANDARD) }
}

private fun AnimatedContentTransitionScope<String>.instant(): ContentTransform =
    EnterTransition.None togetherWith ExitTransition.None using null

private fun animationsOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, FULL) == 0f
