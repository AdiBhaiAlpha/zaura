package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.browser.BrowserScreen
import com.example.ui.browser.BrowserViewModel
import com.example.ui.theme.MyApplicationTheme

/**
 * Main entry point for the Zaura Browser application.
 * Manages the top-level Activity lifecycle and handles incoming web intents.
 */
class MainActivity : ComponentActivity() {

    private var browserViewModelRef: BrowserViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val browserViewModel: BrowserViewModel = viewModel()
                    browserViewModelRef = browserViewModel

                    // Handle incoming VIEW intents (links clicked from external apps)
                    val incomingUrl = intent?.dataString
                    if (!incomingUrl.isNullOrBlank() && (incomingUrl.startsWith("http://") || incomingUrl.startsWith("https://"))) {
                        browserViewModel.loadWebUrl(incomingUrl)
                    }

                    BrowserScreen(viewModel = browserViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val incomingUrl = intent.dataString
        if (!incomingUrl.isNullOrBlank() && (incomingUrl.startsWith("http://") || incomingUrl.startsWith("https://"))) {
            browserViewModelRef?.loadWebUrl(incomingUrl)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        browserViewModelRef?.onTrimMemory(level)
    }
}
