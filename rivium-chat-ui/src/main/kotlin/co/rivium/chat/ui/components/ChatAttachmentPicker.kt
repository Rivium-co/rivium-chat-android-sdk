package co.rivium.chat.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Attachment type selected by the user.
 */
enum class AttachmentType {
    CAMERA,
    GALLERY,
    VIDEO,
    FILE,
    LOCATION,
    CONTACT
}

/**
 * Result of an attachment selection.
 */
data class AttachmentResult(
    val type: AttachmentType,
    val uri: Uri? = null,
    val uris: List<Uri>? = null,
    val metadata: Map<String, Any>? = null
)

/**
 * A bottom sheet picker for chat attachments.
 * Supports camera, gallery, video, files, location, and contacts.
 *
 * @param onAttachmentSelected Called when an attachment is selected.
 * @param allowMultiple Whether to allow multiple file selection.
 * @param showCamera Whether to show the camera option.
 * @param showGallery Whether to show the gallery option.
 * @param showVideo Whether to show the video option.
 * @param showFile Whether to show the file option.
 * @param showLocation Whether to show the location option.
 * @param showContact Whether to show the contact option.
 * @param onDismiss Called when the picker is dismissed.
 * @param modifier Modifier for the component.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatAttachmentPicker(
    onAttachmentSelected: ((AttachmentResult) -> Unit)? = null,
    allowMultiple: Boolean = false,
    showCamera: Boolean = true,
    showGallery: Boolean = true,
    showVideo: Boolean = true,
    showFile: Boolean = true,
    showLocation: Boolean = false,
    showContact: Boolean = false,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState()

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            // Note: In production, you'd need to provide the URI beforehand
            // This is a simplified implementation
            onAttachmentSelected?.invoke(
                AttachmentResult(type = AttachmentType.CAMERA)
            )
        }
        onDismiss()
    }

    // Gallery launcher (single)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            onAttachmentSelected?.invoke(
                AttachmentResult(type = AttachmentType.GALLERY, uri = it)
            )
        }
        onDismiss()
    }

    // Gallery launcher (multiple)
    val multiGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAttachmentSelected?.invoke(
                AttachmentResult(type = AttachmentType.GALLERY, uris = uris)
            )
        }
        onDismiss()
    }

    // Video launcher
    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            onAttachmentSelected?.invoke(
                AttachmentResult(type = AttachmentType.VIDEO, uri = it)
            )
        }
        onDismiss()
    }

    // File launcher
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            onAttachmentSelected?.invoke(
                AttachmentResult(type = AttachmentType.FILE, uri = it)
            )
        }
        onDismiss()
    }

    // Multiple files launcher
    val multiFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAttachmentSelected?.invoke(
                AttachmentResult(type = AttachmentType.FILE, uris = uris)
            )
        }
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        ChatAttachmentPickerContent(
            showCamera = showCamera,
            showGallery = showGallery,
            showVideo = showVideo,
            showFile = showFile,
            showLocation = showLocation,
            showContact = showContact,
            onCameraClick = {
                // In production, create a temp file and pass its URI to cameraLauncher
                onAttachmentSelected?.invoke(
                    AttachmentResult(
                        type = AttachmentType.CAMERA,
                        metadata = mapOf("needsCameraPermission" to true)
                    )
                )
                onDismiss()
            },
            onGalleryClick = {
                if (allowMultiple) {
                    multiGalleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                } else {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            },
            onVideoClick = {
                videoLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            onFileClick = {
                if (allowMultiple) {
                    multiFileLauncher.launch(arrayOf("*/*"))
                } else {
                    fileLauncher.launch(arrayOf("*/*"))
                }
            },
            onLocationClick = {
                onAttachmentSelected?.invoke(
                    AttachmentResult(
                        type = AttachmentType.LOCATION,
                        metadata = mapOf("needsLocationPermission" to true)
                    )
                )
                onDismiss()
            },
            onContactClick = {
                onAttachmentSelected?.invoke(
                    AttachmentResult(
                        type = AttachmentType.CONTACT,
                        metadata = mapOf("needsContactsPermission" to true)
                    )
                )
                onDismiss()
            }
        )
    }
}

/**
 * Content for the attachment picker (can be used standalone).
 */
@Composable
fun ChatAttachmentPickerContent(
    showCamera: Boolean = true,
    showGallery: Boolean = true,
    showVideo: Boolean = true,
    showFile: Boolean = true,
    showLocation: Boolean = false,
    showContact: Boolean = false,
    onCameraClick: () -> Unit = {},
    onGalleryClick: () -> Unit = {},
    onVideoClick: () -> Unit = {},
    onFileClick: () -> Unit = {},
    onLocationClick: () -> Unit = {},
    onContactClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val items = buildList {
        if (showCamera) {
            add(AttachmentOption(
                type = AttachmentType.CAMERA,
                icon = Icons.Default.CameraAlt,
                label = "Camera",
                color = Color(0xFFE53935),
                onClick = onCameraClick
            ))
        }
        if (showGallery) {
            add(AttachmentOption(
                type = AttachmentType.GALLERY,
                icon = Icons.Default.Photo,
                label = "Gallery",
                color = Color(0xFF8E24AA),
                onClick = onGalleryClick
            ))
        }
        if (showVideo) {
            add(AttachmentOption(
                type = AttachmentType.VIDEO,
                icon = Icons.Default.Videocam,
                label = "Video",
                color = Color(0xFFE91E63),
                onClick = onVideoClick
            ))
        }
        if (showFile) {
            add(AttachmentOption(
                type = AttachmentType.FILE,
                icon = Icons.Default.InsertDriveFile,
                label = "Document",
                color = Color(0xFF3949AB),
                onClick = onFileClick
            ))
        }
        if (showLocation) {
            add(AttachmentOption(
                type = AttachmentType.LOCATION,
                icon = Icons.Default.LocationOn,
                label = "Location",
                color = Color(0xFF43A047),
                onClick = onLocationClick
            ))
        }
        if (showContact) {
            add(AttachmentOption(
                type = AttachmentType.CONTACT,
                icon = Icons.Default.Person,
                label = "Contact",
                color = Color(0xFF1E88E5),
                onClick = onContactClick
            ))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        // Handle bar
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 12.dp)
        ) {
            Surface(
                modifier = Modifier.size(width = 40.dp, height = 4.dp),
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            ) {}
        }

        // Title
        Text(
            text = "Share",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Grid of options
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items) { option ->
                AttachmentGridItem(option = option)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

private data class AttachmentOption(
    val type: AttachmentType,
    val icon: ImageVector,
    val label: String,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
private fun AttachmentGridItem(option: AttachmentOption) {
    Column(
        modifier = Modifier
            .clickable(onClick = option.onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = option.color.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = option.icon,
                    contentDescription = option.label,
                    tint = option.color,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = option.label,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
