package io.github.tomlmbl.launcher.signals

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.SystemClock
import io.github.tomlmbl.launcher.context.Ssid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Wi-Fi network read in the foreground: connection identity and SSID, null if redacted. */
data class WifiReading(val network: Long, val ssid: Ssid?)

/** Why switching works or not, for settings; in the order these causes are checked. */
enum class WifiStatus { NO_PERMISSION, APPROXIMATE, LOCATION_OFF, WIFI_OFF, NOT_CONNECTED, CONNECTED }

/**
 * Wi-Fi for automatic switching: without SSID or location, connection tracking while the process lives ([tracker]);
 * with `FLAG_INCLUDE_LOCATION_INFO`, the connected network's SSID, only while a screen asks for it ([acquire]).
 * `TRANSPORT_WIFI` request: a VPN does not hide Wi-Fi, and a network without internet is seen.
 * Logs nothing.
 */
class WifiSignal(private val context: Context) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()

    private val _tracker = MutableStateFlow(ConnectionTracker())
    val tracker: StateFlow<ConnectionTracker> = _tracker.asStateFlow()

    private val _reading = MutableStateFlow<WifiReading?>(null)
    val reading: StateFlow<WifiReading?> = _reading.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    /** Wi-Fi on, tracked to evaluate departure again at every change, Home hidden included. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private var holders = 0
    private var located: ConnectivityManager.NetworkCallback? = null

    /** Connection tracking, for the life of the process; callback without flags, no location permission. */
    fun start() {
        connectivity.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) =
                _tracker.update { it.available(network.networkHandle, SystemClock.elapsedRealtime()) }

            override fun onLost(network: Network) =
                _tracker.update { it.lost(network.networkHandle, SystemClock.elapsedRealtime()) }
        })
        // System broadcast, also delivered to a non-exported receiver; sticky: the current state arrives on registration.
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                _enabled.value = wifiEnabled()
            }
        }, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION), Context.RECEIVER_NOT_EXPORTED)
    }

    /** Reads the SSID while at least one screen asks for it (Home visible, switching settings). */
    fun acquire() {
        if (holders++ > 0) return
        val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _reading.value = WifiReading(network.networkHandle, ssidOf(caps.transportInfo as? WifiInfo))
            }

            override fun onLost(network: Network) {
                _reading.update { it?.takeUnless { r -> r.network == network.networkHandle } }
            }
        }
        connectivity.registerNetworkCallback(request, callback)
        located = callback
    }

    fun release() {
        if (holders == 0 || --holders > 0) return
        located?.let { connectivity.unregisterNetworkCallback(it) }
        located = null
        _reading.value = null
    }

    fun status(): WifiStatus {
        val fine = hasPreciseLocation()
        return when {
            !fine && !granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> WifiStatus.NO_PERMISSION
            !fine -> WifiStatus.APPROXIMATE
            !locationEnabled() -> WifiStatus.LOCATION_OFF
            !wifiEnabled() -> WifiStatus.WIFI_OFF
            tracker.value.network == null -> WifiStatus.NOT_CONNECTED
            else -> WifiStatus.CONNECTED
        }
    }

    /** Wi-Fi on: a loss with Wi-Fi off (by hand, airplane mode) is not a departure. */
    fun wifiEnabled() = context.getSystemService(WifiManager::class.java).isWifiEnabled

    /** SSID readable: precise location granted and turned on, otherwise a return would go unseen. */
    fun ssidReadable() = hasPreciseLocation() && locationEnabled()

    private fun hasPreciseLocation() = granted(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun locationEnabled() = context.getSystemService(LocationManager::class.java).isLocationEnabled

    private fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val FLAG_INCLUDE_LOCATION_INFO = ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO

        /**
         * Without the permission or with location off, the system returns "<unknown ssid>" without error. Otherwise
         * `getSSID` gives the UTF-8 name in quotes, or its bytes in hexadecimal if it is not UTF-8.
         */
        fun ssidOf(info: WifiInfo?): Ssid? = parseSsid(info?.ssid)
    }
}

/** Parsing of `getSSID`, kept apart so it can be tested off-device. */
internal fun parseSsid(raw: String?): Ssid? {
    if (raw == null) return null
    if (raw == WifiManager.UNKNOWN_SSID || raw.isEmpty()) return null
    if (raw.length >= 2 && raw.first() == '"' && raw.last() == '"') {
        return raw.substring(1, raw.length - 1).takeIf { it.isNotEmpty() }?.let { Ssid.of(it.encodeToByteArray()) }
    }
    return Ssid(raw.lowercase()).takeIf { raw.length % 2 == 0 && raw.all { it.isLetterOrDigit() && it.lowercaseChar() in "0123456789abcdef" } }
}
