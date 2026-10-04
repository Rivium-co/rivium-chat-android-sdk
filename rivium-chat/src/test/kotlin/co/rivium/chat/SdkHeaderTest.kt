package co.rivium.chat

import co.rivium.chat.services.ApiService
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SdkHeaderTest {

    /** Sends one request through the SDK's header interceptor and returns it as it would go on the wire. */
    private fun send(): Request {
        var sent: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(ApiService.defaultHeadersInterceptor("rv_live_test"))
            // Answers instead of the network.
            .addInterceptor { chain ->
                sent = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("[]".toResponseBody(null))
                    .build()
            }
            .build()
        client.newCall(Request.Builder().url("${RiviumChatConfig.BASE_URL}/api/v1/rooms?userId=alice").build())
            .execute().close()
        return sent!!
    }

    @Test
    fun `every request sends X-Rivium-SDK with the SDK version`() {
        val request = send()

        assertEquals("android/${RiviumChatConfig.SDK_VERSION}", request.header("X-Rivium-SDK"))
        assertEquals("android/${BuildConfig.SDK_VERSION}", ApiService.SDK_HEADER_VALUE)
        assertTrue(Regex("^android/[A-Za-z0-9.+_-]{1,32}$").matches(ApiService.SDK_HEADER_VALUE))
    }

    @Test
    fun `nothing else about the request changes`() {
        val request = send()

        assertEquals("rv_live_test", request.header("x-api-key"))
        assertEquals("application/json", request.header("Content-Type"))
        assertEquals(setOf("x-api-key", "content-type", "x-rivium-sdk"), request.headers.names().map { it.lowercase() }.toSet())
        assertEquals("https://chat.rivium.co/api/v1/rooms?userId=alice", request.url.toString())
    }

    @Test
    fun `the version is the one the library is published with`() {
        // Unit tests run with the module directory as the working directory.
        val gradle = File("build.gradle.kts").readText()
        val published = Regex("""val sdkVersion = "([^"]+)"""").find(gradle)!!.groupValues[1]
        assertEquals(published, RiviumChatConfig.SDK_VERSION)
    }
}
