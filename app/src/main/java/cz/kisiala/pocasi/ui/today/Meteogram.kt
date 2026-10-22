package cz.kisiala.pocasi.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.East
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import cz.kisiala.pocasi.R
import cz.kisiala.pocasi.model.HourlyWeather
import cz.kisiala.pocasi.ui.theme.WeatherTheme
import kotlin.math.roundToInt

/** Počet sloupců (po 3 h) v ose i v řádku větru: 0, 3, … 24 h. */
private const val COLUMNS = 9

/** Srážky, od kterých je sloupec plnou barvou primary (jinak světlejší). */
private const val HEAVY_PRECIP_MM = 1.0

/** Výška grafu odpovídá alespoň tomuto úhrnu, aby slabé srážky nevypadaly jako liják. */
private const val PRECIP_SCALE_MIN_MM = 4.0

/**
 * Karta „Dalších 24 hodin“: teplota jako křivka, srážky jako sloupce, osa po 3 h
 * a pod tím řádek větru. [hours] má 25 hodnot (teď … +24 h).
 */
@Composable
fun Next24HoursCard(hours: List<HourlyWeather>, modifier: Modifier = Modifier) {
    WeatherCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.next24_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                MeteogramLegend()
            }
            Spacer(Modifier.height(16.dp))
            Meteogram(
                hours = hours,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp),
            )
            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            WindRow(hours)
        }
    }
}

/** Legenda meteogramu (teplota křivkou, srážky sloupci) – sdílí ji i Výhled. */
@Composable
fun MeteogramLegend() {
    val labelStyle = MaterialTheme.typography.labelLarge
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(width = 12.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(WeatherTheme.colors.temperature),
        )
        Text(stringResource(R.string.legend_temperature), style = labelStyle)
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(stringResource(R.string.legend_precipitation), style = labelStyle)
    }
}

/**
 * Teplota jako křivka a srážky jako sloupce, osa po 3 h; body sedí nad [COLUMNS] sloupci (25 hodnot = 24 h).
 * [markStart] zvýrazní první bod tečkou (aktuální stav na obrazovce Dnes). Používá ho i rozbalený den ve Výhledu.
 */
@Composable
fun Meteogram(hours: List<HourlyWeather>, modifier: Modifier = Modifier, markStart: Boolean = true) {
    if (hours.size < 2) return
    val style = MeteogramStyle(
        temperature = WeatherTheme.colors.temperature,
        heavyPrecip = MaterialTheme.colorScheme.primary,
        lightPrecip = MaterialTheme.colorScheme.inversePrimary,
        grid = MaterialTheme.colorScheme.outlineVariant,
        axisText = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        temperatureText = MaterialTheme.typography.labelMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        ),
    )
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier) { drawMeteogram(hours, style, textMeasurer, markStart) }
}

/** Barvy a písmo meteogramu – v aplikaci z motivu, ve widgetu z jeho barevného schématu. */
data class MeteogramStyle(
    val temperature: Color,
    val heavyPrecip: Color,
    val lightPrecip: Color,
    val grid: Color,
    val axisText: TextStyle,
    val temperatureText: TextStyle,
)

