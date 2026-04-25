package co.rivium.chat.example.ecommerce.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import co.rivium.chat.example.ecommerce.models.DemoUser
import co.rivium.chat.example.ecommerce.models.DemoUsers
import co.rivium.chat.example.ecommerce.models.MockOrders
import co.rivium.chat.example.ecommerce.models.Order
import co.rivium.chat.example.ecommerce.models.UserRole
import co.rivium.chat.example.ecommerce.widgets.OrderListCard
import co.rivium.chat.ui.state.LocalRiviumChatClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    currentUser: DemoUser,
    onOrderClick: (String) -> Unit,
    onLogout: () -> Unit
) {
    val client = LocalRiviumChatClient.current
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    val unreadCounts = remember { mutableStateMapOf<String, Int>() }
    val roomToOrderMap = remember { mutableStateMapOf<String, String>() }

    // Filter orders based on user role
    val orders = remember(currentUser) {
        MockOrders.orders.filter {
            when (currentUser.role) {
                UserRole.BUYER -> it.buyerId == currentUser.id
                UserRole.SELLER -> it.sellerId == currentUser.id
            }
        }
    }

    // Load rooms, unread counts, and subscribe to chat channels for real-time updates
    LaunchedEffect(orders) {
        isLoading = true
        // Load unread counts
        try {
            val summary = client.getUnreadSummary()
            summary.rooms.forEach { roomUnread ->
                roomUnread.externalId?.let { externalId ->
                    unreadCounts[externalId] = roomUnread.unreadCount
                }
            }
        } catch (_: Exception) {}

        // Subscribe to chat channels (observe only, without presence)
        orders.forEach { order ->
            launch {
                try {
                    val room = client.getRoomByExternalId(order.id)
                    roomToOrderMap[room.id] = order.id
                    client.observeRoom(room.id)
                } catch (_: Exception) {}
            }
        }
        isLoading = false
    }

    // Listen for new messages to update unread counts in real-time
    LaunchedEffect(Unit) {
        client.onMessage.collect { message ->
            if (message.senderUserId != currentUser.id) {
                val orderId = roomToOrderMap[message.roomId]
                if (orderId != null) {
                    unreadCounts[orderId] = (unreadCounts[orderId] ?: 0) + 1
                }
            }
        }
    }

    // Refresh unread counts when screen resumes (returning from chat)
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch {
                    try {
                        val summary = client.getUnreadSummary()
                        val counts = mutableMapOf<String, Int>()
                        summary.rooms.forEach { roomUnread ->
                            roomUnread.externalId?.let { externalId ->
                                counts[externalId] = roomUnread.unreadCount
                            }
                        }
                        unreadCounts.clear()
                        unreadCounts.putAll(counts)
                    } catch (_: Exception) {}
                    // Re-observe rooms
                    roomToOrderMap.keys.forEach { roomId ->
                        client.observeRoom(roomId)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // User avatar
                        if (currentUser.avatarUrl != null) {
                            AsyncImage(
                                model = currentUser.avatarUrl,
                                contentDescription = currentUser.name,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (currentUser.role == UserRole.BUYER)
                                            Icons.Default.Person else Icons.Default.Store,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "My Orders",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = currentUser.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Logout"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (orders.isEmpty()) {
            EmptyOrdersState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(orders, key = { it.id }) { order ->
                    OrderListCard(
                        order = order,
                        unreadCount = unreadCounts[order.id] ?: 0,
                        onClick = { onOrderClick(order.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyOrdersState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ShoppingBag,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No orders yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "Your orders will appear here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
