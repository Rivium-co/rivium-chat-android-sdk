package co.rivium.chat.example.ecommerce.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import co.rivium.chat.models.Attachment
import co.rivium.chat.ui.components.FileUploader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Example file upload service.
 *
 * In a real app, you would upload to your storage service:
 * - AWS S3
 * - Firebase Storage
 * - Cloudflare R2
 * - Supabase Storage
 * - Your own server
 *
 * RiviumChat never stores files - it only stores the URL you return.
 *
 * For this demo, images are compressed and converted to data URIs (base64)
 * so they can be displayed on both parties' devices without a real server.
 */
class DemoFileUploader(private val context: Context) : FileUploader {

    override suspend fun uploadFile(uri: String, mimeType: String?, fileName: String?): Attachment? {
        val isImage = mimeType?.startsWith("image/") == true

        if (isImage) {
            return uploadImage(uri, mimeType, fileName)
        }

        // Non-image files: placeholder URL (no real server in demo)
        return Attachment(
            url = "https://example.com/files/$fileName",
            mimeType = mimeType,
            name = fileName,
            size = null
        )
    }

    private suspend fun uploadImage(uri: String, mimeType: String?, fileName: String?): Attachment? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(Uri.parse(uri))
                    ?: return@withContext null
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (originalBitmap == null) return@withContext null

                // Resize to max 400px on longest side to keep data URI small
                val maxSize = 400
                val scale = minOf(
                    maxSize.toFloat() / originalBitmap.width,
                    maxSize.toFloat() / originalBitmap.height,
                    1f // Don't upscale
                )
                val resized = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        originalBitmap,
                        (originalBitmap.width * scale).toInt(),
                        (originalBitmap.height * scale).toInt(),
                        true
                    )
                } else {
                    originalBitmap
                }

                // Compress to JPEG at 60% quality
                val outputStream = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
                val bytes = outputStream.toByteArray()

                if (resized !== originalBitmap) resized.recycle()
                originalBitmap.recycle()

                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val dataUri = "data:image/jpeg;base64,$base64"

                Attachment(
                    url = dataUri,
                    mimeType = "image/jpeg",
                    name = fileName,
                    size = bytes.size
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
