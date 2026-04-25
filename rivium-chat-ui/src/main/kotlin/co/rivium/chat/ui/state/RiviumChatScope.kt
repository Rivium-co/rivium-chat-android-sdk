package co.rivium.chat.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import co.rivium.chat.RiviumChatClient
import co.rivium.chat.RiviumChatConfig

/**
 * CompositionLocal for accessing RiviumChatClient throughout the compose tree.
 */
val LocalRiviumChatClient = staticCompositionLocalOf<RiviumChatClient> {
    error("No RiviumChatClient provided. Wrap your composable with RiviumChatScope or RiviumChatProvider.")
}

/**
 * Provides RiviumChatClient to the composition tree.
 * This variant creates and manages the client lifecycle automatically.
 *
 * @param config The RiviumChat configuration.
 * @param autoConnect Whether to automatically connect on composition.
 * @param onConnected Callback when connection is established.
 * @param onConnectionError Callback when connection fails.
 * @param content The composable content.
 */
@Composable
fun RiviumChatScope(
    config: RiviumChatConfig,
    autoConnect: Boolean = true,
    onConnected: (() -> Unit)? = null,
    onConnectionError: ((Throwable) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val client = rememberRiviumChatClient(config)

    LaunchedEffect(client, autoConnect) {
        if (autoConnect) {
            try {
                client.connect()
                onConnected?.invoke()
            } catch (e: Exception) {
                onConnectionError?.invoke(e)
            }
        }
    }

    DisposableEffect(client) {
        onDispose {
            client.disconnect()
        }
    }

    CompositionLocalProvider(LocalRiviumChatClient provides client) {
        content()
    }
}

/**
 * Provides an existing RiviumChatClient to the composition tree.
 * Use this when you manage the client lifecycle externally.
 *
 * @param client The pre-configured RiviumChatClient instance.
 * @param content The composable content.
 */
@Composable
fun RiviumChatProvider(
    client: RiviumChatClient,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalRiviumChatClient provides client) {
        content()
    }
}

/**
 * Remembers a RiviumChatClient instance that survives recomposition.
 * Creates a new client if the config changes.
 */
@Composable
private fun rememberRiviumChatClient(config: RiviumChatConfig): RiviumChatClient {
    return androidx.compose.runtime.remember(config) {
        RiviumChatClient(config)
    }
}
