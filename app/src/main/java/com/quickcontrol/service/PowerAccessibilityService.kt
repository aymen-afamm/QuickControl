package com.quickcontrol.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class PowerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PowerAccessibility"

        @Volatile
        var instance: PowerAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used — we only need performGlobalAction
    }

    override fun onInterrupt() {
        // Not used
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "Accessibility Service destroyed")
    }

    fun showPowerDialog() {
        Log.d(TAG, "Performing GLOBAL_ACTION_POWER_DIALOG")
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    }
}
