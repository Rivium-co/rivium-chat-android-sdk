package co.rivium.chat.services

import co.rivium.chat.RiviumChatConfig
import co.rivium.chat.events.*
import co.rivium.chat.models.*
import co.rivium.chat.models.SubscriptionStatus
import io.github.centrifugal.centrifuge.Client
import io.github.centrifugal.centrifuge.ConnectedEvent
import io.github.centrifugal.centrifuge.ConnectingEvent
import io.github.centrifugal.centrifuge.DisconnectedEvent
import io.github.centrifugal.centrifuge.ErrorEvent
import io.github.centrifugal.centrifuge.EventListener
import io.github.centrifugal.centrifuge.JoinEvent
import io.github.centrifugal.centrifuge.LeaveEvent
import io.github.centrifugal.centrifuge.Options
import io.github.centrifugal.centrifuge.PublicationEvent
import io.github.centrifugal.centrifuge.PublishResult
import io.github.centrifugal.centrifuge.ResultCallback
import io.github.centrifugal.centrifuge.Subscription
import io.github.centrifugal.centrifuge.SubscribedEvent
import io.github.centrifugal.centrifuge.SubscribingEvent
import io.github.centrifugal.centrifuge.SubscriptionErrorEvent
import io.github.centrifugal.centrifuge.SubscriptionEventListener
import io.github.centrifugal.centrifuge.UnsubscribedEvent
import io.github.centrifugal.centrifuge.PresenceResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant

/**
 * Realtime service for RiviumChat using Centrifugo.
 * Handles WebSocket connections, subscriptions, and event streaming.
 */
