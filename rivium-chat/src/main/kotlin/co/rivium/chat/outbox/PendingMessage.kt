package co.rivium.chat.outbox

import co.rivium.chat.models.Attachment
import co.rivium.chat.models.Message
import co.rivium.chat.models.MessageType
import co.rivium.chat.services.intOrNull
import co.rivium.chat.services.stringOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * A message written while offline that waits in the outbox
 * (only with [co.rivium.chat.RiviumChatConfig.queueOfflineMessages]).
 *
 * [localId] is `local_<clientMessageId>`, the id of the pending [Message]
 * the SDK returned from `sendMessage()`.
 */
data class PendingMessage(
    val localId: String,
    val clientMessageId: String,
    val roomId: String,
    val senderUserId: String,
    val content: String,
    val type: MessageType = MessageType.TEXT,
    val attachments: List<Attachment>? = null,
    val metadata: Map<String, Any>? = null,
    val replyToId: String? = null,
    /** When the message was written, epoch milliseconds. */
    val createdAt: Long
) {
    /** The pending message as the app shows it: `isPending = true`. */
    fun toMessage(): Message = Message(
        id = localId,
        roomId = roomId,
        senderUserId = senderUserId,
        content = content,
        type = type,
        attachments = attachments,
        metadata = metadata?.mapValues { it.value.toString() },
        replyToId = replyToId,
        createdAt = Instant.ofEpochMilli(createdAt).toString(),
        isPending = true,
        clientMessageId = clientMessageId
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("localId", localId)
        put("clientMessageId", clientMessageId)
        put("roomId", roomId)
        put("senderUserId", senderUserId)
        put("content", content)
        put("type", type.name.lowercase())
        attachments?.let { list ->
            put("attachments", JSONArray(list.map { att ->
                JSONObject().apply {
                    put("url", att.url)
                    att.mimeType?.let { put("mimeType", it) }
                    att.name?.let { put("name", it) }
                    att.size?.let { put("size", it) }
                }
            }))
        }
        metadata?.let { put("metadata", JSONObject(it)) }
        replyToId?.let { put("replyToId", it) }
        put("createdAt", createdAt)
    }

    companion object {
        /** Prefix of a pending message's id. */
        const val LOCAL_ID_PREFIX = "local_"

        fun localIdFor(clientMessageId: String) = LOCAL_ID_PREFIX + clientMessageId

        fun fromJson(json: JSONObject): PendingMessage {
            val clientMessageId = json.getString("clientMessageId")
            return PendingMessage(
                localId = json.stringOrNull("localId") ?: localIdFor(clientMessageId),
                clientMessageId = clientMessageId,
                roomId = json.getString("roomId"),
                senderUserId = json.getString("senderUserId"),
                content = json.optString("content", ""),
                type = runCatching { MessageType.valueOf(json.optString("type", "text").uppercase()) }
                    .getOrDefault(MessageType.TEXT),
                attachments = json.optJSONArray("attachments")?.let { arr ->
                    (0 until arr.length()).map { i ->
                        val att = arr.getJSONObject(i)
                        Attachment(
                            url = att.getString("url"),
                            mimeType = att.stringOrNull("mimeType"),
                            name = att.stringOrNull("name"),
                            size = att.intOrNull("size")
                        )
                    }
                },
                metadata = json.optJSONObject("metadata")?.let { toMap(it) },
                replyToId = json.stringOrNull("replyToId"),
                createdAt = json.getLong("createdAt")
            )
        }

        /** Encodes a list for a store. */
        fun listToJson(items: List<PendingMessage>): String = JSONArray(items.map { it.toJson() }).toString()

        /** Decodes what [listToJson] wrote; unreadable entries are skipped. */
        fun listFromJson(text: String): List<PendingMessage> {
            val arr = try {
                JSONArray(text)
            } catch (e: Exception) {
                return emptyList()
            }
            return (0 until arr.length()).mapNotNull { i ->
                try {
                    fromJson(arr.getJSONObject(i))
                } catch (e: Exception) {
                    null
                }
            }
        }

        private fun toMap(json: JSONObject): Map<String, Any> {
            val out = LinkedHashMap<String, Any>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.opt(key)
                if (value == null || value == JSONObject.NULL) continue
                out[key] = toPlain(value)
            }
            return out
        }

        private fun toPlain(value: Any): Any = when (value) {
            is JSONObject -> toMap(value)
            is JSONArray -> (0 until value.length()).mapNotNull { i ->
                value.opt(i)?.takeIf { it != JSONObject.NULL }?.let { toPlain(it) }
            }
            else -> value
        }
    }
}