/** Kreslení meteogramu do [DrawScope.size] – sdílí ho aplikace i widget (bitmapa). */
fun DrawScope.drawMeteogram(
    hours: List<HourlyWeather>,
    style: MeteogramStyle,
    textMeasurer: TextMeasurer,
    markStart: Boolean = true,
) {
    if (hours.size < 2) return
    val tempColor = style.temperature
    val heavyPrecipColor = style.heavyPrecip
    val lightPrecipColor = style.lightPrecip
    val gridColor = style.grid
    val axisStyle = style.axisText
    val tempLabelStyle = style.temperatureText
    // Okraj = polovina šířky sloupce v řádku větru → body grafu sedí přesně nad větrem.
    val inset = size.width / (COLUMNS * 2)
    val step = (size.width - 2 * inset) / (hours.size - 1)
    fun x(i: Int) = inset + i * step

    val axisLabelHeight = 18.dp.toPx()
    val barsBottom = size.height - axisLabelHeight - 4.dp.toPx()
    val barsMaxHeight = 28.dp.toPx()
    val tempTop = 20.dp.toPx()
    val tempBottom = barsBottom - 22.dp.toPx()

    // Mřížka: 3 vodorovné linky.
    val gridYs = listOf(tempTop, (tempTop + barsBottom) / 2f - 6.dp.toPx(), barsBottom - 12.dp.toPx())
    gridYs.forEach { y ->
        drawLine(gridColor, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 1.dp.toPx())
    }

    // Srážky: úhrn za předchozí hodinu → sloupec mezi body i-1 a i.
    val precipScale = maxOf(hours.maxOf { it.precipitation }, PRECIP_SCALE_MIN_MM)
    val barWidth = step * 0.7f
    for (i in 1 until hours.size) {
        val mm = hours[i].precipitation
        if (mm < 0.05) continue
        val h = maxOf((mm / precipScale).toFloat() * barsMaxHeight, 3.dp.toPx())
        val cx = (x(i - 1) + x(i)) / 2f
        drawRoundRect(
            color = if (mm >= HEAVY_PRECIP_MM) heavyPrecipColor else lightPrecipColor,
            topLeft = Offset(cx - barWidth / 2f, barsBottom - h),
            size = Size(barWidth, h),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
    }

    // Teplota: hladká křivka (monotónní kubická interpolace – nepřestřeluje extrémy).
    val temps = hours.map { it.temperature }
    val tMax = temps.max()
    val tMin = temps.min()
    val range = (tMax - tMin).takeIf { it > 0.5 } ?: 1.0
    fun y(t: Double) = (tempBottom - (t - tMin) / range * (tempBottom - tempTop)).toFloat()
    val points = temps.mapIndexed { i, t -> Offset(x(i), y(t)) }
    val path = monotoneCurve(points)
    drawPath(
        path,
        tempColor,
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    if (markStart) drawCircle(tempColor, radius = 4.dp.toPx(), center = points[0])

    // Popisky teploty: teď, maximum, minimum, konec – bez překryvů.
    val labelIndices = listOf(0, temps.indexOf(tMax), temps.indexOf(tMin), temps.lastIndex).distinct()
    val placed = mutableListOf<Pair<Float, Float>>()
    for (i in labelIndices) {
        val layout: TextLayoutResult = textMeasurer.measure(formatTemperature(temps[i]), tempLabelStyle)
        val w = layout.size.width.toFloat()
        val left = (points[i].x - w / 2f).coerceIn(0f, size.width - w)
        val gap = 4.dp.toPx()
        if (placed.any { (l, r) -> left < r + gap && left + w > l - gap }) continue
        placed += left to left + w
        val top = (points[i].y - layout.size.height - 4.dp.toPx()).coerceAtLeast(0f)
        drawText(layout, topLeft = Offset(left, top))
    }

    // Osa hodin po 3 h.
    for (i in hours.indices step 3) {
        val label = textMeasurer.measure("%02d".format(hours[i].time.hour), axisStyle)
        drawText(
            label,
            topLeft = Offset(x(i) - label.size.width / 2f, size.height - label.size.height),
        )
    }
}

@Composable
private fun WindRow(hours: List<HourlyWeather>) {
    val strongColor = WeatherTheme.colors.temperature
    val peak = hours.filter { it.windGusts != null }.maxByOrNull { it.windGusts!! }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.wind_kmh),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (peak != null) {
                Text(
                    stringResource(
                        R.string.gusts_peak,
                        peak.windGusts!!.roundToInt(),
                        "%02d".format(peak.time.hour),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = strongColor,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until COLUMNS) {
                val hour = hours.getOrNull(i * 3)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (hour != null) {
                        val speed = hour.windSpeed.roundToInt()
                        val color = if (speed >= STRONG_WIND_KMH) strongColor else MaterialTheme.colorScheme.onSurface
                        Icon(
                            Icons.Outlined.East,
                            contentDescription = windDirectionName(hour.windDirection),
                            tint = color,
                            // Šipka ukazuje, KAM vítr vane (směr z dat je odkud): +180° − 90° (ikona míří na východ).
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(((hour.windDirection + 90) % 360).toFloat()),
                        )
                        Text(
                            speed.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (speed >= STRONG_WIND_KMH) FontWeight.Bold else FontWeight.Normal,
                            color = color,
                        )
                    }
                }
            }
        }
    }
}


/** Křivka přes body metodou Fritsch–Carlson: hladká, ale bez falešných maxim/minim mezi body. */
private fun monotoneCurve(points: List<Offset>): Path {
    val n = points.size
    val slopes = FloatArray(n - 1) { (points[it + 1].y - points[it].y) / (points[it + 1].x - points[it].x) }
    val tangents = FloatArray(n) { i ->
        when (i) {
            0 -> slopes[0]
            n - 1 -> slopes[n - 2]
            else -> if (slopes[i - 1] * slopes[i] <= 0f) 0f else (slopes[i - 1] + slopes[i]) / 2f
        }
    }
    for (i in 0 until n - 1) {
        if (slopes[i] == 0f) {
            tangents[i] = 0f
            tangents[i + 1] = 0f
            continue
        }
        val a = tangents[i] / slopes[i]
        val b = tangents[i + 1] / slopes[i]
        val s = a * a + b * b
        if (s > 9f) {
            val t = 3f / kotlin.math.sqrt(s)
            tangents[i] = t * a * slopes[i]
            tangents[i + 1] = t * b * slopes[i]
        }
    }
    return Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 0 until n - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val dx = (p1.x - p0.x) / 3f
            cubicTo(p0.x + dx, p0.y + tangents[i] * dx, p1.x - dx, p1.y - tangents[i + 1] * dx, p1.x, p1.y)
        }
    }
}
