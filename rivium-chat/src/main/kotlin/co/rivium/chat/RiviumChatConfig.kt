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
 * Returns a user token for the current user, issued by your server.
 *
 * Your server calls `POST https://chat.rivium.co/api/v1/users/token` with the
 * `x-server-secret` header (Node SDK: `chat.users.createToken`) and returns the
 * token to the app. Never put the server secret in the app.
 */
typealias ChatTokenProvider = suspend () -> String

/**
 * Configuration for RiviumChat SDK.
 *
 * @param apiKey Your RiviumChat API key
 * @param userId The external user ID for the current user
 * @param userInfo Optional user info (displayName, locale, etc.)
 * @param fileUploader Optional file uploader callback for sending attachments
 * @param tokenProvider Recommended. Proves who the user is: every request
 *   carries a token your server issued, so nobody holding the public [apiKey]
 *   can act as another user. Called on connect, shortly before the token
 *   expires, and when the server reports an expired token — refreshes are
 *   invisible to the user. Without it the SDK uses the legacy mode
 *   (API key + [userId]), which a project can disable in Rivium Console.
 */
data class RiviumChatConfig(
    val apiKey: String,
    val userId: String,
    val userInfo: Map<String, String>? = null,
    val fileUploader: FileUploader? = null,
    val tokenProvider: ChatTokenProvider? = null
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

        /** Name this SDK reports to the API. */
        const val SDK_NAME = "android"

        /** Version of this SDK. */
        const val SDK_VERSION = BuildConfig.SDK_VERSION
    }
}
