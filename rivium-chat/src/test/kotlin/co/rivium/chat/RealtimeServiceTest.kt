package co.rivium.chat

import co.rivium.chat.services.RealtimeService
import io.github.centrifugal.centrifuge.ConnectionTokenEvent
import io.github.centrifugal.centrifuge.TokenCallback
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RealtimeServiceTest {

    private val config = RiviumChatConfig(apiKey = "rv_live_test", userId = "alice")

    private class Answer(val error: Throwable?, val token: String?)

    /** Asks the service's token getter once, as the realtime client does on expiry. */
    private fun ask(service: RealtimeService): Answer {
        val latch = CountDownLatch(1)
        var answer: Answer? = null
        service.connectionTokenGetter().getConnectionToken(ConnectionTokenEvent(), TokenCallback { error, token ->
            answer = Answer(error, token)
            latch.countDown()
        })
        assertTrue("token getter never answered", latch.await(5, TimeUnit.SECONDS))
        return answer!!
    }

    @Test
    fun `token getter fetches a fresh token from the same source every time`() {
        val calls = AtomicInteger()
        val service = RealtimeService(config) { "token-${calls.incrementAndGet()}" }

        assertEquals("token-1", ask(service).token)
        assertEquals("token-2", ask(service).token)
        assertEquals(2, calls.get())
    }

    @Test
    fun `token getter hands a failure back to the realtime client`() {
        val service = RealtimeService(config) { throw IllegalStateException("offline") }

        val answer = ask(service)

        assertNull(answer.token)
        assertEquals("offline", answer.error?.message)
    }

    @Test
    fun `typing is throttled per room, not per client`() {
        val service = RealtimeService(config) { "t" }

        assertTrue(service.shouldPublishTyping("room-a", 10_000))
        // Another room right away is not muted by room-a.
        assertTrue(service.shouldPublishTyping("room-b", 10_100))
        // The same room inside two seconds is.
        assertFalse(service.shouldPublishTyping("room-a", 11_000))
        assertTrue(service.shouldPublishTyping("room-a", 12_000))
    }

    @Test
    fun `presence of a room that is not subscribed answers at once`() = runBlocking {
        val service = RealtimeService(config) { "t" }

        assertEquals(emptySet<String>(), service.getRoomPresence("room-a"))
    }
}
