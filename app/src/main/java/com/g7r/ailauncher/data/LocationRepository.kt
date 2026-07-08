package com.g7r.ailauncher.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

data class GeoPoint(val lat: Double, val lon: Double)

object LocationRepository {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun lastKnown(lm: LocationManager): Location? {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var best: Location? = null
        for (p in providers) {
            val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
            if (best == null || loc.time > best.time) best = loc
        }
        return best
    }

    /** Best-effort current location: last known first, else a one-shot fix. */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): GeoPoint? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        lastKnown(lm)?.let { return GeoPoint(it.latitude, it.longitude) }

        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return null
        }
        return suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            runCatching {
                lm.getCurrentLocation(provider, signal, context.mainExecutor) { loc ->
                    cont.resume(loc?.let { GeoPoint(it.latitude, it.longitude) })
                }
            }.onFailure { cont.resume(null) }
            cont.invokeOnCancellation { signal.cancel() }
        }
    }

    @Suppress("DEPRECATION")
    fun placeName(context: Context, point: GeoPoint): String? = runCatching {
        val results = Geocoder(context, Locale.JAPAN).getFromLocation(point.lat, point.lon, 1)
        val a = results?.firstOrNull() ?: return null
        a.subLocality ?: a.locality ?: a.subAdminArea ?: a.adminArea
    }.getOrNull()
}
