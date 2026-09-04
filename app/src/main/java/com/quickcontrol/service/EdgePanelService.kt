package com.quickcontrol.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
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
        private const val ACTION_STOP = "com.quickcontrol.ACTION_STOP"

        var isRunning = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences
    private lateinit var permissionManager: PermissionManager
    private lateinit var volumeManager: VolumeManager
    private lateinit var flashlightManager: FlashlightManager

    private val mainHandler = Handler(Looper.getMainLooper())
    private var triggerView: View? = null
    private var panelView: View? = null
    private var isPanelOpen = false

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            SettingsActivity.KEY_EDGE_POSITION,
            SettingsActivity.KEY_GESTURE_SENSITIVITY,
            SettingsActivity.KEY_EDGE_INDICATOR -> {
                refreshEdgeTrigger()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        permissionManager = PermissionManager(this)
        volumeManager = VolumeManager(this)
        flashlightManager = FlashlightManager(this)
        isRunning = true

        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        showEdgeTrigger()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        removeEdgeTrigger()
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

    // --- Invisible Edge Trigger Zone ---

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun showEdgeTrigger() {
        if (triggerView != null) return

        triggerView = LayoutInflater.from(this).inflate(R.layout.edge_handle, null)

        val isRightEdge = prefs.getString(
            SettingsActivity.KEY_EDGE_POSITION, SettingsActivity.EDGE_LEFT
        ) == SettingsActivity.EDGE_RIGHT

        val sensitivity = prefs.getString(
            SettingsActivity.KEY_GESTURE_SENSITIVITY, SettingsActivity.SENSITIVITY_MEDIUM
        )

        val indicatorMode = prefs.getString(
            SettingsActivity.KEY_EDGE_INDICATOR, SettingsActivity.INDICATOR_INVISIBLE
        )

        // Trigger width: Low=6dp, Medium=8dp, High=12dp
        val triggerWidthDp = when (sensitivity) {
            SettingsActivity.SENSITIVITY_LOW -> 6
            SettingsActivity.SENSITIVITY_HIGH -> 12
            else -> 8
        }
        val triggerWidthPx = dpToPx(triggerWidthDp)

        // Configure optional visual hint indicator
        val indicator = triggerView?.findViewById<View>(R.id.edgeIndicator)
        if (indicatorMode == SettingsActivity.INDICATOR_SUBTLE) {
            indicator?.visibility = View.VISIBLE
            val lp = indicator?.layoutParams as? FrameLayout.LayoutParams
            if (lp != null) {
                lp.gravity = Gravity.CENTER_VERTICAL or (if (isRightEdge) Gravity.END else Gravity.START)
                indicator.layoutParams = lp
            }
        } else {
            indicator?.visibility = View.GONE
        }

        val edgeGravity = if (isRightEdge) (Gravity.END or Gravity.TOP) else (Gravity.START or Gravity.TOP)

        val params = WindowManager.LayoutParams(
            triggerWidthPx,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = edgeGravity
            x = 0
            y = 0
        }

        setupTriggerGesture(isRightEdge, sensitivity)

        try {
            windowManager.addView(triggerView, params)
            Log.d(TAG, "Edge trigger mounted (right=$isRightEdge, width=${triggerWidthDp}dp)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add edge trigger view", e)
        }
    }

    private fun setupTriggerGesture(isRightEdge: Boolean, sensitivity: String?) {
        // Swipe distance threshold to trigger panel open
        val swipeThresholdDp = when (sensitivity) {
            SettingsActivity.SENSITIVITY_LOW -> 40
            SettingsActivity.SENSITIVITY_HIGH -> 20
            else -> 28
        }
        val swipeThresholdPx = dpToPx(swipeThresholdDp)

        var startX = 0f
        var startY = 0f
        var isSwipeTriggered = false

        triggerView?.setOnTouchListener { _, event ->
            if (isPanelOpen) return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    isSwipeTriggered = false
                    Log.d(TAG, "Touch DOWN at ($startX, $startY)")
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isSwipeTriggered) return@setOnTouchListener true

                    val currentX = event.rawX
                    val currentY = event.rawY
                    val deltaX = if (isRightEdge) (startX - currentX) else (currentX - startX)
                    val deltaY = Math.abs(currentY - startY)
                    Log.d(TAG, "Touch MOVE: cur=($currentX, $currentY), deltaX=$deltaX, deltaY=$deltaY, thresh=$swipeThresholdPx")

                    // Horizontal inward swipe criteria:
                    // 1. Inward movement reaches threshold
                    // 2. Horizontal displacement exceeds vertical displacement (prioritizes horizontal swipe)
                    if (deltaX >= swipeThresholdPx && deltaX > deltaY) {
                        isSwipeTriggered = true
                        Log.d(TAG, "SWIPE TRIGGERED! Opening panel...")
                        openPanel()
                        true
                    } else {
                        true
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    Log.d(TAG, "Touch UP/CANCEL: isSwipeTriggered=$isSwipeTriggered")
                    isSwipeTriggered = false
                    true
                }

                else -> false
            }
        }
    }

    private fun removeEdgeTrigger() {
        triggerView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove edge trigger view", e)
            }
        }
        triggerView = null
    }

    private fun refreshEdgeTrigger() {
        mainHandler.post {
            removeEdgeTrigger()
            showEdgeTrigger()
        }
    }

    // --- Control Panel ---

    private fun openPanel() {
        if (panelView != null || isPanelOpen) return

        panelView = LayoutInflater.from(this).inflate(R.layout.edge_panel, null)

        val isRightEdge = prefs.getString(
            SettingsActivity.KEY_EDGE_POSITION, SettingsActivity.EDGE_LEFT
        ) == SettingsActivity.EDGE_RIGHT

        val container = panelView?.findViewById<LinearLayout>(R.id.panelContainer)
        val scrim = panelView?.findViewById<View>(R.id.panelScrim)

        // Align container to Left or Right side
        val containerParams = container?.layoutParams as? FrameLayout.LayoutParams
        if (containerParams != null) {
            containerParams.gravity = if (isRightEdge) Gravity.END else Gravity.START
            container.layoutParams = containerParams
        }

        val panelWidthPx = 260f * resources.displayMetrics.density
        val initialTranslationX = if (isRightEdge) panelWidthPx else -panelWidthPx
        container?.translationX = initialTranslationX
        scrim?.alpha = 0f

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

            // Smooth slide-in and scrim fade-in
            val slideAnim = ObjectAnimator.ofFloat(container, "translationX", initialTranslationX, 0f).apply {
                duration = 240
                interpolator = DecelerateInterpolator()
            }
            val scrimAnim = ObjectAnimator.ofFloat(scrim, "alpha", 0f, 1f).apply {
                duration = 240
            }
            AnimatorSet().apply {
                playTogether(slideAnim, scrimAnim)
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add panel view", e)
        }
    }

    private fun closePanel() {
        if (!isPanelOpen || panelView == null) return

        val isRightEdge = prefs.getString(
            SettingsActivity.KEY_EDGE_POSITION, SettingsActivity.EDGE_LEFT
        ) == SettingsActivity.EDGE_RIGHT

        val container = panelView?.findViewById<LinearLayout>(R.id.panelContainer)
        val scrim = panelView?.findViewById<View>(R.id.panelScrim)
        val panelWidthPx = 260f * resources.displayMetrics.density
        val targetTranslationX = if (isRightEdge) panelWidthPx else -panelWidthPx

        if (container != null && scrim != null) {
            val slideAnim = ObjectAnimator.ofFloat(container, "translationX", 0f, targetTranslationX).apply {
                duration = 200
                interpolator = AccelerateInterpolator()
            }
            val scrimAnim = ObjectAnimator.ofFloat(scrim, "alpha", 1f, 0f).apply {
                duration = 200
            }
            AnimatorSet().apply {
                playTogether(slideAnim, scrimAnim)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        removePanel()
                    }
                })
                start()
            }
        } else {
            removePanel()
        }
    }

    private fun removePanel() {
        panelView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove panel view", e)
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
                mainHandler.postDelayed({
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
                mainHandler.postDelayed({
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
