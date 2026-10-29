package co.rivium.chat

import co.rivium.chat.events.ConnectionErrorEvent
import co.rivium.chat.models.ConnectionState
import co.rivium.chat.services.RealtimeService
import co.rivium.chat.services.RiviumChatException
import io.github.centrifugal.centrifuge.subscribeErrorOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RealtimeLifecycleTest {

    private val config = RiviumChatConfig(apiKey = "rv_live_test", userId = "user-42")

    /** Exceptions that escaped to a thread: on Android these crash the app. */
    private val uncaught = Collections.synchronizedList(mutableListOf<Throwable>())
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun catchUncaught() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught += e }
    }

    @After
    fun restore() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out waiting for $what")
            Thread.sleep(10)
        }
    }

    @Test
    fun `a failing connection token sets the error state and reports it`() = runBlocking {
        repeat(20) {
            val service = RealtimeService(config) { throw RiviumChatException("Failed to get token: 401") }
            val error = CoroutineScope(Dispatchers.IO).async {
                withTimeout(5_000) { service.connectionError.first() }
            }
            Thread.sleep(20) // let the collector start

            service.connect()

            val event: ConnectionErrorEvent = error.await()
            assertTrue(event.error is RiviumChatException)
            waitUntil("error state") { service.currentConnectionState == ConnectionState.ERROR }
            // The replayed state a late subscriber sees must be the last one,
            // not a "connecting" that was emitted earlier but delivered later.
            assertEquals(listOf(ConnectionState.ERROR), service.connectionState.replayCache)
            assertFalse(service.hasClient)
        }
    }

    @Test
    fun `disconnect while the token is still being fetched opens no connection`() {
        val release = CountDownLatch(1)
        val asked = CountDownLatch(1)
        val service = RealtimeService(config) {
            asked.countDown()
            release.await(5, TimeUnit.SECONDS)
            "token"
        }

        service.connect()
        assertTrue(asked.await(5, TimeUnit.SECONDS))
        service.disconnect()
        release.countDown()
        Thread.sleep(200)

        assertFalse("a socket was opened after disconnect()", service.hasClient)
        assertEquals(ConnectionState.DISCONNECTED, service.currentConnectionState)
    }

    @Test
    fun `dispose while the token is still being fetched opens no connection`() {
        val release = CountDownLatch(1)
        val asked = CountDownLatch(1)
        val service = RealtimeService(config) {
            asked.countDown()
            release.await(5, TimeUnit.SECONDS)
            "token"
        }

        service.connect()
        assertTrue(asked.await(5, TimeUnit.SECONDS))
        service.dispose()
        release.countDown()
        Thread.sleep(200)

        assertFalse("a socket was opened after dispose()", service.hasClient)
    }

    @Test
    fun `a malformed realtime event does not crash the app`() {
        val service = RealtimeService(config) { "token" }
        val bad = listOf(
            """{"event":"message_edited","data":{"messageId":"m1","content":"x","editedBy":"user-7","editedAt":"not a date"}}""",
            """{"event":"message","data":{"id":"m1"}}""",
            """{"event":"reaction_added","data":{"messageId":"m1"}}""",
            """{"event":"read","data":{"userId":"user-7"}}"""
        )

        bad.forEach { service.handleChatPublication("room-1", it.toByteArray()) }
        Thread.sleep(300)

        assertEquals(emptyList<Throwable>(), uncaught.toList())
    }

    @Test
    fun `a refused subscription carries the server's code and reason`() {
        val (code, reason) = RealtimeService.subscriptionFailure(
            subscribeErrorOf(403, "Not a participant in this room")
        )

        assertEquals(403, code)
        assertEquals("Not a participant in this room", reason)
    }

    @Test
    fun `an error without a server code keeps its message`() {
        val (code, reason) = RealtimeService.subscriptionFailure(IllegalStateException("socket closed"))

        assertNull(code)
        assertEquals("socket closed", reason)
    }
}
