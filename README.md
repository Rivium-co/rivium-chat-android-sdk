# RiviumChat Android SDK

Real-time messaging SDK for Android. Add chat to your app with pre-built Compose UI components, real-time messaging, read receipts, typing indicators, reactions, and push notifications.

## Features

- Real-time messaging via WebSocket (<50ms latency)
- Pre-built Jetpack Compose UI components (chat screen, message bubbles, input field, typing indicator, etc.)
- Read receipts with delivery status (sent, delivered, read)
- Emoji reactions and message pinning
- Typing and presence indicators
- Threaded replies and swipe-to-reply
- File and image sharing with inline previews
- Full-text message search
- Unread message counts
- Push notifications via [Rivium Push](https://rivium.co/cloud/rivium-push) for offline users
- Customizable theming with Material 3

## Installation

### Gradle (Maven Central)

Add to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("co.rivium:rivium-chat-android:0.1.0")
    implementation("co.rivium:rivium-chat-android-ui:0.1.0")
}
```

## Quick Start

### 1. Initialize the Client

```kotlin
import co.rivium.chat.RiviumChatClient
import co.rivium.chat.RiviumChatConfig

val config = RiviumChatConfig(
    apiKey = "your_api_key",
    userId = "user-123",
    userInfo = mapOf("displayName" to "John")
)

val client = RiviumChatClient(config)
client.connect()
```

### Secure user identity (recommended)

Your API key ships inside the app, so on its own it cannot prove who the user
is. Add a `tokenProvider` that asks **your server** for a user token:

```kotlin
val config = RiviumChatConfig(
    apiKey = "your_api_key",
    userId = "user-123",
    tokenProvider = { myBackend.getChatToken() }  // suspend fun returning the token
)

// Revoked or invalid token — send the user to login.
scope.launch { client.onAuthError.collect { signOut() } }
```

Your server mints it with the server secret (never put the secret in the app):

```http
POST https://chat.rivium.co/api/v1/users/token
x-api-key: your_api_key
x-server-secret: your_server_secret

{ "userId": "user-123" }
```

Tokens last 1 hour. The SDK refreshes them before they expire and retries a
request once if the server reports an expired token, so users never notice.

### 2. Create or Join a Room

```kotlin
val room = client.findOrCreateRoom(
    externalId = "order-123",
    participants = listOf(
        mapOf("externalUserId" to "user-123", "displayName" to "John", "role" to "member"),
        mapOf("externalUserId" to "user-456", "displayName" to "Jane", "role" to "member")
    )
)
```

### 3. Send Messages

```kotlin
val message = client.sendMessage(room.id, content = "Hello!")
```

### 4. Listen for Real-Time Events

```kotlin
// New messages
client.onMessage.collect { message ->
    println("New message: ${message.content}")
}

// Typing indicators
client.onTypingEvent.collect { event ->
    println("${event.userId} is typing: ${event.isTyping}")
}

// Presence (online/offline)
client.onPresenceChange.collect { event ->
    println("${event.userId} is online: ${event.isOnline}")
}

// Read receipts
client.onReadReceipt.collect { event ->
    println("${event.userId} read messages at ${event.readAt}")
}
```

### 5. Mark Messages as Read

```kotlin
client.markAsRead(room.id)
```

## Modules

| Module | Description | Min SDK |
|--------|-------------|---------|
| `rivium-chat-android` | Core SDK - API client, real-time messaging, models | API 21 |
| `rivium-chat-android-ui` | Pre-built Compose UI components with Material 3 theming | API 21 |

## UI Components

`rivium-chat-android-ui` includes ready-to-use Jetpack Compose components:

- `ChatScreen` - Full chat screen with messages, input, and state management
- `ChatMessageBubble` - Message bubble with read receipts, reactions, and reply preview
- `ChatInputField` - Text input with attachment and typing indicator support
- `TypingIndicator` - Animated typing dots
- `PresenceIndicator` - Online/offline status dot
- `UnreadBadge` - Unread message count badge
- `MessageReactionPicker` - Emoji reaction selector
- `ChatRoomListTile` - Room list item with last message preview
- `ReadReceipts` - Delivery status indicators (sent, delivered, read)
- `SwipeableMessage` - Swipe-to-reply gesture
- `MessageSearchBar` - Full-text message search

## Example App

The `android_ecommerce/` directory contains a complete e-commerce chat example demonstrating:

- Buyer/seller chat for order support
- Real-time messaging with read receipts
- Typing and presence indicators
- Emoji reactions and message pinning
- Push notifications (via Rivium Push)
- Unread message badges
- Message pagination
- Reply to messages

## Push Notifications

RiviumChat integrates with [Rivium Push](https://rivium.co/cloud/rivium-push) for offline push notifications.

## Links

- [Rivium Chat](https://rivium.co/cloud/rivium-chat) - Learn more about Rivium Chat
- [Documentation](https://rivium.co/cloud/rivium-chat/docs/quick-start) - Full documentation and guides
- [Rivium Console](https://console.rivium.co) - Manage your chat rooms

## License

MIT
