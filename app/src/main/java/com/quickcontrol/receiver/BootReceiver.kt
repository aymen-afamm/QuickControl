package com.quickcontrol.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.quickcontrol.service.EdgePanelService

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val PREFS_NAME = "quickcontrol_prefs"
        private const val KEY_AUTO_START = "auto_start"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs: SharedPreferences =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            if (prefs.getBoolean(KEY_AUTO_START, false)) {
                Log.d(TAG, "Boot completed, auto-starting EdgePanelService")
                val serviceIntent = Intent(context, EdgePanelService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
