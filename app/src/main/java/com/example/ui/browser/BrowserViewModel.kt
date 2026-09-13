package com.example.ui.browser

import android.app.Application
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.core.ai.AIAnswerResult
import com.example.core.ai.AIService
import com.example.core.ai.OpenRouterAIService
import com.example.core.ai.OpenRouterConfig
import com.example.core.ai.OpenRouterProvider
import com.example.core.ai.PageContentExtractor
import com.example.core.ai.ResearchReport
import com.example.core.ai.TabContentContext
import com.example.core.browser.ContentBlocker
import com.example.core.browser.WebViewPool
import com.example.core.database.*
import com.example.core.model.*
import com.example.core.search.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class ActiveScreen {
    HOME,
    WEBVIEW,
    SEARCH,
    TAB_MANAGER,
    BOOKMARKS_HISTORY,
    DOWNLOADS,
    READER_MODE,
    RESEARCH,
    SETTINGS
}

data class BrowserUiState(
    val activeScreen: ActiveScreen = ActiveScreen.HOME,
    val tabs: List<BrowserTab> = emptyList(),
    val activeTabId: String = "",
    val currentUrl: String = "about:blank",
    val currentTitle: String = "Zaura",
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isSecure: Boolean = true,
    val isPrivate: Boolean = false,
    val isDesktopMode: Boolean = false,
    val isBookmarked: Boolean = false,
    // Search & AI State
    val searchQuery: String = "",
    val searchType: SearchType = SearchType.WEB,
    val searchResults: List<SearchResult> = emptyList(),
    val aiAnswerResult: AIAnswerResult? = null,
    val isLoadingSearchResults: Boolean = false,
    val isLoadingAiAnswer: Boolean = false,
    // AI Settings & OpenRouter Gateway
    val isAiEnabled: Boolean = true,
    val selectedAiModel: String = OpenRouterConfig.DEFAULT_PRIMARY_MODEL,
    val isAiStreamingEnabled: Boolean = true,
    val isAiWebContextEnabled: Boolean = true,
    val aiResponseStyle: String = "Concise",
    val customOpenRouterApiKey: String? = null,
    val customProxyUrl: String? = null,
    // Reader Mode
    val readerArticle: ReaderArticle? = null,
    // Deep Research
    val researchReport: ResearchReport? = null,
    val isLoadingResearch: Boolean = false,
    val savedSessions: List<ResearchSession> = emptyList(),
    // Page context AI sheet
    val isAiSheetVisible: Boolean = false,
    val aiSheetResponse: String? = null,
    val isAiSheetLoading: Boolean = false,
    // Privacy Shield Dialog
    val isPrivacyDialogVisible: Boolean = false,
    val isTrackerProtectionEnabled: Boolean = true,
    val blockedTrackersCount: Int = 0
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val database = ZauraDatabase.getDatabase(application)
    private val dao = database.browserDao()
    val historyRepository: HistoryRepository = RoomHistoryRepository(dao)
    val bookmarkRepository: BookmarkRepository = RoomBookmarkRepository(dao)

    val contentBlocker = ContentBlocker()
    val webViewPool = WebViewPool(application, contentBlocker)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun getOpenRouterKey(): String {
        val custom = _uiState.value.customOpenRouterApiKey
        if (!custom.isNullOrBlank()) return custom
        val envKey = try {
            val field = BuildConfig::class.java.getField("OPENROUTER_API_KEY")
            val raw = field.get(null) as? String
            if (raw.isNullOrBlank() || raw == "OPENROUTER_API_KEY_PLACEHOLDER") null else raw
        } catch (_: Exception) {
            null
        }
        val sysEnv = try {
            System.getenv("OPENROUTER_API_KEY")?.ifBlank { null }
        } catch (_: Exception) {
            null
        }
        return envKey ?: sysEnv ?: OpenRouterConfig.HARDCODED_OPENROUTER_API_KEY
    }

    val openRouterProvider = OpenRouterProvider(
        client = okHttpClient,
        apiKeyProvider = { getOpenRouterKey() },
        proxyUrlProvider = { _uiState.value.customProxyUrl?.ifBlank { null } },
        defaultModelProvider = { _uiState.value.selectedAiModel },
        fallbackModelProvider = { OpenRouterConfig.DEFAULT_FALLBACK_MODEL }
    )

    val aiService: AIService = OpenRouterAIService(
        provider = openRouterProvider,
        modelIdProvider = { _uiState.value.selectedAiModel }
    )

    private val searchProviders: List<SearchProvider> = listOf(
        LiveWebSearchProvider(okHttpClient),
        GoogleSearchProvider(
            client = okHttpClient,
            apiKeyProvider = { null },
            searchEngineIdProvider = { null }
        )
    )

    private val searchRepository = SearchRepository(searchProviders)

    val historyFlow: Flow<List<HistoryItem>> = historyRepository.getAllHistory()

    val bookmarksFlow: Flow<List<BookmarkItem>> = bookmarkRepository.getAllBookmarks()

    val downloadsFlow: Flow<List<DownloadItem>> = dao.getAllDownloads().map { list ->
        list.map {
            DownloadItem(
                id = it.id,
                fileName = it.fileName,
                url = it.url,
                mimeType = it.mimeType,
                fileSize = it.fileSize,
                downloadedBytes = it.downloadedBytes,
                status = try { DownloadStatus.valueOf(it.status) } catch (_: Exception) { DownloadStatus.COMPLETED },
                timestamp = it.timestamp,
                filePath = it.filePath
            )
        }
    }

    init {
        loadInitialTabs()
        loadSavedResearchSessions()
    }

    private fun loadInitialTabs() {
        viewModelScope.launch {
            val initialTabId = UUID.randomUUID().toString()
            val initialTab = BrowserTab(
                id = initialTabId,
                url = "about:blank",
                title = "New Tab",
                groupName = "General",
                isPrivate = false
            )

            _uiState.update {
                it.copy(
                    tabs = listOf(initialTab),
                    activeTabId = initialTabId,
                    activeScreen = ActiveScreen.HOME,
                    currentUrl = "about:blank",
                    currentTitle = "Zaura"
                )
            }
        }
    }

    private fun loadSavedResearchSessions() {
        viewModelScope.launch {
            dao.getAllResearchSessions().collect { list ->
                val sessions = list.map {
                    ResearchSession(
                        id = it.id,
                        title = it.title,
                        query = it.query,
                        aiAnswer = it.aiAnswer,
                        sources = emptyList(),
                        followups = emptyList(),
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt
                    )
                }
                _uiState.update { it.copy(savedSessions = sessions) }
            }
        }
    }

    fun navigateTo(input: String) {
        val classified = QueryClassifier.classify(input)
        when (classified.intent) {
            QueryIntentType.URL -> {
                loadWebUrl(classified.normalizedTarget)
            }
            QueryIntentType.QUESTION -> {
                performSearch(classified.rawQuery, SearchType.WEB, triggerAiAnswer = true)
            }
            QueryIntentType.AI_COMMAND -> {
                openDeepResearch(classified.rawQuery)
            }
            else -> {
                performSearch(classified.rawQuery, SearchType.WEB, triggerAiAnswer = false)
            }
        }
    }

    fun loadWebUrl(url: String) {
        val activeId = _uiState.value.activeTabId
        val updatedTabs = _uiState.value.tabs.map {
            if (it.id == activeId) it.copy(url = url, title = "Loading…") else it
        }

        val isPrivate = _uiState.value.tabs.find { it.id == activeId }?.isPrivate ?: false

        _uiState.update {
            it.copy(
                activeScreen = ActiveScreen.WEBVIEW,
                currentUrl = url,
                currentTitle = "Loading…",
                isLoading = true,
                tabs = updatedTabs,
                isPrivate = isPrivate
            )
        }

        // Record in history if not in private mode
        viewModelScope.launch {
            historyRepository.recordVisit(url, url, isPrivate)
        }
    }

    private var searchJob: Job? = null
    private var aiSearchJob: Job? = null
    private var aiSheetJob: Job? = null

    fun performSearch(query: String, searchType: SearchType = SearchType.WEB, triggerAiAnswer: Boolean = false) {
        searchJob?.cancel()
        aiSearchJob?.cancel()
        aiService.cancelActiveRequests()

        val shouldRunAi = _uiState.value.isAiEnabled && searchType == SearchType.WEB

        _uiState.update {
            it.copy(
                activeScreen = ActiveScreen.SEARCH,
                searchQuery = query,
                searchType = searchType,
                isLoadingSearchResults = true,
                isLoadingAiAnswer = shouldRunAi,
                searchResults = emptyList(),
                aiAnswerResult = null
            )
        }

        searchJob = viewModelScope.launch {
            val result = searchRepository.search(query, searchType)
            val results = result.getOrNull() ?: emptyList()

            _uiState.update {
                it.copy(
                    searchResults = results,
                    isLoadingSearchResults = false
                )
            }

            if (!shouldRunAi) {
                _uiState.update { it.copy(isLoadingAiAnswer = false) }
                return@launch
            }

            // Synthesize AI grounded answer with progressive streaming
            aiSearchJob = launch {
                try {
                    if (_uiState.value.isAiStreamingEnabled) {
                        aiService.streamAnswer(query, results).collect { chunk ->
                            if (chunk.error != null) {
                                _uiState.update {
                                    it.copy(
                                        aiAnswerResult = AIAnswerResult(
                                            answer = chunk.error,
                                            isGrounded = false,
                                            modelUsed = chunk.modelUsed
                                        ),
                                        isLoadingAiAnswer = false
                                    )
                                }
                            } else {
                                val currentText = chunk.fullTextSoFar
                                _uiState.update {
                                    it.copy(
                                        aiAnswerResult = AIAnswerResult(
                                            answer = currentText,
                                            citations = chunk.citations,
                                            isGrounded = chunk.citations.isNotEmpty(),
                                            modelUsed = chunk.modelUsed
                                        ),
                                        isLoadingAiAnswer = !chunk.isDone
                                    )
                                }
                            }
                        }
                    } else {
                        val aiAnswer = aiService.generateAnswer(query, results)
                        _uiState.update {
                            it.copy(
                                aiAnswerResult = aiAnswer,
                                isLoadingAiAnswer = false
                            )
                        }
                    }
                } catch (_: kotlinx.coroutines.CancellationException) {
                    // Job cancelled cleanly
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isLoadingAiAnswer = false,
                            aiAnswerResult = it.aiAnswerResult ?: AIAnswerResult(
                                answer = "Zaura AI is temporarily unavailable.",
                                isGrounded = false
                            )
                        )
                    }
                }
            }
        }
    }

    fun stopAiGeneration() {
        aiSearchJob?.cancel()
        aiSheetJob?.cancel()
        aiService.cancelActiveRequests()
        _uiState.update {
            it.copy(
                isLoadingAiAnswer = false,
                isAiSheetLoading = false,
                isLoadingResearch = false
            )
        }
    }

    fun onTrimMemory(level: Int) {
        webViewPool.onTrimMemory(level, _uiState.value.activeTabId)
    }

    fun onPageStarted(url: String) {
        _uiState.update {
            it.copy(
                currentUrl = url,
                isLoading = true,
                progress = 10,
                isSecure = url.startsWith("https://", ignoreCase = true)
            )
        }
        viewModelScope.launch {
            val isBookmarked = if (url.isBlank() || url == "about:blank") false else dao.isUrlBookmarked(url)
            _uiState.update { it.copy(isBookmarked = isBookmarked) }
        }
    }

    fun onPageFinished(url: String, title: String? = null) {
        val activeId = _uiState.value.activeTabId
        val updatedTabs = _uiState.value.tabs.map {
            if (it.id == activeId) it.copy(url = url, title = title ?: it.title) else it
        }

        _uiState.update {
            it.copy(
                currentUrl = url,
                currentTitle = title ?: it.currentTitle,
                isLoading = false,
                progress = 100,
                isSecure = url.startsWith("https://", ignoreCase = true),
                tabs = updatedTabs,
                blockedTrackersCount = contentBlocker.totalBlockedCount
            )
        }

        viewModelScope.launch {
            val isBookmarked = if (url.isBlank() || url == "about:blank") false else dao.isUrlBookmarked(url)
            _uiState.update { it.copy(isBookmarked = isBookmarked) }
        }

        // Save history update via repository ensuring privacy
        val isPrivate = _uiState.value.isPrivate
        val pageTitle = title ?: _uiState.value.currentTitle
        viewModelScope.launch {
            historyRepository.recordVisit(url, pageTitle, isPrivate)
        }
    }

    fun onProgressChanged(progress: Int) {
        _uiState.update {
            it.copy(
                progress = progress,
                isLoading = progress < 100
            )
        }
    }

    fun openNewTab(isPrivate: Boolean = false, url: String = "about:blank") {
        val newTabId = UUID.randomUUID().toString()
        val newTab = BrowserTab(
            id = newTabId,
            url = url,
            title = if (url == "about:blank") "New Tab" else url,
            groupName = "General",
            isPrivate = isPrivate
        )

        val updatedTabs = _uiState.value.tabs + newTab
        _uiState.update {
            it.copy(
                tabs = updatedTabs,
                activeTabId = newTabId,
                activeScreen = if (url == "about:blank") ActiveScreen.HOME else ActiveScreen.WEBVIEW,
                currentUrl = url,
                currentTitle = newTab.title,
                isPrivate = isPrivate
            )
        }
        webViewPool.suspendInactiveTabs(newTabId)
    }

    fun switchTab(tabId: String) {
        val tab = _uiState.value.tabs.find { it.id == tabId } ?: return
        _uiState.update {
            it.copy(
                activeTabId = tabId,
                activeScreen = if (tab.url == "about:blank") ActiveScreen.HOME else ActiveScreen.WEBVIEW,
                currentUrl = tab.url,
                currentTitle = tab.title,
                isPrivate = tab.isPrivate
            )
        }
        viewModelScope.launch {
            val isBookmarked = if (tab.url.isBlank() || tab.url == "about:blank") false else dao.isUrlBookmarked(tab.url)
            _uiState.update { it.copy(isBookmarked = isBookmarked) }
        }
        webViewPool.suspendInactiveTabs(tabId)
    }

    fun closeTab(tabId: String) {
        val currentTabs = _uiState.value.tabs
        val remaining = currentTabs.filterNot { it.id == tabId }
        webViewPool.destroyWebView(tabId)

        if (remaining.isEmpty()) {
            openNewTab(false)
        } else {
            val nextActive = if (tabId == _uiState.value.activeTabId) remaining.last().id else _uiState.value.activeTabId
            val nextTab = remaining.find { it.id == nextActive }
            _uiState.update {
                it.copy(
                    tabs = remaining,
                    activeTabId = nextActive,
                    currentUrl = nextTab?.url ?: "about:blank",
                    currentTitle = nextTab?.title ?: "Zaura",
                    isPrivate = nextTab?.isPrivate ?: false
                )
            }
        }
    }

    fun closeAllTabs() {
        webViewPool.clearAllWebViews()
        openNewTab(false)
    }

    fun toggleBookmarkCurrentPage() {
        val url = _uiState.value.currentUrl
        val title = _uiState.value.currentTitle
        if (url.isBlank() || url == "about:blank") return

        viewModelScope.launch {
            val isBookmarked = bookmarkRepository.toggleBookmark(url, title)
            _uiState.update { it.copy(isBookmarked = isBookmarked) }
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            bookmarkRepository.deleteBookmark(id)
            val currentUrl = _uiState.value.currentUrl
            val isCurrentBookmarked = dao.isUrlBookmarked(currentUrl)
            _uiState.update { it.copy(isBookmarked = isCurrentBookmarked) }
        }
    }

    fun deleteHistory(id: Long) {
        viewModelScope.launch { historyRepository.deleteHistoryItem(id) }
    }

    fun clearHistorySince(sinceTimestamp: Long) {
        viewModelScope.launch {
            historyRepository.clearHistory(sinceTimestamp)
        }
    }

    fun updateNavigationState(canGoBack: Boolean, canGoForward: Boolean) {
        _uiState.update {
            it.copy(
                canGoBack = canGoBack,
                canGoForward = canGoForward
            )
        }
    }

    fun toggleDesktopMode() {
        val newMode = !_uiState.value.isDesktopMode
        _uiState.update { it.copy(isDesktopMode = newMode) }
        webViewPool.setDesktopMode(_uiState.value.activeTabId, newMode)
    }

    fun openReaderMode(htmlContent: String) {
        val url = _uiState.value.currentUrl
        val article = PageContentExtractor.extractReadableArticle(htmlContent, url)
        _uiState.update {
            it.copy(
                readerArticle = article,
                activeScreen = ActiveScreen.READER_MODE
            )
        }
    }

    fun openDeepResearch(query: String) {
        _uiState.update {
            it.copy(
                activeScreen = ActiveScreen.RESEARCH,
                isLoadingResearch = true
            )
        }

        viewModelScope.launch {
            val searchResult = searchRepository.search(query, SearchType.WEB)
            val sources = searchResult.getOrNull() ?: emptyList()
            val report = aiService.runDeepResearch(query, sources)

            _uiState.update {
                it.copy(
                    researchReport = report,
                    isLoadingResearch = false
                )
            }

            // Save to database
            dao.insertResearchSession(
                ResearchSessionEntity(
                    id = UUID.randomUUID().toString(),
                    title = report.title,
                    query = query,
                    aiAnswer = report.executiveSummary,
                    sourcesJson = "",
                    followupsJson = ""
                )
            )
        }
    }

    fun compareTabs(tabIds: List<String>) {
        val selectedTabs = _uiState.value.tabs.filter { tabIds.contains(it.id) }
        val contexts = selectedTabs.map {
            TabContentContext(
                tabId = it.id,
                title = it.title,
                url = it.url,
                content = "${it.title} at ${it.url}"
            )
        }

        _uiState.update { it.copy(isLoadingResearch = true) }
        viewModelScope.launch {
            val summary = aiService.compareTabs(contexts)
            val report = ResearchReport(
                title = "Tab Comparison (${selectedTabs.size} tabs)",
                query = "Comparison of open tabs",
                executiveSummary = summary,
                keyFindings = selectedTabs.map { "${it.title} (${it.url})" },
                comprehensiveAnalysis = summary,
                sourceCitations = emptyList(),
                suggestedQuestions = listOf("What are alternatives to these sites?")
            )
            _uiState.update {
                it.copy(
                    researchReport = report,
                    isLoadingResearch = false
                )
            }
        }
    }

    fun showAiSheet() {
        _uiState.update { it.copy(isAiSheetVisible = true, aiSheetResponse = null) }
    }

    fun hideAiSheet() {
        _uiState.update { it.copy(isAiSheetVisible = false) }
    }

    fun summarizeCurrentPage(htmlContent: String) {
        aiSheetJob?.cancel()
        _uiState.update { it.copy(isAiSheetLoading = true, aiSheetResponse = "") }
        aiSheetJob = viewModelScope.launch {
            try {
                if (_uiState.value.isAiStreamingEnabled) {
                    aiService.streamSummarizePage(
                        _uiState.value.currentUrl,
                        _uiState.value.currentTitle,
                        htmlContent
                    ).collect { chunk ->
                        _uiState.update {
                            it.copy(
                                aiSheetResponse = chunk.fullTextSoFar,
                                isAiSheetLoading = !chunk.isDone
                            )
                        }
                    }
                } else {
                    val summary = aiService.summarizePage(
                        _uiState.value.currentUrl,
                        _uiState.value.currentTitle,
                        htmlContent
                    )
                    _uiState.update {
                        it.copy(
                            aiSheetResponse = summary,
                            isAiSheetLoading = false
                        )
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // User stopped or dismissed
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        aiSheetResponse = "Unable to summarize page at this time.",
                        isAiSheetLoading = false
                    )
                }
            }
        }
    }

    fun askAboutCurrentPage(htmlContent: String, question: String) {
        aiSheetJob?.cancel()
        _uiState.update { it.copy(isAiSheetLoading = true, aiSheetResponse = "") }
        aiSheetJob = viewModelScope.launch {
            try {
                val answer = aiService.askAboutPage(
                    _uiState.value.currentUrl,
                    _uiState.value.currentTitle,
                    htmlContent,
                    question
                )
                _uiState.update {
                    it.copy(
                        aiSheetResponse = answer,
                        isAiSheetLoading = false
                    )
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // Cancelled
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        aiSheetResponse = "Unable to answer from this page at the moment.",
                        isAiSheetLoading = false
                    )
                }
            }
        }
    }

    fun translateCurrentPage(htmlContent: String, targetLanguage: String) {
        aiSheetJob?.cancel()
        _uiState.update { it.copy(isAiSheetLoading = true) }
        aiSheetJob = viewModelScope.launch {
            try {
                val translation = aiService.translateText(htmlContent.take(2000), targetLanguage)
                _uiState.update {
                    it.copy(
                        aiSheetResponse = translation,
                        isAiSheetLoading = false
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        aiSheetResponse = "Translation unavailable at this time.",
                        isAiSheetLoading = false
                    )
                }
            }
        }
    }

    // AI Settings Management
    fun setAiEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isAiEnabled = enabled) }
    }

    fun setSelectedAiModel(modelId: String) {
        _uiState.update { it.copy(selectedAiModel = modelId) }
    }

    fun setAiStreamingEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isAiStreamingEnabled = enabled) }
    }

    fun setAiWebContextEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isAiWebContextEnabled = enabled) }
    }

    fun setAiResponseStyle(style: String) {
        _uiState.update { it.copy(aiResponseStyle = style) }
    }

    fun setCustomOpenRouterKey(key: String?) {
        _uiState.update { it.copy(customOpenRouterApiKey = key?.ifBlank { null }) }
    }

    fun setCustomProxyUrl(url: String?) {
        _uiState.update { it.copy(customProxyUrl = url?.ifBlank { null }) }
    }

    fun clearAiHistory() {
        _uiState.update {
            it.copy(
                aiAnswerResult = null,
                aiSheetResponse = null,
                researchReport = null
            )
        }
    }

    fun showPrivacyDialog() {
        _uiState.update { it.copy(isPrivacyDialogVisible = true) }
    }

    fun hidePrivacyDialog() {
        _uiState.update { it.copy(isPrivacyDialogVisible = false) }
    }

    fun toggleTrackerProtection(enabled: Boolean) {
        contentBlocker.isEnabled = enabled
        _uiState.update { it.copy(isTrackerProtectionEnabled = enabled) }
    }

    fun clearAllData() {
        viewModelScope.launch {
            dao.clearAllTabs()
            dao.clearHistory()
            dao.clearAllPermissions()
            webViewPool.clearAllWebViews()
            withContext(Dispatchers.Main) {
                CookieManager.getInstance().removeAllCookies(null)
            }
            openNewTab(false)
        }
    }

    fun setScreen(screen: ActiveScreen) {
        _uiState.update { it.copy(activeScreen = screen) }
    }
}
