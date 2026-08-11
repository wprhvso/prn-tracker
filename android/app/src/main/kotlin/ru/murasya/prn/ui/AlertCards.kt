package ru.murasya.prn.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.murasya.prn.R
import ru.murasya.prn.data.Med
import ru.murasya.prn.domain.Alert
import ru.murasya.prn.domain.AlertKind
import ru.murasya.prn.domain.MINUTE_MS
import ru.murasya.prn.domain.formatNumber
import ru.murasya.prn.text.shortDuration

/** Everything the notifications say, said again on the home screen where it cannot be missed. */
@Composable
fun AlertCard(alert: Alert, now: Long, onTake: (Med) -> Unit) {
    Banner(
        icon = iconOf(alert.kind),
        container = containerOf(alert.kind),
        content = containerContentOf(alert.kind),
        title = alert.state.med.name,
        body = bodyOf(alert, now),
        action = if (alert.kind == AlertKind.DUE) stringResource(R.string.action_take) else null,
        actionIcon = R.drawable.ic_check,
        onAction = { onTake(alert.state.med) },
    )
}

@Composable
fun Banner(
    @DrawableRes icon: Int,
    container: Color,
    content: Color,
    title: String,
    body: String,
    action: String?,
    @DrawableRes actionIcon: Int,
    onAction: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
            BannerText(title, body, Modifier.weight(1f))
            if (action != null) BannerAction(action, actionIcon, onAction)
        }
    }
}

@Composable
private fun BannerText(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BannerAction(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
) {
    FilledTonalButton(onClick = onClick, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Text(text = label, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
    }
}

@Composable
private fun bodyOf(alert: Alert, now: Long): String {
    val med = alert.state.med
    return when (alert.kind) {
        AlertKind.DUE -> dueBody(alert, now)
        AlertKind.LOW_STOCK -> stockLeft(med.stockMg ?: 0.0)
        AlertKind.OUT_OF_STOCK -> stringResource(R.string.alert_empty)
    }
}

@Composable
private fun stockLeft(mg: Double): String = stringResource(R.string.stock_left, formatNumber(mg))

@Composable
private fun dueBody(alert: Alert, now: Long): String {
    val context = LocalContext.current
    val late = alert.state.dueAt?.let { now - it } ?: 0L
    return if (late < MINUTE_MS) {
        stringResource(R.string.alert_due)
    } else {
        stringResource(R.string.alert_due_late, shortDuration(context, late))
    }
}

@DrawableRes
private fun iconOf(kind: AlertKind): Int =
    when (kind) {
        AlertKind.DUE -> R.drawable.ic_due
        AlertKind.LOW_STOCK -> R.drawable.ic_info
        AlertKind.OUT_OF_STOCK -> R.drawable.ic_warning
    }

@Composable
private fun containerOf(kind: AlertKind) =
    when (kind) {
        AlertKind.DUE -> MaterialTheme.colorScheme.primaryContainer
        AlertKind.LOW_STOCK -> MaterialTheme.colorScheme.tertiaryContainer
        AlertKind.OUT_OF_STOCK -> MaterialTheme.colorScheme.errorContainer
    }

@Composable
private fun containerContentOf(kind: AlertKind) =
    when (kind) {
        AlertKind.DUE -> MaterialTheme.colorScheme.onPrimaryContainer
        AlertKind.LOW_STOCK -> MaterialTheme.colorScheme.onTertiaryContainer
        AlertKind.OUT_OF_STOCK -> MaterialTheme.colorScheme.onErrorContainer
    }
