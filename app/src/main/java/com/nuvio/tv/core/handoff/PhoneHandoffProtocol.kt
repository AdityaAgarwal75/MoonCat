package com.nuvio.tv.core.handoff

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal const val PHONE_HANDOFF_MAX_POSITION_MS = 86_400_000L
internal val phoneHandoffKeyFormat = Regex("[a-f0-9]{64}")
private val nonceFormat = Regex("[a-f0-9]{32}")
private val timeFormat = Regex("[0-9]{1,12}")
private val imdbFormat = Regex("tt[0-9]{5,12}")
private val numericFormat = Regex("[0-9]{1,9}")
private const val CLOCK_WINDOW_SECONDS = 45L
private const val MAX_PENDING_NONCES = 256

internal data class PhoneHandoff(
    val contentId: String,
    val videoId: String,
    val type: String,
    val title: String,
    val season: Int?,
    val episode: Int?,
    val episodeTitle: String?,
    val positionMs: Long
)

internal class PhoneHandoffAuthenticator(
    private val key: String,
    private val secondsNow: () -> Long = { System.currentTimeMillis() / 1000 }
) {
    private val nonces = mutableMapOf<String, Long>()

    @Synchronized
    fun accept(path: String, time: String?, nonce: String?, signature: String?, body: String): Boolean {
        if (time == null || nonce == null || signature == null ||
            !timeFormat.matches(time) || !nonceFormat.matches(nonce) || !phoneHandoffKeyFormat.matches(signature)
        ) return false
        val timestamp = time.toLong()
        val now = secondsNow()
        if (timestamp !in (now - CLOCK_WINDOW_SECONDS)..(now + CLOCK_WINDOW_SECONDS)) return false
        nonces.entries.removeAll { it.value < now }
        if (nonce in nonces || nonces.size >= MAX_PENDING_NONCES) return false
        val expected = signPhoneHandoff(key, path, time, nonce, body)
        if (!MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), signature.toByteArray(Charsets.US_ASCII))) return false
        nonces[nonce] = timestamp + CLOCK_WINDOW_SECONDS
        return true
    }
}

internal fun signPhoneHandoff(key: String, path: String, time: String, nonce: String, body: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    return mac.doFinal("POST\n$path\n$time\n$nonce\n$body".toByteArray(Charsets.UTF_8)).toPhoneHandoffHex()
}

internal fun ByteArray.toPhoneHandoffHex(): String = buildString(size * 2) {
    val digits = "0123456789abcdef"
    for (byte in this@toPhoneHandoffHex) {
        val value = byte.toInt() and 255
        append(digits[value shr 4])
        append(digits[value and 15])
    }
}

internal fun parsePhoneHandoff(raw: String): PhoneHandoff? {
    val payload = runCatching { JsonParser.parseString(raw) }.getOrNull() as? JsonObject ?: return null
    fun text(name: String): String? =
        payload[name]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    fun integer(name: String): Int? = payload[name]
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber && numericFormat.matches(it.asString) }
        ?.asInt

    val contentId = text("contentId")?.takeIf(imdbFormat::matches) ?: return null
    val videoId = text("videoId") ?: return null
    val type = text("type") ?: return null
    val title = text("title")?.trim()?.takeIf { it.length in 1..180 && it.none(Char::isISOControl) } ?: return null
    val position = integer("positionMs")?.toLong()?.takeIf { it in 0..PHONE_HANDOFF_MAX_POSITION_MS } ?: return null
    val season = integer("season")
    val episode = integer("episode")
    when (type) {
        "movie" -> if (videoId != contentId || season != null || episode != null) return null
        "series" -> if (season == null || episode == null || season !in 0..999 ||
            episode !in 0..9999 || videoId != "$contentId:$season:$episode"
        ) return null
        else -> return null
    }
    val episodeTitle = text("episodeTitle")?.trim()?.takeIf { it.length in 1..160 && it.none(Char::isISOControl) }
    return PhoneHandoff(contentId, videoId, type, title, season, episode, episodeTitle, position)
}

internal fun isPrivatePhoneHandoffAddress(address: String): Boolean {
    val cells = address.split('.')
    if (cells.size != 4 || cells.any { it.length !in 1..3 || it.any { c -> c !in '0'..'9' } }) return false
    val parts = cells.map(String::toInt)
    if (parts.any { it !in 0..255 }) return false
    return parts[0] == 10 || (parts[0] == 172 && parts[1] in 16..31) ||
        (parts[0] == 192 && parts[1] == 168)
}
