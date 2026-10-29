package co.rivium.chat

import co.rivium.chat.events.AuthErrorEvent
import co.rivium.chat.services.ApiService
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Requests and responses as the Chat API sends them. */
class ApiResponseShapesTest {

    private val sent = mutableListOf<Request>()

    private fun message(id: String, content: String = "hello") = """
        {"id":"$id","roomId":"room-1","senderUserId":"bob","content":"$content",
         "type":"text","isDeleted":false,"createdAt":"2026-10-29T10:00:00.123Z"}
    """.trimIndent()

    /** An ApiService whose requests are answered with [body] instead of the network. */
    private fun api(
        body: String,
        tokenProvider: ChatTokenProvider? = null,
        onAuthError: ((AuthErrorEvent) -> Unit)? = null
    ): ApiService {
        val transport = Interceptor { chain ->
            sent += chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody(null))
                .build()
        }
        val config = RiviumChatConfig(apiKey = "rv_live_test", userId = "alice", tokenProvider = tokenProvider)
        return ApiService(config, onAuthError, transport)
    }

    @Test
    fun `room list reads lastMessage and unreadCount`() = runBlocking {
        val body = """
            [{"id":"room-1","type":"direct","participants":[],
              "lastMessage":${message("m-9", "latest")},"unreadCount":3},
             {"id":"room-2","type":"group","participants":[],"lastMessage":null,"unreadCount":0}]
        """.trimIndent()

        val rooms = api(body).listRooms("alice")

        assertEquals("m-9", rooms[0].lastMessage?.id)
        assertEquals("latest", rooms[0].lastMessage?.content)
        assertEquals(3, rooms[0].unreadCount)
        assertNull(rooms[1].lastMessage)
        assertEquals(0, rooms[1].unreadCount)
    }

    @Test
    fun `a room without the list-only fields still parses`() = runBlocking {
        val room = api("""{"id":"room-1","type":"direct","participants":[]}""").getRoom("room-1")

        assertNull(room.lastMessage)
        assertEquals(0, room.unreadCount)
    }

    @Test
    fun `searchMessagesWithTotal reports the total number of matches`() = runBlocking {
        val body = """{"messages":[${message("m-1")},${message("m-2")}],"total":5}"""

        val result = api(body).searchMessagesWithTotal("room-1", "alice", "hello", limit = 2)

        assertEquals(listOf("m-1", "m-2"), result.messages.map { it.id })
        assertEquals(5, result.total)
        assertTrue(result.hasMore)
    }

    @Test
    fun `searchMessagesWithTotal - the last page has no more`() = runBlocking {
        val body = """{"messages":[${message("m-3")}],"total":3}"""

        val result = api(body).searchMessagesWithTotal("room-1", "alice", "hello", limit = 2, offset = 2)

        assertEquals(3, result.total)
        assertEquals(false, result.hasMore)
    }

    @Test
    fun `searchMessagesWithTotal accepts a bare array`() = runBlocking {
        val result = api("[${message("m-1")}]").searchMessagesWithTotal("room-1", "alice", "hello")

        assertEquals(1, result.total)
        assertEquals(false, result.hasMore)
    }

    @Test
    fun `search reads the messages of the messages-and-total object`() = runBlocking {
        val body = """{"messages":[${message("m-1")},${message("m-2")}],"total":7}"""

        val results = api(body).searchMessages("room-1", "alice", "hello")

        assertEquals(listOf("m-1", "m-2"), results.map { it.id })
    }

    @Test
    fun `search text is encoded in the URL`() = runBlocking {
        api("""{"messages":[],"total":0}""").searchMessages("room-1", "alice", "Order #1001 & co")

        val url = sent.single().url
        assertEquals("Order #1001 & co", url.queryParameter("q"))
        assertEquals("alice", url.queryParameter("userId"))
        assertEquals("20", url.queryParameter("limit"))
        assertEquals("0", url.queryParameter("offset"))
        assertNull(url.fragment)
        assertTrue(url.encodedQuery!!.contains("q=Order%20%231001%20%26%20co"))
    }

    @Test
    fun `external ID is encoded as one path segment`() = runBlocking {
        api("""{"id":"room-1","type":"direct","participants":[]}""").getRoomByExternalId("Order #1001/a&b")

        val url = sent.single().url
        assertEquals(
            listOf("api", "v1", "rooms", "by-external-id", "Order #1001/a&b"),
            url.pathSegments
        )
        assertNull(url.fragment)
        assertNull(url.query)
    }

    @Test
    fun `mentions return the mentioning messages of the mention records`() = runBlocking {
        val body = """
            [{"id":"men-1","messageId":"m-1","mentionedUserId":"alice","roomId":"room-1",
              "createdAt":"2026-10-29T10:00:01.000Z","message":${message("m-1", "hi @alice")}},
             {"id":"men-2","messageId":"m-2","mentionedUserId":"alice","roomId":"room-1",
              "createdAt":"2026-10-29T10:00:02.000Z","message":null}]
        """.trimIndent()

        val mentions = api(body).getMentions("room-1", "alice")

        assertEquals(1, mentions.size)
        assertEquals("m-1", mentions[0].id)
        assertEquals("hi @alice", mentions[0].content)
        assertEquals("bob", mentions[0].senderUserId)
    }

    @Test
    fun `a failing tokenProvider on the realtime path reports token_provider_failed`() {
        val events = mutableListOf<AuthErrorEvent>()
        val service = api("[]", tokenProvider = { throw IllegalStateException("offline") }) { events += it }

        try {
            runBlocking { service.userTokenOrNull() }
            fail("expected the provider error")
        } catch (e: IllegalStateException) {
            assertEquals("offline", e.message)
        }

        assertEquals("token_provider_failed", events.single().code)
        assertNotNull(events.single().error)
    }

    @Test
    fun `without a tokenProvider the realtime path asks for no user token`() = runBlocking {
        assertNull(api("[]").userTokenOrNull())
    }
}
