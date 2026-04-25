package co.rivium.chat.models

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Represents an emoji reaction on a message.
 */
@Serializable
data class Reaction(
    val id: String,
    val messageId: String,
    val userId: String,
    val emoji: String,
    val createdAt: String // ISO 8601 format
) {
    fun getCreatedAtInstant(): Instant = Instant.parse(createdAt)
}
