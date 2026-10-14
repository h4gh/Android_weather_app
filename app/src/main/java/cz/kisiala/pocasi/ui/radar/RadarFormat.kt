package cz.kisiala.pocasi.ui.radar

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import cz.kisiala.pocasi.R
import cz.kisiala.pocasi.model.PrecipitationIntensity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("H:mm")

/** Čas snímku v místním čase („11:35“). */
fun formatRadarTime(time: Instant): String = TIME.format(time.atZone(ZoneId.systemDefault()))

/** Směr k srážkám příslovcem („západně“) z azimutu ve stupních. */
@Composable
fun directionAdverb(bearing: Double): String {
    val names = stringArrayResource(R.array.direction_adverb)
    return names[((bearing + 22.5) / 45).toInt() % 8]
}

@get:StringRes
val PrecipitationIntensity.label: Int
    get() = when (this) {
        PrecipitationIntensity.Light -> R.string.intensity_light
        PrecipitationIntensity.Moderate -> R.string.intensity_moderate
        PrecipitationIntensity.Heavy -> R.string.intensity_heavy
        PrecipitationIntensity.Torrential -> R.string.intensity_torrential
    }

/**
 * Otočení šipky [androidx.compose.material.icons.automirrored.filled.ArrowForward] (míří na východ)
 * tak, aby ukazovala směrem k srážkám.
 */
fun arrowRotation(bearing: Double): Float = (bearing - 90).toFloat()
