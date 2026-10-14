package cz.kisiala.pocasi.ui.radar

import android.graphics.Bitmap
import android.view.Gravity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cz.kisiala.pocasi.model.Place
import cz.kisiala.pocasi.model.RadarGrid
import kotlinx.coroutines.flow.Flow
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.ImageSource
import org.maplibre.geojson.Point

/** Okraje mapy v px, pod kterými jsou plovoucí prvky (lišta nahoře, panel dole). */
data class MapInsets(val top: Int, val bottom: Int, val side: Int)

/**
 * Mapa OpenFreeMap (MapLibre) s překryvem radaru a tečkou polohy.
 * Styl se řídí světlým/tmavým schématem aplikace.
 */
@Composable
fun RadarMap(
    radarImage: Bitmap?,
    radarOpacity: Float,
    myLocation: Place?,
    insets: MapInsets,
    centerRequests: Flow<Place>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val dotColors = DotColors(dot = colors.primary, ring = colors.surface, halo = colors.onSurface.copy(alpha = 0.14f))

    val mapView = remember {
        val options = MapLibreMapOptions.createFromAttributes(context)
            .minZoomPreference(MIN_ZOOM)
            .maxZoomPreference(MAX_ZOOM)
            .rotateGesturesEnabled(false)
            .tiltGesturesEnabled(false)
            .compassEnabled(false)
            .logoEnabled(false)
            .attributionEnabled(true)
        MapView(context, options).apply { onCreate(null) }
    }
    var controller by remember { mutableStateOf<RadarMapController?>(null) }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        // Přidání observeru přehraje ON_START/ON_RESUME podle aktuálního stavu.
        lifecycle.addObserver(observer)
        mapView.getMapAsync { controller = RadarMapController(it) }
        onDispose {
            lifecycle.removeObserver(observer)
            val state = lifecycle.currentState
            if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)

    val c = controller ?: return
    LaunchedEffect(c, dark) { c.setStyle(if (dark) STYLE_DARK else STYLE_LIGHT) }
    LaunchedEffect(c, insets) { c.setInsets(insets) }
    LaunchedEffect(c, radarImage, radarOpacity) { c.setRadar(radarImage, radarOpacity) }
    LaunchedEffect(c, myLocation, dotColors) { c.setLocation(myLocation, dotColors) }
    LaunchedEffect(c, centerRequests) { centerRequests.collect { c.centerOn(it) } }
}

data class DotColors(val dot: Color, val ring: Color, val halo: Color)

/** Vrstvy na mapě; po změně stylu (světlý ↔ tmavý) je přidá znovu s posledními hodnotami. */
private class RadarMapController(private val map: MapLibreMap) {
    private var style: Style? = null
    private var styleUrl: String? = null
    private var radarImage: Bitmap? = null
    private var radarOpacity = 1f
    private var location: Place? = null
    private var dotColors: DotColors? = null
    private var insets = MapInsets(0, 0, 0)
    private var cameraPlaced = false

    init {
        map.setLatLngBoundsForCameraTarget(PAN_BOUNDS)
        map.uiSettings.attributionGravity = Gravity.BOTTOM or Gravity.START
    }

    fun setStyle(url: String) {
        if (url == styleUrl) return
        styleUrl = url
        style = null
        map.setStyle(Style.Builder().fromUri(url)) { loaded ->
            style = loaded
            useCzechLabels(loaded)
            addRadarLayer(loaded)
            addLocationLayers(loaded)
            applyRadar()
            applyLocation()
            if (!cameraPlaced) placeInitialCamera()
        }
    }

    fun setInsets(value: MapInsets) {
        insets = value
        map.uiSettings.setAttributionMargins(value.side, 0, 0, value.bottom + value.side / 2)
        if (style != null && !cameraPlaced) placeInitialCamera()
    }

    fun setRadar(image: Bitmap?, opacity: Float) {
        radarImage = image
        radarOpacity = opacity
        applyRadar()
    }

    fun setLocation(place: Place?, colors: DotColors) {
        location = place
        dotColors = colors
        applyLocation()
    }

