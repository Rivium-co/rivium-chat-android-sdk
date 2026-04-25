package co.rivium.chat.models

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Represents a participant in a chat room.
 */
@Serializable
data class Participant(
    val id: String,
    val externalUserId: String,
    val displayName: String? = null,
    val locale: String? = null,
    val role: ParticipantRole = ParticipantRole.MEMBER,
    val lastReadAt: String? = null, // ISO 8601 format
    val joinedAt: String // ISO 8601 format
) {
    fun getLastReadAtInstant(): Instant? = lastReadAt?.let { Instant.parse(it) }
    fun getJoinedAtInstant(): Instant = Instant.parse(joinedAt)
}
