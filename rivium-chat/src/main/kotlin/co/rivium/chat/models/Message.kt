package co.rivium.chat.models

import kotlinx.serialization.Serializable
import co.rivium.chat.services.intOrNull
import co.rivium.chat.services.stringMap
import co.rivium.chat.services.stringOr
import co.rivium.chat.services.stringOrNull
import org.json.JSONObject
import java.time.Instant

/**
 * Represents a chat message.
 */
@Serializable
data class Message(
    val id: String,
    val roomId: String,
    val senderUserId: String,
    val content: String,
    val type: MessageType = MessageType.TEXT,
    val attachments: List<Attachment>? = null,
    val metadata: Map<String, String>? = null,
    val replyToId: String? = null,
    val replyTo: Message? = null,
    val isDeleted: Boolean = false,
    val createdAt: String, // ISO 8601 format
    val isEdited: Boolean = false,
    val editedAt: String? = null, // ISO 8601 format
    val editHistory: List<Map<String, String>>? = null,
    val isPinned: Boolean = false,
    val pinnedAt: String? = null, // ISO 8601 format
    val pinnedBy: String? = null,
    val reactions: List<Reaction>? = null,
    // Local state flags (not persisted)
    val isPending: Boolean = false,
    val isFailed: Boolean = false,
    /**
     * The id the sending app gave the message (sent by the SDK when
     * `queueOfflineMessages` is on); matches a pending copy to the stored one.
     */
    val clientMessageId: String? = null
) {
    fun getCreatedAtInstant(): Instant = Instant.parse(createdAt)
    fun getEditedAtInstant(): Instant? = editedAt?.let { Instant.parse(it) }
    fun getPinnedAtInstant(): Instant? = pinnedAt?.let { Instant.parse(it) }

    companion object {
        fun fromJson(json: JSONObject): Message {
            val attachments = json.optJSONArray("attachments")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val att = arr.getJSONObject(i)
                    Attachment(
                        url = att.getString("url"),
                        mimeType = att.stringOrNull("mimeType"),
                        name = att.stringOrNull("name"),
                        size = att.intOrNull("size")
                    )
                }
            }

            val reactions = json.optJSONArray("reactions")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val r = arr.getJSONObject(i)
                    Reaction(
                        id = r.getString("id"),
                        messageId = r.getString("messageId"),
                        userId = r.getString("userId"),
                        emoji = r.getString("emoji"),
                        createdAt = r.getString("createdAt")
                    )
                }
            }

            val replyTo = json.optJSONObject("replyTo")?.let { fromJson(it) }

            val metadata = json.optJSONObject("metadata")?.stringMap()

            val editHistory = json.optJSONArray("editHistory")?.let { arr ->
                (0 until arr.length()).map { i ->
                    arr.getJSONObject(i).stringMap()
                }
            }

            return Message(
                id = json.getString("id"),
                roomId = json.getString("roomId"),
                senderUserId = json.getString("senderUserId"),
                content = json.stringOr("content", ""),
                type = MessageType.valueOf(json.stringOr("type", "text").uppercase()),
                attachments = attachments,
                metadata = metadata,
                replyToId = json.stringOrNull("replyToId"),
                replyTo = replyTo,
                isDeleted = json.optBoolean("isDeleted", false),
                createdAt = json.getString("createdAt"),
                isEdited = json.optBoolean("isEdited", false),
                editedAt = json.stringOrNull("editedAt"),
                editHistory = editHistory,
                isPinned = json.optBoolean("isPinned", false),
                pinnedAt = json.stringOrNull("pinnedAt"),
                pinnedBy = json.stringOrNull("pinnedBy"),
                reactions = reactions,
                clientMessageId = json.stringOrNull("clientMessageId")
            )
        }
    }
}

/**
 * Paginated response for message queries.
 */
@Serializable
data class PaginatedMessages(
    val messages: List<Message>,
    val hasMore: Boolean
)
