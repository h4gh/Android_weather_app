package cz.kisiala.pocasi.model

import java.time.DayOfWeek
import java.time.ZonedDateTime

/** Jeden čas upozornění s počasím: [hour]:[minute] ve vybrané dny v týdnu. */
data class NotificationTime(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val days: Set<DayOfWeek>,
    val enabled: Boolean = true,
) {
    /** Nejbližší výskyt po [now] (v časovém pásmu [now]); `null`, když je vypnutý nebo bez dní. */
    fun nextAfter(now: ZonedDateTime): ZonedDateTime? {
        if (!enabled || days.isEmpty()) return null
        val today = now.toLocalDate()
        return (0L..7L).asSequence()
            .map { today.plusDays(it) }
            .filter { it.dayOfWeek in days }
            .map { it.atTime(hour, minute).atZone(now.zone) }
            .firstOrNull { it.isAfter(now) }
    }
}

/** Nastavení upozornění: časy a místo; [place] `null` = Moje poloha (poslední zjištěná v aplikaci). */
data class NotificationSettings(
    val times: List<NotificationTime> = emptyList(),
    val place: Place? = null,
) {
    /** Nejbližší výskyt ze všech zapnutých časů. */
    fun nextAfter(now: ZonedDateTime): ZonedDateTime? = times.mapNotNull { it.nextAfter(now) }.minOrNull()
}
