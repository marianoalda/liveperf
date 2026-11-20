package com.marianoalda.liveperf

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Reads/writes airplane mode. Silent writes need
 * `adb shell pm grant com.marianoalda.liveperf android.permission.WRITE_SECURE_SETTINGS`.
 */
class AirplaneController(private val context: Context) {
    fun isAirplaneModeOn(): Boolean =
        Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0,
        ) == 1

    fun setAirplaneMode(enable: Boolean): Boolean {
        return try {
            Settings.Global.putInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                if (enable) 1 else 0,
            )
            val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).apply {
                putExtra("state", enable)
            }
            context.sendBroadcast(intent)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "WRITE_SECURE_SETTINGS missing", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "airplane toggle failed", e)
            false
        }
    }

    companion object {
        private const val TAG = "AirplaneController"
    }
}
