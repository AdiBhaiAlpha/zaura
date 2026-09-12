package com.example.core.browser

import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

class ContentBlocker {
    private val blockedTrackerDomains = ConcurrentHashMap<String, Boolean>()
    var isEnabled: Boolean = true
    var totalBlockedCount: Int = 0
        private set

    init {
        // High-confidence standard tracking and telemetry domains
        val defaultBlocklist = listOf(
            "google-analytics.com",
            "doubleclick.net",
            "googletagmanager.com",
            "facebook.net",
            "scorecardresearch.com",
            "quantserve.com",
            "criteo.com",
            "outbrain.com",
            "taboola.com",
            "adroll.com",
            "adnxs.com",
            "hotjar.com",
            "mixpanel.com",
            "segment.io",
            "clarity.ms",
            "trackersim.com"
        )
        for (domain in defaultBlocklist) {
            blockedTrackerDomains[domain] = true
        }
    }

    fun isUrlBlocked(url: String): Boolean {
        if (!isEnabled) return false
        try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: return false
            
            for (blocked in blockedTrackerDomains.keys) {
                if (host == blocked || host.endsWith(".$blocked")) {
                    totalBlockedCount++
                    return true
                }
            }
        } catch (_: Exception) {
            return false
        }
        return false
    }

    fun addBlockedDomain(domain: String) {
        blockedTrackerDomains[domain.lowercase().trim()] = true
    }

    fun removeBlockedDomain(domain: String) {
        blockedTrackerDomains.remove(domain.lowercase().trim())
    }

    fun resetBlockedCount() {
        totalBlockedCount = 0
    }
}
