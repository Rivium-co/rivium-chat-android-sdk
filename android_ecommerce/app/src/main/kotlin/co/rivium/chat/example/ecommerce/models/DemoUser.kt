package co.rivium.chat.example.ecommerce.models

/**
 * Represents a demo user for the e-commerce example.
 */
data class DemoUser(
    val id: String,
    val name: String,
    val role: UserRole,
    val avatarUrl: String? = null
)

enum class UserRole {
    BUYER,
    SELLER
}

/**
 * Demo users for the example app.
 */
object DemoUsers {
    val buyer = DemoUser(
        id = "buyer-001",
        name = "John Buyer",
        role = UserRole.BUYER,
        avatarUrl = "https://api.dicebear.com/7.x/avataaars/png?seed=buyer001"
    )

    val seller = DemoUser(
        id = "seller-001",
        name = "Sarah Seller",
        role = UserRole.SELLER,
        avatarUrl = "https://api.dicebear.com/7.x/avataaars/png?seed=seller001"
    )

    fun getAll(): List<DemoUser> = listOf(buyer, seller)

    fun getOtherUser(currentUserId: String): DemoUser {
        return if (currentUserId == buyer.id) seller else buyer
    }
}
