package com.brainrot.detector.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.brainrot.detector.data.AppInfo
import com.brainrot.detector.data.WatchSettings
import com.brainrot.detector.data.formatDuration
import com.brainrot.detector.data.formatMinutes
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onGrantUsageAccess: () -> Unit,
    onGrantOverlay: () -> Unit,
    onGrantNotifications: () -> Unit,
) {
    val settings by viewModel.settingsState.collectAsState()
    val apps by viewModel.apps.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val query by viewModel.query.collectAsState()
    val permissions by viewModel.permissions.collectAsState()

    var limitDialogFor by remember { mutableStateOf<AppInfo?>(null) }

    val visibleApps = remember(apps, query, settings.showSystemApps, settings.watched) {
        viewModel.visibleApps(apps, query, settings.showSystemApps, settings.watched)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Brain Rot") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { HeaderCard(settings) }

            if (!permissions.allGranted) {
                item {
                    PermissionsCard(
                        permissions = permissions,
                        onGrantUsageAccess = onGrantUsageAccess,
                        onGrantOverlay = onGrantOverlay,
                        onGrantNotifications = onGrantNotifications,
                    )
                }
            }

            item {
                MonitoringCard(
                    settings = settings,
                    enabled = permissions.allGranted,
                    onToggle = viewModel::setMonitoring,
                )
            }

            item {
                LimitsCard(
                    settings = settings,
                    onDefaultLimit = viewModel::setDefaultLimitMinutes,
                    onSnooze = viewModel::setSnoozeMinutes,
                )
            }

            item {
                AppListHeader(
                    query = query,
                    onQuery = viewModel::setQuery,
                    showSystem = settings.showSystemApps,
                    onShowSystem = viewModel::setShowSystemApps,
                    watchedCount = settings.watched.size,
                    totalCount = apps.size,
                )
            }

            if (loading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
            }

            items(visibleApps, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    watched = settings.isWatched(app.packageName),
                    limitLabel = formatDuration(settings.limitFor(app.packageName)),
                    hasOverride = app.packageName in settings.perAppLimitMs,
                    onWatchedChange = { viewModel.setWatched(app.packageName, it) },
                    onEditLimit = { limitDialogFor = app },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }

    limitDialogFor?.let { app ->
        LimitDialog(
            app = app,
            defaultLimitMs = settings.defaultLimitMs,
            currentOverrideMs = settings.perAppLimitMs[app.packageName],
            onDismiss = { limitDialogFor = null },
            onPick = { minutes ->
                viewModel.setAppLimitMinutes(app.packageName, minutes)
                limitDialogFor = null
            },
        )
    }
}

@Composable
private fun HeaderCard(settings: WatchSettings) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CryingBrainGif(modifier = Modifier.size(96.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = "Pick your poison",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Stay in a watched app past ${formatDuration(settings.defaultLimitMs)} " +
                        "and this brain shows up on top of it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PermissionsCard(
    permissions: PermissionState,
    onGrantUsageAccess: () -> Unit,
    onGrantOverlay: () -> Unit,
    onGrantNotifications: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Permissions needed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            PermissionRow(
                title = "Usage access",
                description = "Lets the app see which app you are in right now.",
                granted = permissions.usageAccess,
                onGrant = onGrantUsageAccess,
            )
            PermissionRow(
                title = "Draw over other apps",
                description = "Lets the crying brain appear on top of the app you are using.",
                granted = permissions.overlay,
                onGrant = onGrantOverlay,
            )
            PermissionRow(
                title = "Notifications",
                description = "The watcher runs as a foreground service, which needs one.",
                granted = permissions.notifications,
                onGrant = onGrantNotifications,
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (granted) {
            Text("Granted", color = MaterialTheme.colorScheme.primary)
        } else {
            Button(onClick = onGrant) { Text("Grant") }
        }
    }
}

@Composable
private fun MonitoringCard(
    settings: WatchSettings,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (settings.monitoring) "Watching" else "Not watching",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when {
                        !enabled -> "Grant the permissions above first."
                        settings.watched.isEmpty() -> "Select at least one app below."
                        else -> "${settings.watched.size} app(s) selected."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.monitoring,
                onCheckedChange = onToggle,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun LimitsCard(
    settings: WatchSettings,
    onDefaultLimit: (Int) -> Unit,
    onSnooze: (Int) -> Unit,
) {
    val defaultMinutes = TimeUnit.MILLISECONDS.toMinutes(settings.defaultLimitMs).toInt()
    val snoozeMinutes = TimeUnit.MILLISECONDS.toMinutes(settings.snoozeMs).toInt()

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Default time limit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Used by every watched app that has no limit of its own.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ChipRow(
                choices = WatchSettings.LIMIT_CHOICES_MIN,
                selected = defaultMinutes,
                onSelect = onDefaultLimit,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Snooze",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "How long the brain stays quiet after you dismiss it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ChipRow(
                choices = WatchSettings.SNOOZE_CHOICES_MIN,
                selected = snoozeMinutes,
                onSelect = onSnooze,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChipRow(choices: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.forEach { minutes ->
            FilterChip(
                selected = minutes == selected,
                onClick = { onSelect(minutes) },
                label = { Text(formatMinutes(minutes)) },
            )
        }
    }
}

@Composable
private fun AppListHeader(
    query: String,
    onQuery: (String) -> Unit,
    showSystem: Boolean,
    onShowSystem: (Boolean) -> Unit,
    watchedCount: Int,
    totalCount: Int,
) {
    Column {
        Text(
            text = "Apps on this phone ($totalCount found, $watchedCount watched)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Search apps") },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Show system apps",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(checked = showSystem, onCheckedChange = onShowSystem)
        }
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    watched: Boolean,
    limitLabel: String,
    hasOverride: Boolean,
    onWatchedChange: (Boolean) -> Unit,
    onEditLimit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onWatchedChange(!watched) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = app.icon
        if (icon != null) {
            Image(
                bitmap = icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge)
            if (watched) {
                TextButton(onClick = onEditLimit, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(
                        text = if (hasOverride) "$limitLabel limit" else "$limitLabel (default)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = watched, onCheckedChange = onWatchedChange)
    }
}

@Composable
private fun LimitDialog(
    app: AppInfo,
    defaultLimitMs: Long,
    currentOverrideMs: Long?,
    onDismiss: () -> Unit,
    onPick: (Int?) -> Unit,
) {
    val currentMinutes = currentOverrideMs?.let { TimeUnit.MILLISECONDS.toMinutes(it).toInt() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Limit for ${app.label}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DialogOption(
                    label = "Use default (${formatDuration(defaultLimitMs)})",
                    selected = currentMinutes == null,
                    onClick = { onPick(null) },
                )
                WatchSettings.LIMIT_CHOICES_MIN.forEach { minutes ->
                    DialogOption(
                        label = formatMinutes(minutes),
                        selected = currentMinutes == minutes,
                        onClick = { onPick(minutes) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun DialogOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        if (selected) Text("✓", color = MaterialTheme.colorScheme.primary)
    }
}
