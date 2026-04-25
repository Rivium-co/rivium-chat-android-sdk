package co.rivium.chat.models

import kotlinx.serialization.Serializable
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
    val isFailed: Boolean = false
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
                        mimeType = att.optString("mimeType", null),
                        name = att.optString("name", null),
                        size = if (att.has("size")) att.getInt("size") else null
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

            val metadata = json.optJSONObject("metadata")?.let { obj ->
                val map = mutableMapOf<String, String>()
                obj.keys().forEach { key -> map[key] = obj.optString(key) }
                map
            }

            val editHistory = json.optJSONArray("editHistory")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val entry = arr.getJSONObject(i)
                    val map = mutableMapOf<String, String>()
                    entry.keys().forEach { key -> map[key] = entry.optString(key) }
                    map
                }
            }

            return Message(
                id = json.getString("id"),
                roomId = json.getString("roomId"),
                senderUserId = json.getString("senderUserId"),
                content = json.optString("content", ""),
                type = MessageType.valueOf(json.optString("type", "text").uppercase()),
                attachments = attachments,
                metadata = metadata,
                replyToId = json.optString("replyToId", null),
                replyTo = replyTo,
                isDeleted = json.optBoolean("isDeleted", false),
                createdAt = json.getString("createdAt"),
                isEdited = json.optBoolean("isEdited", false),
                editedAt = json.optString("editedAt", null),
                editHistory = editHistory,
                isPinned = json.optBoolean("isPinned", false),
                pinnedAt = json.optString("pinnedAt", null),
                pinnedBy = json.optString("pinnedBy", null),
                reactions = reactions
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
