package co.rivium.chat

import java.io.File

/**
 * Result of a file upload operation.
 */
data class FileUploadResult(
    val url: String,
    val mimeType: String? = null,
    val name: String? = null,
    val size: Int? = null
)

/**
 * Callback type for uploading files.
 *
 * Implement this to integrate with your storage service (S3, Firebase, Supabase, etc.).
 */
typealias FileUploader = suspend (File) -> FileUploadResult

/**
 * Configuration for RiviumChat SDK.
 *
 * @param apiKey Your RiviumChat API key
 * @param userId The external user ID for the current user
 * @param userInfo Optional user info (displayName, locale, etc.)
 * @param fileUploader Optional file uploader callback for sending attachments
 */
data class RiviumChatConfig(
    val apiKey: String,
    val userId: String,
    val userInfo: Map<String, String>? = null,
    val fileUploader: FileUploader? = null
) {
    init {
        require(apiKey.isNotBlank()) { "API key cannot be blank" }
        require(userId.isNotBlank()) { "User ID cannot be blank" }
    }

    companion object {
        /** Base URL for the RiviumChat API. */
        const val BASE_URL = "https://chat.rivium.co"

        /** WebSocket URL for Centrifugo realtime server. */
        const val CENTRIFUGO_URL = "wss://ws-chat.rivium.co/connection/websocket"
    }
}
