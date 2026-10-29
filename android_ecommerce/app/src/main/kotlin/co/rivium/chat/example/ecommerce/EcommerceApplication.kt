package co.rivium.chat.example.ecommerce

import android.app.Application
import android.util.Log
import co.rivium.push.sdk.RiviumPush
import co.rivium.push.sdk.RiviumPushConfig
import co.rivium.push.sdk.RiviumPushLogLevel

class EcommerceApplication : Application() {

    companion object {
        private const val TAG = "EcommerceApp"
        // Same public key as the RiviumChat SDK, from local.properties (RIVIUM_API_KEY).
        private val API_KEY = BuildConfig.RIVIUM_API_KEY
    }

    override fun onCreate() {
        super.onCreate()

        if (API_KEY.isBlank()) {
            Log.e(TAG, "RIVIUM_API_KEY is not set: copy local.properties.example to local.properties and add your key")
            return
        }

        val config = RiviumPushConfig(
            apiKey = API_KEY,
            showServiceNotification = false
        )

        RiviumPush.init(this, config)
        RiviumPush.setLogLevel(RiviumPushLogLevel.DEBUG)

        Log.i(TAG, "Rivium Push SDK initialized")
    }
}
