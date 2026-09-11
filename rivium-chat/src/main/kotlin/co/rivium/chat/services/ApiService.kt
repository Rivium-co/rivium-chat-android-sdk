package co.rivium.chat.services

import co.rivium.chat.RiviumChatConfig
import co.rivium.chat.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import co.rivium.chat.events.AuthErrorEvent
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * HTTP API service for RiviumChat backend.
 */
class ApiService(
    private val config: RiviumChatConfig,
    private val onAuthError: ((AuthErrorEvent) -> Unit)? = null
) {

    private val tokens: TokenManager? = config.tokenProvider?.let { TokenManager(it) }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("x-api-key", config.apiKey)
                .addHeader("Content-Type", "application/json")
                .build()
            chain.proceed(request)
        }
        .addInterceptor(userTokenInterceptor())
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
        val code = JSONObject(response.peekBody(PEEK_LIMIT).string()).optString("code")
        if (code.startsWith("token_")) code else null
    } catch (e: Exception) {
        null
    }

    private fun authErrorMessage(response: Response): String = try {
        JSONObject(response.peekBody(PEEK_LIMIT).string()).optString("message", "Authentication failed")
    } catch (e: Exception) {
        "Authentication failed"
    }

    private fun reportAuthError(code: String, message: String, error: Throwable?) {
        onAuthError?.invoke(AuthErrorEvent(code, message, error))
    }

    /** Forgets the cached user token (e.g. on logout). */
    fun clearUserToken() = tokens?.clear()

    /** A user token for the realtime connection, when a tokenProvider is set. */
    internal suspend fun userTokenOrNull(): String? = tokens?.get()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private companion object {
        const val USER_TOKEN_HEADER = "x-user-token"
        const val PEEK_LIMIT = 4096L
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
            .url("${RiviumChatConfig.BASE_URL}/api/v1/rooms")
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
            .url("${RiviumChatConfig.BASE_URL}/api/v1/rooms/find-or-create")
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
        val request = Request.Builder()
            .url("${RiviumChatConfig.BASE_URL}/api/v1/rooms/by-external-id/$externalId")
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
            .url("${RiviumChatConfig.BASE_URL}/api/v1/rooms?userId=$userId")
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
            .url("${RiviumChatConfig.BASE_URL}/api/v1/rooms/$roomId")
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
            .url("${RiviumChatConfig.BASE_URL}/api/v1/rooms/$roomId/participants")
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
        replyToId: String? = null
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
        }

        val request = Request.Builder()
            .url("${RiviumChatConfig.BASE_URL}/api/v1/rooms/$roomId/messages")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RiviumChatException("Failed to send message: ${response.code}")
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
        val url = StringBuilder("${RiviumChatConfig.BASE_URL}/api/v1/rooms/$roomId/messages?userId=$userId&limit=$limit")
        before?.let { url.append("&before=$it") }

        val request = Request.Builder()
            .url(url.toString())
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
            .url("${RiviumChatConfig.BASE_URL}/api/v1/rooms/$roomId/read")
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
            .url("${RiviumChatConfig.BASE_URL}/api/v1/messages/$messageId?userId=$userId")
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
            .url("${RiviumChatConfig.BASE_URL}/api/v1/messages/$messageId")
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
    ): List<Message> = withContext(Dispatchers.IO) {
        val url = "${RiviumChatConfig.BASE_URL}/api/v1/rooms/$roomId/messages/search?userId=$userId&q=$query&limit=$limit&offset=$offset"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RiviumChatException("Failed to search messages: ${response.code}")
        }

        val json = JSONArray(response.body!!.string())
        (0 until json.length()).map { parseMessage(json.getJSONObject(it)) }
    }

    // ─── Unread Summary ──────────────────────────────────────────────────

    /**
     * Get unread summary for all rooms.
     */
    suspend fun getUnreadSummary(userId: String): UnreadSummary = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${RiviumChatConfig.BASE_URL}/api/v1/rooms/unread-summary?userId=$userId")
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
                    externalId = room.optString("externalId", null),
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
            .url("${RiviumChatConfig.BASE_URL}/api/v1/messages/$messageId/reactions")
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
            .url("${RiviumChatConfig.BASE_URL}/api/v1/messages/$messageId/reactions")
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
            .url("${RiviumChatConfig.BASE_URL}/api/v1/messages/$messageId/reactions")
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
            .url("${RiviumChatConfig.BASE_URL}/api/v1/messages/$messageId/pin")
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
            .url("${RiviumChatConfig.BASE_URL}/api/v1/messages/$messageId/pin")
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
            .url("${RiviumChatConfig.BASE_URL}/api/v1/rooms/$roomId/pinned")
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
            .url("${RiviumChatConfig.BASE_URL}/api/v1/rooms/$roomId/mentions?userId=$userId&limit=$limit&offset=$offset")
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RiviumChatException("Failed to get mentions: ${response.code}")
        }

        val json = JSONArray(response.body!!.string())
        (0 until json.length()).map { parseMessage(json.getJSONObject(it)) }
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
            .url("${RiviumChatConfig.BASE_URL}/api/v1/centrifugo/token")
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

        val metadata = json.optJSONObject("metadata")?.let { obj ->
            val map = mutableMapOf<String, String>()
            obj.keys().forEach { key -> map[key] = obj.optString(key) }
            map
        }

        return Room(
            id = json.getString("id"),
            type = RoomType.valueOf(json.optString("type", "direct").uppercase()),
            externalId = json.optString("externalId", null),
            name = json.optString("name", null),
            metadata = metadata,
            isActive = json.optBoolean("isActive", true),
            createdAt = json.optString("createdAt", null),
            updatedAt = json.optString("updatedAt", null),
            participants = participants
        )
    }

    fun parseParticipant(json: JSONObject): Participant {
        return Participant(
            id = json.getString("id"),
            externalUserId = json.getString("externalUserId"),
            displayName = json.optString("displayName", null),
            locale = json.optString("locale", null),
            role = ParticipantRole.valueOf(json.optString("role", "member").uppercase()),
            lastReadAt = json.optString("lastReadAt", null),
            joinedAt = json.getString("joinedAt")
        )
    }

    fun parseMessage(json: JSONObject): Message {
        val attachments = json.optJSONArray("attachments")?.let { arr ->
            (0 until arr.length()).map { parseAttachment(arr.getJSONObject(it)) }
        }

        val reactions = json.optJSONArray("reactions")?.let { arr ->
            (0 until arr.length()).map { parseReaction(arr.getJSONObject(it)) }
        }

        val replyTo = json.optJSONObject("replyTo")?.let { parseMessage(it) }

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

    private fun parseAttachment(json: JSONObject): Attachment {
        return Attachment(
            url = json.getString("url"),
            mimeType = json.optString("mimeType", null),
            name = json.optString("name", null),
            size = if (json.has("size")) json.getInt("size") else null
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
class RiviumChatException(message: String, cause: Throwable? = null) : Exception(message, cause)
