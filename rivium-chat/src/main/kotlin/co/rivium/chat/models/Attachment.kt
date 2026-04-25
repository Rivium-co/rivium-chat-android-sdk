package co.rivium.chat.models

import kotlinx.serialization.Serializable

/**
 * Represents a file attachment in a message.
 */
@Serializable
data class Attachment(
    val url: String,
    val mimeType: String? = null,
    val name: String? = null,
    val size: Int? = null
)
