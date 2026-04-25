package co.rivium.chat.example.ecommerce.models

import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Represents an e-commerce order.
 */
data class Order(
    val id: String,
    val orderNumber: String,
    val status: OrderStatus,
    val items: List<OrderItem>,
    val buyerId: String,
    val sellerId: String,
    val createdAt: Instant,
    val totalAmount: BigDecimal,
    val shippingAddress: String? = null,
    val trackingNumber: String? = null
) {
    val itemCount: Int get() = items.sumOf { it.quantity }

    val formattedTotal: String
        get() = "$${totalAmount.setScale(2)}"

    val primaryImage: String?
        get() = items.firstOrNull()?.imageUrl
}

/**
 * Represents an item in an order.
 */
data class OrderItem(
    val id: String,
    val name: String,
    val quantity: Int,
    val price: BigDecimal,
    val imageUrl: String? = null
)

/**
 * Order status enum.
 */
enum class OrderStatus(val displayName: String, val color: Long) {
    PENDING("Pending", 0xFFF59E0B),
    CONFIRMED("Confirmed", 0xFF3B82F6),
    SHIPPED("Shipped", 0xFF8B5CF6),
    DELIVERED("Delivered", 0xFF10B981),
    CANCELLED("Cancelled", 0xFFEF4444)
}

/**
 * Mock orders for demo purposes.
 */
object MockOrders {
    private val now = Instant.now()

    val orders = listOf(
        Order(
            id = "order-001",
            orderNumber = "ORD-2024-001",
            status = OrderStatus.SHIPPED,
            items = listOf(
                OrderItem(
                    id = "item-001",
                    name = "Wireless Bluetooth Headphones",
                    quantity = 1,
                    price = BigDecimal("79.99"),
                    imageUrl = "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=200"
                ),
                OrderItem(
                    id = "item-002",
                    name = "Phone Case",
                    quantity = 2,
                    price = BigDecimal("15.99"),
                    imageUrl = "https://images.unsplash.com/photo-1601784551446-20c9e07cdbdb?w=200"
                )
            ),
            buyerId = DemoUsers.buyer.id,
            sellerId = DemoUsers.seller.id,
            createdAt = now.minus(2, ChronoUnit.DAYS),
            totalAmount = BigDecimal("111.97"),
            shippingAddress = "123 Main St, New York, NY 10001",
            trackingNumber = "1Z999AA10123456784"
        ),
        Order(
            id = "order-002",
            orderNumber = "ORD-2024-002",
            status = OrderStatus.CONFIRMED,
            items = listOf(
                OrderItem(
                    id = "item-003",
                    name = "Smart Watch Pro",
                    quantity = 1,
                    price = BigDecimal("299.99"),
                    imageUrl = "https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=200"
                )
            ),
            buyerId = DemoUsers.buyer.id,
            sellerId = DemoUsers.seller.id,
            createdAt = now.minus(1, ChronoUnit.DAYS),
            totalAmount = BigDecimal("299.99"),
            shippingAddress = "456 Oak Ave, Los Angeles, CA 90001"
        ),
        Order(
            id = "order-003",
            orderNumber = "ORD-2024-003",
            status = OrderStatus.PENDING,
            items = listOf(
                OrderItem(
                    id = "item-004",
                    name = "Laptop Stand",
                    quantity = 1,
                    price = BigDecimal("49.99"),
                    imageUrl = "https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?w=200"
                ),
                OrderItem(
                    id = "item-005",
                    name = "USB-C Hub",
                    quantity = 1,
                    price = BigDecimal("39.99"),
                    imageUrl = "https://images.unsplash.com/photo-1625723044792-44de16ccb4e9?w=200"
                ),
                OrderItem(
                    id = "item-006",
                    name = "Wireless Mouse",
                    quantity = 1,
                    price = BigDecimal("29.99"),
                    imageUrl = "https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?w=200"
                )
            ),
            buyerId = DemoUsers.buyer.id,
            sellerId = DemoUsers.seller.id,
            createdAt = now.minus(3, ChronoUnit.HOURS),
            totalAmount = BigDecimal("119.97"),
            shippingAddress = "789 Pine Rd, Chicago, IL 60601"
        ),
        Order(
            id = "order-004",
            orderNumber = "ORD-2024-004",
            status = OrderStatus.DELIVERED,
            items = listOf(
                OrderItem(
                    id = "item-007",
                    name = "Mechanical Keyboard",
                    quantity = 1,
                    price = BigDecimal("149.99"),
                    imageUrl = "https://images.unsplash.com/photo-1511467687858-23d96c32e4ae?w=200"
                )
            ),
            buyerId = DemoUsers.buyer.id,
            sellerId = DemoUsers.seller.id,
            createdAt = now.minus(7, ChronoUnit.DAYS),
            totalAmount = BigDecimal("149.99"),
            shippingAddress = "321 Elm St, Seattle, WA 98101",
            trackingNumber = "1Z999AA10123456785"
        ),
        Order(
            id = "order-005",
            orderNumber = "ORD-2024-005",
            status = OrderStatus.CANCELLED,
            items = listOf(
                OrderItem(
                    id = "item-008",
                    name = "Gaming Chair",
                    quantity = 1,
                    price = BigDecimal("249.99"),
                    imageUrl = "https://images.unsplash.com/photo-1598550476439-6847785fcea6?w=200"
                )
            ),
            buyerId = DemoUsers.buyer.id,
            sellerId = DemoUsers.seller.id,
            createdAt = now.minus(5, ChronoUnit.DAYS),
            totalAmount = BigDecimal("249.99"),
            shippingAddress = "555 Maple Dr, Miami, FL 33101"
        )
    )

    fun getById(id: String): Order? = orders.find { it.id == id }
}
