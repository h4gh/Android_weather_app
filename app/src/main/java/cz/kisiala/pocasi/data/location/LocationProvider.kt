package cz.kisiala.pocasi.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Poloha zařízení přes systémový LocationManager (bez Google Play Services) a reverzní geokódování. */
class LocationProvider(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
            ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
        }

    /** Aktuální poloha, nebo `null`, pokud chybí oprávnění či je poloha vypnutá. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Location? {
        if (!hasPermission() || !LocationManagerCompat.isLocationEnabled(locationManager)) return null
        val lastKnown = lastKnownLocation()
        // Nedávná známá poloha stačí – počasí se na pár set metrech nemění a je to okamžité.
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time < RECENT_LOCATION_MS) return lastKnown
        val provider = bestProvider() ?: return lastKnown
        val fresh = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    locationManager, provider, signal, ContextCompat.getMainExecutor(appContext),
                ) { location -> if (cont.isActive) cont.resume(location) }
            }
        }
        return fresh ?: lastKnown
    }

    private fun bestProvider(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            locationManager.hasProvider(LocationManager.FUSED_PROVIDER) &&
            locationManager.isProviderEnabled(LocationManager.FUSED_PROVIDER)
        ) return LocationManager.FUSED_PROVIDER
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { locationManager.isProviderEnabled(it) }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Location? =
        locationManager.getProviders(true)
            .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

    /** Název obce pro souřadnice, nebo `null`, pokud geokódování není k dispozici. */
    suspend fun placeName(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(appContext, Locale.forLanguageTag("cs-CZ"))
        val address = withTimeoutOrNull(GEOCODER_TIMEOUT_MS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine<Address?> { cont ->
                    geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (cont.isActive) cont.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(null)
                        }
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    runCatching { geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull() }.getOrNull()
                }
            }
        } ?: return null
        return address.locality ?: address.subLocality ?: address.subAdminArea
    }

    private companion object {
        const val LOCATION_TIMEOUT_MS = 8_000L
        const val RECENT_LOCATION_MS = 10 * 60_000L
        const val GEOCODER_TIMEOUT_MS = 5_000L
    }
}
