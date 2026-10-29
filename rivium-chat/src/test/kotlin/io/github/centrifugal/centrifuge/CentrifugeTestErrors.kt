package io.github.centrifugal.centrifuge

/**
 * Builds the error the realtime client library hands to `onError` when the
 * server refuses a subscribe. Its constructor is package-private, hence this
 * file's package.
 */
fun subscribeErrorOf(code: Int, message: String, temporary: Boolean = false): Throwable =
    SubscriptionSubscribeError(ReplyError(code, message, temporary))
