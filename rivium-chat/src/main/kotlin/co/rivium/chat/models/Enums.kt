package co.rivium.chat.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Message content types supported by RiviumChat. */
@Serializable
enum class MessageType {
    @SerialName("text") TEXT,
    @SerialName("image") IMAGE,
    @SerialName("file") FILE,
    @SerialName("system") SYSTEM
}

/** Room types for conversations. */
@Serializable
enum class RoomType {
    @SerialName("direct") DIRECT,
    @SerialName("group") GROUP
}

/** Participant roles within a room. */
@Serializable
enum class ParticipantRole {
    @SerialName("admin") ADMIN,
    @SerialName("member") MEMBER
}

/** Connection states for the realtime service. */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/** Subscription states for room channels. */
enum class SubscriptionStatus {
    SUBSCRIBING,
    SUBSCRIBED,
    UNSUBSCRIBED,

    /** The server rejected the subscribe, or it failed in transit. */
    ERROR
}