class RealtimeService(
    private val config: RiviumChatConfig,
    private val getToken: () -> String
) {

    private var client: Client? = null
    private val subscriptions = mutableMapOf<String, Subscription>()

    /**
     * Channels confirmed subscribed by the server. A channel in [subscriptions]
     * but missing here was created and never confirmed — it must not be treated
     * as usable, or a rejected subscribe leaves the room silently realtime-dead.
     */
    private val subscribedChannels = mutableSetOf<String>()

    /**
     * Guards [subscriptions] and [subscribedChannels].
     *
     * Subscribe and unsubscribe hand a channel name back and forth with
     * Centrifuge's own registry. A screen that leaves a room and rejoins it
     * across a lifecycle event drives both from different threads, and
     * interleaving them leaves the two registries disagreeing — which surfaces
     * as "Subscription to a channel already exists in client's internal
     * registry".
     */
    private val channelLock = Any()
    private val pendingRoomSubscriptions = mutableListOf<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Connection state
    private var _currentConnectionState = ConnectionState.DISCONNECTED
    val currentConnectionState: ConnectionState get() = _currentConnectionState

    // Event flows
    private val _connectionState = MutableSharedFlow<ConnectionState>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val connectionState: SharedFlow<ConnectionState> = _connectionState.asSharedFlow()

    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 100)
    val messages: SharedFlow<Message> = _messages.asSharedFlow()

    private val _readReceipts = MutableSharedFlow<ReadReceipt>(extraBufferCapacity = 100)
    val readReceipts: SharedFlow<ReadReceipt> = _readReceipts.asSharedFlow()

    private val _typing = MutableSharedFlow<TypingEvent>(extraBufferCapacity = 100)
    val typing: SharedFlow<TypingEvent> = _typing.asSharedFlow()

    private val _presence = MutableSharedFlow<PresenceEvent>(extraBufferCapacity = 100)
    val presence: SharedFlow<PresenceEvent> = _presence.asSharedFlow()

    private val _reactions = MutableSharedFlow<ReactionEvent>(extraBufferCapacity = 100)
    val reactions: SharedFlow<ReactionEvent> = _reactions.asSharedFlow()

    private val _messageDeletions = MutableSharedFlow<MessageDeletion>(extraBufferCapacity = 100)
    val messageDeletions: SharedFlow<MessageDeletion> = _messageDeletions.asSharedFlow()

    private val _messageEdits = MutableSharedFlow<MessageEditEvent>(extraBufferCapacity = 100)
    val messageEdits: SharedFlow<MessageEditEvent> = _messageEdits.asSharedFlow()

    private val _messagePins = MutableSharedFlow<MessagePinEvent>(extraBufferCapacity = 100)
    val messagePins: SharedFlow<MessagePinEvent> = _messagePins.asSharedFlow()

    private val _connectionError = MutableSharedFlow<ConnectionErrorEvent>(extraBufferCapacity = 100)
    val connectionError: SharedFlow<ConnectionErrorEvent> = _connectionError.asSharedFlow()

    private val _subscriptionState = MutableSharedFlow<SubscriptionStateEvent>(extraBufferCapacity = 100)
    val subscriptionState: SharedFlow<SubscriptionStateEvent> = _subscriptionState.asSharedFlow()

    private val _recoveryFailed = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val recoveryFailed: SharedFlow<String> = _recoveryFailed.asSharedFlow()

    private fun updateState(state: ConnectionState) {
        _currentConnectionState = state
        scope.launch { _connectionState.emit(state) }
    }

    /**
     * Connect to the Centrifugo server.
     */
    fun connect() {
        if (client != null) return

        scope.launch {
            try {
                updateState(ConnectionState.CONNECTING)

                val token = getToken()
                val options = Options()
                options.token = token

                val centrifugeClient = Client(RiviumChatConfig.CENTRIFUGO_URL, options, object : EventListener() {
                    override fun onConnecting(client: Client, event: ConnectingEvent) {
                        updateState(ConnectionState.CONNECTING)
                    }

                    override fun onConnected(client: Client, event: ConnectedEvent) {
                        updateState(ConnectionState.CONNECTED)
                        // Process any pending room subscriptions
                        val pending = pendingRoomSubscriptions.toList()
                        pendingRoomSubscriptions.clear()
                        pending.forEach { roomId -> subscribeToRoom(roomId) }
                    }

                    override fun onDisconnected(client: Client, event: DisconnectedEvent) {
                        updateState(ConnectionState.DISCONNECTED)
                        if (event.reason.isNotEmpty()) {
                            scope.launch {
                                _connectionError.emit(ConnectionErrorEvent(error = "Disconnected: ${event.reason}"))
                            }
                        }
                    }

                    override fun onError(client: Client, event: ErrorEvent) {
                        updateState(ConnectionState.ERROR)
                        scope.launch {
                            _connectionError.emit(ConnectionErrorEvent(error = event.error.message ?: "Unknown error"))
                        }
                    }
                })

                client = centrifugeClient
                centrifugeClient.connect()
            } catch (e: Exception) {
                updateState(ConnectionState.ERROR)
                _connectionError.emit(ConnectionErrorEvent(error = e))
            }
        }
    }

    /**
     * Disconnect from the Centrifugo server.
     */
    /**
     * Tears one channel down.
     *
     * Centrifuge's registry is cleared **before** unsubscribing. The old order
     * — drop our entry, unsubscribe, then free Centrifuge's — left a window
     * where our map said the channel was free while Centrifuge still held the
     * name, so a re-subscribe arriving in that window threw "already exists".
     */
    private fun teardownChannel(channel: String) {
        synchronized(channelLock) {
            val sub = subscriptions.remove(channel)
            subscribedChannels.remove(channel)
            if (sub != null) {
                client?.removeSubscription(sub)
                try {
                    sub.unsubscribe()
                } catch (_: Exception) {
                    // Socket may already be gone; freeing the registry above is
                    // the part that must not be skipped.
                }
            }
        }
    }

    /**
     * Returns true when the caller should build a new subscription for
     * [channel]. A stale, never-confirmed subscription is torn down first —
     * returning early on it is what made subscribeToRoom report success while
     * the channel stayed dead.
     */
    private fun shouldSubscribe(channel: String): Boolean {
        synchronized(channelLock) {
            if (subscribedChannels.contains(channel)) return false
            if (subscriptions.containsKey(channel)) {
                teardownChannel(channel)
            }
            return true
        }
    }

    /** Whether every channel of [roomId] is currently subscribed. */
    fun isRoomSubscribed(roomId: String): Boolean = synchronized(channelLock) {
        subscribedChannels.containsAll(
            listOf("chat:room_$roomId", "presence:room_$roomId", "typing:room_$roomId")
        )
    }

    /** Channels of [roomId] currently subscribed. */
    fun subscribedChannelsFor(roomId: String): Set<String> = synchronized(channelLock) {
        listOf("chat", "presence", "typing")
            .map { "$it:room_$roomId" }
            .filter { subscribedChannels.contains(it) }
            .toSet()
    }

    fun disconnect() {
        // Free Centrifuge's registry as well as our own map. Clearing only our
        // map left every channel name held by the client, so each one threw
        // "already exists" on the next subscribe for the life of the process.
        synchronized(channelLock) {
            subscriptions.keys.toList().forEach { teardownChannel(it) }
            subscriptions.clear()
            subscribedChannels.clear()
        }
        client?.disconnect()
        client = null
        updateState(ConnectionState.DISCONNECTED)
    }

    /**
     * Drops the current connection and opens a new one.
     *
     * [connect] returns early while a client object exists, leaving a host no
     * way to recover a socket it believes is stale after a long background.
     */
    fun reconnect() {
        disconnect()
        connect()
    }

    /**
     * Subscribe to a room's channels (chat, typing, presence).
     * If not yet connected, queues the subscription for when connection is ready.
     */
    fun subscribeToRoom(roomId: String) {
        val client = this.client
        if (client == null) {
            if (!pendingRoomSubscriptions.contains(roomId)) {
                pendingRoomSubscriptions.add(roomId)
            }
            return
        }

        subscribeToChatChannel(client, roomId)
        subscribeToPresenceChannel(client, roomId)
        subscribeToTypingChannel(client, roomId)
    }

    /**
     * Subscribe only to a room's chat channel (messages, read receipts) without presence/typing.
     * Used for unread badge updates without appearing online.
     */
    fun observeRoom(roomId: String) {
        val client = this.client ?: return
        subscribeToChatChannel(client, roomId)
    }

    /**
     * Unsubscribe from a room's channels.
     */
    fun unsubscribeFromRoom(roomId: String) {
        listOf("chat:room_$roomId", "typing:room_$roomId", "presence:room_$roomId")
            .forEach { teardownChannel(it) }
    }

    /**
     * Leave presence and typing channels but keep chat channel for unread updates.
     */
    fun leaveRoom(roomId: String) {
        listOf("typing:room_$roomId", "presence:room_$roomId")
            .forEach { teardownChannel(it) }
    }

    /**
     * Gets currently online users in a room via Centrifugo presence.
     */
    fun getRoomPresence(roomId: String): Set<String> {
        val channel = "presence:room_$roomId"
        val sub = subscriptions[channel] ?: return emptySet()

        val future = java.util.concurrent.CompletableFuture<Set<String>>()
        sub.presence { error, result ->
            if (error != null || result == null) {
                future.complete(emptySet())
            } else {
                future.complete(
                    result.clients.values
                        .map { it.user }
                        .filter { it.isNotEmpty() }
                        .toSet()
                )
            }
        }

        return try {
            future.get(5, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            emptySet()
        }
    }

    private var lastTypingTime: Long = 0

    /**
     * Publish typing indicator via realtime channel (throttled to 2 seconds).
     */
    fun publishTyping(roomId: String) {
        val now = System.currentTimeMillis()
        if (now - lastTypingTime < 2000) return
        lastTypingTime = now

        val channel = "typing:room_$roomId"
        val subscription = subscriptions[channel] ?: return

        val data = JSONObject().apply {
            put("userId", config.userId)
            put("isTyping", true)
        }

        try {
            subscription.publish(data.toString().toByteArray(), object : ResultCallback<PublishResult> {
                override fun onDone(error: Throwable?, result: PublishResult?) {
                    // Ignore publish result
                }
            })
        } catch (e: Exception) {
            // Ignore publish errors
        }
    }

    private fun subscribeToChatChannel(client: Client, roomId: String) {
        val channel = "chat:room_$roomId"
        if (!shouldSubscribe(channel)) return

        val subscription = client.newSubscription(channel, object : SubscriptionEventListener() {
            override fun onPublication(sub: Subscription, event: PublicationEvent) {
                handleChatPublication(roomId, event.data)
            }

            override fun onSubscribing(sub: Subscription, event: SubscribingEvent) {
                scope.launch {
                    _subscriptionState.emit(
                        SubscriptionStateEvent(
                            roomId = roomId,
                            channel = channel,
                            status = SubscriptionStatus.SUBSCRIBING
                        )
                    )
                }
            }

            override fun onSubscribed(sub: Subscription, event: SubscribedEvent) {
                synchronized(channelLock) { subscribedChannels.add(channel) }
                scope.launch {
                    _subscriptionState.emit(
                        SubscriptionStateEvent(
                            roomId = roomId,
                            channel = channel,
                            status = SubscriptionStatus.SUBSCRIBED
                        )
                    )

                    // Check for recovery failure
                    if (event.wasRecovering() == true && event.recovered != true) {
                        _recoveryFailed.emit(roomId)
                    }
                }
            }

            override fun onUnsubscribed(sub: Subscription, event: UnsubscribedEvent) {
                synchronized(channelLock) { subscribedChannels.remove(channel) }
                scope.launch {
                    _subscriptionState.emit(
                        SubscriptionStateEvent(
                            roomId = roomId,
                            channel = channel,
                            status = SubscriptionStatus.UNSUBSCRIBED,
                            code = event.code,
                            reason = event.reason
                        )
                    )
                }
            }

            override fun onError(sub: Subscription, event: SubscriptionErrorEvent) {
                // Drop the dead subscription so the next subscribeToRoom builds
                // a fresh one instead of short-circuiting on a stale entry.
                synchronized(channelLock) {
                    subscribedChannels.remove(channel)
                    subscriptions.remove(channel)?.let { client?.removeSubscription(it) }
                }
                scope.launch {
                    _subscriptionState.emit(
                        SubscriptionStateEvent(
                            roomId = roomId,
                            channel = channel,
                            status = SubscriptionStatus.ERROR,
                            reason = event.error.message
                        )
                    )
                    _connectionError.emit(
                        ConnectionErrorEvent(error = "Subscription failed on $channel: ${event.error.message}")
                    )
                    _recoveryFailed.emit(roomId)
                }
            }
        })

        synchronized(channelLock) { subscriptions[channel] = subscription }
        subscription.subscribe()
    }

    private fun subscribeToPresenceChannel(client: Client, roomId: String) {
        val channel = "presence:room_$roomId"
        if (!shouldSubscribe(channel)) return

        val subscription = client.newSubscription(channel, object : SubscriptionEventListener() {
            override fun onSubscribed(sub: Subscription, event: SubscribedEvent) {
                synchronized(channelLock) { subscribedChannels.add(channel) }
                // Query current presence and emit events for all online users.
                // This ensures we detect users who joined before we subscribed.
                sub.presence { error, result ->
                    if (error == null && result != null) {
                        result.clients.values.forEach { info ->
                            if (info.user.isNotEmpty()) {
                                scope.launch {
                                    _presence.emit(PresenceEvent(roomId, info.user, true))
                                }
                            }
                        }
                    }
                }
            }

            override fun onJoin(sub: Subscription, event: JoinEvent) {
                val userId = event.info.user
                if (userId.isNotEmpty()) {
                    scope.launch {
                        _presence.emit(PresenceEvent(roomId, userId, true))
                    }
                }
            }


            override fun onUnsubscribed(sub: Subscription, event: UnsubscribedEvent) {
                synchronized(channelLock) { subscribedChannels.remove(channel) }
            }

            // Without this a rejected subscribe was invisible: the channel
            // never went live, nothing was raised, and the room looked fine
            // while presence/typing silently never arrived.
            override fun onError(sub: Subscription, event: SubscriptionErrorEvent) {
                synchronized(channelLock) {
                    subscribedChannels.remove(channel)
                    subscriptions.remove(channel)?.let { client?.removeSubscription(it) }
                }
                scope.launch {
                    _subscriptionState.emit(
                        SubscriptionStateEvent(
                            roomId = roomId,
                            channel = channel,
                            status = SubscriptionStatus.ERROR,
                            reason = event.error.message
                        )
                    )
                    _connectionError.emit(
                        ConnectionErrorEvent(error = "Subscription failed on $channel: ${event.error.message}")
                    )
                }
            }

            override fun onLeave(sub: Subscription, event: LeaveEvent) {
                val userId = event.info.user
                if (userId.isNotEmpty()) {
                    scope.launch {
                        _presence.emit(PresenceEvent(roomId, userId, false))
                    }
                }
            }
        })

        synchronized(channelLock) { subscriptions[channel] = subscription }
        subscription.subscribe()
    }

    private fun subscribeToTypingChannel(client: Client, roomId: String) {
        val channel = "typing:room_$roomId"
        if (!shouldSubscribe(channel)) return

        val subscription = client.newSubscription(channel, object : SubscriptionEventListener() {
            override fun onPublication(sub: Subscription, event: PublicationEvent) {
                handleTypingPublication(roomId, event.data)
            }

            override fun onSubscribed(sub: Subscription, event: SubscribedEvent) {
                synchronized(channelLock) { subscribedChannels.add(channel) }
            }

            override fun onUnsubscribed(sub: Subscription, event: UnsubscribedEvent) {
                synchronized(channelLock) { subscribedChannels.remove(channel) }
            }

            // Without this a rejected subscribe was invisible: the channel
            // never went live, nothing was raised, and the room looked fine
            // while presence/typing silently never arrived.
            override fun onError(sub: Subscription, event: SubscriptionErrorEvent) {
                synchronized(channelLock) {
                    subscribedChannels.remove(channel)
                    subscriptions.remove(channel)?.let { client?.removeSubscription(it) }
                }
                scope.launch {
                    _subscriptionState.emit(
                        SubscriptionStateEvent(
                            roomId = roomId,
                            channel = channel,
                            status = SubscriptionStatus.ERROR,
                            reason = event.error.message
                        )
                    )
                    _connectionError.emit(
                        ConnectionErrorEvent(error = "Subscription failed on $channel: ${event.error.message}")
                    )
                }
            }

        })

        synchronized(channelLock) { subscriptions[channel] = subscription }
        subscription.subscribe()
    }

    private fun handleChatPublication(roomId: String, data: ByteArray) {
        try {
            val json = JSONObject(String(data))
            // Match Flutter: check "event" first, fall back to "type"
            val eventType = if (json.has("event")) json.optString("event", null)
                            else json.optString("type", null)

            // Match Flutter: payload is in "data" sub-object, or root JSON as fallback
            val payload = if (json.has("data") && json.optJSONObject("data") != null)
                              json.getJSONObject("data")
                          else json

            scope.launch {
                when (eventType) {
                    "message" -> {
                        _messages.emit(Message.fromJson(payload))
                    }
                    "read" -> {
                        _readReceipts.emit(ReadReceipt.fromJson(payload))
                    }
                    "deleted" -> {
                        _messageDeletions.emit(MessageDeletion.fromJson(payload))
                    }
                    "message_edited" -> {
                        _messageEdits.emit(
                            MessageEditEvent(
                                messageId = payload.getString("messageId"),
                                roomId = payload.optString("roomId", null) ?: roomId,
                                content = payload.getString("content"),
                                editedBy = payload.getString("editedBy"),
                                editedAt = Instant.parse(payload.getString("editedAt"))
                            )
                        )
                    }
                    "reaction_added" -> {
                        _reactions.emit(
                            ReactionEvent(
                                messageId = payload.getString("messageId"),
                                roomId = payload.optString("roomId", null) ?: roomId,
                                userId = payload.getString("userId"),
                                emoji = payload.getString("emoji"),
                                added = true,
                                reactionId = payload.optString("reactionId", null)
                            )
                        )
                    }
                    "reaction_removed" -> {
                        _reactions.emit(
                            ReactionEvent(
                                messageId = payload.getString("messageId"),
                                roomId = payload.optString("roomId", null) ?: roomId,
                                userId = payload.getString("userId"),
                                emoji = payload.getString("emoji"),
                                added = false
                            )
                        )
                    }
                    "message_pinned" -> {
                        _messagePins.emit(
                            MessagePinEvent(
                                messageId = payload.getString("messageId"),
                                roomId = payload.optString("roomId", null) ?: roomId,
                                userId = payload.getString("pinnedBy"),
                                pinned = true
                            )
                        )
                    }
                    "message_unpinned" -> {
                        _messagePins.emit(
                            MessagePinEvent(
                                messageId = payload.getString("messageId"),
                                roomId = payload.optString("roomId", null) ?: roomId,
                                userId = payload.getString("unpinnedBy"),
                                pinned = false
                            )
                        )
                    }
                    else -> {
                        // Fallback: if no event type, try parsing as a direct message
                        if (payload.has("id") && payload.has("content")) {
                            _messages.emit(Message.fromJson(payload))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore parsing errors
        }
    }

    private fun handleTypingPublication(roomId: String, data: ByteArray) {
        try {
            val json = JSONObject(String(data))
            val userId = json.getString("userId")
            if (userId != config.userId) {
                scope.launch {
                    _typing.emit(
                        TypingEvent(
                            roomId = roomId,
                            userId = userId,
                            isTyping = json.optBoolean("isTyping", true)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Ignore parsing errors
        }
    }

    /**
     * Disposes all resources.
     */
    fun dispose() {
        disconnect()
    }
}
