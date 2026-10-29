package co.rivium.chat.services

import co.rivium.chat.RiviumChatConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Builds a Chat API URL: `<base>/api/v1/<segments...>?<query>`.
 *
 * Every segment is encoded as one path segment ("/", "?", "#", "%" and spaces
 * included), and every query value as a query value ("&", "=", "+", "#" and
 * spaces included), so caller-supplied IDs and text arrive at the server
 * exactly as given. Query entries whose value is null are left out.
 */
internal fun apiUrl(
    vararg segments: String,
    query: List<Pair<String, String?>> = emptyList(),
    baseUrl: String = RiviumChatConfig.BASE_URL
): HttpUrl {
    val builder = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("api")
        .addPathSegment("v1")
    segments.forEach { segment ->
        // "", "." and ".." would point the request at a different route.
        require(segment.isNotEmpty() && segment != "." && segment != "..") {
            "RiviumChat: \"$segment\" is not a valid ID"
        }
        builder.addPathSegment(segment)
    }
    query.forEach { (name, value) -> if (value != null) builder.addQueryParameter(name, value) }
    return builder.build()
}
