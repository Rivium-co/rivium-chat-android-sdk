package co.rivium.chat

import co.rivium.chat.services.ApiService
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every caller-supplied value in a URL reaches the server exactly as given. */
class UrlEncodingTest {

    private val sent = mutableListOf<Request>()

    private val message = """{"id":"m-1","roomId":"r","senderUserId":"bob","content":"x","createdAt":"2026-10-29T10:00:00.123Z"}"""
    private val room = """{"id":"r","type":"direct","participants":[]}"""

    private fun api(body: String): ApiService {
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
        return ApiService(RiviumChatConfig(apiKey = "rv_live_test", userId = "alice"), null, transport)
    }

    // A value with every character that changes a URL's meaning.
    private val roomId = "room/1?x=#a b%2F"
    private val messageId = "msg/1?#"
    private val userId = "user+1&admin=true#x y"

    private fun last(): HttpUrl = sent.last().url

    private fun assertPath(vararg segments: String) {
        assertEquals(listOf("api", "v1", *segments), last().pathSegments)
        assertNull(last().fragment)
    }

    @Test
    fun `room routes encode the room ID as one segment`() = runBlocking {
        api(room).getRoom(roomId)
        assertPath("rooms", roomId)
        assertNull(last().query)

        api("""{"id":"p","externalUserId":"u","joinedAt":"2026-10-29T10:00:00Z"}""").addParticipant(roomId, userId)
        assertPath("rooms", roomId, "participants")

        api(message).sendMessage(roomId, userId, "hi")
        assertPath("rooms", roomId, "messages")

        api("{}").markAsRead(roomId, userId)
        assertPath("rooms", roomId, "read")

        api("[]").getPinnedMessages(roomId)
        assertPath("rooms", roomId, "pinned")
    }

    @Test
    fun `query values are encoded, plus signs included`() = runBlocking {
        api("[]").listRooms(userId)
        assertPath("rooms")
        assertEquals(userId, last().queryParameter("userId"))
        assertEquals(listOf("userId"), last().queryParameterNames.toList())

        api("""{"messages":[],"hasMore":false}""").getMessages(roomId, userId, 10, "2026-10-29T10:00:00.123+04:00")
        assertPath("rooms", roomId, "messages")
        assertEquals(userId, last().queryParameter("userId"))
        assertEquals("10", last().queryParameter("limit"))
        assertEquals("2026-10-29T10:00:00.123+04:00", last().queryParameter("before"))
        assertTrue(last().encodedQuery!!.contains("%2B"))

        api("""{"totalUnread":0,"rooms":[]}""").getUnreadSummary(userId)
        assertPath("rooms", "unread-summary")
        assertEquals(userId, last().queryParameter("userId"))

        api("[]").getMentions(roomId, userId, 5, 2)
        assertPath("rooms", roomId, "mentions")
        assertEquals(userId, last().queryParameter("userId"))
        assertEquals("5", last().queryParameter("limit"))
        assertEquals("2", last().queryParameter("offset"))

        api("""{"messages":[]}""").searchMessages(roomId, userId, "a+b & c")
        assertPath("rooms", roomId, "messages", "search")
        assertEquals("a+b & c", last().queryParameter("q"))
    }

    @Test
    fun `message routes encode the message ID as one segment`() = runBlocking {
        api("{}").deleteMessage(messageId, userId)
        assertPath("messages", messageId)
        assertEquals(userId, last().queryParameter("userId"))

        api(message).editMessage(messageId, userId, "new")
        assertPath("messages", messageId)

        api("""{"id":"x","messageId":"m","userId":"u","emoji":"+1","createdAt":"t"}""").addReaction(messageId, userId, "+1")
        assertPath("messages", messageId, "reactions")

        api("{}").removeReaction(messageId, userId, "+1")
        assertPath("messages", messageId, "reactions")

        api("[]").getReactions(messageId)
        assertPath("messages", messageId, "reactions")

        api(message).pinMessage(messageId, userId)
        assertPath("messages", messageId, "pin")

        api("{}").unpinMessage(messageId, userId)
        assertPath("messages", messageId, "pin")
    }

    @Test
    fun `an ID that would change the route is refused`() {
        for (bad in listOf("", ".", "..")) {
            try {
                runBlocking { api(room).getRoom(bad) }
                throw AssertionError("getRoom(\"$bad\") should fail")
            } catch (e: IllegalArgumentException) {
                // expected
            }
        }
        assertTrue(sent.isEmpty())
    }
}
