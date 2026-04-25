package co.rivium.chat.models

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Represents a chat room (conversation).
 */
@Serializable
data class Room(
    val id: String,
    val type: RoomType = RoomType.DIRECT,
    val externalId: String? = null,
    val name: String? = null,
    val metadata: Map<String, String>? = null,
    val isActive: Boolean = true,
    val createdAt: String? = null, // ISO 8601 format
    val updatedAt: String? = null, // ISO 8601 format
    val participants: List<Participant> = emptyList()
) {
    fun getCreatedAtInstant(): Instant? = createdAt?.let { Instant.parse(it) }
    fun getUpdatedAtInstant(): Instant? = updatedAt?.let { Instant.parse(it) }
}
