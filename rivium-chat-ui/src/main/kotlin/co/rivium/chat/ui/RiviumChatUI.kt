/**
 * RiviumChat UI SDK for Android
 *
 * A comprehensive UI toolkit for building chat interfaces with Jetpack Compose.
 * This library provides ready-to-use components that work seamlessly with
 * the RiviumChat Core SDK.
 *
 * ## Quick Start
 *
 * ```kotlin
 * // 1. Wrap your app with RiviumChatScope
 * RiviumChatScope(
 *     config = RiviumChatConfig(
 *         apiKey = "your-api-key",
 *         userId = "user-123"
 *     )
 * ) {
 *     // 2. Use ChatScreen for a complete chat experience
 *     ChatScreen(
 *         roomId = "room-456",
 *         currentUserId = "user-123"
 *     )
 * }
 * ```
 *
 * ## Available Components
 *
 * ### State Management
 * - [RiviumChatScope] - Provides RiviumChatClient to the composition tree
 * - [RiviumChatProvider] - Alternative for external client management
 * - [ChatChannelState] - Manages state for a single chat room
 *
 * ### Core Chat Components
 * - [ChatScreen] - Complete chat UI with messages, input, and typing indicator
 * - [ChatMessageBubble] - Individual message display with attachments and reactions
 * - [ChatInputField] - Message input with emoji, attachments, and voice support
 *
 * ### Status Indicators
 * - [TypingIndicator] - Shows who is typing
 * - [PresenceIndicator] - Online/offline status dot
 * - [UnreadBadge] - Unread message count badge
 *
 * ### Message Interactions
 * - [MessageContextMenu] - Long-press menu with actions
 * - [MessageReactionPicker] - Quick emoji reaction picker
 *
 * ### List Components
 * - [ChatRoomListTile] - Room list item with customization
 * - [ChatRoomListTileSkeleton] - Loading placeholder
 *
 * ## Theming
 *
 * Wrap your content with [RiviumChatTheme] for consistent styling:
 *
 * ```kotlin
 * RiviumChatTheme(
 *     colors = RiviumChatColors(...),  // Optional custom colors
 *     dimensions = RiviumChatDimensions(...)  // Optional custom dimensions
 * ) {
 *     ChatScreen(...)
 * }
 * ```
 *
 * @see co.rivium.chat.RiviumChatClient
 * @see co.rivium.chat.RiviumChatConfig
 */
package co.rivium.chat.ui

// Re-export state management
import co.rivium.chat.ui.state.ChatChannelState
import co.rivium.chat.ui.state.RiviumChatProvider
import co.rivium.chat.ui.state.RiviumChatScope
import co.rivium.chat.ui.state.LocalRiviumChatClient
import co.rivium.chat.ui.state.rememberChatChannelState

// Re-export theme
import co.rivium.chat.ui.theme.RiviumChatColors
import co.rivium.chat.ui.theme.RiviumChatDimensions
import co.rivium.chat.ui.theme.RiviumChatTheme
import co.rivium.chat.ui.theme.LocalRiviumChatColors
import co.rivium.chat.ui.theme.LocalRiviumChatDimensions

// Re-export components
import co.rivium.chat.ui.components.AttachmentPickerContent
import co.rivium.chat.ui.components.ChatInputField
import co.rivium.chat.ui.components.ChatMessageBubble
import co.rivium.chat.ui.components.ChatRoomListTile
import co.rivium.chat.ui.components.ChatRoomListTileSkeleton
import co.rivium.chat.ui.components.ChatScreen
import co.rivium.chat.ui.components.CompactTypingIndicator
import co.rivium.chat.ui.components.FileUploader
import co.rivium.chat.ui.components.MessageAction
import co.rivium.chat.ui.components.MessageContextMenu
import co.rivium.chat.ui.components.MessageReactionPicker
import co.rivium.chat.ui.components.MessageReactions
import co.rivium.chat.ui.components.PresenceIndicator
import co.rivium.chat.ui.components.PresenceStatus
import co.rivium.chat.ui.components.TypingDots
import co.rivium.chat.ui.components.TypingIndicator
import co.rivium.chat.ui.components.UnreadBadge
import co.rivium.chat.ui.components.UnreadDot
import co.rivium.chat.ui.components.defaultReactions

// Re-export new components (matching Flutter SDK)
import co.rivium.chat.ui.components.SwipeableMessage
import co.rivium.chat.ui.components.ReplyPreview
import co.rivium.chat.ui.components.ReplyInputPreview
import co.rivium.chat.ui.components.ReadReceipts
import co.rivium.chat.ui.components.ReadReceiptUser
import co.rivium.chat.ui.components.ReadReceiptDetails
import co.rivium.chat.ui.components.MessageStatus
import co.rivium.chat.ui.components.MentionsList
import co.rivium.chat.ui.components.MentionUser
import co.rivium.chat.ui.components.MentionsController
import co.rivium.chat.ui.components.rememberMentionsController
import co.rivium.chat.ui.components.VoiceMessageRecorder
import co.rivium.chat.ui.components.VoiceMessagePlayer
import co.rivium.chat.ui.components.VoiceRecorderState
import co.rivium.chat.ui.components.VoiceRecordingResult
import co.rivium.chat.ui.components.MessageSearchBar
import co.rivium.chat.ui.components.MessageSearchOverlay
import co.rivium.chat.ui.components.HighlightedSearchText
import co.rivium.chat.ui.components.LinkPreview
import co.rivium.chat.ui.components.LinkPreviewData
import co.rivium.chat.ui.components.LinkPreviewComposer
import co.rivium.chat.ui.components.LinkExtractor
import co.rivium.chat.ui.components.ChatAttachmentPicker
import co.rivium.chat.ui.components.ChatAttachmentPickerContent
import co.rivium.chat.ui.components.AttachmentType
import co.rivium.chat.ui.components.AttachmentResult
