package ru.murasya.prn.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ru.murasya.prn.R
import ru.murasya.prn.notify.canScheduleExact
import ru.murasya.prn.notify.requestExactAlarms

/** What the reminders need from the system, and how to go ask for it. */
class PermissionState(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val askNotifications: () -> Unit,
    val askExactAlarms: () -> Unit,
)

/**
 * Re-checked on every resume, because both of these can be flipped in Settings behind the app's
 * back and there is no callback for that.
 */
@Composable
fun rememberPermissionState(): PermissionState {
    val context = LocalContext.current
    var notifications by remember { mutableStateOf(notificationsEnabled(context)) }
    var exactAlarms by remember { mutableStateOf(canScheduleExact(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            notifications = notificationsEnabled(context)
        }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notifications = notificationsEnabled(context)
        exactAlarms = canScheduleExact(context)
    }

    return PermissionState(
        notifications = notifications,
        exactAlarms = exactAlarms,
        askNotifications = {
            val canPrompt = !asked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            if (canPrompt) {
                asked = true
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openNotificationSettings(context)
            }
        },
        askExactAlarms = { requestExactAlarms(context) },
    )
}

@Composable
fun NotificationBanner(state: PermissionState) {
    Banner(
        icon = Icons.Rounded.Notifications,
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer,
        title = stringResource(R.string.perm_notifications_title),
        body = stringResource(R.string.perm_notifications_text),
        action = stringResource(R.string.perm_notifications_action),
        actionIcon = Icons.Rounded.Notifications,
        onAction = state.askNotifications,
    )
}

@Composable
fun ExactAlarmBanner(state: PermissionState) {
    Banner(
        icon = Icons.Rounded.Warning,
        container = MaterialTheme.colorScheme.surfaceContainerHigh,
        content = MaterialTheme.colorScheme.onSurface,
        title = stringResource(R.string.perm_alarms_title),
        body = stringResource(R.string.perm_alarms_text),
        action = stringResource(R.string.perm_alarms_action),
        actionIcon = Icons.Rounded.Warning,
        onAction = state.askExactAlarms,
    )
}

private fun notificationsEnabled(context: Context): Boolean =
    context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() ?: false

private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
