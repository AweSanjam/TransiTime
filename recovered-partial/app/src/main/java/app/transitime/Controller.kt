package app.transitime

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class UiState(
    val station: Station? = null,
    val lastSyncMs: Long = 0L,
    val syncing: Boolean = false,
    val error: String? = null,
    val needsPermission: Boolean = false,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("transitime", Context.MODE_PRIVATE)
    val intervalChoices: List<Int> =
        context.resources.getStringArray(R.array.sync_interval_values).map { it.toInt() }
    private val defaultInterval = context.resources.getInteger(R.integer.sync_interval_default)

    private val _interval = MutableStateFlow(sp.getInt("interval", defaultInterval))
    val interval: StateFlow<Int> = _interval.asStateFlow()

    // Ordered pins: first pinned stays on top.
    private val _pins = MutableStateFlow(
        sp.getString("pins", "").orEmpty().split('\n').filter { it.isNotBlank() }
    )
    val pins: StateFlow<List<String>> = _pins.asStateFlow()

    fun setInterval(sec: Int) {
        sp.edit().putInt("interval", sec).apply()
        _interval.value = sec
    }

    fun togglePin(routeId: String) {
        _pins.update { if (routeId in it) it - routeId else it + routeId }
        sp.edit().putString("pins", _pins.value.joinToString("\n")).apply()
    }
}

class Controller(private val context: Context) {
    val prefs = Prefs(context)
    private val api = TransitApi(BuildConfig.TRANSIT_API_KEY)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var location: Location? = null
    private var locationAtMs = 0L

    fun hasPermission() =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Called only while the activity is resumed, so location is never read in the background. */
    suspend fun sync() = mutex.withLock {
        if (!hasPermission()) {
            _state.update { it.copy(needsPermission = true) }
            return
        }
        if (BuildConfig.TRANSIT_API_KEY.isBlank()) {
            _state.update { it.copy(needsPermission = false, error = "No Transit API key in build") }
            return
        }
        _state.update { it.copy(needsPermission = false, syncing = true) }
        try {
            val now = System.currentTimeMillis()
            if (location == null || now - locationAtMs > LOCATION_MAX_AGE_MS) {
                currentLocation()?.let { location = it; locationAtMs = now }
            }
            val loc = location
            if (loc == null) {
                _state.update { it.copy(error = "Waiting for location…") }
                return
            }
            val station = withContext(Dispatchers.IO) { api.nearestStation(loc.latitude, loc.longitude) }
            _state.update {
                it.copy(
                    station = station,
                    lastSyncMs = System.currentTimeMillis(),
                    error = if (station == null) "No bus stops nearby" else null,
                )
            }
        } catch (e: RateLimited) {
            _state.update { it.copy(error = "Rate limited, retrying") }
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: "Sync failed") }
        } finally {
            _state.update { it.copy(syncing = false) }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        for (provider in providers) {
            val fix = withTimeoutOrNull(12_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    lm.getCurrentLocation(provider, signal, context.mainExecutor) { cont.resume(it) }
                    cont.invokeOnCancellation { signal.cancel() }
                }
            }
            if (fix != null) return fix
        }
        return providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    companion object {
        private const val LOCATION_MAX_AGE_MS = 60_000L
    }
}
