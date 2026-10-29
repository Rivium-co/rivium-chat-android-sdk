# Changelog

## [0.2.0] - 2026-10-30

- Added: offline sending, off by default. With `RiviumChatConfig(queueOfflineMessages = true)`, a message that cannot be sent because of the network (no network, timeout, server busy, `tokenProvider` unreachable) is kept and sent when the connection is back, in the order written and never twice. `sendMessage()` then returns it as a pending message (`isPending = true`, id `local_<clientMessageId>`) instead of throwing. Errors the server answers (not a member, invalid message, plan limit) still throw. Without the option nothing changes.
- Added: `onMessageSent` (a queued message was stored) and `onMessageFailed` (it left the queue unsent, `reason` `"expired"` after 24 hours or `"rejected"` by the server), plus `getPendingMessages(roomId)`, `retryPendingMessages()`, `cancelPendingMessage(localId)` and `clearPendingMessages()`.
- Added: the queue is kept in memory by default; pass `context` to keep it in SharedPreferences across app restarts, or `outboxStore` for your own storage (`OutboxStore`).
- Added: `Message.clientMessageId`, and `RiviumChatException.statusCode` (the HTTP status of a refused request).
- UI kit: with `queueOfflineMessages` on, a message written offline shows "Waiting for network…" and turns into the sent message once it is stored (shown once); if it cannot be sent it shows Retry.
- Fixed: `listRooms()` now fills `lastMessage` and `unreadCount` on each `Room`.
- Fixed: `searchMessages()` returned nothing usable; it now returns the matching messages.
- Fixed: `getMentions()` now returns the messages that mention the user.
- Fixed: user IDs, room IDs, message IDs, external IDs and search text containing characters such as `/`, `?`, `#`, `&`, `+` or spaces (for example "Order #1001") reached the server cut short or changed. Every value placed in a request URL is now encoded. An empty ID, `.` or `..` is refused with `IllegalArgumentException`.
- Fixed: fields the server sends as `null` (such as `editedAt`, `pinnedAt`, `replyToId`, `displayName`, `name`, `externalId`, attachment `mimeType`) came through as the text `"null"` on devices. They are now `null`. `null` values inside `metadata` are left out of the map, and an attachment with `"size": null` no longer fails to parse.
- Fixed: the realtime connection now fetches a new token when its token expires, so long sessions stay connected.
- Fixed: a failing `tokenProvider` during `connect()` now emits `token_provider_failed` on `onAuthError`, as it already did for requests.
- Fixed: typing indicators are throttled per room, so typing in one room no longer holds back the indicator in another.
- Changed: `getRoomPresence()` is now a `suspend` function. It no longer blocks the calling thread for up to 5 seconds. Call it from a coroutine.
- UI kit: Edit in the message menu now opens an edit dialog and saves the change.
- UI kit: closing a chat screen no longer stops a conversation list from receiving that room's new messages.
- Added: `searchMessagesWithTotal()` returns a `SearchResult` with the matching `messages`, the `total` number of matches and `hasMore`. `searchMessages()` is unchanged.
- Fixed: when the connection token cannot be fetched (unknown key, project requires a user token, no network, failing `tokenProvider`), the connection state now ends as `ERROR`; it could be left showing `CONNECTING`. The error is reported on `onConnectionError`.
- Fixed: a room subscription the server refuses (for example "Not a participant in this room") is reported on `onSubscriptionState` with the server's `code` and `reason`, for the presence and typing channels too.
- Fixed: a malformed realtime event could crash the app; it is now ignored.
- Fixed: calling `disconnect()` or `dispose()` while `connect()` was still fetching its token no longer opens a connection afterwards.
- UI kit: Pin and Unpin show the new state right away, and pinned messages show a pin icon.
- UI kit: a failed message shows a Retry button.
- UI kit: typing indicators and reply labels show the participant's display name (or their user id when they have none). `ChatChannelState` gains `participantDisplayNames` and `displayNameFor()`; `ChatInputField` gains `replyingToName` and `ChatMessageBubble` gains `replySenderName`.
- UI kit: an action that fails (pin, reaction, delete) no longer replaces the conversation with "Failed to load messages".

## [0.1.4] - 2026-10-05

- Added: requests send an `X-Rivium-SDK` header (`android/<version>`); `RiviumChatConfig.SDK_VERSION` gives the SDK version.

## [0.1.3] - 2026-09-26

- Added: `lastMessage` and `unreadCount` on `Room`. A chat list can show the
  latest message and an unread badge from `listRooms()` alone, with no extra
  calls.

## [0.1.2] - 2026-09-12

- Added: `tokenProvider` for secure user identity. Tokens are refreshed automatically before they expire and after an expired-token response.
- Added: `onAuthError` flow for identity errors a refresh cannot fix.

## [0.1.1] - 2026-09-09

- Fixed: subscribing to a room could throw "Subscription to a channel already exists" after leaving and rejoining it.
- Fixed: `subscribeToRoom` could report success while a channel had not subscribed, leaving the room without live updates.
- Fixed: presence and typing channels reported nothing when a subscribe failed.
- Added: `isRoomSubscribed()` and `subscribedChannelsFor()` to check subscription state.
- Added: `reconnect()` to recover a stale connection.

## [0.1.0] - 2026-04-26

- Initial release
