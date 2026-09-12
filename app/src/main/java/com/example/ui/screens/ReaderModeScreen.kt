package com.example.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ReaderArticle
import java.util.*

enum class ReaderTheme(val nameLabel: String, val bg: Color, val text: Color) {
    DARK("Dark", Color(0xFF111827), Color(0xFFF3F4F6)),
    SEPIA("Sepia", Color(0xFFFBF0D9), Color(0xFF3B2F2F)),
    LIGHT("Light", Color(0xFFFFFFFF), Color(0xFF1F2937))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderModeScreen(
    article: ReaderArticle,
    onClose: () -> Unit,
    onSummarizeAi: () -> Unit,
    onTranslateAi: () -> Unit,
    modifier: Modifier = Modifier
) {
    var fontSize by remember { mutableStateOf(17f) }
    var currentTheme by remember { mutableStateOf(ReaderTheme.DARK) }
    var isSpeaking by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var tts: TextToSpeech? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        val speech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
        tts = speech
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    val estimatedReadingTimeMin = remember(article.content) {
        val wordCount = article.content.split("\\s+".toRegex()).size
        maxOf(1, wordCount / 200)
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Reader Mode",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "~$estimatedReadingTimeMin min read",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close Reader")
                    }
                },
                actions = {
                    // TTS Play / Pause
                    IconButton(
                        onClick = {
                            if (isSpeaking) {
                                tts?.stop()
                                isSpeaking = false
                            } else {
                                val textToRead = "${article.title}. ${article.content.take(4000)}"
                                tts?.speak(textToRead, TextToSpeech.QUEUE_FLUSH, null, "reader_tts")
                                isSpeaking = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.PauseCircle else Icons.Default.VolumeUp,
                            contentDescription = "Read Aloud",
                            tint = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // AI Summarize
                    IconButton(onClick = onSummarizeAi) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Summarize AI",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // AI Translate
                    IconButton(onClick = onTranslateAi) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = "Translate AI"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = currentTheme.bg,
                    titleContentColor = currentTheme.text,
                    navigationIconContentColor = currentTheme.text,
                    actionIconContentColor = currentTheme.text
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                color = currentTheme.bg,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Font size controls
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (fontSize > 13f) fontSize -= 2f },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("A-", style = MaterialTheme.typography.titleMedium, color = currentTheme.text)
                        }
                        Text(
                            text = "${fontSize.toInt()}sp",
                            style = MaterialTheme.typography.bodySmall,
                            color = currentTheme.text,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        IconButton(
                            onClick = { if (fontSize < 28f) fontSize += 2f },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("A+", style = MaterialTheme.typography.titleMedium, color = currentTheme.text)
                        }
                    }

                    // Theme selector chips
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReaderTheme.values().forEach { theme ->
                            Surface(
                                shape = CircleShape,
                                color = theme.bg,
                                border = androidx.compose.foundation.BorderStroke(
                                    if (currentTheme == theme) 2.dp else 1.dp,
                                    if (currentTheme == theme) MaterialTheme.colorScheme.primary else Color.Gray
                                ),
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { currentTheme = theme }
                            ) {}
                        }
                    }
                }
            }
        },
        containerColor = currentTheme.bg,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Article Title
            Text(
                text = article.title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = (fontSize + 6).sp,
                    lineHeight = (fontSize + 12).sp
                ),
                color = currentTheme.text
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Source URL
            Text(
                text = article.url,
                style = MaterialTheme.typography.labelSmall,
                color = currentTheme.text.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = currentTheme.text.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(20.dp))

            // Article Body
            Text(
                text = article.content,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.6f).sp,
                    fontFamily = FontFamily.Serif
                ),
                color = currentTheme.text
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
