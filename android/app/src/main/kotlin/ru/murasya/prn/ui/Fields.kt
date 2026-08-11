package ru.murasya.prn.ui

import android.graphics.Color as AndroidColor
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ru.murasya.prn.R
import ru.murasya.prn.domain.MINUTES_PER_HOUR
import ru.murasya.prn.domain.formatMinuteOfDay

private const val SWATCH_SIZE = 32
private const val MAX_HUE = 360f
private const val LABEL_WIDTH = 96

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
 * A scrolling row of swatches for the common case, and hue/saturation/brightness behind a toggle
 * for everything else — including the greys, white and black that a fixed palette cannot reach.
 * The sliders stay folded away so the form keeps its height.
 */
@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    val start = remember { hsvOf(selected) }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var brightness by remember { mutableFloatStateOf(start[2]) }
    var custom by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            ChannelSliders(
                hue = hue,
                saturation = saturation,
                brightness = brightness,
                onChange = { h, s, v ->
                    hue = h
                    saturation = s
                    brightness = v
                    onSelect(Color.hsv(h, s, v).toArgb())
                },
            )
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
private fun ChannelSliders(hue: Float, saturation: Float, brightness: Float, onChange: (Float, Float, Float) -> Unit) {
    val tint = Color.hsv(hue, saturation, brightness)
    Column {
        ChannelSlider(stringResource(R.string.colour_hue), hue, MAX_HUE, tint) {
            onChange(it, saturation, brightness)
        }
        ChannelSlider(stringResource(R.string.colour_saturation), saturation, 1f, tint) {
            onChange(hue, it, brightness)
        }
        ChannelSlider(stringResource(R.string.colour_brightness), brightness, 1f, tint) {
            onChange(hue, saturation, it)
        }
    }
}

@Composable
private fun ChannelSlider(label: String, value: Float, max: Float, tint: Color, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(LABEL_WIDTH.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..max,
            colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint),
            modifier = Modifier.weight(1f),
        )
    }
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
