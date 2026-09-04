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
        const val PREFS_NAME = "quickcontrol_prefs"
        const val KEY_EDGE_POSITION = "edge_position"
        const val KEY_GESTURE_SENSITIVITY = "gesture_sensitivity"
        const val KEY_EDGE_INDICATOR = "edge_indicator"
        const val KEY_AUTO_START = "auto_start"

        const val EDGE_LEFT = "left"
        const val EDGE_RIGHT = "right"

        const val SENSITIVITY_LOW = "low"
        const val SENSITIVITY_MEDIUM = "medium"
        const val SENSITIVITY_HIGH = "high"

        const val INDICATOR_INVISIBLE = "invisible"
        const val INDICATOR_SUBTLE = "subtle"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        permissionManager = PermissionManager(this)

        setupEdgePosition()
        setupSensitivity()
        setupIndicator()
        setupAutoStart()
        setupBattery()
        setupPermissions()
    }

    private fun setupEdgePosition() {
        val positionGroup = findViewById<RadioGroup>(R.id.edgePositionGroup)
        val currentPosition = prefs.getString(KEY_EDGE_POSITION, EDGE_LEFT)

        if (currentPosition == EDGE_RIGHT) {
            findViewById<RadioButton>(R.id.radioEdgeRight).isChecked = true
        } else {
            findViewById<RadioButton>(R.id.radioEdgeLeft).isChecked = true
        }

        positionGroup.setOnCheckedChangeListener { _, checkedId ->
            val position = if (checkedId == R.id.radioEdgeRight) EDGE_RIGHT else EDGE_LEFT
            prefs.edit().putString(KEY_EDGE_POSITION, position).apply()
        }
    }

    private fun setupSensitivity() {
        val sensitivityGroup = findViewById<RadioGroup>(R.id.sensitivityGroup)
        val currentSensitivity = prefs.getString(KEY_GESTURE_SENSITIVITY, SENSITIVITY_MEDIUM)

        when (currentSensitivity) {
            SENSITIVITY_LOW -> findViewById<RadioButton>(R.id.radioSensLow).isChecked = true
            SENSITIVITY_HIGH -> findViewById<RadioButton>(R.id.radioSensHigh).isChecked = true
            else -> findViewById<RadioButton>(R.id.radioSensMedium).isChecked = true
        }

        sensitivityGroup.setOnCheckedChangeListener { _, checkedId ->
            val sensitivity = when (checkedId) {
                R.id.radioSensLow -> SENSITIVITY_LOW
                R.id.radioSensHigh -> SENSITIVITY_HIGH
                else -> SENSITIVITY_MEDIUM
            }
            prefs.edit().putString(KEY_GESTURE_SENSITIVITY, sensitivity).apply()
        }
    }

    private fun setupIndicator() {
        val indicatorGroup = findViewById<RadioGroup>(R.id.indicatorGroup)
        val currentIndicator = prefs.getString(KEY_EDGE_INDICATOR, INDICATOR_INVISIBLE)

        if (currentIndicator == INDICATOR_SUBTLE) {
            findViewById<RadioButton>(R.id.radioIndicatorSubtle).isChecked = true
        } else {
            findViewById<RadioButton>(R.id.radioIndicatorInvisible).isChecked = true
        }

        indicatorGroup.setOnCheckedChangeListener { _, checkedId ->
            val indicator = if (checkedId == R.id.radioIndicatorSubtle) INDICATOR_SUBTLE else INDICATOR_INVISIBLE
            prefs.edit().putString(KEY_EDGE_INDICATOR, indicator).apply()
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
