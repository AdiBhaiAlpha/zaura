package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.QueryIntentType
import com.example.core.search.QueryClassifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZauraTopBar(
    url: String,
    isLoading: Boolean,
    progress: Int,
    isSecure: Boolean,
    isPrivate: Boolean,
    onNavigate: (String) -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onVoiceSearch: () -> Unit,
    onOpenAiSheet: () -> Unit,
    onSecurityInfoClick: () -> Unit,
    isBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var textInput by remember(url) { mutableStateOf(if (url == "about:blank") "" else url) }
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val classified = remember(textInput) {
        QueryClassifier.classify(textInput)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Omnibox Container (Sharp UI: 10-12dp corner radius, flat container, crisp border)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("zaura_omnibox_container"),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Security Lock / Intent Icon
                    IconButton(
                        onClick = onSecurityInfoClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        if (isPrivate) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Private Mode",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(15.dp)
                            )
                        } else if (isSecure) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Secure Connection",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(15.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Insecure",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    // Query Intent Badge when typing
                    if (isFocused && textInput.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (classified.intent) {
                                QueryIntentType.URL -> MaterialTheme.colorScheme.primaryContainer
                                QueryIntentType.QUESTION -> MaterialTheme.colorScheme.secondaryContainer
                                QueryIntentType.AI_COMMAND -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.surface
                            },
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = when (classified.intent) {
                                    QueryIntentType.URL -> "Go"
                                    QueryIntentType.QUESTION -> "Ask AI"
                                    QueryIntentType.AI_COMMAND -> "AI"
                                    else -> "Search"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Input field
                    TextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = {
                            Text(
                                text = if (isPrivate) "Private search or type URL" else "Search or type URL",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                maxLines = 1
                            )
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                                onNavigate(classified.normalizedTarget)
                            }
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { isFocused = it.isFocused }
                            .testTag("omnibox_input_field")
                    )

                    // Bookmarking UI toggle in address bar
                    if (url.isNotBlank() && url != "about:blank") {
                        IconButton(
                            onClick = onToggleBookmark,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("omnibox_bookmark_button")
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isBookmarked) "Remove Bookmark" else "Save Bookmark",
                                tint = if (isBookmarked) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    // Clear button or Voice search
                    if (textInput.isNotBlank()) {
                        IconButton(
                            onClick = { textInput = "" },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onVoiceSearch,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Reload / Stop button
            if (isLoading) {
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Stop Loading",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(19.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = onReload,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reload Page",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // Compact sharp AI trigger button (8dp rounded)
            Surface(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenAiSheet)
                    .testTag("topbar_ai_button"),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Zaura AI",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        // Linear Progress Indicator when loading (Height: 2dp, Zaura Cyan)
        if (isLoading && progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
        }
    }
}
