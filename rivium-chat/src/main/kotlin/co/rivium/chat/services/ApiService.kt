package co.rivium.chat.services

import co.rivium.chat.RiviumChatConfig
import co.rivium.chat.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import co.rivium.chat.events.AuthErrorEvent
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.TimeUnit

/**
 * HTTP API service for RiviumChat backend.
 */
class ApiService internal constructor(
    private val config: RiviumChatConfig,
    private val onAuthError: ((AuthErrorEvent) -> Unit)?,
    /** Answers requests instead of the network; tests only. */
    transport: Interceptor?
) {

    constructor(
        config: RiviumChatConfig,
        onAuthError: ((AuthErrorEvent) -> Unit)? = null
    ) : this(config, onAuthError, null)

    private val tokens: TokenManager? = config.tokenProvider?.let { TokenManager(it) }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(defaultHeadersInterceptor(config.apiKey))
        .addInterceptor(userTokenInterceptor())
        .apply { transport?.let { addInterceptor(it) } }
        .build()

    /**
     * Adds the user token and handles its expiry, so callers never see a token
     * error they could not act on. Calls run on OkHttp's blocking call thread,
     * hence `runBlocking` around the app's suspending provider.
     */
    private fun userTokenInterceptor() = Interceptor { chain ->
        val manager = tokens ?: return@Interceptor chain.proceed(chain.request())

        val first = try {
            runBlocking { manager.get() }
        } catch (e: Exception) {
            reportAuthError("token_provider_failed", "tokenProvider failed", e)
            throw IOException("RiviumChat: tokenProvider failed", e)
        }
        var response = chain.proceed(chain.request().newBuilder().header(USER_TOKEN_HEADER, first).build())

        // An expired token is routine: fetch a new one and replay the request
        // once. The user never sees it.
        if (response.code == 401 && authErrorCode(response) == "token_expired") {
            val fresh = try {
                runBlocking { manager.refresh() }
            } catch (e: Exception) {
                reportAuthError("token_provider_failed", "tokenProvider failed", e)
                throw IOException("RiviumChat: tokenProvider failed", e)
            }
            response.close()
            response = chain.proceed(chain.request().newBuilder().header(USER_TOKEN_HEADER, fresh).build())
        }

        if (response.code == 401) {
            val code = authErrorCode(response)
            if (code != null) reportAuthError(code, authErrorMessage(response), null)
        }
        response
    }

    /** The identity error code (`token_*`) of a 401 body, if any. */
    private fun authErrorCode(response: Response): String? = try {
        val code = JSONObject(response.peekBody(PEEK_LIMIT).string()).stringOr("code", "")
        if (code.startsWith("token_")) code else null
    } catch (e: Exception) {
        null
    }

    private fun authErrorMessage(response: Response): String = try {
        JSONObject(response.peekBody(PEEK_LIMIT).string()).stringOr("message", "Authentication failed")
    } catch (e: Exception) {
        "Authentication failed"
    }

    private fun reportAuthError(code: String, message: String, error: Throwable?) {
        onAuthError?.invoke(AuthErrorEvent(code, message, error))
    }

    /** Forgets the cached user token (e.g. on logout). */
    fun clearUserToken() = tokens?.clear()

    /**
     * A user token for the realtime connection, when a tokenProvider is set.
     * A failing provider is reported as `token_provider_failed`, the same way
     * the REST calls report it, and then rethrown.
     */
    internal suspend fun userTokenOrNull(): String? {
        val manager = tokens ?: return null
        return try {
            manager.get()
        } catch (e: Exception) {
            reportAuthError("token_provider_failed", "tokenProvider failed", e)
            throw e
        }
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    internal companion object {
        private const val USER_TOKEN_HEADER = "x-user-token"
        private const val PEEK_LIMIT = 4096L

        /** Header that tells the API which SDK and version made a request. */
        const val SDK_HEADER = "X-Rivium-SDK"

        /** Its value: `android/<version>`. */
        const val SDK_HEADER_VALUE = "${RiviumChatConfig.SDK_NAME}/${RiviumChatConfig.SDK_VERSION}"

        /** The headers every request carries. */
        fun defaultHeadersInterceptor(apiKey: String) = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("x-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader(SDK_HEADER, SDK_HEADER_VALUE)
                .build()
            chain.proceed(request)
        }
    }

    // ─── Room Operations ─────────────────────────────────────────────────

    /**
     * Create a new room.
     */
    suspend fun createRoom(
        type: RoomType = RoomType.DIRECT,
        name: String? = null,
        participants: List<Map<String, Any>>,
        metadata: Map<String, Any>? = null
    ): Room = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("type", type.name.lowercase())
            name?.let { put("name", it) }
            put("participants", JSONArray(participants.map { JSONObject(it) }))
            metadata?.let { put("metadata", JSONObject(it)) }
        }

        val request = Request.Builder()
            .url(apiUrl("rooms"))
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RiviumChatException("Failed to create room: ${response.code}")
        }

        parseRoom(JSONObject(response.body!!.string()))
    }

    /**
     * Find or create a room by external ID.
     */
    suspend fun findOrCreateRoom(
        externalId: String,
        type: RoomType = RoomType.DIRECT,
        name: String? = null,
        participants: List<Map<String, Any>>,
        metadata: Map<String, Any>? = null
    ): Room = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("externalId", externalId)
            put("type", type.name.lowercase())
            name?.let { put("name", it) }
            put("participants", JSONArray(participants.map { JSONObject(it) }))
            metadata?.let { put("metadata", JSONObject(it)) }
        }

        val request = Request.Builder()
            .url(apiUrl("rooms", "find-or-create"))
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RiviumChatException("Failed to find or create room: ${response.code}")
        }

        val json = JSONObject(response.body!!.string())
        // API returns { room: {...}, created: bool }
        if (json.has("room")) {
            parseRoom(json.getJSONObject("room"))
        } else {
            parseRoom(json)
        }
    }

    /**
     * Get a room by its external ID.
     */
    suspend fun getRoomByExternalId(externalId: String): Room = withContext(Dispatchers.IO) {
        // External IDs are app-chosen ("Order #1001"): encode them as one path segment.
        val url = apiUrl("rooms", "by-external-id", externalId)
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RiviumChatException("Failed to get room by external ID: ${response.code}")
        }

        parseRoom(JSONObject(response.body!!.string()))
    }

    /**
     * List all rooms for a user.
     */
    suspend fun listRooms(userId: String): List<Room> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(apiUrl("rooms", query = listOf("userId" to userId)))
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RiviumChatException("Failed to list rooms: ${response.code}")
        }

        val json = JSONArray(response.body!!.string())
        (0 until json.length()).map { parseRoom(json.getJSONObject(it)) }
    }

    /**
     * Get a room by ID.
     */
    suspend fun getRoom(roomId: String): Room = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(apiUrl("rooms", roomId))
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RiviumChatException("Failed to get room: ${response.code}")
        }

        parseRoom(JSONObject(response.body!!.string()))
    }

    /**
     * Add a participant to a room.
     */
    suspend fun addParticipant(
        roomId: String,
        externalUserId: String,
        displayName: String? = null,
        locale: String? = null,
        role: ParticipantRole = ParticipantRole.MEMBER
    ): Participant = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("externalUserId", externalUserId)
            displayName?.let { put("displayName", it) }
            locale?.let { put("locale", it) }
            put("role", role.name.lowercase())
        }

        val request = Request.Builder()
            .url(apiUrl("rooms", roomId, "participants"))
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RiviumChatException("Failed to add participant: ${response.code}")
        }

        parseParticipant(JSONObject(response.body!!.string()))
    }

    // ─── Message Operations ──────────────────────────────────────────────

    /**
     * Send a message to a room.
     */
    suspend fun sendMessage(
        roomId: String,
        senderUserId: String,
        content: String,
        type: MessageType = MessageType.TEXT,
        attachments: List<Attachment>? = null,
        metadata: Map<String, Any>? = null,
        replyToId: String? = null,
        clientMessageId: String? = null
    ): Message = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("senderUserId", senderUserId)
            put("content", content)
            put("type", type.name.lowercase())
            attachments?.let {
                put("attachments", JSONArray(it.map { att ->
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
            clientMessageId?.let { put("clientMessageId", it) }
        }

        val request = Request.Builder()
            .url(apiUrl("rooms", roomId, "messages"))
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw RiviumChatException("Failed to send message: ${response.code}", null, response.code)
        }

        parseMessage(JSONObject(response.body!!.string()))
    }

    /**
     * Get messages for a room with pagination.
     */
    suspend fun getMessages(
        roomId: String,
        userId: String,
        limit: Int = 50,
        before: String? = null
    ): PaginatedMessages = withContext(Dispatchers.IO) {
        val url = apiUrl(
            "rooms", roomId, "messages",
            query = listOf("userId" to userId, "limit" to limit.toString(), "before" to before)
        )

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RiviumChatException("Failed to get messages: ${response.code}")
        }

        val json = JSONObject(response.body!!.string())
        val messages = json.getJSONArray("messages")
        PaginatedMessages(
            messages = (0 until messages.length()).map { parseMessage(messages.getJSONObject(it)) },
            hasMore = json.optBoolean("hasMore", false)
        )
    }

    /**
     * Mark messages in a room as read.
     */
    suspend fun markAsRead(roomId: String, userId: String): Unit = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("userId", userId)
        }

        val request = Request.Builder()
            .url(apiUrl("rooms", roomId, "read"))
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RiviumChatException("Failed to mark as read: ${response.code}")
        }
    }

    /**
     * Delete a message.
     */
    suspend fun deleteMessage(messageId: String, userId: String): Unit = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(apiUrl("messages", messageId, query = listOf("userId" to userId)))
            .delete()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RiviumChatException("Failed to delete message: ${response.code}")
        }
    }

    /**
     * Edit a message.
     */
    suspend fun editMessage(
        messageId: String,
        userId: String,
        content: String
    ): Message = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("userId", userId)
            put("content", content)
        }

        val request = Request.Builder()
            .url(apiUrl("messages", messageId))
            .put(body.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RiviumChatException("Failed to edit message: ${response.code}")
        }

        parseMessage(JSONObject(response.body!!.string()))
    }

    /**
     * Search messages in a room.
     */
    suspend fun searchMessages(
        roomId: String,
        userId: String,
        query: String,
        limit: Int = 20,
        offset: Int = 0
    ): List<Message> = searchMessagesWithTotal(roomId, userId, query, limit, offset).messages

    /** Searches messages in a room and reports how many match in total. */
    suspend fun searchMessagesWithTotal(
        roomId: String,
        userId: String,
        query: String,
        limit: Int = 20,
        offset: Int = 0
    ): SearchResult = withContext(Dispatchers.IO) {
        val url = searchUrl(roomId, userId, query, limit, offset)

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RiviumChatException("Failed to search messages: ${response.code}")
        }

        parseSearchResult(response.body!!.string(), offset)
    }

    /** The search URL; see [apiUrl] for the encoding. */
    internal fun searchUrl(roomId: String, userId: String, query: String, limit: Int, offset: Int): HttpUrl =
        apiUrl(
            "rooms", roomId, "messages", "search",
            query = listOf("userId" to userId, "q" to query, "limit" to limit.toString(), "offset" to offset.toString())
        )

    /** Search answers `{ messages, total }`; a bare array is accepted too. */
    internal fun parseSearchResponse(body: String): List<Message> = parseSearchResult(body, 0).messages

    /** The search answer with its total; a bare array counts its own length. */
    internal fun parseSearchResult(body: String, offset: Int): SearchResult {
        val json = JSONTokener(body).nextValue()
        val array = when (json) {
            is JSONObject -> json.optJSONArray("messages") ?: JSONArray()
            is JSONArray -> json
            else -> JSONArray()
        }
        val messages = (0 until array.length()).map { parseMessage(array.getJSONObject(it)) }
        val total = (json as? JSONObject)?.takeIf { it.has("total") && !it.isNull("total") }
            ?.optInt("total", messages.size) ?: messages.size
        return SearchResult(messages = messages, total = total, hasMore = offset + messages.size < total)
    }

    // ─── Unread Summary ──────────────────────────────────────────────────

    /**
     * Get unread summary for all rooms.
     */
    suspend fun getUnreadSummary(userId: String): UnreadSummary = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(apiUrl("rooms", "unread-summary", query = listOf("userId" to userId)))
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RiviumChatException("Failed to get unread summary: ${response.code}")
        }

        val json = JSONObject(response.body!!.string())
        val rooms = json.getJSONArray("rooms")
        UnreadSummary(
            totalUnread = json.getInt("totalUnread"),
            rooms = (0 until rooms.length()).map { i ->
                val room = rooms.getJSONObject(i)
                RoomUnread(
                    roomId = room.getString("roomId"),
                    externalId = room.stringOrNull("externalId"),
                    unreadCount = room.getInt("unreadCount")
                )
            }
        )
    }

    // ─── Reactions ───────────────────────────────────────────────────────

    /**
     * Add a reaction to a message.
     */
    suspend fun addReaction(
        messageId: String,
        userId: String,
        emoji: String
    ): Reaction = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("userId", userId)
            put("emoji", emoji)
        }

        val request = Request.Builder()
            .url(apiUrl("messages", messageId, "reactions"))
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RiviumChatException("Failed to add reaction: ${response.code}")
        }

        parseReaction(JSONObject(response.body!!.string()))
    }

    /**
     * Remove a reaction from a message.
     */
    suspend fun removeReaction(
        messageId: String,
        userId: String,
        emoji: String
    ): Unit = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("userId", userId)
            put("emoji", emoji)
        }

        val request = Request.Builder()
            .url(apiUrl("messages", messageId, "reactions"))
            .delete(body.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RiviumChatException("Failed to remove reaction: ${response.code}")
        }
    }

    /**
     * Get all reactions for a message.
     */
    suspend fun getReactions(messageId: String): List<Reaction> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(apiUrl("messages", messageId, "reactions"))
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RiviumChatException("Failed to get reactions: ${response.code}")
        }

        val json = JSONArray(response.body!!.string())
        (0 until json.length()).map { parseReaction(json.getJSONObject(it)) }
    }

    // ─── Pin Operations ─────────────────────────────────────────────────

    /**
     * Pin a message.
     */
    suspend fun pinMessage(messageId: String, userId: String): Message = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("userId", userId)
        }

        val request = Request.Builder()
            .url(apiUrl("messages", messageId, "pin"))
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RiviumChatException("Failed to pin message: ${response.code}")
        }

        parseMessage(JSONObject(response.body!!.string()))
    }

    /**
     * Unpin a message.
     */
    suspend fun unpinMessage(messageId: String, userId: String): Unit = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("userId", userId)
        }

        val request = Request.Builder()
            .url(apiUrl("messages", messageId, "pin"))
            .delete(body.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RiviumChatException("Failed to unpin message: ${response.code}")
        }
    }

    /**
     * Get pinned messages in a room.
     */
    suspend fun getPinnedMessages(roomId: String): List<Message> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(apiUrl("rooms", roomId, "pinned"))
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RiviumChatException("Failed to get pinned messages: ${response.code}")
        }

        val json = JSONArray(response.body!!.string())
        (0 until json.length()).map { parseMessage(json.getJSONObject(it)) }
    }

    // ─── Mentions ───────────────────────────────────────────────────────

    /**
     * Get messages where a user was mentioned.
     */
    suspend fun getMentions(
        roomId: String,
        userId: String,
        limit: Int = 20,
        offset: Int = 0
    ): List<Message> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(apiUrl("rooms", roomId, "mentions", query = listOf("userId" to userId, "limit" to limit.toString(), "offset" to offset.toString())))
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RiviumChatException("Failed to get mentions: ${response.code}")
        }

        parseMentionsResponse(response.body!!.string())
    }

    /**
     * Mentions answer a list of mention records, `{ id, messageId,
     * mentionedUserId, roomId, createdAt, message }`; the mentioning message
     * is returned. A record without its message is skipped.
     */
    internal fun parseMentionsResponse(body: String): List<Message> {
        val records = JSONArray(body)
        return (0 until records.length()).mapNotNull { i ->
            val record = records.getJSONObject(i)
            val message = record.optJSONObject("message")
            when {
                message != null -> parseMessage(message)
                // A message itself rather than a mention record.
                record.has("senderUserId") -> parseMessage(record)
                else -> null
            }
        }
    }

    // ─── Centrifugo Token ──────────────────────────────────────────────────

    /**
     * Get a Centrifugo connection token from the server.
     */
    fun getCentrifugoToken(userId: String, info: Map<String, Any>? = null): String {
        val body = JSONObject().apply {
            put("userId", userId)
            info?.let { put("info", JSONObject(it)) }
        }

        val request = Request.Builder()
            .url(apiUrl("centrifugo", "token"))
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RiviumChatException("Failed to get Centrifugo token: ${response.code}")
        }

        val json = JSONObject(response.body!!.string())
        return json.getString("token")
    }

    // ─── Helper Methods ──────────────────────────────────────────────────

    fun parseRoom(json: JSONObject): Room {
        val participants = json.optJSONArray("participants")?.let { arr ->
            (0 until arr.length()).map { parseParticipant(arr.getJSONObject(it)) }
        } ?: emptyList()

        val metadata = json.optJSONObject("metadata")?.stringMap()

        return Room(
            id = json.getString("id"),
            type = RoomType.valueOf(json.stringOr("type", "direct").uppercase()),
            externalId = json.stringOrNull("externalId"),
            name = json.stringOrNull("name"),
            metadata = metadata,
            isActive = json.optBoolean("isActive", true),
            createdAt = json.stringOrNull("createdAt"),
            updatedAt = json.stringOrNull("updatedAt"),
            participants = participants,
            // Sent by GET /rooms. A preview that cannot be read must not
            // fail the whole room list.
            lastMessage = json.optJSONObject("lastMessage")?.let {
                try {
                    parseMessage(it)
                } catch (e: Exception) {
                    null
                }
            },
            unreadCount = json.optInt("unreadCount", 0)
        )
    }

    fun parseParticipant(json: JSONObject): Participant {
        return Participant(
            id = json.getString("id"),
            externalUserId = json.getString("externalUserId"),
            displayName = json.stringOrNull("displayName"),
            locale = json.stringOrNull("locale"),
            role = ParticipantRole.valueOf(json.stringOr("role", "member").uppercase()),
            lastReadAt = json.stringOrNull("lastReadAt"),
            joinedAt = json.getString("joinedAt")
        )
    }

    fun parseMessage(json: JSONObject): Message = Message.fromJson(json)

    private fun parseAttachment(json: JSONObject): Attachment {
        return Attachment(
            url = json.getString("url"),
            mimeType = json.stringOrNull("mimeType"),
            name = json.stringOrNull("name"),
            size = json.intOrNull("size")
        )
    }

    fun parseReaction(json: JSONObject): Reaction {
        return Reaction(
            id = json.getString("id"),
            messageId = json.getString("messageId"),
            userId = json.getString("userId"),
            emoji = json.getString("emoji"),
            createdAt = json.getString("createdAt")
        )
    }

    /**
     * Disposes resources.
     */
    fun dispose() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}

/** Exception thrown by RiviumChat SDK operations. */
class RiviumChatException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The HTTP status the API answered with, when the error came from an answer. */
    var statusCode: Int? = null
        private set

    constructor(message: String, cause: Throwable?, statusCode: Int?) : this(message, cause) {
        this.statusCode = statusCode
    }
}
