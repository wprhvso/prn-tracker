package ru.murasya.prn.ui

import android.graphics.Color as AndroidColor
import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.murasya.prn.R
import ru.murasya.prn.domain.MINUTES_PER_HOUR
import ru.murasya.prn.domain.formatMinuteOfDay

private const val SWATCH_SIZE = 32
private const val MAX_HUE = 360f
private const val HUE_SECTOR = 60f
private const val HUE_BAR_HEIGHT = 30
private const val MARKER_RADIUS = 9
private const val SHADE_ASPECT = 1.7f
private const val TUNE_TURN = 180f
private const val RING_CHOSEN = 3
private const val RING_IDLE = 1
private const val CHECK_SIZE = 18

private val HUE_STOPS: List<Color> = List(7) { step -> Color.hsv(step * HUE_SECTOR, 1f, 1f) }

@Composable
fun PlainField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { char -> char.isDigit() || char == '.' || char == ',' }) },
        label = { Text(label) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    val start = remember { hsvOf(selected) }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var brightness by remember { mutableFloatStateOf(start[2]) }
    var custom by remember { mutableStateOf(false) }

    val turn by animateFloatAsState(if (custom) TUNE_TURN else 0f, animationSpec = spatial(), label = "tune")
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SwatchRow(selected, Modifier.weight(1f)) { argb ->
                val picked = hsvOf(argb)
                hue = picked[0]
                saturation = picked[1]
                brightness = picked[2]
                onSelect(argb)
            }
            IconButton(onClick = { custom = !custom }) {
                Icon(
                    painter = painterResource(R.drawable.ic_tune),
                    contentDescription = stringResource(R.string.action_custom_colour),
                    modifier = Modifier.graphicsLayer { rotationZ = turn },
                )
            }
        }
        Reveal(visible = custom) {
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ShadeField(hue, saturation, brightness) { s, v ->
                    saturation = s
                    brightness = v
                    onSelect(argbOf(hue, s, v))
                }
                HueBar(hue) { h ->
                    hue = h
                    onSelect(argbOf(h, saturation, brightness))
                }
            }
        }
    }
}

@Composable
private fun SwatchRow(selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MED_COLORS.forEach { argb -> ColorDot(argb, argb == selected, onSelect) }
    }
}

@Composable
private fun ShadeField(hue: Float, saturation: Float, brightness: Float, onChange: (Float, Float) -> Unit) {
    val latest by rememberUpdatedState(onChange)
    val pure = argbOf(hue, 1f, 1f)
    val across = remember(pure) { Brush.horizontalGradient(listOf(Color.White, Color(pure))) }
    val down = remember { Brush.verticalGradient(listOf(Color.Transparent, Color.Black)) }
    Canvas(
        contentDescription = stringResource(R.string.colour_shade),
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(SHADE_ASPECT)
                .clip(MaterialTheme.shapes.medium)
                .pointerInput(Unit) {
                    detectTapGestures { at -> latest(saturationAt(at, size), brightnessAt(at, size)) }
                }.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { at -> latest(saturationAt(at, size), brightnessAt(at, size)) },
                        onDrag = { change, _ ->
                            latest(saturationAt(change.position, size), brightnessAt(change.position, size))
                        },
                    )
                },
    ) {
        drawRect(brush = across)
        drawRect(brush = down)
        marker(
            Offset(
                x = saturation.coerceIn(0f, 1f) * size.width,
                y = (1f - brightness.coerceIn(0f, 1f)) * size.height,
            ),
        )
    }
}

@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    val latest by rememberUpdatedState(onChange)
    val spectrum = remember { Brush.horizontalGradient(HUE_STOPS) }
    Canvas(
        contentDescription = stringResource(R.string.colour_hue),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(HUE_BAR_HEIGHT.dp)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures { at -> latest(hueAt(at, size)) }
                }.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { at -> latest(hueAt(at, size)) },
                        onDrag = { change, _ -> latest(hueAt(change.position, size)) },
                    )
                },
    ) {
        drawRect(brush = spectrum)
        marker(Offset(x = hue.coerceIn(0f, MAX_HUE) / MAX_HUE * size.width, y = size.height / 2f))
    }
}

private fun DrawScope.marker(at: Offset) {
    val radius = MARKER_RADIUS.dp.toPx()
    drawCircle(Color.Black, radius, at, style = Stroke(width = 3.dp.toPx()))
    drawCircle(Color.White, radius, at, style = Stroke(width = 2.dp.toPx()))
}

@Composable
private fun ColorDot(argb: Int, chosen: Boolean, onSelect: (Int) -> Unit) {
    val color = Color(argb)
    val scheme = MaterialTheme.colorScheme
    val target = if (chosen) scheme.onSurface else scheme.outlineVariant
    val edge by animateColorAsState(target, animationSpec = effects(), label = "dotEdge")
    val ring by
        animateDpAsState(
            targetValue = if (chosen) RING_CHOSEN.dp else RING_IDLE.dp,
            animationSpec = spatial(),
            label = "dotRing",
        )
    Box(
        modifier =
            Modifier
                .size(SWATCH_SIZE.dp)
                .clip(CircleShape)
                .background(color)
                .border(ring, edge, CircleShape)
                .clickable { onSelect(argb) },
        contentAlignment = Alignment.Center,
    ) {
        PopIn(chosen) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = onSwatch(color),
                modifier = Modifier.size(CHECK_SIZE.dp),
            )
        }
    }
}

@Composable
fun TimeButton(label: String, minuteOfDay: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        modifier = modifier,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(8.dp))
        Text(text = formatMinuteOfDay(minuteOfDay), style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrnTimePickerDialog(title: String, initialMinuteOfDay: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val context = LocalContext.current
    val state =
        rememberTimePickerState(
            initialHour = initialMinuteOfDay / MINUTES_PER_HOUR,
            initialMinute = initialMinuteOfDay % MINUTES_PER_HOUR,
            is24Hour = DateFormat.is24HourFormat(context),
        )
    var typing by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                if (typing) TimeInput(state = state) else TimePicker(state = state)
                TimeDialogButtons(
                    typing = typing,
                    onToggle = { typing = !typing },
                    onDismiss = onDismiss,
                    onConfirm = { onConfirm(state.hour * MINUTES_PER_HOUR + state.minute) },
                )
            }
        }
    }
}

@Composable
private fun TimeDialogButtons(typing: Boolean, onToggle: () -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onToggle) {
            Icon(
                painter = painterResource(if (typing) R.drawable.ic_clock else R.drawable.ic_keyboard),
                contentDescription = stringResource(if (typing) R.string.action_dial else R.string.action_keyboard),
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_ok)) }
    }
}

private fun hsvOf(argb: Int): FloatArray {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(argb, hsv)
    return hsv
}

private fun argbOf(hue: Float, saturation: Float, brightness: Float): Int =
    Color
        .hsv(
            hue.coerceIn(0f, MAX_HUE),
            saturation.coerceIn(0f, 1f),
            brightness.coerceIn(0f, 1f),
        ).toArgb()

private fun saturationAt(at: Offset, size: IntSize): Float = (at.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)

private fun brightnessAt(at: Offset, size: IntSize): Float = 1f - (at.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)

private fun hueAt(at: Offset, size: IntSize): Float =
    (at.x / size.width.coerceAtLeast(1) * MAX_HUE).coerceIn(0f, MAX_HUE)
