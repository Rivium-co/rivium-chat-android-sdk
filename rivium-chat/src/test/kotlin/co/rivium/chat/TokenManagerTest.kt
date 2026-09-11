package co.rivium.chat

import co.rivium.chat.services.TokenManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class TokenManagerTest {

    /** A JWT-shaped token expiring at [expMs]; only the payload matters here. */
    private fun jwt(id: String, expMs: Long): String {
        fun b64(s: String) = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(s.toByteArray())
        val payload = JSONObject().put("sub", id).put("exp", expMs / 1000).toString()
        return "${b64("{\"alg\":\"HS256\"}")}.${b64(payload)}.sig"
    }

    @Test
    fun `reuses the cached token`() = runBlocking {
        val calls = AtomicInteger()
        val manager = TokenManager({ jwt("t${calls.incrementAndGet()}", System.currentTimeMillis() + 3_600_000) })

        assertEquals(manager.get(), manager.get())
        assertEquals(1, calls.get())
    }

    @Test
    fun `refreshes a token that is about to expire`() = runBlocking {
        val calls = AtomicInteger()
        val manager = TokenManager({
            val n = calls.incrementAndGet()
            // First token expires inside the 60 s refresh margin.
            jwt("t$n", System.currentTimeMillis() + if (n == 1) 30_000 else 3_600_000)
        })

        manager.get()
        manager.get()
        assertEquals(2, calls.get())
    }

    @Test
    fun `concurrent callers share one refresh`() = runBlocking {
        val calls = AtomicInteger()
        val manager = TokenManager({
            calls.incrementAndGet()
            delay(50)
            jwt("t", System.currentTimeMillis() + 3_600_000)
        })

        val tokens = (1..8).map { async { manager.get() } }.awaitAll()
        assertEquals(1, calls.get())
        assertEquals(1, tokens.toSet().size)
    }

    @Test
    fun `clear forces a new token`() = runBlocking {
        val calls = AtomicInteger()
        val manager = TokenManager({ jwt("t${calls.incrementAndGet()}", System.currentTimeMillis() + 3_600_000) })

        manager.get()
        manager.clear()
        manager.get()
        assertEquals(2, calls.get())
    }

    @Test
    fun `expiryOf reads exp and tolerates junk`() {
        val exp = 1_800_000_000_000L
        assertEquals(exp, TokenManager.expiryOf(jwt("x", exp)))
        assertNull(TokenManager.expiryOf("not-a-jwt"))
    }
}
