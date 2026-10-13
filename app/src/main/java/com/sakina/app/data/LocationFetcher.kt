package com.sakina.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.function.Consumer
import kotlin.coroutines.resume

sealed interface GpsResult {
    data class Found(val latitude: Double, val longitude: Double, val accuracyMeters: Float?) : GpsResult
    data object Disabled : GpsResult
    data object TimedOut : GpsResult
    data object NoPermission : GpsResult
}

/** A single position fix from the platform location service (no Google Play services, no background use). */
class LocationFetcher(private val context: Context) {
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMs: Long = 25_000): GpsResult {
        if (!hasPermission()) return GpsResult.NoPermission
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter {
            runCatching { lm.isProviderEnabled(it) }.getOrDefault(false)
        }
        if (providers.isEmpty()) return GpsResult.Disabled

        // A recent last-known fix (under two minutes old) is good enough for prayer times.
        val recent = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 120_000 }
            .minByOrNull { it.accuracy }
        if (recent != null) return recent.toResult()

        val fix = withTimeoutOrNull(timeoutMs) { request(providers.first()) }
        return fix?.toResult() ?: GpsResult.TimedOut
    }

    private fun Location.toResult() = GpsResult.Found(latitude, longitude, if (hasAccuracy()) accuracy else null)

    @SuppressLint("MissingPermission")
    private suspend fun request(provider: String): Location? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT >= 30) {
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context), Consumer { loc ->
                if (cont.isActive) cont.resume(loc)
            })
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    lm.removeUpdates(this)
                    if (cont.isActive) cont.resume(location)
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            cont.invokeOnCancellation { lm.removeUpdates(listener) }
            @Suppress("DEPRECATION")
            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        }
    }
}
