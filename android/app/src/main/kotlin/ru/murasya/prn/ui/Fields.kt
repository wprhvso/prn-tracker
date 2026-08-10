package ru.murasya.prn.ui

import android.graphics.Color as AndroidColor
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
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
import ru.murasya.prn.R
import ru.murasya.prn.domain.MINUTES_PER_HOUR
import ru.murasya.prn.domain.formatMinuteOfDay

private const val SWATCH_SIZE = 34
private const val SWATCH_SATURATION = 0.62f
private const val SWATCH_VALUE = 0.85f
private const val MAX_HUE = 360f

@Composable
fun PlainField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supporting?.let { hint -> { Text(hint) } },
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
    supporting: String? = null,
    decimal: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { char -> char.isDigit() || char == '.' || char == ',' }) },
        label = { Text(label) },
        supportingText = supporting?.let { hint -> { Text(hint) } },
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
 * Twelve presets for speed and a hue slider for everything else. Saturation and lightness are held
 * fixed so every colour the user can reach still reads against both themes.
 */
@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ColorRow(selected, onSelect)
        HueSlider(selected, onSelect)
    }
}

@Composable
private fun ColorRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MED_COLORS.forEach { argb -> ColorDot(argb, argb == selected, onSelect) }
    }
}

@Composable
private fun HueSlider(selected: Int, onSelect: (Int) -> Unit) {
    val chosen = Color(selected)
    Slider(
        value = hueOf(selected),
        onValueChange = { hue -> onSelect(Color.hsv(hue, SWATCH_SATURATION, SWATCH_VALUE).toArgb()) },
        valueRange = 0f..MAX_HUE,
        colors = SliderDefaults.colors(thumbColor = chosen, activeTrackColor = chosen),
    )
}

private fun hueOf(argb: Int): Float {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(argb, hsv)
    return hsv[0]
}

@Composable
private fun ColorDot(argb: Int, chosen: Boolean, onSelect: (Int) -> Unit) {
    val color = Color(argb)
    Box(
        modifier =
            Modifier
                .size(SWATCH_SIZE.dp)
                .clip(CircleShape)
                .background(color)
                .border(if (chosen) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
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
    OutlinedButton(onClick = onClick, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(8.dp))
        Text(text = formatMinuteOfDay(minuteOfDay), style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrnTimePickerDialog(initialMinuteOfDay: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val context = LocalContext.current
    val state =
        rememberTimePickerState(
            initialHour = initialMinuteOfDay / MINUTES_PER_HOUR,
            initialMinute = initialMinuteOfDay % MINUTES_PER_HOUR,
            is24Hour = DateFormat.is24HourFormat(context),
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * MINUTES_PER_HOUR + state.minute) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        text = { TimeInput(state = state) },
    )
}
