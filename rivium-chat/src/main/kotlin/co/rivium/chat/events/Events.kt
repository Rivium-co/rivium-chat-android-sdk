package co.rivium.chat.events

import co.rivium.chat.models.Message
import co.rivium.chat.models.SubscriptionStatus
import org.json.JSONObject
import java.time.Instant

/** Event emitted when a user reads messages in a room. */
data class ReadReceipt(
    val userId: String,
    val roomId: String,
    val readAt: Instant
) {
    companion object {
        fun fromJson(json: JSONObject): ReadReceipt {
            return ReadReceipt(
                userId = json.getString("userId"),
                roomId = json.getString("roomId"),
                readAt = Instant.parse(json.getString("readAt"))
            )
        }
    }
}

/** Event emitted when a message is deleted. */
data class MessageDeletion(
    val messageId: String,
    val roomId: String,
    val deletedBy: String
) {
    companion object {
        fun fromJson(json: JSONObject): MessageDeletion {
            return MessageDeletion(
                messageId = json.getString("messageId"),
                roomId = json.getString("roomId"),
                deletedBy = json.getString("deletedBy")
            )
        }
    }
}

/** Event emitted when a user is typing. */
data class TypingEvent(
    val roomId: String,
    val userId: String,
    val isTyping: Boolean = true
)

/** Event emitted when a reaction is added or removed. */
data class ReactionEvent(
    val messageId: String,
    val roomId: String,
    val userId: String,
    val emoji: String,
    val added: Boolean,
    val reactionId: String? = null
)

/** Event emitted when a message is edited. */
data class MessageEditEvent(
    val messageId: String,
    val roomId: String,
    val content: String,
    val editedBy: String,
    val editedAt: Instant
)

/** Event emitted when a message is pinned or unpinned. */
data class MessagePinEvent(
    val messageId: String,
    val roomId: String,
    val userId: String,
    val pinned: Boolean
)

/** Event emitted when a connection error occurs. */
data class ConnectionErrorEvent(
    val error: Any
)

/** Event emitted when user presence changes. */
data class PresenceEvent(
    val roomId: String,
    val userId: String,
    val isOnline: Boolean
)

/** Event emitted when subscription state changes. */
data class SubscriptionStateEvent(
    val roomId: String,
    val channel: String,
    val status: SubscriptionStatus,
    val code: Int? = null,
    val reason: String? = null
)
