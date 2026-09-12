package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.ai.OpenRouterConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isTrackerProtectionEnabled: Boolean,
    onToggleTrackerProtection: (Boolean) -> Unit,
    onClearAllData: () -> Unit,
    onBack: () -> Unit,
    isAiEnabled: Boolean = true,
    selectedAiModel: String = OpenRouterConfig.DEFAULT_PRIMARY_MODEL,
    isAiStreamingEnabled: Boolean = true,
    isAiWebContextEnabled: Boolean = true,
    aiResponseStyle: String = "Concise",
    customApiKey: String? = null,
    customProxyUrl: String? = null,
    onToggleAiEnabled: (Boolean) -> Unit = {},
    onSelectAiModel: (String) -> Unit = {},
    onToggleStreaming: (Boolean) -> Unit = {},
    onToggleWebContext: (Boolean) -> Unit = {},
    onSelectResponseStyle: (String) -> Unit = {},
    onSaveDevConfig: (apiKey: String, proxyUrl: String) -> Unit = { _, _ -> },
    onClearAiHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchEngine by remember { mutableStateOf("DuckDuckGo") }
    var trackingMode by remember { mutableStateOf("Standard") }

    var showSearchEngineDialog by remember { mutableStateOf(false) }
    var showTrackingDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showStyleDialog by remember { mutableStateOf(false) }
    var showDevConfigDialog by remember { mutableStateOf(false) }
    var showClearAiDialog by remember { mutableStateOf(false) }

    var devKeyInput by remember(customApiKey) { mutableStateOf(customApiKey ?: "") }
    var devProxyInput by remember(customProxyUrl) { mutableStateOf(customProxyUrl ?: "") }

    val searchEngines = listOf("DuckDuckGo", "Google", "Bing", "Brave")
    val trackingModes = listOf("Strict", "Standard", "Off")
    val responseStyles = listOf("Concise", "Detailed", "Research")

    val selectedModelDescriptor = OpenRouterConfig.CURATED_MODELS.find { it.id == selectedAiModel }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Section 1: Search Engine
            SettingsSectionHeader(title = "Search Engine")

            SettingsClickableRow(
                title = "Default Search Engine",
                subtitle = searchEngine,
                onClick = { showSearchEngineDialog = true },
                tag = "settings_search_engine"
            )

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Section 2: Privacy & Tracking
            SettingsSectionHeader(title = "Privacy & Tracking")

            SettingsClickableRow(
                title = "Tracking Protection",
                subtitle = if (isTrackerProtectionEnabled) trackingMode else "Off",
                onClick = { showTrackingDialog = true },
                tag = "settings_tracking_mode"
            )

            SettingsSwitchRow(
                title = "Block Trackers & Ads",
                subtitle = "Prevents third-party trackers, beacons, and fingerprinting",
                checked = isTrackerProtectionEnabled,
                onCheckedChange = { onToggleTrackerProtection(it) },
                tag = "settings_tracker_switch"
            )

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Section 3: AI Intelligence (OpenRouter Gateway)
            SettingsSectionHeader(title = "Zaura AI Intelligence")

            SettingsSwitchRow(
                title = "AI Intelligence Enabled",
                subtitle = "Power search answers, page summaries, and research workspace",
                checked = isAiEnabled,
                onCheckedChange = onToggleAiEnabled,
                tag = "settings_ai_enabled_switch"
            )

            if (isAiEnabled) {
                SettingsClickableRow(
                    title = "AI Model",
                    subtitle = selectedModelDescriptor?.displayName ?: selectedAiModel,
                    onClick = { showModelDialog = true },
                    tag = "settings_ai_model"
                )

                SettingsClickableRow(
                    title = "AI Response Style",
                    subtitle = aiResponseStyle,
                    onClick = { showStyleDialog = true },
                    tag = "settings_ai_style"
                )

                SettingsSwitchRow(
                    title = "Web Grounding Context",
                    subtitle = "Ground AI answers on verified top search engine sources",
                    checked = isAiWebContextEnabled,
                    onCheckedChange = onToggleWebContext,
                    tag = "settings_ai_web_context_switch"
                )

                SettingsSwitchRow(
                    title = "Streaming Responses",
                    subtitle = "Progressively stream tokens in real-time as they generate",
                    checked = isAiStreamingEnabled,
                    onCheckedChange = onToggleStreaming,
                    tag = "settings_ai_streaming_switch"
                )

                SettingsClickableRow(
                    title = "Gateway & Developer Configuration",
                    subtitle = if (!customApiKey.isNullOrBlank() || !customProxyUrl.isNullOrBlank()) "Custom Gateway Configured" else "Default Zaura OpenRouter Gateway",
                    onClick = { showDevConfigDialog = true },
                    tag = "settings_dev_config"
                )

                SettingsClickableRow(
                    title = "Clear AI History & Cache",
                    subtitle = "Clear generated AI search cards, page answers, and deep research",
                    titleColor = MaterialTheme.colorScheme.primary,
                    onClick = { showClearAiDialog = true },
                    tag = "settings_clear_ai_history"
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Section 4: Data & Cache
            SettingsSectionHeader(title = "Data & Cache")

            SettingsClickableRow(
                title = "Clear Browsing Data",
                subtitle = "Clear cache, cookies, history, and active sessions",
                titleColor = MaterialTheme.colorScheme.error,
                onClick = { showClearDialog = true },
                tag = "settings_clear_data"
            )

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Section 5: About Zaura
            SettingsSectionHeader(title = "About Zaura")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = "Zaura Icon",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Zaura Browser",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Version 1.0.0 • OpenRouter Gateway Architecture",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Dialogs
        if (showSearchEngineDialog) {
            AlertDialog(
                onDismissRequest = { showSearchEngineDialog = false },
                title = { Text("Default Search Engine") },
                text = {
                    Column {
                        searchEngines.forEach { engine ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchEngine = engine
                                        showSearchEngineDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = searchEngine == engine,
                                    onClick = {
                                        searchEngine = engine
                                        showSearchEngineDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = engine, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSearchEngineDialog = false }) {
                        Text("Done")
                    }
                }
            )
        }

        if (showTrackingDialog) {
            AlertDialog(
                onDismissRequest = { showTrackingDialog = false },
                title = { Text("Tracking Protection Mode") },
                text = {
                    Column {
                        trackingModes.forEach { mode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        trackingMode = mode
                                        onToggleTrackerProtection(mode != "Off")
                                        showTrackingDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = trackingMode == mode,
                                    onClick = {
                                        trackingMode = mode
                                        onToggleTrackerProtection(mode != "Off")
                                        showTrackingDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = mode, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTrackingDialog = false }) {
                        Text("Done")
                    }
                }
            )
        }

        // AI Model Selection Dialog
        if (showModelDialog) {
            AlertDialog(
                onDismissRequest = { showModelDialog = false },
                title = { Text("Select AI Model") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        OpenRouterConfig.CURATED_MODELS.forEach { model ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedAiModel == model.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectAiModel(model.id)
                                        showModelDialog = false
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedAiModel == model.id,
                                        onClick = {
                                            onSelectAiModel(model.id)
                                            showModelDialog = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = model.displayName,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = model.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showModelDialog = false }) {
                        Text("Done")
                    }
                }
            )
        }

        // AI Response Style Dialog
        if (showStyleDialog) {
            AlertDialog(
                onDismissRequest = { showStyleDialog = false },
                title = { Text("AI Response Style") },
                text = {
                    Column {
                        responseStyles.forEach { style ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectResponseStyle(style)
                                        showStyleDialog = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = aiResponseStyle == style,
                                    onClick = {
                                        onSelectResponseStyle(style)
                                        showStyleDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = style, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showStyleDialog = false }) {
                        Text("Done")
                    }
                }
            )
        }

        // Clear AI History Confirmation Dialog
        if (showClearAiDialog) {
            AlertDialog(
                onDismissRequest = { showClearAiDialog = false },
                title = { Text("Clear AI History?") },
                text = { Text("This will clear all generated AI answer cards, cached page summaries, and active research dossiers.") },
                confirmButton = {
                    Button(
                        onClick = {
                            onClearAiHistory()
                            showClearAiDialog = false
                        }
                    ) {
                        Text("Clear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearAiDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Developer / Gateway Dialog
        if (showDevConfigDialog) {
            AlertDialog(
                onDismissRequest = { showDevConfigDialog = false },
                title = { Text("OpenRouter Gateway Settings") },
                text = {
                    Column {
                        Text(
                            text = "In production, keys remain safely on the Zaura backend proxy. For local development, enter your custom OpenRouter key or backend proxy URL.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = devKeyInput,
                            onValueChange = { devKeyInput = it },
                            label = { Text("OpenRouter API Key") },
                            placeholder = { Text("sk-or-v1-...") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = devProxyInput,
                            onValueChange = { devProxyInput = it },
                            label = { Text("Backend Proxy URL (Optional)") },
                            placeholder = { Text("https://your-backend.com/api/ai") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onSaveDevConfig(devKeyInput.trim(), devProxyInput.trim())
                            showDevConfigDialog = false
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDevConfigDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Clear Browsing Data?") },
                text = { Text("This will permanently remove your history, open tabs, cookies, and cache.") },
                confirmButton = {
                    Button(
                        onClick = {
                            onClearAllData()
                            showClearDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Clear All Data")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        ),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsClickableRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tag: String,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onBackground
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(tag)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = titleColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
