package co.rivium.chat

import co.rivium.chat.events.MessageFailedEvent
import co.rivium.chat.events.MessageSentEvent
import co.rivium.chat.outbox.MemoryOutboxStore
import co.rivium.chat.outbox.PendingMessage
import co.rivium.chat.services.RiviumChatException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * The offline outbox through the public client, against a fake Chat API that
 * behaves like the real one (a repeat of the same clientMessageId in a room
 * answers the first stored message).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineOutboxTest {

    /** A fake Chat API: stores messages, de-duplicates by clientMessageId. */
    class FakeChatApi : Interceptor {
        @Volatile var online = true
        /** Store the message, then lose the answer (the app sees a network error). */
        @Volatile var loseNextAnswer = false
        /** Answer every send with this status instead. */
        @Volatile var status: Int? = null
        val bodies = mutableListOf<JSONObject>()
        val stored = mutableListOf<JSONObject>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (!online) throw IOException("Unable to resolve host")
            val segments = request.url.pathSegments
            val roomId = segments[segments.indexOf("rooms") + 1]
            val buffer = Buffer()
            request.body?.writeTo(buffer)
            val body = JSONObject(buffer.readUtf8())
            synchronized(this) { bodies += body }

            status?.let { return respond(chain, it, """{"message":"refused"}""") }

            val cmid = body.optString("clientMessageId", "")
            val (code, message) = synchronized(this) {
                val existing = stored.firstOrNull {
                    cmid.isNotEmpty() && it.getString("roomId") == roomId &&
                        it.getString("senderUserId") == body.getString("senderUserId") &&
                        it.optString("clientMessageId") == cmid
                }
                if (existing != null) {
                    200 to existing
                } else {
                    val m = JSONObject().apply {
                        put("id", "m-${stored.size + 1}")
                        put("roomId", roomId)
                        put("senderUserId", body.getString("senderUserId"))
                        put("content", body.getString("content"))
                        put("type", body.optString("type", "text"))
                        put("createdAt", "2026-10-29T10:00:00.000Z")
                        if (cmid.isNotEmpty()) put("clientMessageId", cmid)
                    }
                    stored += m
                    201 to m
                }
            }
            if (loseNextAnswer) {
                loseNextAnswer = false
                throw IOException("timeout")
            }
            return respond(chain, code, message.toString())
        }

        private fun respond(chain: Interceptor.Chain, code: Int, body: String) = Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("x")
            .body(body.toResponseBody(null))
            .build()

        fun contents() = synchronized(this) { stored.map { it.getString("content") } }
    }

    private val api = FakeChatApi()
    private var now = 1_800_000_000_000L

    /** A scope that is never advanced: background flushes only run when a test asks. */
    private val idle = TestScope(StandardTestDispatcher())

    private fun client(
        queue: Boolean = true,
        store: MemoryOutboxStore = MemoryOutboxStore(),
        tokenProvider: ChatTokenProvider? = null
    ) = RiviumChatClient(
        RiviumChatConfig(
            apiKey = "rv_live_demo_app",
            userId = "user-42",
            queueOfflineMessages = queue,
            outboxStore = store,
            tokenProvider = tokenProvider
        ),
        transport = api,
        outboxScope = idle,
        clock = { now }
    )

    private fun CoroutineScope.collect(client: RiviumChatClient): Pair<MutableList<MessageSentEvent>, MutableList<MessageFailedEvent>> {
        val sent = mutableListOf<MessageSentEvent>()
        val failed = mutableListOf<MessageFailedEvent>()
        launch(kotlinx.coroutines.Dispatchers.Unconfined) { client.onMessageSent.collect { sent += it } }
        launch(kotlinx.coroutines.Dispatchers.Unconfined) { client.onMessageFailed.collect { failed += it } }
        return sent to failed
    }

    @Test
    fun `option off - offline send throws and the body has no clientMessageId`() = runBlocking {
        val chat = client(queue = false)
        api.online = false
        try {
            chat.sendMessage("room-1", "Order #1001 is ready")
            fail("expected the offline send to throw")
        } catch (e: IOException) {
            // unchanged behaviour
        }
        api.online = true
        val m = chat.sendMessage("room-1", "Order #1001 is ready")
        assertEquals("m-1", m.id)
        assertFalse(api.bodies.single().has("clientMessageId"))
        assertTrue(chat.getPendingMessages().isEmpty())
        chat.dispose()
    }

    @Test
    fun `option on - online send carries a UUID clientMessageId`() = runBlocking {
        val chat = client()
        val m = chat.sendMessage("room-1", "hello")
        val cmid = api.bodies.single().getString("clientMessageId")
        assertTrue(cmid.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")))
        assertEquals(cmid, m.clientMessageId)
        assertFalse(m.isPending)
        chat.dispose()
    }

    @Test
    fun `offline send is queued, returned as pending, sent once on reconnect`() = runBlocking {
        val chat = client()
        val events = collect(chat)
        api.online = false

        val pending = chat.sendMessage("room-1", "Order #1001 is ready")
        assertTrue(pending.isPending)
        assertEquals("local_${pending.clientMessageId}", pending.id)
        assertEquals("user-42", pending.senderUserId)
        assertEquals(listOf(pending.id), chat.getPendingMessages("room-1").map { it.id })

        api.online = true
        chat.retryPendingMessages()
        chat.retryPendingMessages() // a second trigger sends nothing more

        assertEquals(listOf("Order #1001 is ready"), api.contents()) // the other user sees it once
        assertTrue(chat.getPendingMessages().isEmpty())
        val sent = events.first.single()
        assertEquals(pending.id, sent.localId)
        assertEquals(pending.clientMessageId, sent.clientMessageId)
        assertEquals("m-1", sent.message.id)
        assertEquals(pending.clientMessageId, sent.message.clientMessageId)
        coroutineContext.cancelChildren()
        chat.dispose()
    }

    @Test
    fun `a send whose answer was lost is stored once`() = runBlocking {
        val chat = client()
        api.loseNextAnswer = true

        val pending = chat.sendMessage("room-1", "hello")
        assertTrue(pending.isPending)
        chat.retryPendingMessages()

        assertEquals(1, api.stored.size)
        assertEquals(2, api.bodies.size)
        assertEquals(api.bodies[0].getString("clientMessageId"), api.bodies[1].getString("clientMessageId"))
        chat.dispose()
    }

    @Test
    fun `order is kept across several queued messages and rooms`() = runBlocking {
        val chat = client()
        api.online = false
        chat.sendMessage("room-1", "a1")
        chat.sendMessage("room-2", "b1")
        chat.sendMessage("room-1", "a2")
        api.online = true
        // Online again, but room-1 still has older messages: this one waits behind them.
        val behind = chat.sendMessage("room-1", "a3")
        assertTrue(behind.isPending)

        chat.retryPendingMessages()

        assertEquals(listOf("a1", "b1", "a2", "a3"), api.contents())
        chat.dispose()
    }

    @Test
    fun `permanent errors are not queued and throw as before`() = runBlocking {
        val chat = client()
        for (code in listOf(400, 401, 403, 404, 413)) {
            api.status = code
            try {
                chat.sendMessage("room-1", "hello")
                fail("expected $code to throw")
            } catch (e: RiviumChatException) {
                assertEquals(code, e.statusCode)
            }
        }
        assertTrue(chat.getPendingMessages().isEmpty())
        chat.dispose()
    }

    @Test
    fun `5xx, 408 and 429 are queued`() = runBlocking {
        val chat = client()
        api.status = 503
        assertTrue(chat.sendMessage("room-1", "a").isPending)
        api.status = 429
        assertTrue(chat.sendMessage("room-2", "b").isPending)
        api.status = 408
        assertTrue(chat.sendMessage("room-3", "c").isPending)
        assertEquals(3, chat.getPendingMessages().size)
        chat.dispose()
    }

    @Test
    fun `a failing tokenProvider is transient - the message is queued`() = runBlocking {
        var fail = true
        val chat = client(tokenProvider = { if (fail) throw IllegalStateException("token server unreachable") else "tok" })
        val pending = chat.sendMessage("room-1", "hello")
        assertTrue(pending.isPending)
        assertTrue(api.bodies.isEmpty())
        fail = false
        chat.retryPendingMessages()
        assertEquals(listOf("hello"), api.contents())
        chat.dispose()
    }

    @Test
    fun `stored items of another sender are dropped, never sent`() = runBlocking {
        val store = MemoryOutboxStore()
        store.save(listOf(
            PendingMessage("local_x", "x", "room-1", "user-7", "from another user", createdAt = now),
            PendingMessage("local_y", "y", "room-1", "user-42", "mine", createdAt = now)
        ))
        val chat = client(store = store)
        assertEquals(listOf("local_y"), chat.getPendingMessages().map { it.id })
        chat.retryPendingMessages()
        assertEquals(listOf("mine"), api.contents())
        chat.dispose()
    }

    @Test
    fun `a queued message the server then refuses fails with reason rejected`() = runBlocking {
        val chat = client()
        val events = collect(chat)
        api.online = false
        val pending = chat.sendMessage("room-1", "hello")
        api.online = true
        api.status = 403
        chat.retryPendingMessages()

        val failed = events.second.single()
        assertEquals(pending.id, failed.localId)
        assertEquals("room-1", failed.roomId)
        assertEquals("rejected", failed.reason)
        assertTrue(chat.getPendingMessages().isEmpty())
        coroutineContext.cancelChildren()
        chat.dispose()
    }

    @Test
    fun `expired message fails with reason expired and cancel removes one`() = runBlocking {
        val chat = client()
        val events = collect(chat)
        api.online = false
        val old = chat.sendMessage("room-1", "old")
        val other = chat.sendMessage("room-2", "cancel me")
        now += 24L * 60 * 60 * 1000 + 1

        assertTrue(chat.cancelPendingMessage(other.id))
        assertFalse(chat.cancelPendingMessage(other.id))
        api.online = true
        chat.retryPendingMessages()

        val failed = events.second.single()
        assertEquals(old.id, failed.localId)
        assertEquals(MessageFailedEvent.REASON_EXPIRED, failed.reason)
        assertTrue(api.stored.isEmpty())
        assertTrue(events.first.isEmpty()) // cancel emits nothing
        coroutineContext.cancelChildren()
        chat.dispose()
    }

    @Test
    fun `the store keeps the queue for the next client, clearPendingMessages empties it`() = runBlocking {
        val store = MemoryOutboxStore()
        val first = client(store = store)
        api.online = false
        val pending = first.sendMessage("room-1", "survives a restart")
        first.dispose()
        assertEquals(1, store.load().size)

        api.online = true
        val second = client(store = store)
        assertEquals(listOf(pending.id), second.getPendingMessages().map { it.id })
        second.retryPendingMessages()
        assertEquals(listOf("survives a restart"), api.contents())
        assertEquals(pending.clientMessageId, api.bodies.last().getString("clientMessageId"))

        api.online = false
        second.sendMessage("room-1", "x")
        second.clearPendingMessages()
        assertTrue(store.load().isEmpty())
        assertTrue(second.getPendingMessages().isEmpty())
        second.dispose()
    }

    @Test
    fun `racing reconnect triggers send each message once`() = runBlocking {
        val chat = client()
        api.online = false
        repeat(5) { chat.sendMessage("room-1", "m$it") }
        api.online = true

        (1..8).map { async(kotlinx.coroutines.Dispatchers.IO) { chat.retryPendingMessages() } }.awaitAll()

        assertEquals(listOf("m0", "m1", "m2", "m3", "m4"), api.contents())
        assertEquals(5, api.bodies.size)
        chat.dispose()
    }

    @Test
    fun `pending message JSON round-trips`() {
        val item = PendingMessage(
            localId = "local_abc",
            clientMessageId = "abc",
            roomId = "room-1",
            senderUserId = "user-42",
            content = "Order #1001",
            attachments = listOf(co.rivium.chat.models.Attachment("https://cdn.example.com/a.png", "image/png", "a.png", 12)),
            metadata = mapOf("orderId" to "1001", "count" to 2),
            replyToId = "m-7",
            createdAt = 1_800_000_000_000L
        )
        val back = PendingMessage.listFromJson(PendingMessage.listToJson(listOf(item))).single()
        assertEquals(item.copy(metadata = null), back.copy(metadata = null))
        assertEquals("1001", back.metadata!!["orderId"])
        assertEquals(2, back.metadata!!["count"])
        assertTrue(PendingMessage.listFromJson("not json").isEmpty())
        assertNull(PendingMessage.listFromJson("[]").firstOrNull())
    }

}
