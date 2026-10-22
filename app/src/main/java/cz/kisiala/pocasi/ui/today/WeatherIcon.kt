package cz.kisiala.pocasi.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import cz.kisiala.pocasi.model.WeatherIconType
import cz.kisiala.pocasi.ui.theme.WeatherTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Barvy ikony počasí – v aplikaci z motivu, ve widgetu z jeho barevného schématu. */
data class WeatherIconColors(
    val sun: Color,
    val moon: Color,
    val cloud: Color,
    /** Výplň mraku = pozadí pod ikonou (mrak zakryje slunce). */
    val cloudFill: Color,
    val drop: Color,
)

/** Obrysová ikona počasí kreslená přes Canvas (slunce/měsíc, mrak, déšť, sníh). */
@Composable
fun WeatherIcon(
    type: WeatherIconType,
    isDay: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val colors = WeatherIconColors(
        sun = WeatherTheme.colors.temperature,
        moon = MaterialTheme.colorScheme.onSurfaceVariant,
        cloud = MaterialTheme.colorScheme.onSurfaceVariant,
        cloudFill = MaterialTheme.colorScheme.background,
        drop = MaterialTheme.colorScheme.primary,
    )
    Canvas(modifier.semantics { if (contentDescription != null) this.contentDescription = contentDescription }) {
        drawWeatherIcon(type, isDay, colors)
    }
}

/** Kreslení ikony do čtverce velikosti [DrawScope.size] – sdílí ji aplikace i widget (bitmapa). */
fun DrawScope.drawWeatherIcon(type: WeatherIconType, isDay: Boolean, colors: WeatherIconColors) {
    val s = size.minDimension
    val stroke = Stroke(width = s * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val celestial: DrawScope.(Offset, Float) -> Unit = { center, r ->
        if (isDay) drawSun(center, r, colors.sun, stroke) else drawMoon(center, r, colors.moon, stroke)
    }
    when (type) {
        WeatherIconType.Clear -> celestial(Offset(s * 0.5f, s * 0.5f), s * 0.2f)
        WeatherIconType.PartlyCloudy -> {
            celestial(Offset(s * 0.38f, s * 0.36f), s * 0.15f)
            drawCloud(Offset(s * 0.28f, s * 0.36f), s * 0.68f, colors.cloud, colors.cloudFill, stroke)
        }
        WeatherIconType.Cloudy -> drawCloud(Offset(s * 0.12f, s * 0.24f), s * 0.76f, colors.cloud, colors.cloudFill, stroke)
        WeatherIconType.Rain, WeatherIconType.Snow -> {
            drawCloud(Offset(s * 0.12f, s * 0.12f), s * 0.76f, colors.cloud, colors.cloudFill, stroke)
            val xs = listOf(0.34f, 0.5f, 0.66f)
            xs.forEach { x ->
                if (type == WeatherIconType.Rain) {
                    drawLine(
                        colors.drop,
                        Offset(s * (x + 0.03f), s * 0.68f),
                        Offset(s * (x - 0.03f), s * 0.84f),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round,
                    )
                } else {
                    drawCircle(colors.drop, radius = s * 0.035f, center = Offset(s * x, s * 0.76f))
                }
            }
        }
    }
}

private fun DrawScope.drawSun(center: Offset, radius: Float, color: Color, stroke: Stroke) {
    drawCircle(color, radius, center, style = stroke)
    repeat(8) { i ->
        val a = i * PI / 4
        val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
        drawLine(
            color,
            center + dir * (radius * 1.45f),
            center + dir * (radius * 1.95f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawMoon(center: Offset, radius: Float, color: Color, stroke: Stroke) {
    val r = radius * 1.3f
    val outer = Path().apply { addOval(Rect(center, r)) }
    val bite = Path().apply { addOval(Rect(center + Offset(r * 0.55f, -r * 0.45f), r * 0.85f)) }
    val crescent = Path().apply { op(outer, bite, PathOperation.Difference) }
    drawPath(crescent, color, style = stroke)
}

/** Mrak jako sjednocení dvou kruhů a „pilulky“; nejdřív výplň (zakryje slunce), pak obrys. */
private fun DrawScope.drawCloud(topLeft: Offset, width: Float, color: Color, fill: Color, stroke: Stroke) {
    val w = width
    val (x, y) = topLeft
    val base = Path().apply {
        addRoundRect(RoundRect(Rect(x, y + 0.30f * w, x + w, y + 0.62f * w), CornerRadius(0.16f * w)))
    }
    val big = Path().apply { addOval(Rect(Offset(x + 0.60f * w, y + 0.30f * w), 0.27f * w)) }
    val small = Path().apply { addOval(Rect(Offset(x + 0.30f * w, y + 0.36f * w), 0.17f * w)) }
    val upper = Path().apply { op(base, big, PathOperation.Union) }
    val cloud = Path().apply { op(upper, small, PathOperation.Union) }
    drawPath(cloud, fill)
    drawPath(cloud, color, style = stroke)
}
