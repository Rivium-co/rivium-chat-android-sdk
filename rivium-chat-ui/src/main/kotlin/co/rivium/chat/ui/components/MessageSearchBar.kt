package co.rivium.chat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A search bar widget for searching messages in a chat.
 * Similar to the search functionality in Telegram and WhatsApp.
 *
 * @param onQueryChanged Called when the search query changes.
 * @param onSubmitted Called when search is submitted.
 * @param onClose Called when the search bar is closed.
 * @param onPrevious Called to navigate to the previous result.
 * @param onNext Called to navigate to the next result.
 * @param currentResult Current result index (1-based).
 * @param totalResults Total number of results.
 * @param isSearching Whether search is currently in progress.
 * @param placeholder Placeholder text for the search field.
 * @param autofocus Whether to auto-focus the search field.
 * @param debounceDuration Debounce duration for search queries in milliseconds.
 * @param modifier Modifier for the component.
 */
@Composable
fun MessageSearchBar(
    onQueryChanged: ((String) -> Unit)? = null,
    onSubmitted: ((String) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    currentResult: Int = 0,
    totalResults: Int = 0,
    isSearching: Boolean = false,
    placeholder: String = "Search messages...",
    autofocus: Boolean = true,
    debounceDuration: Long = 300,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Autofocus
    LaunchedEffect(autofocus) {
        if (autofocus) {
            focusRequester.requestFocus()
        }
    }

    // Debounced search
    LaunchedEffect(query) {
        if (query.isNotEmpty()) {
            delay(debounceDuration)
            onQueryChanged?.invoke(query)
        } else {
            onQueryChanged?.invoke("")
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back/Close button
            IconButton(onClick = { onClose?.invoke() }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Close search"
                )
            }

            // Search field
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { onSubmitted?.invoke(query) }
                ),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            )

            // Loading indicator or results count
            when {
                isSearching -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
                totalResults > 0 -> {
                    Text(
                        text = "$currentResult/$totalResults",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
                query.isNotEmpty() -> {
                    Text(
                        text = "No results",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            // Clear button
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        query = ""
                        focusRequester.requestFocus()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Navigation buttons
            if (totalResults > 1) {
                IconButton(
                    onClick = { onPrevious?.invoke() },
                    enabled = currentResult > 1
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Previous result"
                    )
                }
                IconButton(
                    onClick = { onNext?.invoke() },
                    enabled = currentResult < totalResults
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Next result"
                    )
                }
            }
        }

        // Bottom border
        Divider(
            modifier = Modifier.align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

private fun Modifier.align(alignment: Alignment): Modifier = this

/**
 * An overlay search bar that slides down from the top.
 *
 * @param visible Whether the search bar is visible.
 * @param onQueryChanged Called when the search query changes.
 * @param onDismiss Called when the overlay is dismissed.
 * @param currentResult Current result index.
 * @param totalResults Total number of results.
 * @param isSearching Whether search is in progress.
 * @param onPrevious Called to navigate to previous result.
 * @param onNext Called to navigate to next result.
 * @param modifier Modifier for the component.
 */
@Composable
fun MessageSearchOverlay(
    visible: Boolean,
    onQueryChanged: ((String) -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    currentResult: Int = 0,
    totalResults: Int = 0,
    isSearching: Boolean = false,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
        modifier = modifier
    ) {
        Surface(shadowElevation = 4.dp) {
            MessageSearchBar(
                onQueryChanged = onQueryChanged,
                onClose = onDismiss,
                currentResult = currentResult,
                totalResults = totalResults,
                isSearching = isSearching,
                onPrevious = onPrevious,
                onNext = onNext
            )
        }
    }
}

/**
 * Highlighted text widget for search results.
 *
 * @param text The full text to display.
 * @param query The search query to highlight.
 * @param style Style for non-highlighted text.
 * @param highlightStyle Style for highlighted text.
 * @param highlightBackgroundColor Background color for highlighted text.
 * @param maxLines Maximum lines to display.
 * @param overflow Text overflow behavior.
 * @param modifier Modifier for the component.
 */
@Composable
fun HighlightedSearchText(
    text: String,
    query: String,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    highlightStyle: TextStyle? = null,
    highlightBackgroundColor: Color? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    modifier: Modifier = Modifier
) {
    if (query.isEmpty()) {
        Text(
            text = text,
            style = style,
            maxLines = maxLines,
            overflow = overflow,
            modifier = modifier
        )
        return
    }

    val actualHighlightStyle = highlightStyle ?: style.copy(
        fontWeight = FontWeight.Bold,
        background = highlightBackgroundColor ?: MaterialTheme.colorScheme.primaryContainer
    )

    val annotatedString = buildAnnotatedString {
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()

        var start = 0
        var index = lowerText.indexOf(lowerQuery)

        while (index != -1) {
            // Add non-highlighted text before match
            if (index > start) {
                withStyle(SpanStyle()) {
                    append(text.substring(start, index))
                }
            }

            // Add highlighted match
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    background = highlightBackgroundColor ?: MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                append(text.substring(index, index + query.length))
            }

            start = index + query.length
            index = lowerText.indexOf(lowerQuery, start)
        }

        // Add remaining text
        if (start < text.length) {
            append(text.substring(start))
        }
    }

    Text(
        text = annotatedString,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier
    )
}
