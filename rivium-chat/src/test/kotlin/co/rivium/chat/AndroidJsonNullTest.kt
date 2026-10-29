package co.rivium.chat

import co.rivium.chat.models.Message
import co.rivium.chat.services.ApiService
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JSON nulls as Android's org.json reads them.
 *
 * The unit tests run on the JVM with the reference org.json, whose
 * `optString(key, null)` answers null for a JSON null. Android's own org.json
 * answers the text "null" (and `optString(key)` answers "null" too). [AndroidJson]
 * reproduces that, so these tests see what a phone sees.
 */
class AndroidJsonNullTest {

    /** A JSONObject whose optString behaves like Android's. */
    private class AndroidJson(source: String) : JSONObject(source) {
        override fun optString(key: String): String = optString(key, "")!!

        override fun optString(key: String, defaultValue: String?): String? {
            val value = opt(key) ?: return defaultValue
            // Android: JSON.toString(value) ?: fallback, and JSON.NULL.toString() == "null".
            return if (value is String) value else value.toString()
        }

        override fun optJSONObject(key: String): JSONObject? =
            (opt(key) as? JSONObject)?.let { AndroidJson(it.toString()) }

        override fun optJSONArray(key: String): JSONArray? =
            (opt(key) as? JSONArray)?.let { AndroidJsonArray(it.toString()) }
    }

    private class AndroidJsonArray(source: String) : JSONArray(source) {
        override fun getJSONObject(index: Int): JSONObject = AndroidJson(super.getJSONObject(index).toString())
    }

    private val api = ApiService(RiviumChatConfig(apiKey = "rv_live_test", userId = "alice"))

    private val nullMessage = """
        {"id":"m-1","roomId":"room-1","senderUserId":"bob","content":"hi","type":"text",
         "createdAt":"2026-10-29T10:00:00.123Z","isDeleted":false,"isEdited":false,
         "editedAt":null,"replyToId":null,"replyTo":null,"isPinned":false,
         "pinnedAt":null,"pinnedBy":null,"metadata":{"orderId":"1001","note":null},
         "attachments":[{"url":"https://x/y.png","mimeType":null,"name":null,"size":null}]}
    """.trimIndent()

    @Test
    fun `the stand-in really behaves like Android`() {
        val json = AndroidJson("""{"a":null}""")
        assertEquals("null", json.optString("a", null))
        assertEquals("null", json.optString("a"))
    }

    @Test
    fun `message JSON nulls stay null`() {
        for (message in listOf(api.parseMessage(AndroidJson(nullMessage)), Message.fromJson(AndroidJson(nullMessage)))) {
            assertNull(message.editedAt)
            assertNull(message.getEditedAtInstant())
            assertNull(message.replyToId)
            assertNull(message.replyTo)
            assertNull(message.pinnedAt)
            assertNull(message.pinnedBy)
            assertEquals(mapOf("orderId" to "1001"), message.metadata)
            val attachment = message.attachments!!.single()
            assertNull(attachment.mimeType)
            assertNull(attachment.name)
            assertNull(attachment.size)
        }
    }

    @Test
    fun `a null message type reads as text`() {
        val json = AndroidJson(nullMessage.replace("\"type\":\"text\"", "\"type\":null"))
        assertEquals(co.rivium.chat.models.MessageType.TEXT, api.parseMessage(json).type)
    }

    @Test
    fun `room and participant JSON nulls stay null`() {
        val room = api.parseRoom(
            AndroidJson(
                """
                {"id":"room-1","type":"group","externalId":null,"name":null,"metadata":null,
                 "createdAt":null,"updatedAt":null,"unreadCount":2,
                 "lastMessage":$nullMessage,
                 "participants":[{"id":"p-1","externalUserId":"bob","displayName":null,
                   "locale":null,"role":"member","lastReadAt":null,"joinedAt":"2026-10-29T10:00:00.123Z"}]}
                """.trimIndent()
            )
        )
        assertNull(room.externalId)
        assertNull(room.name)
        assertNull(room.metadata)
        assertNull(room.createdAt)
        assertNull(room.updatedAt)
        assertNull(room.lastMessage!!.editedAt)
        val participant = room.participants.single()
        assertNull(participant.displayName)
        assertNull(participant.locale)
        assertNull(participant.lastReadAt)
    }
}
