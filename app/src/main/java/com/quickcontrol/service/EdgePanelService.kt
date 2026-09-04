package com.quickcontrol.service

import android.animation.ObjectAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.quickcontrol.R
import com.quickcontrol.manager.FlashlightManager
import com.quickcontrol.manager.PermissionManager
import com.quickcontrol.manager.VolumeManager
import com.quickcontrol.ui.MainActivity
import com.quickcontrol.ui.SettingsActivity

class EdgePanelService : Service() {

    companion object {
        private const val TAG = "EdgePanelService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "quickcontrol_channel"
        private const val PREFS_NAME = "quickcontrol_prefs"
        private const val KEY_HANDLE_Y = "handle_y"
        private const val KEY_HANDLE_OPACITY = "handle_opacity"
        private const val ACTION_STOP = "com.quickcontrol.ACTION_STOP"

        var isRunning = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences
    private lateinit var permissionManager: PermissionManager
    private lateinit var volumeManager: VolumeManager
    private lateinit var flashlightManager: FlashlightManager

    private var handleView: View? = null
    private var panelView: View? = null
    private var isPanelOpen = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        permissionManager = PermissionManager(this)
        volumeManager = VolumeManager(this)
        flashlightManager = FlashlightManager(this)
        isRunning = true
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        showHandle()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        removeHandle()
        removePanel()
        flashlightManager.release()
        Log.d(TAG, "Service destroyed")
    }

