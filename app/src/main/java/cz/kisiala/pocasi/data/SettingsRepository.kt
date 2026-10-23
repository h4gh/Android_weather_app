package cz.kisiala.pocasi.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cz.kisiala.pocasi.model.NotificationSettings
import cz.kisiala.pocasi.model.NotificationTime
import cz.kisiala.pocasi.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Uživatelská nastavení uložená v DataStore. */
class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore
    private val json = Json { ignoreUnknownKeys = true }

    /** Zvolený vzhled; výchozí [ThemeMode.System]. */
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[THEME_MODE]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.System
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    /** Časy a místo upozornění s počasím; výchozí = žádné časy, Moje poloha. */
    val notifications: Flow<NotificationSettings> = dataStore.data.map { decodeNotifications(it[NOTIFICATIONS]) }

    suspend fun updateNotifications(transform: (NotificationSettings) -> NotificationSettings) {
        dataStore.edit { prefs ->
            val current = decodeNotifications(prefs[NOTIFICATIONS])
            prefs[NOTIFICATIONS] = json.encodeToString(StoredNotifications.serializer(), StoredNotifications(transform(current)))
        }
    }

    /** Poškozená data nesmí shodit aplikaci – bereme je jako výchozí. */
    private fun decodeNotifications(raw: String?): NotificationSettings =
        raw?.let { runCatching { json.decodeFromString(StoredNotifications.serializer(), it) }.getOrNull() }
            ?.toSettings()
            ?: NotificationSettings()

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOTIFICATIONS = stringPreferencesKey("notifications")
    }
}

@Serializable
private data class StoredNotifications(
    val times: List<StoredTime> = emptyList(),
    /** Zakódované místo ([encodePlace]); `null` = Moje poloha. */
    val place: String? = null,
) {
    constructor(settings: NotificationSettings) : this(settings.times.map(::StoredTime), settings.place?.let(::encodePlace))

    fun toSettings() = NotificationSettings(times.map { it.toTime() }, place?.let(::decodePlace))
}

@Serializable
private data class StoredTime(
    val id: Long,
    val hour: Int,
    val minute: Int,
    /** Dny v týdnu 1 = pondělí … 7 = neděle. */
    val days: List<Int>,
    val enabled: Boolean = true,
) {
    constructor(time: NotificationTime) : this(time.id, time.hour, time.minute, time.days.map { it.value }.sorted(), time.enabled)

    fun toTime() = NotificationTime(
        id = id,
        hour = hour.coerceIn(0, 23),
        minute = minute.coerceIn(0, 59),
        days = days.filter { it in 1..7 }.map(DayOfWeek::of).toSet(),
        enabled = enabled,
    )
}
