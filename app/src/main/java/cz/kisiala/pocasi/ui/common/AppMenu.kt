package cz.kisiala.pocasi.ui.common

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.kisiala.pocasi.BuildConfig
import cz.kisiala.pocasi.R
import cz.kisiala.pocasi.data.AppLocales
import cz.kisiala.pocasi.data.SettingsRepository
import cz.kisiala.pocasi.model.AppLanguage
import cz.kisiala.pocasi.model.NotificationSettings
import cz.kisiala.pocasi.model.ThemeMode
import cz.kisiala.pocasi.ui.notifications.nextOccurrenceText
import java.time.ZonedDateTime

/** Akce společné pro všechny obrazovky – menu ⋮ a hledání místa (obrazovky otevírá `AppRoot`). */
class AppMenuActions(
    /** Zdroj vzhledu a upozornění pro rychlé volby v menu. */
    val settings: SettingsRepository,
    val onThemeMode: (ThemeMode) -> Unit,
    val onBlackBackground: (Boolean) -> Unit,
    val onNotifications: () -> Unit,
    val onAbout: () -> Unit,
    val onSearchPlace: () -> Unit,
    val onEditFavorites: () -> Unit,
)

/**
 * Tlačítko s ozubeným kolem a menu přes celou šířku: rychlé volby Vzhled a Jazyk přímo v menu,
 * Upozornění (kolik je zapnutých a kdy přijde další) a O aplikaci otevírají vlastní obrazovku.
 */
@Composable
fun AppOverflowMenu(actions: AppMenuActions) {
    val visible = remember { MutableTransitionState(false) }
    Box {
        IconButton(onClick = { visible.targetState = true }) {
            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.action_settings))
        }
        // Popup drží i během zavírací animace.
        if (visible.currentState || visible.targetState) {
            Popup(
                popupPositionProvider = BelowAnchorFullWidth,
                onDismissRequest = { visible.targetState = false },
                properties = PopupProperties(focusable = true),
            ) {
                AnimatedVisibility(
                    visibleState = visible,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    MenuPanel(actions, close = { visible.targetState = false })
                }
            }
        }
    }
}

/** Menu pod tlačítkem přes celou šířku okna (okraje dělá padding panelu). */
private object BelowAnchorFullWidth : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ) = IntOffset(0, anchorBounds.bottom.coerceAtMost(windowSize.height - popupContentSize.height).coerceAtLeast(0))
}

@Composable
private fun MenuPanel(actions: AppMenuActions, close: () -> Unit) {
    val settings = actions.settings
    val themeMode by settings.themeMode.collectAsStateWithLifecycle(ThemeMode.System)
    val blackBackground by settings.blackBackground.collectAsStateWithLifecycle(false)
    val notifications by settings.notifications.collectAsStateWithLifecycle(NotificationSettings())
    val activity = LocalActivity.current
    val context = LocalContext.current
    val language = remember { AppLocales.current(context) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        // Tenký obrys, aby menu nesplývalo s obsahem (hlavně s černým pozadím).
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            SectionHeader(Icons.Outlined.Palette, stringResource(R.string.menu_appearance))
            Choices(
                options = listOf(ThemeMode.Light, ThemeMode.Dark, ThemeMode.System),
                selected = themeMode,
                label = {
                    when (it) {
                        ThemeMode.Light -> stringResource(R.string.theme_light)
                        ThemeMode.Dark -> stringResource(R.string.theme_dark)
                        ThemeMode.System -> stringResource(R.string.menu_option_system)
                    }
                },
                onSelect = actions.onThemeMode,
            )
            // Černé pozadí má smysl jen v tmavém režimu.
            if (themeMode.isDark(isSystemInDarkTheme())) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = blackBackground, onValueChange = actions.onBlackBackground, role = Role.Switch)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.theme_black_background),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = blackBackground, onCheckedChange = null)
                }
            }

            MenuDivider()
            SectionHeader(Icons.Outlined.Language, stringResource(R.string.menu_language))
            Choices(
                options = listOf(AppLanguage.Czech, AppLanguage.English, AppLanguage.System),
                selected = language,
                // Názvy jazyků vždy v daném jazyce, ať je pozná i ten, kdo nerozumí aktuálnímu.
                label = {
                    when (it) {
                        AppLanguage.Czech -> "Čeština"
                        AppLanguage.English -> "English"
                        AppLanguage.System -> stringResource(R.string.menu_option_system)
                    }
                },
                onSelect = { selected ->
                    if (selected != language) {
                        // Aktivita se po změně jazyka vytvoří znovu.
                        close()
                        activity?.let { AppLocales.set(it, selected) }
                    }
                },
            )

            MenuDivider()
            val active = notifications.times.count { it.enabled && it.days.isNotEmpty() }
            val blocked = active > 0 && !NotificationManagerCompat.from(context).areNotificationsEnabled()
            NavigationRow(
                icon = if (active > 0 && !blocked) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff,
                title = stringResource(R.string.menu_notifications),
                summary = when {
                    blocked -> stringResource(R.string.menu_notifications_blocked)
                    active == 0 -> stringResource(R.string.menu_notifications_off)
                    else -> {
                        val count = pluralStringResource(R.plurals.menu_notifications_on, active, active)
                        val now = ZonedDateTime.now()
                        notifications.nextAfter(now)?.let { "$count · ${nextOccurrenceText(it, now)}" } ?: count
                    }
                },
                summaryIsError = blocked,
                onClick = {
                    close()
                    actions.onNotifications()
                },
            )
            NavigationRow(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.menu_about),
                summary = stringResource(R.string.menu_version, BuildConfig.VERSION_NAME),
                onClick = {
                    close()
                    actions.onAbout()
                },
            )
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun <T> Choices(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
    ) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(label(option), maxLines = 1) },
            )
        }
    }
}

@Composable
private fun NavigationRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    summaryIsError: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (summaryIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
}
