package co.rivium.chat.models

/** One page of message search results. */
data class SearchResult(
    /** The matching messages on this page. */
    val messages: List<Message>,
    /** Number of messages that match the query, across all pages. */
    val total: Int,
    /** Whether more matches follow this page (`offset + messages.size < total`). */
    val hasMore: Boolean
)
