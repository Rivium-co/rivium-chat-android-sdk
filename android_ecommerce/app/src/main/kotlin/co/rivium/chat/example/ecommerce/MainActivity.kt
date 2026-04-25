package co.rivium.chat.example.ecommerce

import android.app.Activity
import android.os.Bundle
import androidx.compose.ui.platform.LocalContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import co.rivium.chat.example.ecommerce.models.DemoUser
import co.rivium.chat.example.ecommerce.screens.LoginScreen
import co.rivium.chat.example.ecommerce.screens.OrderChatScreen
import co.rivium.chat.example.ecommerce.screens.OrdersScreen
import co.rivium.chat.example.ecommerce.ui.theme.RiviumChatEcommerceTheme
import co.rivium.chat.RiviumChatClient
import co.rivium.chat.RiviumChatConfig
import co.rivium.chat.ui.state.RiviumChatProvider
import co.rivium.push.sdk.RiviumPush

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RiviumChatEcommerceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EcommerceApp()
                }
            }
        }
    }
}

@Composable
fun EcommerceApp() {
    var currentUser by remember { mutableStateOf<DemoUser?>(null) }
    var riviumChatClient by remember { mutableStateOf<RiviumChatClient?>(null) }
    val activity = LocalContext.current as? Activity

    val navController = rememberNavController()

    // Handle login
    val onLogin: (DemoUser) -> Unit = { user ->
        currentUser = user
        val client = RiviumChatClient(
            config = RiviumChatConfig(
                apiKey = "rv_live_64e0ada5eeb66e3adf6136337802a5a34713ce4966372854",
                userId = user.id
            )
        )
        client.connect()
        riviumChatClient = client

        // Request notification permission and register for push
        activity?.let { RiviumPush.requestNotificationPermission(it) }
        RiviumPush.register(user.id)

        navController.navigate("orders") {
            popUpTo("login") { inclusive = true }
        }
    }

    // Handle logout
    val onLogout: () -> Unit = {
        riviumChatClient?.disconnect()
        riviumChatClient = null
        currentUser = null
        RiviumPush.unregister()
        navController.navigate("login") {
            popUpTo(0) { inclusive = true }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (currentUser != null) "orders" else "login"
    ) {
        composable("login") {
            LoginScreen(onLogin = onLogin)
        }

        composable("orders") {
            val client = riviumChatClient
            val user = currentUser
            if (client != null && user != null) {
                RiviumChatProvider(client = client) {
                    OrdersScreen(
                        currentUser = user,
                        onOrderClick = { orderId ->
                            navController.navigate("chat/$orderId")
                        },
                        onLogout = onLogout
                    )
                }
            }
        }

        composable(
            route = "chat/{orderId}",
            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId") ?: return@composable
            val client = riviumChatClient
            val user = currentUser
            if (client != null && user != null) {
                RiviumChatProvider(client = client) {
                    OrderChatScreen(
                        orderId = orderId,
                        currentUser = user,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
