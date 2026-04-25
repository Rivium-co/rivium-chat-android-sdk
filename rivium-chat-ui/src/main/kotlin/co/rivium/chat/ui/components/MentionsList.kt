package co.rivium.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * A user to display in the mentions list.
 */
data class MentionUser(
    val id: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val isOnline: Boolean = false
)

/**
 * A widget that displays an autocomplete list for @mentions.
 * Shows when the user types '@' in the input field.
 *
 * @param users List of users that can be mentioned.
 * @param query The current search query (text after '@').
 * @param onUserSelected Called when a user is selected from the list.
 * @param maxSuggestions Maximum number of suggestions to show.
 * @param itemBuilder Custom item builder for mention suggestions.
 * @param backgroundColor Background color of the list.
 * @param borderRadius Border radius of the list container.
 * @param elevation Elevation of the list container.
 * @param modifier Modifier for the component.
 */
@Composable
fun MentionsList(
    users: List<MentionUser>,
    query: String,
    onUserSelected: (MentionUser) -> Unit,
    maxSuggestions: Int = 5,
    itemBuilder: (@Composable (MentionUser) -> Unit)? = null,
    backgroundColor: Color? = null,
    borderRadius: Dp = 12.dp,
    elevation: Dp = 4.dp,
    modifier: Modifier = Modifier
) {
    val filteredUsers = remember(users, query, maxSuggestions) {
        if (query.isEmpty()) {
            users.take(maxSuggestions)
        } else {
            val lowerQuery = query.lowercase()
            users.filter { it.displayName.lowercase().contains(lowerQuery) }
                .take(maxSuggestions)
        }
    }

    if (filteredUsers.isEmpty()) return

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(borderRadius),
        color = backgroundColor ?: MaterialTheme.colorScheme.surface,
        shadowElevation = elevation,
        tonalElevation = elevation
    ) {
        LazyColumn(
            modifier = Modifier
                .heightIn(max = 200.dp)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(borderRadius)
                )
        ) {
            items(filteredUsers) { user ->
                if (itemBuilder != null) {
                    Box(
                        modifier = Modifier.clickable { onUserSelected(user) }
                    ) {
                        itemBuilder(user)
                    }
                } else {
                    MentionUserTile(
                        user = user,
                        query = query,
                        onClick = { onUserSelected(user) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MentionUserTile(
    user: MentionUser,
    query: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with online indicator
        Box {
            if (user.avatarUrl != null) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = user.displayName,
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
                        Text(
                            text = user.displayName.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Online indicator
            if (user.isOnline) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.BottomEnd)
                        .background(Color.Green, CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Name with highlighted query
        HighlightedText(
            text = user.displayName,
            query = query,
            style = MaterialTheme.typography.bodyMedium,
            highlightColor = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun HighlightedText(
    text: String,
    query: String,
    style: androidx.compose.ui.text.TextStyle,
    highlightColor: Color
) {
    if (query.isEmpty()) {
        Text(text = text, style = style)
        return
    }

    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    val startIndex = lowerText.indexOf(lowerQuery)

    if (startIndex == -1) {
        Text(text = text, style = style)
        return
    }

    val endIndex = startIndex + query.length

    Text(
        text = buildAnnotatedString {
            if (startIndex > 0) {
                append(text.substring(0, startIndex))
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor)) {
                append(text.substring(startIndex, endIndex))
            }
            if (endIndex < text.length) {
                append(text.substring(endIndex))
            }
        },
        style = style
    )
}

/**
 * Controller to manage mentions in a text input.
 * Tracks @ mentions and provides the current query.
 */
class MentionsController(
    private val availableUsers: List<MentionUser>
) {
    var currentQuery: String? by mutableStateOf(null)
        private set

    private var mentionStartIndex: Int? = null

    /**
     * Whether the user is currently typing a mention.
     */
    val isMentioning: Boolean
        get() = currentQuery != null

    /**
     * Call this when the text changes to update mention state.
     */
    fun onTextChanged(text: String, cursorPosition: Int) {
        if (cursorPosition == 0 || cursorPosition > text.length) {
            currentQuery = null
            mentionStartIndex = null
            return
        }

        val textBeforeCursor = text.substring(0, cursorPosition)
        val lastAtIndex = textBeforeCursor.lastIndexOf('@')

        if (lastAtIndex == -1) {
            currentQuery = null
            mentionStartIndex = null
            return
        }

        // Check if there's a space between @ and cursor
        val textAfterAt = textBeforeCursor.substring(lastAtIndex + 1)
        if (textAfterAt.contains(' ') || textAfterAt.contains('\n')) {
            currentQuery = null
            mentionStartIndex = null
            return
        }

        // Check if @ is at start or preceded by space
        if (lastAtIndex > 0 && text[lastAtIndex - 1] != ' ' && text[lastAtIndex - 1] != '\n') {
            currentQuery = null
            mentionStartIndex = null
            return
        }

        mentionStartIndex = lastAtIndex
        currentQuery = textAfterAt
    }

    /**
     * Insert a mention into the text.
     * Returns the new text with the mention inserted.
     */
    fun insertMention(text: String, cursorPosition: Int, user: MentionUser): Pair<String, Int> {
        val startIndex = mentionStartIndex ?: return text to cursorPosition

        val beforeMention = text.substring(0, startIndex)
        val afterMention = if (cursorPosition < text.length) text.substring(cursorPosition) else ""

        val mentionText = "@${user.displayName} "
        val newText = beforeMention + mentionText + afterMention
        val newCursorPosition = beforeMention.length + mentionText.length

        currentQuery = null
        mentionStartIndex = null

        return newText to newCursorPosition
    }

    /**
     * Clear the current mention state.
     */
    fun clear() {
        currentQuery = null
        mentionStartIndex = null
    }
}

/**
 * Remember a MentionsController.
 */
@Composable
fun rememberMentionsController(
    availableUsers: List<MentionUser>
): MentionsController {
    return remember(availableUsers) {
        MentionsController(availableUsers)
    }
}
