package co.rivium.chat.services

import co.rivium.chat.ChatTokenProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Holds the current user token and refreshes it through the app's
 * [ChatTokenProvider].
 *
 * - Reuses the cached token until shortly before it expires, then fetches a
 *   new one before the request, so an expired token rarely reaches the server.
 * - Concurrent callers share one refresh: a burst of requests triggers a
 *   single call to your server.
 */
internal class TokenManager(
    private val provider: ChatTokenProvider,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private var token: String? = null
    private var expiresAt: Long? = null

    /** A token valid for at least [REFRESH_MARGIN_MS]. */
    suspend fun get(): String {
        val cached = token
        val expiry = expiresAt
        if (cached != null && (expiry == null || now() < expiry - REFRESH_MARGIN_MS)) return cached
        return refresh()
    }

    /** Fetches a new token even if the cached one looks valid. */
    suspend fun refresh(): String = mutex.withLock {
        // Another caller may have refreshed while this one waited for the lock.
        val cached = token
        val expiry = expiresAt
        if (cached != null && (expiry == null || now() < expiry - REFRESH_MARGIN_MS)) return cached

        val fresh = provider()
        token = fresh
        expiresAt = expiryOf(fresh)
        fresh
    }

    fun clear() {
        token = null
        expiresAt = null
    }

    companion object {
        /** Refresh this long before `exp`, to absorb clock skew and request time. */
        const val REFRESH_MARGIN_MS = 60_000L

        /** The `exp` claim of a JWT in ms, or null if it has none or cannot be read. */
        fun expiryOf(token: String): Long? = try {
            val payload = token.split(".").getOrNull(1)
            if (payload == null) null else {
                val exp = JSONObject(decodeBase64Url(payload)).optLong("exp", 0L)
                if (exp > 0L) exp * 1000L else null
            }
        } catch (e: Exception) {
            null
        }

        /**
         * Decodes base64url without padding. Hand-rolled because
         * `java.util.Base64` needs API 26 (this SDK supports 21) and
         * `android.util.Base64` is unavailable in JVM unit tests.
         */
        private fun decodeBase64Url(input: String): String {
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
            val out = java.io.ByteArrayOutputStream()
            var buffer = 0
            var bits = 0
            for (c in input) {
                if (c == '=') break
                val value = alphabet.indexOf(c)
                require(value >= 0) { "not base64url" }
                buffer = (buffer shl 6) or value
                bits += 6
                if (bits >= 8) {
                    bits -= 8
                    out.write((buffer shr bits) and 0xFF)
                }
            }
            return String(out.toByteArray(), Charsets.UTF_8)
        }
    }
}
