package co.rivium.chat.example.ecommerce.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import co.rivium.chat.example.ecommerce.models.DemoUser
import co.rivium.chat.example.ecommerce.models.DemoUsers
import co.rivium.chat.example.ecommerce.models.MockOrders
import co.rivium.chat.example.ecommerce.services.DemoFileUploader
import co.rivium.chat.example.ecommerce.widgets.OrderHeaderWidget
import androidx.compose.ui.platform.LocalContext
import co.rivium.chat.models.Message
import co.rivium.chat.models.Room
import co.rivium.chat.ui.components.ChatScreen
import co.rivium.chat.ui.components.PresenceIndicator
import co.rivium.chat.ui.state.LocalRiviumChatClient
import co.rivium.chat.ui.theme.RiviumChatTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderChatScreen(
    orderId: String,
    currentUser: DemoUser,
    onBack: () -> Unit
) {
    val client = LocalRiviumChatClient.current
    val context = LocalContext.current

    // Get order data
    val order = remember(orderId) { MockOrders.getById(orderId) }
    val otherUser = remember(currentUser) { DemoUsers.getOtherUser(currentUser.id) }
    val fileUploader = remember(context) { DemoFileUploader(context) }

    // State
    var room by remember { mutableStateOf<Room?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val pinnedMessages = remember { mutableStateListOf<Message>() }

    // UI state
    var showPinnedMessages by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Presence
    var otherUserTyping by remember { mutableStateOf(false) }
    var otherUserOnline by remember { mutableStateOf(false) }

    // Initialize room
    LaunchedEffect(orderId) {
        isLoading = true
        error = null

        try {
            // Find or create room for this order
            val loadedRoom = client.findOrCreateRoom(
                externalId = orderId,
                name = "Order ${order?.orderNumber ?: orderId} Chat",
                participants = listOf(
                    mapOf("externalUserId" to currentUser.id, "displayName" to currentUser.name),
                    mapOf("externalUserId" to otherUser.id, "displayName" to otherUser.name)
                ),
                metadata = mapOf(
                    "orderId" to orderId,
                    "orderNumber" to (order?.orderNumber ?: ""),
                    "type" to "order_support"
                )
            )

            room = loadedRoom

            // Subscribe to room events
            client.subscribeRoom(loadedRoom.id)

            // Check initial presence
            try {
                val onlineUsers = client.getRoomPresence(loadedRoom.id)
                otherUserOnline = onlineUsers.contains(otherUser.id)
            } catch (_: Exception) {
                // Presence query failed, rely on realtime events
            }

            // Load pinned messages
            val pinned = client.getPinnedMessages(loadedRoom.id)
            pinnedMessages.clear()
            pinnedMessages.addAll(pinned)

            isLoading = false
        } catch (e: Exception) {
            error = e.message ?: "Failed to load chat"
            isLoading = false
        }
    }

    // Subscribe to typing and presence events
    LaunchedEffect(room) {
        room?.let { r ->
            // Listen for typing with auto-reset timeout
            var typingResetJob: Job? = null
            launch {
                client.onTypingEvent.collect { event ->
                    if (event.roomId == r.id && event.userId != currentUser.id) {
                        otherUserTyping = event.isTyping
                        // Auto-reset typing after 3 seconds
                        typingResetJob?.cancel()
                        if (event.isTyping) {
                            typingResetJob = launch {
                                delay(3000)
                                otherUserTyping = false
                            }
                        }
                    }
                }
            }

            // Listen for presence
            launch {
                client.onPresenceChange.collect { event ->
                    if (event.userId == otherUser.id) {
                        otherUserOnline = event.isOnline
                    }
                }
            }

            // Listen for message pins
            launch {
                client.onMessagePinChanged.collect { event ->
                    if (event.roomId == r.id) {
                        if (event.pinned) {
                            // Refresh pinned messages
                            try {
                                val pinned = client.getPinnedMessages(r.id)
                                pinnedMessages.clear()
                                pinnedMessages.addAll(pinned)
                            } catch (_: Exception) {}
                        } else {
                            pinnedMessages.removeAll { it.id == event.messageId }
                        }
                    }
                }
            }
        }
    }

    // Cleanup
    DisposableEffect(room) {
        onDispose {
            room?.let { r ->
                client.unsubscribeRoom(r.id)
            }
        }
    }

    if (order == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Order not found")
        }
        return
    }

    RiviumChatTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Other user avatar with presence
                        Box {
                            if (otherUser.avatarUrl != null) {
                                AsyncImage(
                                    model = otherUser.avatarUrl,
                                    contentDescription = otherUser.name,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = otherUser.name.first().toString(),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                            }

                            PresenceIndicator(
                                isOnline = otherUserOnline,
                                modifier = Modifier.align(Alignment.BottomEnd)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = otherUser.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (otherUserTyping) "typing..."
                                       else if (otherUserOnline) "Online"
                                       else "Offline",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (otherUserTyping) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (pinnedMessages.isNotEmpty()) {
                        IconButton(onClick = { showPinnedMessages = !showPinnedMessages }) {
                            Icon(
                                Icons.Default.PushPin,
                                "Pinned messages",
                                tint = if (showPinnedMessages)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "More options")
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("View Order Details") },
                                onClick = {
                                    showMenu = false
                                    // Navigate to order details
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Order header
            OrderHeaderWidget(order = order)

            // Pinned messages banner
            AnimatedVisibility(
                visible = showPinnedMessages && pinnedMessages.isNotEmpty(),
                enter = slideInVertically(),
                exit = slideOutVertically()
            ) {
                PinnedMessagesBanner(
                    pinnedMessages = pinnedMessages,
                    onClose = { showPinnedMessages = false }
                )
            }

            // Chat content
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = error ?: "Unknown error",
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap to retry",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    // Retry loading
                                }
                            )
                        }
                    }
                }

                room != null -> {
                    // Use the SDK's ChatScreen composable - handles messages,
                    // input, context menus, reactions, read receipts, etc.
                    ChatScreen(
                        roomId = room!!.id,
                        currentUserId = currentUser.id,
                        fileUploader = fileUploader,
                        userDisplayNames = mapOf(
                            currentUser.id to currentUser.name,
                            otherUser.id to otherUser.name
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun PinnedMessagesBanner(
    pinnedMessages: List<Message>,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${pinnedMessages.size} pinned message${if (pinnedMessages.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                pinnedMessages.firstOrNull()?.let { msg ->
                    Text(
                        text = msg.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
