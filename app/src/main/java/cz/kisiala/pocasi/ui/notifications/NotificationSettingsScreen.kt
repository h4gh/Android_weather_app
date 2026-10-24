package cz.kisiala.pocasi.ui.notifications

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.kisiala.pocasi.AppContainer
import cz.kisiala.pocasi.R
import cz.kisiala.pocasi.model.NotificationSettings
import cz.kisiala.pocasi.model.NotificationTime
import cz.kisiala.pocasi.model.Place
import cz.kisiala.pocasi.notifications.NotificationScheduler
import cz.kisiala.pocasi.notifications.WeatherNotifier
import cz.kisiala.pocasi.ui.today.WeatherCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Celoobrazovkové nastavení upozornění s počasím (dialog přes celou obrazovku jako O aplikaci). */
@Composable
fun NotificationSettingsScreen(container: AppContainer, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        NotificationSettingsContent(container, onClose)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationSettingsContent(container: AppContainer, onClose: () -> Unit) {
    val context = LocalContext.current
    val repository = container.settingsRepository
    val settings by repository.notifications.collectAsStateWithLifecycle(null)
    val favorites by container.placeRepository.favorites.collectAsStateWithLifecycle()
    val lastLocation by container.placeRepository.lastLocation.collectAsStateWithLifecycle(null)
    fun update(transform: (NotificationSettings) -> NotificationSettings) {
        container.appScope.launch { repository.updateNotifications(transform) }
    }

    // Oprávnění se mohou změnit v systémovém nastavení – po návratu do aplikace je načteme znovu.
    var notificationsAllowed by remember { mutableStateOf(notificationsAllowed(context)) }
    var exactAllowed by remember { mutableStateOf(NotificationScheduler.canScheduleExact(context)) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = notificationsAllowed(context)
        exactAllowed = NotificationScheduler.canScheduleExact(context)
        onPauseOrDispose {}
    }
    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = notificationsAllowed(context)
    }
    /** Systémový dotaz jen jednou – po odmítnutí se upozornění povolují v nastavení aplikace. */
    fun requestNotifications(openSettingsIfAsked: Boolean) {
        if (notificationsAllowed(context)) return
        val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (canAsk && !permissionAsked) {
            permissionAsked = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (openSettingsIfAsked) {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }

    // Úprava času: id existujícího, NEW_TIME pro nový, null = dialog zavřený.
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_notifications)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_close))
                    }
                },
            )
        },
    ) { padding ->
        val current = settings ?: return@Scaffold
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.notif_settings_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
            val anyEnabled = current.times.any { it.enabled && it.days.isNotEmpty() }
            if (!notificationsAllowed && current.times.isNotEmpty()) {
                WarningCard(Icons.Outlined.NotificationsOff, stringResource(R.string.notif_settings_blocked)) {
                    requestNotifications(openSettingsIfAsked = true)
                }
            } else if (!exactAllowed && anyEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                WarningCard(Icons.Outlined.Schedule, stringResource(R.string.notif_settings_inexact)) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
                    )
                }
            }

            SectionTitle(stringResource(R.string.notif_settings_place))
            PlaceChooser(
                selected = current.place,
                lastLocationName = lastLocation?.name,
                favorites = favorites,
                onSelect = { place -> update { it.copy(place = place) } },
            )

            SectionTitle(stringResource(R.string.notif_settings_times))
            if (current.times.isEmpty()) {
                Text(
                    stringResource(R.string.notif_settings_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            current.times.sortedBy { it.hour * 60 + it.minute }.forEach { time ->
                TimeCard(
                    time = time,
                    onEditTime = { editing = time.id },
                    onToggle = { enabled ->
                        update { s -> s.withTime(time.id) { it.copy(enabled = enabled) } }
                        if (enabled) requestNotifications(openSettingsIfAsked = false)
                    },
                    onToggleDay = { day ->
                        update { s -> s.withTime(time.id) { it.copy(days = if (day in it.days) it.days - day else it.days + day) } }
                    },
                    onDelete = { update { s -> s.copy(times = s.times.filterNot { it.id == time.id }) } },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { editing = NEW_TIME }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.notif_settings_add))
                }
                TextButton(
                    onClick = {
                        if (notificationsAllowed) {
                            container.appScope.launch(Dispatchers.Default) { WeatherNotifier.notify(context.applicationContext) }
                        } else {
                            requestNotifications(openSettingsIfAsked = true)
                        }
                    },
                ) { Text(stringResource(R.string.notif_settings_test)) }
            }
        }

        editing?.let { id ->
            val time = current.times.firstOrNull { it.id == id }
            TimePickerDialog(
                initialHour = time?.hour ?: DEFAULT_HOUR,
                initialMinute = time?.minute ?: 0,
                onConfirm = { hour, minute ->
                    editing = null
                    if (time != null) {
                        update { s -> s.withTime(id) { it.copy(hour = hour, minute = minute, enabled = true) } }
                    } else {
                        val new = NotificationTime(System.currentTimeMillis(), hour, minute, DayOfWeek.entries.toSet())
                        update { s -> s.copy(times = s.times + new) }
                    }
                    requestNotifications(openSettingsIfAsked = false)
                },
                onDismiss = { editing = null },
            )
        }
    }
}

