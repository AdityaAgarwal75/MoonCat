package com.nuvio.tv.core.handoff

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.net.Inet4Address
import java.security.SecureRandom
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class PhoneHandoffStatus(
    val enabled: Boolean = false,
    val online: Boolean = false,
    val paired: Boolean = false
)

internal class PhoneHandoffReceiver(context: Context) {
    private val preferences = context.getSharedPreferences("phone_handoff", Context.MODE_PRIVATE)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val random = SecureRandom()
    private var key = preferences.getString("key", null)?.takeIf(phoneHandoffKeyFormat::matches)
    private var paired = key != null && preferences.getBoolean("paired", false)
    private var pairUntil = 0L
    private var handler: ((PhoneHandoff) -> Boolean)? = null
    private var server: PhoneHandoffServer? = null
    private val _status = MutableStateFlow(PhoneHandoffStatus(enabled = key != null, paired = paired))
    val status = _status.asStateFlow()
    private val _pairingQr = MutableStateFlow<String?>(null)
    val pairingQr = _pairingQr.asStateFlow()
    private val expirePairing = Runnable { closePairing() }

    fun resume(onPlay: (PhoneHandoff) -> Boolean) {
        handler = onPlay
        refreshServer()
    }

    fun pause() {
        handler = null
        closePairing()
        refreshServer()
    }

    fun beginPairing(): Boolean {
        if (handler == null || lanAddress() == null) return false
        if (key == null) {
            key = ByteArray(32).also(random::nextBytes).toPhoneHandoffHex()
            preferences.edit().putString("key", key).apply()
        }
        refreshServer()
        val host = server?.host ?: return false
        pairUntil = SystemClock.elapsedRealtime() + PAIR_WINDOW_MS
        main.removeCallbacks(expirePairing)
        main.postDelayed(expirePairing, PAIR_WINDOW_MS)
        _pairingQr.value = "mooncat://pair?host=$host&port=$PORT&key=$key"
        return true
    }

    fun closePairing() {
        main.removeCallbacks(expirePairing)
        pairUntil = 0
        _pairingQr.value = null
    }

    fun revoke() {
        closePairing()
        key = null
        paired = false
        preferences.edit().clear().apply()
        refreshServer()
    }

    private fun refreshServer() {
        val address = if (handler != null && key != null) lanAddress() else null
        if (server?.host != address) {
            server?.stop()
            server = null
            if (address != null) {
                val receiverKey = key ?: return
                server = runCatching {
                    PhoneHandoffServer(
                        host = address,
                        port = PORT,
                        key = receiverKey,
                        pair = { onMain(receiverKey) { markPaired() } },
                        play = { command -> onMain(receiverKey) { paired && handler?.invoke(command) == true } }
                    ).apply { start(3_000, true) }
                }.getOrNull()
            }
        }
        _status.value = PhoneHandoffStatus(enabled = key != null, online = server != null, paired = paired)
    }

    private fun markPaired(): Boolean {
        if (SystemClock.elapsedRealtime() >= pairUntil) return false
        paired = true
        preferences.edit().putBoolean("paired", true).apply()
        _status.value = _status.value.copy(paired = true)
        closePairing()
        return true
    }

    private fun onMain(expectedKey: String, action: () -> Boolean): Boolean {
        val task = FutureTask {
            key == expectedKey && handler != null && action()
        }
        main.post(task)
        return try {
            task.get(2, TimeUnit.SECONDS)
        } catch (_: Exception) {
            task.cancel(false)
            false
        }
    }

    private fun lanAddress(): String? {
        val manager = connectivity ?: return null
        val networks = listOfNotNull(manager.activeNetwork) + manager.allNetworks
        return networks.distinct().firstNotNullOfOrNull { network ->
            val capabilities = manager.getNetworkCapabilities(network)
            val localTransport = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
            if (!localTransport || capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                null
            } else {
                manager.getLinkProperties(network)?.linkAddresses
                    ?.mapNotNull { (it.address as? Inet4Address)?.hostAddress }
                    ?.firstOrNull(::isPrivatePhoneHandoffAddress)
            }
        }
    }

    private companion object {
        const val PORT = 57342
        const val PAIR_WINDOW_MS = 120_000L
    }
}
