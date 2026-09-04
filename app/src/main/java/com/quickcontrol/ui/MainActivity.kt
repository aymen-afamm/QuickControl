package com.quickcontrol.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.quickcontrol.R
import com.quickcontrol.manager.PermissionManager
import com.quickcontrol.service.EdgePanelService

class MainActivity : AppCompatActivity() {

    private lateinit var permissionManager: PermissionManager

    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var lockStatus: TextView
    private lateinit var accessibilityStatus: TextView
    private lateinit var btnOverlay: Button
    private lateinit var btnLock: Button
    private lateinit var btnAccessibility: Button
    private lateinit var btnStart: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionManager = PermissionManager(this)

        statusDot = findViewById(R.id.statusDot)
        statusText = findViewById(R.id.statusText)
        overlayStatus = findViewById(R.id.overlayStatus)
        lockStatus = findViewById(R.id.lockStatus)
        accessibilityStatus = findViewById(R.id.accessibilityStatus)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnLock = findViewById(R.id.btnLock)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnStart = findViewById(R.id.btnStart)

        // Overlay permission
        btnOverlay.setOnClickListener {
            if (!permissionManager.canDrawOverlays()) {
                startActivity(permissionManager.createOverlayPermissionIntent())
            }
        }

        // Device admin
        btnLock.setOnClickListener {
            if (!permissionManager.isDeviceAdminActive()) {
                startActivity(permissionManager.createDeviceAdminIntent())
            }
        }

        // Accessibility service
        btnAccessibility.setOnClickListener {
            if (!permissionManager.isAccessibilityServiceEnabled()) {
                startActivity(permissionManager.createAccessibilitySettingsIntent())
            }
        }

        // Start/Stop
        btnStart.setOnClickListener {
            if (EdgePanelService.isRunning) {
                stopService(Intent(this, EdgePanelService::class.java))
            } else {
                if (permissionManager.canDrawOverlays()) {
                    val intent = Intent(this, EdgePanelService::class.java)
                    startForegroundService(intent)
                } else {
                    startActivity(permissionManager.createOverlayPermissionIntent())
                }
            }
            // Update UI after a short delay to let service state change
            btnStart.postDelayed({ updateUI() }, 500)
        }

        // Settings
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        // Service status
        val running = EdgePanelService.isRunning
        statusDot.setBackgroundColor(
            if (running) getColor(R.color.success) else getColor(R.color.error)
        )
        statusText.text = if (running) {
            getString(R.string.service_running)
        } else {
            getString(R.string.service_stopped)
        }
        btnStart.text = if (running) {
            getString(R.string.stop_button)
        } else {
            getString(R.string.start_button)
        }

        // Overlay
        val hasOverlay = permissionManager.canDrawOverlays()
        overlayStatus.visibility = if (hasOverlay) View.VISIBLE else View.GONE
        btnOverlay.visibility = if (hasOverlay) View.GONE else View.VISIBLE

        // Device admin
        val hasAdmin = permissionManager.isDeviceAdminActive()
        lockStatus.visibility = if (hasAdmin) View.VISIBLE else View.GONE
        btnLock.visibility = if (hasAdmin) View.GONE else View.VISIBLE

        // Accessibility
        val hasAccessibility = permissionManager.isAccessibilityServiceEnabled()
        accessibilityStatus.visibility = if (hasAccessibility) View.VISIBLE else View.GONE
        btnAccessibility.visibility = if (hasAccessibility) View.GONE else View.VISIBLE
    }
}
