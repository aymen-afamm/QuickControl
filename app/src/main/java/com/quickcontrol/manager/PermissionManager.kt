package com.quickcontrol.manager

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import com.quickcontrol.receiver.QuickControlDeviceAdminReceiver
import com.quickcontrol.service.PowerAccessibilityService

class PermissionManager(private val context: Context) {

    private val devicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent =
        ComponentName(context, QuickControlDeviceAdminReceiver::class.java)

    // --- Overlay Permission ---

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    fun createOverlayPermissionIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // --- Device Admin (Lock Screen) ---

    fun isDeviceAdminActive(): Boolean = devicePolicyManager.isAdminActive(adminComponent)

    fun createDeviceAdminIntent(): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "QuickControl needs Device Administrator permission to lock the screen " +
                        "when you press the Lock button. This permission is only used for screen locking."
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun lockScreen() {
        if (isDeviceAdminActive()) {
            devicePolicyManager.lockNow()
        }
    }

    // --- Accessibility Service (Power Menu) ---

    fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "${context.packageName}/${PowerAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(serviceName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    fun createAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun showPowerDialog(): Boolean {
        val service = PowerAccessibilityService.instance
        if (service != null) {
            service.showPowerDialog()
            return true
        }
        return false
    }

    // --- Battery Optimization ---

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun createBatteryOptimizationIntent(): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