    // --- Notification ---

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, EdgePanelService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(openPending)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.notification_stop),
                    stopPending
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    // --- Handle ---

    private fun showHandle() {
        if (handleView != null) return

        handleView = LayoutInflater.from(this).inflate(R.layout.edge_handle, null)

        val opacity = when (prefs.getString(KEY_HANDLE_OPACITY, "medium")) {
            "low" -> 0.3f
            "medium" -> 0.5f
            "high" -> 0.8f
            else -> 0.5f
        }
        handleView?.alpha = opacity

        val savedY = prefs.getInt(KEY_HANDLE_Y, 0)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            x = 0
            y = savedY
        }

        setupHandleTouch(params)

        try {
            windowManager.addView(handleView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add handle view", e)
        }
    }

    private fun setupHandleTouch(params: WindowManager.LayoutParams) {
        var initialY = 0
        var initialTouchY = 0f
        var isDragging = false
        var startTime = 0L

        handleView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = params.y
                    initialTouchY = event.rawY
                    isDragging = false
                    startTime = System.currentTimeMillis()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - initialTouchY
                    if (Math.abs(deltaY) > 10) {
                        isDragging = true
                    }
                    params.y = initialY + deltaY.toInt()
                    try {
                        windowManager.updateViewLayout(handleView, params)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update handle layout", e)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - startTime
                    if (!isDragging && elapsed < 300) {
                        // Tap — toggle panel
                        togglePanel()
                    } else {
                        // Save position after drag
                        prefs.edit().putInt(KEY_HANDLE_Y, params.y).apply()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun removeHandle() {
        handleView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove handle", e)
            }
        }
        handleView = null
    }

    // --- Panel ---

    private fun togglePanel() {
        if (isPanelOpen) {
            closePanel()
        } else {
            openPanel()
        }
    }

    private fun openPanel() {
        if (panelView != null) return

        panelView = LayoutInflater.from(this).inflate(R.layout.edge_panel, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        setupPanelButtons()

        try {
            windowManager.addView(panelView, params)
            isPanelOpen = true

            // Slide-in animation
            val container = panelView?.findViewById<LinearLayout>(R.id.panelContainer)
            container?.let {
                ObjectAnimator.ofFloat(it, "translationX", -260f * resources.displayMetrics.density, 0f)
                    .setDuration(250)
                    .start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add panel view", e)
        }
    }

    private fun closePanel() {
        val container = panelView?.findViewById<LinearLayout>(R.id.panelContainer)
        if (container != null) {
            val animator = ObjectAnimator.ofFloat(
                container, "translationX", 0f, -260f * resources.displayMetrics.density
            )
            animator.duration = 200
            animator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    removePanel()
                }
            })
            animator.start()
        } else {
            removePanel()
        }
    }

    private fun removePanel() {
        panelView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove panel", e)
            }
        }
        panelView = null
        isPanelOpen = false
    }

    private fun setupPanelButtons() {
        val panel = panelView ?: return

        // Scrim — tap to dismiss
        panel.findViewById<View>(R.id.panelScrim)?.setOnClickListener {
            closePanel()
        }

        // Lock Screen
        panel.findViewById<View>(R.id.btnLockScreen)?.setOnClickListener {
            if (permissionManager.isDeviceAdminActive()) {
                closePanel()
                // Small delay so panel closes before screen locks
                handleView?.postDelayed({
                    permissionManager.lockScreen()
                }, 300)
            } else {
                Toast.makeText(this, R.string.lock_no_permission, Toast.LENGTH_SHORT).show()
            }
        }

        // Power Menu
        panel.findViewById<View>(R.id.btnPowerMenu)?.setOnClickListener {
            if (permissionManager.isAccessibilityServiceEnabled()) {
                closePanel()
                handleView?.postDelayed({
                    permissionManager.showPowerDialog()
                }, 300)
            } else {
                Toast.makeText(this, R.string.power_no_permission, Toast.LENGTH_SHORT).show()
            }
        }

        // Volume Down
        panel.findViewById<View>(R.id.btnVolumeDown)?.setOnClickListener {
            volumeManager.volumeDown()
            updateVolumeUI()
        }

        // Volume Up
        panel.findViewById<View>(R.id.btnVolumeUp)?.setOnClickListener {
            volumeManager.volumeUp()
            updateVolumeUI()
        }

        // Volume SeekBar
        val seekBar = panel.findViewById<SeekBar>(R.id.volumeSeekBar)
        val volumeLevel = panel.findViewById<TextView>(R.id.volumeLevel)
        if (seekBar != null && volumeLevel != null) {
            volumeManager.setupSeekBar(seekBar) { level ->
                volumeLevel.text = level.toString()
            }
            volumeLevel.text = volumeManager.currentVolume.toString()
        }

        // Mute
        panel.findViewById<View>(R.id.btnMute)?.setOnClickListener {
            volumeManager.toggleMute()
            updateVolumeUI()
            val msg = if (volumeManager.isMuted) R.string.muted else R.string.unmuted
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Flashlight
        val flashlightText = panel.findViewById<TextView>(R.id.flashlightText)
        panel.findViewById<View>(R.id.btnFlashlight)?.setOnClickListener {
            if (flashlightManager.isAvailable()) {
                flashlightManager.toggle()
            } else {
                Toast.makeText(this, R.string.flashlight_not_available, Toast.LENGTH_SHORT).show()
            }
        }
        flashlightManager.setOnStateChangedListener { isOn ->
            flashlightText?.post {
                flashlightText.text = if (isOn) {
                    getString(R.string.flashlight_on)
                } else {
                    getString(R.string.flashlight)
                }
            }
        }
        // Set initial flashlight text
        if (flashlightManager.isFlashlightOn()) {
            flashlightText?.text = getString(R.string.flashlight_on)
        }

        // Settings
        panel.findViewById<View>(R.id.btnOpenSettings)?.setOnClickListener {
            closePanel()
            val intent = Intent(this, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    private fun updateVolumeUI() {
        val panel = panelView ?: return
        val seekBar = panel.findViewById<SeekBar>(R.id.volumeSeekBar)
        val volumeLevel = panel.findViewById<TextView>(R.id.volumeLevel)
        seekBar?.progress = volumeManager.currentVolume
        volumeLevel?.text = volumeManager.currentVolume.toString()
    }
}
