package co.rivium.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage

/**
 * Data for a link preview.
 */
data class LinkPreviewData(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val siteName: String? = null,
    val favicon: String? = null
) {
    val hasContent: Boolean
        get() = title != null || description != null || imageUrl != null

    companion object {
        /**
         * Creates preview data from Open Graph metadata.
         */
        fun fromOpenGraph(metadata: Map<String, String>, url: String): LinkPreviewData {
            return LinkPreviewData(
                url = url,
                title = metadata["og:title"] ?: metadata["title"],
                description = metadata["og:description"] ?: metadata["description"],
                imageUrl = metadata["og:image"],
                siteName = metadata["og:site_name"],
                favicon = metadata["favicon"]
            )
        }
    }
}

/**
 * A widget that displays a link preview with image, title, and description.
 * Similar to link previews in Telegram, WhatsApp, and Slack.
 *
 * @param data The preview data to display.
 * @param onTap Called when the preview is tapped.
 * @param onClose Called when the close button is tapped.
 * @param isMe Whether this preview is on the current user's message.
 * @param showCloseButton Whether to show the close button.
 * @param largeImage Whether to show the image in large format.
 * @param borderRadius Border radius of the preview container.
 * @param maxWidth Maximum width of the preview.
 * @param modifier Modifier for the component.
 */
@Composable
fun LinkPreview(
    data: LinkPreviewData,
    onTap: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    isMe: Boolean = false,
    showCloseButton: Boolean = false,
    largeImage: Boolean = false,
    borderRadius: Dp = 12.dp,
    maxWidth: Dp = 300.dp,
    modifier: Modifier = Modifier
) {
    if (!data.hasContent) return

    val backgroundColor = if (isMe) {
        Color.White.copy(alpha = 0.1f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val borderColor = if (isMe) {
        Color.White.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = modifier
            .widthIn(max = maxWidth)
            .then(
                if (onTap != null) Modifier.clickable { onTap() }
                else Modifier
            ),
        shape = RoundedCornerShape(borderRadius),
        color = backgroundColor
    ) {
        Row(modifier = Modifier.padding(start = 3.dp)) {
            // Left border
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .then(
                        if (largeImage && data.imageUrl != null) {
                            Modifier.height(180.dp)
                        } else {
                            Modifier.height(80.dp)
                        }
                    )
                    .background(borderColor)
            )

            if (largeImage) {
                LargeLayout(
                    data = data,
                    isMe = isMe,
                    showCloseButton = showCloseButton,
                    onClose = onClose
                )
            } else {
                CompactLayout(
                    data = data,
                    isMe = isMe,
                    showCloseButton = showCloseButton,
                    onClose = onClose
                )
            }
        }
    }
}

@Composable
private fun LargeLayout(
    data: LinkPreviewData,
    isMe: Boolean,
    showCloseButton: Boolean,
    onClose: (() -> Unit)?
) {
    Column {
        // Large image
        if (data.imageUrl != null) {
            SubcomposeAsyncImage(
                model = data.imageUrl,
                contentDescription = data.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.91f)
                    .clip(RoundedCornerShape(topEnd = 12.dp)),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.91f)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.91f)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ImageNotSupported,
                            contentDescription = "Image failed to load"
                        )
                    }
                }
            )
        }

        // Content
        Column(modifier = Modifier.padding(12.dp)) {
            // Site name with favicon
            if (data.siteName != null || data.favicon != null) {
                SiteInfo(data = data, isMe = isMe)
            }

            // Title
            if (data.title != null) {
                if (data.siteName != null) Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Description
            if (data.description != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = data.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isMe) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Close button
            if (showCloseButton && onClose != null) {
                CloseButton(isMe = isMe, onClose = onClose)
            }
        }
    }
}

@Composable
private fun CompactLayout(
    data: LinkPreviewData,
    isMe: Boolean,
    showCloseButton: Boolean,
    onClose: (() -> Unit)?
) {
    Row(
        modifier = Modifier.padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Thumbnail image
        if (data.imageUrl != null) {
            SubcomposeAsyncImage(
                model = data.imageUrl,
                contentDescription = data.title,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ImageNotSupported,
                            contentDescription = "Image failed to load",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )
            Spacer(modifier = Modifier.width(10.dp))
        }

        // Text content
        Column(modifier = Modifier.weight(1f)) {
            // Site name with favicon
            if (data.siteName != null || data.favicon != null) {
                SiteInfo(data = data, isMe = isMe)
            }

            // Title
            if (data.title != null) {
                if (data.siteName != null) Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Description
            if (data.description != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = data.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isMe) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Close button
        if (showCloseButton && onClose != null) {
            CloseButton(isMe = isMe, onClose = onClose)
        }
    }
}

@Composable
private fun SiteInfo(data: LinkPreviewData, isMe: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (data.favicon != null) {
            AsyncImage(
                model = data.favicon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        if (data.siteName != null) {
            Text(
                text = data.siteName.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isMe) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun CloseButton(isMe: Boolean, onClose: () -> Unit) {
    IconButton(
        onClick = onClose,
        modifier = Modifier.size(24.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Remove preview",
            tint = if (isMe) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * A widget for displaying link previews while composing a message.
 *
 * @param data The preview data to display.
 * @param isLoading Whether the preview is loading.
 * @param onRemove Called when the preview is removed.
 * @param modifier Modifier for the component.
 */
@Composable
fun LinkPreviewComposer(
    data: LinkPreviewData? = null,
    isLoading: Boolean = false,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Loading preview...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    if (data == null || !data.hasContent) return

    LinkPreview(
        data = data,
        showCloseButton = true,
        onClose = onRemove,
        modifier = modifier
    )
}

/**
 * Utility to extract URLs from text.
 */
object LinkExtractor {
    private val urlPattern = Regex(
        """https?://(www\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_+.~#?&/=]*)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Extracts all URLs from the given text.
     */
    fun extractUrls(text: String): List<String> {
        return urlPattern.findAll(text).map { it.value }.toList()
    }

    /**
     * Extracts the first URL from the given text.
     */
    fun extractFirstUrl(text: String): String? {
        return urlPattern.find(text)?.value
    }

    /**
     * Checks if the text contains any URLs.
     */
    fun containsUrl(text: String): Boolean {
        return urlPattern.containsMatchIn(text)
    }
}