private const val NEW_TIME = -1L
private const val DEFAULT_HOUR = 7

private fun NotificationSettings.withTime(id: Long, transform: (NotificationTime) -> NotificationTime) =
    copy(times = times.map { if (it.id == id) transform(it) else it })

private fun notificationsAllowed(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun formatTime(hour: Int, minute: Int) = "%d:%02d".format(hour, minute)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun WarningCard(icon: ImageVector, text: String, onAllow: () -> Unit) {
    WeatherCard {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            TextButton(onClick = onAllow) { Text(stringResource(R.string.notif_settings_allow)) }
        }
    }
}

@Composable
private fun PlaceChooser(selected: Place?, lastLocationName: String?, favorites: List<Place>, onSelect: (Place?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val myLocation = lastLocationName?.let { stringResource(R.string.notif_settings_location_known, it) }
        ?: stringResource(R.string.place_my_location)
    // Zvolené místo mezitím odebrané z oblíbených v nabídce necháme, ať je vidět, co platí.
    val options = (favorites + listOfNotNull(selected)).distinctBy { it.id }
    WeatherCard {
        Box {
            ListItem(
                headlineContent = { Text(selected?.name ?: myLocation) },
                supportingContent = if (selected == null) {
                    { Text(stringResource(R.string.notif_settings_location_hint)) }
                } else {
                    null
                },
                leadingContent = {
                    Icon(if (selected == null) Icons.Outlined.MyLocation else Icons.Outlined.Place, contentDescription = null)
                },
                trailingContent = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { open = true },
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text(myLocation) },
                    leadingIcon = { Icon(Icons.Outlined.MyLocation, contentDescription = null) },
                    onClick = {
                        open = false
                        onSelect(null)
                    },
                )
                options.forEach { place ->
                    DropdownMenuItem(
                        text = { Text(place.name) },
                        leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                        onClick = {
                            open = false
                            onSelect(place)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeCard(
    time: NotificationTime,
    onEditTime: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onDelete: () -> Unit,
) {
    val active = time.enabled && time.days.isNotEmpty()
    WeatherCard {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatTime(time.hour, time.minute),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onEditTime)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.notif_settings_delete))
                }
                Switch(checked = time.enabled, onCheckedChange = onToggle)
            }
            Text(
                nextText(time),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(4.dp))
            WeekdayRow(time.days, onToggleDay)
        }
    }
}

/** „Příště zítra v 7:00“ / „Vyberte alespoň jeden den“; u vypnutého času nic. */
@Composable
private fun nextText(time: NotificationTime): String {
    if (time.days.isEmpty()) return stringResource(R.string.notif_settings_no_days)
    if (!time.enabled) return ""
    val now = ZonedDateTime.now()
    val next = time.nextAfter(now) ?: return ""
    return nextOccurrenceText(next, now)
}

/** „Příště zítra v 7:00“ – sdílí ho i menu ⋮. */
@Composable
fun nextOccurrenceText(next: ZonedDateTime, now: ZonedDateTime): String {
    val clock = formatTime(next.hour, next.minute)
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), next.toLocalDate())
    val whenText = when (days) {
        0L -> stringResource(R.string.notif_settings_today, clock)
        1L -> stringResource(R.string.notif_settings_tomorrow, clock)
        else -> stringResource(R.string.notif_settings_on_day, stringArrayResource(R.array.weekday_on)[next.dayOfWeek.value - 1], clock)
    }
    return stringResource(R.string.notif_settings_next, whenText)
}

/** Dny v týdnu jako kulatá přepínací tlačítka (jako v Hodinách). */
@Composable
private fun WeekdayRow(days: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    val short = stringArrayResource(R.array.weekday_short)
    val full = stringArrayResource(R.array.weekday_full)
    Row(Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach { day ->
            val selected = day in days
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .minimumInteractiveComponentSize()
                        .size(40.dp)
                        .clip(CircleShape)
                        .then(
                            if (selected) {
                                Modifier.background(MaterialTheme.colorScheme.primary)
                            } else {
                                Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            },
                        )
                        .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle(day) })
                        .semantics { contentDescription = full[day.value - 1] },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        short[day.value - 1],
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initialHour: Int, initialMinute: Int, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notif_settings_pick_time)) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
