package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.ai.AIAnswerResult
import com.example.core.model.SearchResult
import com.example.core.model.SearchType
import com.example.ui.components.*

@Composable
fun SearchScreen(
    query: String,
    searchType: SearchType,
    searchResults: List<SearchResult>,
    aiAnswerResult: AIAnswerResult?,
    isLoadingResults: Boolean,
    isLoadingAi: Boolean,
    onTabSelected: (SearchType) -> Unit,
    onResultClick: (String) -> Unit,
    onFollowupClick: (String) -> Unit,
    onDeepResearchClick: () -> Unit,
    onStopAiClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedPreviewImage by remember { mutableStateOf<SearchResult?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Search Type Tabs (Web, News, Images, Videos)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                ZauraChip(
                    text = "Web",
                    selected = searchType == SearchType.WEB,
                    icon = Icons.Default.Public,
                    onClick = { onTabSelected(SearchType.WEB) }
                )
            }
            item {
                ZauraChip(
                    text = "News",
                    selected = searchType == SearchType.NEWS,
                    icon = Icons.Default.Feed,
                    onClick = { onTabSelected(SearchType.NEWS) }
                )
            }
            item {
                ZauraChip(
                    text = "Images",
                    selected = searchType == SearchType.IMAGES,
                    icon = Icons.Default.Image,
                    onClick = { onTabSelected(SearchType.IMAGES) }
                )
            }
            item {
                ZauraChip(
                    text = "Videos",
                    selected = searchType == SearchType.VIDEOS,
                    icon = Icons.Default.PlayCircle,
                    onClick = { onTabSelected(SearchType.VIDEOS) }
                )
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )

        if (isLoadingResults && searchResults.isEmpty()) {
            ZauraLoadingState(
                message = when (searchType) {
                    SearchType.IMAGES -> "Finding images for \"$query\"…"
                    SearchType.VIDEOS -> "Finding videos for \"$query\"…"
                    else -> "Searching web for \"$query\"…"
                }
            )
        } else if (!isLoadingResults && searchResults.isEmpty()) {
            ZauraEmptyState(
                title = "No Results Found",
                subtitle = "We couldn't find ${searchType.name.lowercase()} matches for \"$query\".",
                icon = when (searchType) {
                    SearchType.IMAGES -> Icons.Default.BrokenImage
                    SearchType.VIDEOS -> Icons.Default.VideoLibrary
                    else -> Icons.Default.SearchOff
                }
            )
        } else {
            when (searchType) {
                SearchType.IMAGES -> {
                    ImageGridSection(
                        images = searchResults,
                        query = query,
                        onImageClick = { item -> selectedPreviewImage = item }
                    )
                }

                SearchType.VIDEOS -> {
                    VideoFeedSection(
                        videos = searchResults,
                        query = query,
                        onVideoClick = { url -> onResultClick(url) }
                    )
                }

                else -> {
                    WebResultsSection(
                        query = query,
                        searchType = searchType,
                        searchResults = searchResults,
                        aiAnswerResult = aiAnswerResult,
                        isLoadingAi = isLoadingAi,
                        onResultClick = onResultClick,
                        onFollowupClick = onFollowupClick,
                        onDeepResearchClick = onDeepResearchClick,
                        onStopAiClick = onStopAiClick
                    )
                }
            }
        }
    }

    // Full-screen Image Preview Dialog
    selectedPreviewImage?.let { item ->
        ImagePreviewDialog(
            result = item,
            onDismiss = { selectedPreviewImage = null },
            onOpenInBrowser = { url ->
                selectedPreviewImage = null
                onResultClick(url)
            }
        )
    }
}

/**
 * Dedicated Image Section: Clean 2-column grid with 8dp rounded corners.
 */
@Composable
private fun ImageGridSection(
    images: List<SearchResult>,
    query: String,
    onImageClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(images, key = { it.id }) { item ->
                val displayImg = item.imageUrl?.ifBlank { item.thumbnail } ?: item.thumbnail ?: ""
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clickable { onImageClick(item) }
                        .testTag("image_result_${item.id}"),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = displayImg,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Video Section: Compact 16:9 video preview cards.
 */
@Composable
private fun VideoFeedSection(
    videos: List<SearchResult>,
    query: String,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)
    ) {
        items(videos, key = { it.id }) { video ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onVideoClick(video.url) }
                    .testTag("video_result_${video.id}"),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                    ) {
                        if (!video.thumbnail.isNullOrBlank()) {
                            AsyncImage(
                                model = video.thumbnail,
                                contentDescription = video.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Play Button Overlay
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .size(42.dp)
                                .align(Alignment.Center)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        // Duration Badge
                        val duration = video.videoDuration ?: "HD"
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.8f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                        ) {
                            Text(
                                text = duration,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = video.snippet.ifBlank { "${video.source} • Video" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Standard Web/News Section: Information-dense real search engine format.
 * Domain / site name -> Title (strong contrast) -> Snippet -> Subtle divider.
 */
@Composable
private fun WebResultsSection(
    query: String,
    searchType: SearchType,
    searchResults: List<SearchResult>,
    aiAnswerResult: AIAnswerResult?,
    isLoadingAi: Boolean,
    onResultClick: (String) -> Unit,
    onFollowupClick: (String) -> Unit,
    onDeepResearchClick: () -> Unit,
    onStopAiClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp)
    ) {
        // AI Grounded Synthesis Box
        item {
            if (isLoadingAi && aiAnswerResult == null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Synthesizing Zaura AI Answer…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = onStopAiClick,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = "Stop",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            } else if (aiAnswerResult != null) {
                ZauraAIAnswerCard(
                    answer = aiAnswerResult.answer,
                    citations = aiAnswerResult.citations,
                    followups = aiAnswerResult.followups,
                    isGenerating = isLoadingAi,
                    modelName = aiAnswerResult.modelUsed,
                    onCitationClick = onResultClick,
                    onFollowupClick = onFollowupClick,
                    onDeepResearchClick = onDeepResearchClick,
                    onStopClick = onStopAiClick
                )
            }
        }

        // Web Search Results (Real Search Engine Density)
        items(searchResults, key = { it.id }) { result ->
            SearchResultRow(
                result = result,
                onClick = { onResultClick(result.url) }
            )
        }
    }
}

/**
 * Information-dense Search Engine row:
 * Domain / site name
 * Title (strong contrast, clickable)
 * Snippet (readable gray)
 * Subtle divider
 */
@Composable
fun SearchResultRow(
    result: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .testTag("search_result_item_${result.id}")
    ) {
        // 1. Domain / site name + Icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = result.domain.ifBlank { result.source },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        // 2. Title (strong contrast, clickable)
        Text(
            text = result.title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 20.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // 3. Snippet (readable gray)
        if (result.snippet.isNotBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = result.snippet,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4. Subtle divider
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    }
}

/**
 * Image Preview Dialog
 */
@Composable
private fun ImagePreviewDialog(
    result: SearchResult,
    onDismiss: () -> Unit,
    onOpenInBrowser: (String) -> Unit
) {
    val displayImg = result.imageUrl?.ifBlank { result.thumbnail } ?: result.thumbnail ?: ""

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = result.title.take(30),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = displayImg,
                        contentDescription = result.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { onOpenInBrowser(result.url) },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Visit Page")
                    }
                }
            }
        }
    }
}
