package co.rivium.chat.example.ecommerce

import android.app.Application
import android.util.Log
import co.rivium.push.sdk.RiviumPush
import co.rivium.push.sdk.RiviumPushConfig
import co.rivium.push.sdk.RiviumPushLogLevel

class EcommerceApplication : Application() {

    companion object {
        private const val TAG = "EcommerceApp"
        // Same API key used by RiviumChat SDK
        private const val API_KEY = "rv_live_64e0ada5eeb66e3adf6136337802a5a34713ce4966372854"
    }

    override fun onCreate() {
        super.onCreate()

        val config = RiviumPushConfig(
            apiKey = API_KEY,
            showServiceNotification = false
        )

        RiviumPush.init(this, config)
        RiviumPush.setLogLevel(RiviumPushLogLevel.DEBUG)

        Log.i(TAG, "Rivium Push SDK initialized")
    }
}
