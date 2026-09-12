package com.example.ui.browser

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.SearchType
import com.example.ui.components.AskPageBottomSheet
import com.example.ui.components.PrivacyShieldDialog
import com.example.ui.components.ZauraBottomBar
import com.example.ui.components.ZauraTopBar
import com.example.ui.screens.*

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bookmarksList by viewModel.bookmarksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val historyList by viewModel.historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val downloadsList by viewModel.downloadsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val context = LocalContext.current
    var showMoreMenu by remember { mutableStateOf(false) }

    // Find in Page state
    var isFindInPageActive by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }

    // Voice Search Speech Recognizer Launcher
    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.navigateTo(spokenText)
            }
        }
    }

    fun launchVoiceSearch() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Search with Zaura or ask a question")
            }
            voiceSearchLauncher.launch(intent)
        } catch (_: Exception) {}
    }

    // Active WebView reference for executing page scripts / extraction
    var currentWebViewRef: WebView? by remember { mutableStateOf(null) }

    // Handle Hardware Back Button
    BackHandler {
        if (isFindInPageActive) {
            isFindInPageActive = false
            currentWebViewRef?.clearMatches()
        } else {
            when (uiState.activeScreen) {
                ActiveScreen.HOME -> {
                    (context as? Activity)?.finish()
                }
                ActiveScreen.WEBVIEW -> {
                    if (currentWebViewRef?.canGoBack() == true) {
                        currentWebViewRef?.goBack()
                    } else {
                        viewModel.setScreen(ActiveScreen.HOME)
                    }
                }
                else -> {
                    if (uiState.currentUrl != "about:blank") {
                        viewModel.setScreen(ActiveScreen.WEBVIEW)
                    } else {
                        viewModel.setScreen(ActiveScreen.HOME)
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Persistent TopBar (Omnibox, SSL lock, Progress, Voice, AI button)
            ZauraTopBar(
                url = uiState.currentUrl,
                isLoading = uiState.isLoading,
                progress = uiState.progress,
                isSecure = uiState.isSecure,
                isPrivate = uiState.isPrivate,
                onNavigate = { query -> viewModel.navigateTo(query) },
                onReload = { currentWebViewRef?.reload() },
                onStop = { currentWebViewRef?.stopLoading() },
                onVoiceSearch = { launchVoiceSearch() },
                onOpenAiSheet = {
                    currentWebViewRef?.evaluateJavascript(
                        "(function() { return document.documentElement.innerText; })();"
                    ) { rawText ->
                        viewModel.showAiSheet()
                    }
                },
                onSecurityInfoClick = { viewModel.showPrivacyDialog() },
                isBookmarked = uiState.isBookmarked,
                onToggleBookmark = { viewModel.toggleBookmarkCurrentPage() }
            )

            // Find In Page Bar (if active)
            AnimatedVisibility(visible = isFindInPageActive) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = findQuery,
                            onValueChange = {
                                findQuery = it
                                currentWebViewRef?.findAllAsync(it)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { currentWebViewRef?.findNext(true) }),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { currentWebViewRef?.findNext(false) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match", modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { currentWebViewRef?.findNext(true) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match", modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = {
                                isFindInPageActive = false
                                currentWebViewRef?.clearMatches()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close find", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Main Content Area
            Box(modifier = Modifier.weight(1f)) {
                when (uiState.activeScreen) {
                    ActiveScreen.HOME -> {
                        HomeScreen(
                            isPrivate = uiState.isPrivate,
                            onNavigate = { url -> viewModel.navigateTo(url) },
                            onPromptClick = { prompt -> viewModel.navigateTo(prompt) },
                            onVoiceSearch = { launchVoiceSearch() },
                            onOpenResearch = { viewModel.setScreen(ActiveScreen.RESEARCH) }
                        )
                    }

                    ActiveScreen.WEBVIEW -> {
                        AndroidView(
                            factory = { ctx ->
                                viewModel.webViewPool.getOrCreateWebView(
                                    tabId = uiState.activeTabId,
                                    isPrivate = uiState.isPrivate,
                                    onProgressChanged = { p -> viewModel.onProgressChanged(p) },
                                    onTitleReceived = { t -> viewModel.onPageFinished(uiState.currentUrl, t) },
                                    onPageStarted = { u -> viewModel.onPageStarted(u) },
                                    onPageFinished = { u ->
                                        viewModel.onPageFinished(u)
                                        viewModel.updateNavigationState(
                                            canGoBack = currentWebViewRef?.canGoBack() == true,
                                            canGoForward = currentWebViewRef?.canGoForward() == true
                                        )
                                    },
                                    onUrlChanged = { u -> viewModel.onPageStarted(u) }
                                ).apply {
                                    currentWebViewRef = this
                                    loadUrl(uiState.currentUrl)
                                }
                            },
                            update = { webView ->
                                currentWebViewRef = webView
                                if (webView.url != uiState.currentUrl && uiState.currentUrl != "about:blank") {
                                    webView.loadUrl(uiState.currentUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    ActiveScreen.SEARCH -> {
                        SearchScreen(
                            query = uiState.searchQuery,
                            searchType = uiState.searchType,
                            searchResults = uiState.searchResults,
                            aiAnswerResult = uiState.aiAnswerResult,
                            isLoadingResults = uiState.isLoadingSearchResults,
                            isLoadingAi = uiState.isLoadingAiAnswer,
                            onTabSelected = { type ->
                                viewModel.performSearch(uiState.searchQuery, type, triggerAiAnswer = (type == SearchType.WEB))
                            },
                            onResultClick = { url -> viewModel.loadWebUrl(url) },
                            onFollowupClick = { query -> viewModel.performSearch(query, SearchType.WEB, triggerAiAnswer = true) },
                            onDeepResearchClick = { viewModel.openDeepResearch(uiState.searchQuery) },
                            onStopAiClick = { viewModel.stopAiGeneration() }
                        )
                    }

                    ActiveScreen.TAB_MANAGER -> {
                        TabManagerScreen(
                            tabs = uiState.tabs,
                            activeTabId = uiState.activeTabId,
                            isPrivateModeActive = uiState.isPrivate,
                            onTabSelected = { tabId -> viewModel.switchTab(tabId) },
                            onCloseTab = { tabId -> viewModel.closeTab(tabId) },
                            onNewTab = { isPrivate -> viewModel.openNewTab(isPrivate) },
                            onCloseAllTabs = { viewModel.closeAllTabs() },
                            onBackToBrowser = {
                                if (uiState.currentUrl == "about:blank") viewModel.setScreen(ActiveScreen.HOME)
                                else viewModel.setScreen(ActiveScreen.WEBVIEW)
                            }
                        )
                    }

                    ActiveScreen.BOOKMARKS_HISTORY -> {
                        BookmarksHistoryScreen(
                            bookmarks = bookmarksList,
                            history = historyList,
                            onOpenUrl = { url -> viewModel.loadWebUrl(url) },
                            onDeleteBookmark = { id -> viewModel.deleteBookmark(id) },
                            onDeleteHistory = { id -> viewModel.deleteHistory(id) },
                            onClearHistoryRange = { timestamp -> viewModel.clearHistorySince(timestamp) },
                            onBack = {
                                if (uiState.currentUrl == "about:blank") viewModel.setScreen(ActiveScreen.HOME)
                                else viewModel.setScreen(ActiveScreen.WEBVIEW)
                            }
                        )
                    }

                    ActiveScreen.DOWNLOADS -> {
                        DownloadsScreen(
                            downloads = downloadsList,
                            onOpenDownload = { /* handle file open */ },
                            onDeleteDownload = { /* handle delete */ },
                            onBack = {
                                if (uiState.currentUrl == "about:blank") viewModel.setScreen(ActiveScreen.HOME)
                                else viewModel.setScreen(ActiveScreen.WEBVIEW)
                            }
                        )
                    }

                    ActiveScreen.READER_MODE -> {
                        uiState.readerArticle?.let { article ->
                            ReaderModeScreen(
                                article = article,
                                onClose = { viewModel.setScreen(ActiveScreen.WEBVIEW) },
                                onSummarizeAi = {
                                    viewModel.summarizeCurrentPage(article.content)
                                    viewModel.showAiSheet()
                                },
                                onTranslateAi = {
                                    viewModel.translateCurrentPage(article.content, "Spanish")
                                    viewModel.showAiSheet()
                                }
                            )
                        }
                    }

                    ActiveScreen.RESEARCH -> {
                        ResearchScreen(
                            currentReport = uiState.researchReport,
                            isLoadingResearch = uiState.isLoadingResearch,
                            openTabs = uiState.tabs,
                            savedSessions = uiState.savedSessions,
                            onRunResearch = { q -> viewModel.openDeepResearch(q) },
                            onCompareTabs = { tabIds -> viewModel.compareTabs(tabIds) },
                            onOpenSourceUrl = { url -> viewModel.loadWebUrl(url) },
                            onLoadSavedSession = { session -> viewModel.openDeepResearch(session.query) },
                            onBack = {
                                if (uiState.currentUrl == "about:blank") viewModel.setScreen(ActiveScreen.HOME)
                                else viewModel.setScreen(ActiveScreen.WEBVIEW)
                            }
                        )
                    }

                    ActiveScreen.SETTINGS -> {
                        SettingsScreen(
                            isTrackerProtectionEnabled = uiState.isTrackerProtectionEnabled,
                            onToggleTrackerProtection = { enabled -> viewModel.toggleTrackerProtection(enabled) },
                            onClearAllData = { viewModel.clearAllData() },
                            onBack = {
                                if (uiState.currentUrl == "about:blank") viewModel.setScreen(ActiveScreen.HOME)
                                else viewModel.setScreen(ActiveScreen.WEBVIEW)
                            },
                            isAiEnabled = uiState.isAiEnabled,
                            selectedAiModel = uiState.selectedAiModel,
                            isAiStreamingEnabled = uiState.isAiStreamingEnabled,
                            isAiWebContextEnabled = uiState.isAiWebContextEnabled,
                            aiResponseStyle = uiState.aiResponseStyle,
                            customApiKey = uiState.customOpenRouterApiKey,
                            customProxyUrl = uiState.customProxyUrl,
                            onToggleAiEnabled = { viewModel.setAiEnabled(it) },
                            onSelectAiModel = { viewModel.setSelectedAiModel(it) },
                            onToggleStreaming = { viewModel.setAiStreamingEnabled(it) },
                            onToggleWebContext = { viewModel.setAiWebContextEnabled(it) },
                            onSelectResponseStyle = { viewModel.setAiResponseStyle(it) },
                            onSaveDevConfig = { key, proxy ->
                                viewModel.setCustomOpenRouterKey(key)
                                viewModel.setCustomProxyUrl(proxy)
                            },
                            onClearAiHistory = { viewModel.clearAiHistory() }
                        )
                    }
                }
            }

            // Bottom Navigation Bar
            ZauraBottomBar(
                canGoBack = currentWebViewRef?.canGoBack() == true || uiState.activeScreen != ActiveScreen.HOME,
                canGoForward = currentWebViewRef?.canGoForward() == true,
                tabCount = uiState.tabs.size,
                isPrivate = uiState.isPrivate,
                onBack = {
                    if (uiState.activeScreen == ActiveScreen.WEBVIEW && currentWebViewRef?.canGoBack() == true) {
                        currentWebViewRef?.goBack()
                    } else if (uiState.activeScreen != ActiveScreen.HOME) {
                        viewModel.setScreen(ActiveScreen.HOME)
                    }
                },
                onForward = {
                    if (uiState.activeScreen == ActiveScreen.WEBVIEW && currentWebViewRef?.canGoForward() == true) {
                        currentWebViewRef?.goForward()
                    }
                },
                onHome = { viewModel.setScreen(ActiveScreen.HOME) },
                onTabsClick = { viewModel.setScreen(ActiveScreen.TAB_MANAGER) },
                onMenuClick = { showMoreMenu = true }
            )
        }

        // Section 9: Clean browser menu (Compact vertical list)
        if (showMoreMenu) {
            DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .width(220.dp)
                    .align(Alignment.BottomEnd)
            ) {
                // 1. New tab
                DropdownMenuItem(
                    text = { Text("New tab", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        viewModel.openNewTab(isPrivate = false)
                        showMoreMenu = false
                    }
                )

                // 2. New private tab
                DropdownMenuItem(
                    text = { Text("New private tab", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        viewModel.openNewTab(isPrivate = true)
                        showMoreMenu = false
                    }
                )

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // 3. Bookmarks
                DropdownMenuItem(
                    text = { Text("Bookmarks", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        viewModel.setScreen(ActiveScreen.BOOKMARKS_HISTORY)
                        showMoreMenu = false
                    }
                )

                // 4. History
                DropdownMenuItem(
                    text = { Text("History", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        viewModel.setScreen(ActiveScreen.BOOKMARKS_HISTORY)
                        showMoreMenu = false
                    }
                )

                // 5. Downloads
                DropdownMenuItem(
                    text = { Text("Downloads", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        viewModel.setScreen(ActiveScreen.DOWNLOADS)
                        showMoreMenu = false
                    }
                )

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // 6. Find in page
                DropdownMenuItem(
                    text = { Text("Find in page", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        isFindInPageActive = true
                        showMoreMenu = false
                    }
                )

                // 7. Reader mode
                DropdownMenuItem(
                    text = { Text("Reader mode", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        currentWebViewRef?.evaluateJavascript(
                            "(function() { return document.documentElement.outerHTML; })();"
                        ) { rawHtml ->
                            val clean = rawHtml?.removeSurrounding("\"")?.replace("\\n", "\n")?.replace("\\\"", "\"") ?: ""
                            viewModel.openReaderMode(clean)
                            showMoreMenu = false
                        }
                    }
                )

                // 8. Desktop site
                DropdownMenuItem(
                    text = { Text(if (uiState.isDesktopMode) "Mobile site" else "Desktop site", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.DesktopWindows, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        viewModel.toggleDesktopMode()
                        showMoreMenu = false
                    }
                )

                // 9. Share
                DropdownMenuItem(
                    text = { Text("Share", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showMoreMenu = false
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, uiState.currentUrl)
                                putExtra(Intent.EXTRA_SUBJECT, uiState.currentTitle)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share"))
                        } catch (_: Exception) {}
                    }
                )

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // 10. Settings
                DropdownMenuItem(
                    text = { Text("Settings", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        viewModel.setScreen(ActiveScreen.SETTINGS)
                        showMoreMenu = false
                    }
                )
            }
        }

        // Contextual Page AI Bottom Sheet
        if (uiState.isAiSheetVisible) {
            AskPageBottomSheet(
                pageTitle = uiState.currentTitle,
                pageUrl = uiState.currentUrl,
                aiResponseText = uiState.aiSheetResponse,
                isLoading = uiState.isAiSheetLoading,
                onSummarize = {
                    currentWebViewRef?.evaluateJavascript(
                        "(function() { return document.documentElement.innerText; })();"
                    ) { text ->
                        val clean = text?.removeSurrounding("\"")?.replace("\\n", "\n") ?: ""
                        viewModel.summarizeCurrentPage(clean)
                    }
                },
                onExplainSimply = {
                    currentWebViewRef?.evaluateJavascript(
                        "(function() { return document.documentElement.innerText; })();"
                    ) { text ->
                        val clean = text?.removeSurrounding("\"")?.replace("\\n", "\n") ?: ""
                        viewModel.askAboutCurrentPage(clean, "Explain the key takeaways in 3 simple bullet points.")
                    }
                },
                onTranslate = { lang ->
                    currentWebViewRef?.evaluateJavascript(
                        "(function() { return document.documentElement.innerText; })();"
                    ) { text ->
                        val clean = text?.removeSurrounding("\"")?.replace("\\n", "\n") ?: ""
                        viewModel.translateCurrentPage(clean, lang)
                    }
                },
                onCustomQuestion = { question ->
                    currentWebViewRef?.evaluateJavascript(
                        "(function() { return document.documentElement.innerText; })();"
                    ) { text ->
                        val clean = text?.removeSurrounding("\"")?.replace("\\n", "\n") ?: ""
                        viewModel.askAboutCurrentPage(clean, question)
                    }
                },
                onStop = { viewModel.stopAiGeneration() },
                onDismiss = { viewModel.hideAiSheet() }
            )
        }

        // Privacy Shield Dialog
        if (uiState.isPrivacyDialogVisible) {
            PrivacyShieldDialog(
                url = uiState.currentUrl,
                isSecure = uiState.isSecure,
                blockedTrackersCount = uiState.blockedTrackersCount,
                isTrackingProtectionEnabled = uiState.isTrackerProtectionEnabled,
                onToggleTrackingProtection = { enabled -> viewModel.toggleTrackerProtection(enabled) },
                onClearSiteData = {
                    currentWebViewRef?.clearCache(true)
                    currentWebViewRef?.clearHistory()
                },
                onDismiss = { viewModel.hidePrivacyDialog() }
            )
        }
    }
}
