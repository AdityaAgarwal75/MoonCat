package com.nuvio.tv.ui.screens.search

import android.content.Context
import com.nuvio.tv.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Delivers named Alexa skill requests to the active Search screen. No remote audio is captured. */
internal class MoonCatAlexaRelay(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mooncat_alexa", Context.MODE_PRIVATE)
    private val baseUrl = BuildConfig.ALEXA_RELAY_URL.trim().trimEnd('/')

    val isConfigured: Boolean get() = baseUrl.startsWith("https://")

    private val deviceId: String by lazy {
        prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }
    }
    private val token: String by lazy {
        prefs.getString("device_token", null) ?: ByteArray(32).also(SecureRandom()::nextBytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }.also {
                prefs.edit().putString("device_token", it).apply()
            }
    }

    suspend fun pairingCode(): String = withContext(Dispatchers.IO) {
        check(isConfigured) { "Alexa relay is not configured" }
        val body = JSONObject().put("deviceId", deviceId).put("token", token)
        request("POST", "$baseUrl/register", body).getString("code").also {
            prefs.edit().putBoolean("registered", true).apply()
        }
    }

    suspend fun takeSearchQuery(): String? = withContext(Dispatchers.IO) {
        if (!isConfigured || !prefs.getBoolean("registered", false)) return@withContext null
        val response = request("GET", "$baseUrl/command?deviceId=$deviceId", null, "Bearer $token")
        response.optString("query").takeIf { it.isNotBlank() }
    }

    private fun request(method: String, url: String, body: JSONObject?, auth: String? = null): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 4000
            readTimeout = 4000
            setRequestProperty("Accept", "application/json")
            if (auth != null) setRequestProperty("Authorization", auth)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        return try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("Alexa relay returned HTTP $status")
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}
