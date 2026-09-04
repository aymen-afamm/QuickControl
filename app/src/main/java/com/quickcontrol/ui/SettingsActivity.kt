package com.quickcontrol.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import com.quickcontrol.R
import com.quickcontrol.manager.PermissionManager

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "quickcontrol_prefs"
        private const val KEY_HANDLE_OPACITY = "handle_opacity"
        private const val KEY_AUTO_START = "auto_start"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        permissionManager = PermissionManager(this)

        setupOpacity()
        setupAutoStart()
        setupBattery()
        setupPermissions()
    }

    private fun setupOpacity() {
        val opacityGroup = findViewById<RadioGroup>(R.id.opacityGroup)
        val currentOpacity = prefs.getString(KEY_HANDLE_OPACITY, "medium")

        when (currentOpacity) {
            "low" -> findViewById<RadioButton>(R.id.opacityLow).isChecked = true
            "medium" -> findViewById<RadioButton>(R.id.opacityMedium).isChecked = true
            "high" -> findViewById<RadioButton>(R.id.opacityHigh).isChecked = true
        }

        opacityGroup.setOnCheckedChangeListener { _, checkedId ->
            val opacity = when (checkedId) {
                R.id.opacityLow -> "low"
                R.id.opacityMedium -> "medium"
                R.id.opacityHigh -> "high"
                else -> "medium"
            }
            prefs.edit().putString(KEY_HANDLE_OPACITY, opacity).apply()
        }
    }

    private fun setupAutoStart() {
        val switchAutoStart = findViewById<Switch>(R.id.switchAutoStart)
        switchAutoStart.isChecked = prefs.getBoolean(KEY_AUTO_START, false)

        switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_AUTO_START, isChecked).apply()
        }
    }

    private fun setupBattery() {
        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            startActivity(permissionManager.createBatteryOptimizationIntent())
        }
    }

    private fun setupPermissions() {
        findViewById<Button>(R.id.btnSetupDeviceAdmin).setOnClickListener {
            if (!permissionManager.isDeviceAdminActive()) {
                startActivity(permissionManager.createDeviceAdminIntent())
            }
        }

        findViewById<Button>(R.id.btnSetupAccessibility).setOnClickListener {
            if (!permissionManager.isAccessibilityServiceEnabled()) {
                startActivity(permissionManager.createAccessibilitySettingsIntent())
            }
        }
    }
}
