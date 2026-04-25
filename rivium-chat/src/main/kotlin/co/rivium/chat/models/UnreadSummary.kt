package co.rivium.chat.models

import kotlinx.serialization.Serializable

/**
 * Summary of unread messages across all rooms.
 */
@Serializable
data class UnreadSummary(
    val totalUnread: Int,
    val rooms: List<RoomUnread>
)

/**
 * Unread count for a specific room.
 */
@Serializable
data class RoomUnread(
    val roomId: String,
    val externalId: String? = null,
    val unreadCount: Int
)