    fun centerOn(place: Place) {
        val zoom = map.cameraPosition.zoom.coerceAtLeast(LOCATE_ZOOM)
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(place.latitude, place.longitude), zoom), 600)
    }

    /** Celá ČR ve viditelné části mapy (mezi horní lištou a spodním panelem). */
    private fun placeInitialCamera() {
        if (insets.bottom == 0) return
        val camera = map.getCameraForLatLngBounds(
            CZECHIA,
            intArrayOf(insets.side, insets.top, insets.side, insets.bottom),
        ) ?: return
        map.moveCamera(CameraUpdateFactory.newCameraPosition(camera))
        cameraPlaced = true
    }

    private fun addRadarLayer(style: Style) {
        val quad = LatLngQuad(
            LatLng(RadarGrid.north, RadarGrid.west),
            LatLng(RadarGrid.north, RadarGrid.east),
            LatLng(RadarGrid.south, RadarGrid.east),
            LatLng(RadarGrid.south, RadarGrid.west),
        )
        style.addSource(ImageSource(RADAR_SOURCE, quad, radarImage ?: EMPTY))
        val layer = RasterLayer(RADAR_LAYER, RADAR_SOURCE).withProperties(
            PropertyFactory.rasterFadeDuration(0f),
            PropertyFactory.rasterResampling(Property.RASTER_RESAMPLING_LINEAR),
        )
        // Pod popisky, aby názvy obcí zůstaly čitelné.
        val firstSymbol = style.layers.firstOrNull { it is SymbolLayer }?.id
        if (firstSymbol != null) style.addLayerBelow(layer, firstSymbol) else style.addLayer(layer)
    }

    private fun addLocationLayers(style: Style) {
        style.addSource(GeoJsonSource(LOCATION_SOURCE))
        style.addLayer(
            CircleLayer(LOCATION_HALO, LOCATION_SOURCE).withProperties(PropertyFactory.circleRadius(22f)),
        )
        style.addLayer(
            CircleLayer(LOCATION_DOT, LOCATION_SOURCE).withProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleStrokeWidth(3f),
            ),
        )
    }

    private fun applyRadar() {
        val style = style ?: return
        val image = radarImage
        style.getSourceAs<ImageSource>(RADAR_SOURCE)?.setImage(image ?: EMPTY)
        style.getLayerAs<RasterLayer>(RADAR_LAYER)?.setProperties(
            PropertyFactory.rasterOpacity(if (image == null) 0f else radarOpacity),
        )
    }

    private fun applyLocation() {
        val style = style ?: return
        val colors = dotColors ?: return
        val place = location
        val source = style.getSourceAs<GeoJsonSource>(LOCATION_SOURCE) ?: return
        if (place == null) {
            source.setGeoJson(org.maplibre.geojson.FeatureCollection.fromFeatures(emptyList()))
        } else {
            source.setGeoJson(Point.fromLngLat(place.longitude, place.latitude))
        }
        style.getLayerAs<CircleLayer>(LOCATION_HALO)?.setProperties(
            PropertyFactory.circleColor(colors.halo.copy(alpha = 1f).toArgb()),
            PropertyFactory.circleOpacity(colors.halo.alpha),
        )
        style.getLayerAs<CircleLayer>(LOCATION_DOT)?.setProperties(
            PropertyFactory.circleColor(colors.dot.toArgb()),
            PropertyFactory.circleStrokeColor(colors.ring.toArgb()),
        )
    }

    /** OpenFreeMap má popisky primárně anglicky („Prague“) – přepneme na české názvy. */
    private fun useCzechLabels(style: Style) {
        val czech = Expression.coalesce(
            Expression.get("name:cs"),
            Expression.get("name:latin"),
            Expression.get("name"),
        )
        style.layers.filterIsInstance<SymbolLayer>().forEach { layer ->
            val field = runCatching { layer.textField.expression?.toString() }.getOrNull() ?: return@forEach
            if ("name" in field) layer.setProperties(PropertyFactory.textField(czech))
        }
    }

    companion object {
        private const val RADAR_SOURCE = "radar"
        private const val RADAR_LAYER = "radar-layer"
        private const val LOCATION_SOURCE = "my-location"
        private const val LOCATION_HALO = "my-location-halo"
        private const val LOCATION_DOT = "my-location-dot"
        private const val LOCATE_ZOOM = 8.0
        private val EMPTY: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        private val CZECHIA = LatLngBounds.from(51.06, 18.86, 48.55, 12.09)

        /** Střed mapy jen nad oblastí radaru (s malou rezervou). */
        private val PAN_BOUNDS = LatLngBounds.from(RadarGrid.north, RadarGrid.east, RadarGrid.south, RadarGrid.west)
    }
}

private const val MIN_ZOOM = 5.5
private const val MAX_ZOOM = 11.0
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/positron"
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
