package ru.murasya.prn.ui

import android.graphics.Color as AndroidColor
import android.text.format.DateFormat
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

/** The six primaries plus the wrap back to red: hue is linear, so the gradient between them is exact. */
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

/** A number the user may leave blank; blank always means "this feature is off". */
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

/**
 * A scrolling row of swatches for the common case, and behind a toggle the picker everyone already
 * knows: a shade field with a hue bar under it. Three abstract sliders asked the user to think in
 * coordinates; this asks them to point at the colour they want. It still reaches the greys, white
 * and black that a fixed palette cannot, and it stays folded away so the form keeps its height.
 */
@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    val start = remember { hsvOf(selected) }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var brightness by remember { mutableFloatStateOf(start[2]) }
    var custom by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SwatchRow(selected, Modifier.weight(1f)) { argb ->
                val picked = hsvOf(argb)
                hue = picked[0]
                saturation = picked[1]
                brightness = picked[2]
                onSelect(argb)
            }
            IconButton(onClick = { custom = !custom }) {
                Icon(painterResource(R.drawable.ic_tune), stringResource(R.string.action_custom_colour))
            }
        }
        if (custom) {
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

@Composable
private fun SwatchRow(selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MED_COLORS.forEach { argb -> ColorDot(argb, argb == selected, onSelect) }
    }
}

/**
 * Saturation left to right, brightness top to bottom, over the currently chosen hue — two channels
 * picked with one finger. Tap and drag are separate `pointerInput` blocks because a single one only
 * ever runs one detector; declaring the drag last is what lets it measure its slop before the tap
 * handler consumes the press.
 *
 * The gesture blocks are keyed on [Unit] so a drag survives recomposition, which means they hold
 * whichever [onChange] they were built with — [rememberUpdatedState] is what keeps that from being
 * the one from the first frame, editing a copy of the form as it stood before the drag began.
 */
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

/** The full spectrum. Hue is linear across the bar, so seven evenly spaced stops draw it exactly. */
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

/** White on black so the ring stays visible over every colour underneath it. */
private fun DrawScope.marker(at: Offset) {
    val radius = MARKER_RADIUS.dp.toPx()
    drawCircle(Color.Black, radius, at, style = Stroke(width = 3.dp.toPx()))
    drawCircle(Color.White, radius, at, style = Stroke(width = 2.dp.toPx()))
}

@Composable
private fun ColorDot(argb: Int, chosen: Boolean, onSelect: (Int) -> Unit) {
    val color = Color(argb)
    val edge = if (chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier =
            Modifier
                .size(SWATCH_SIZE.dp)
                .clip(CircleShape)
                .background(color)
                .border(if (chosen) 3.dp else 1.dp, edge, CircleShape)
                .clickable { onSelect(argb) },
        contentAlignment = Alignment.Center,
    ) {
        if (chosen) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = onSwatch(color),
                modifier = Modifier.size(18.dp),
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

/**
 * The dial first, because that is how people read a clock, with a keyboard mode one tap away for
 * anyone who would rather type. Both halves are the platform's own pickers.
 */
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

/** [Color.hsv] throws on anything out of range, and a drag that runs a pixel past the edge is out. */
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
