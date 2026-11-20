package com.marianoalda.liveperf

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private lateinit var airplane: AirplaneController
    private lateinit var midi: MidiProgramSender

    private var airplaneOnEntry: Boolean = false
    private var airplaneOn by mutableStateOf(false)
    private var midiConnected by mutableStateOf(false)
    private var midiName by mutableStateOf<String?>(null)
    private var message by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        airplane = AirplaneController(this)
        midi = MidiProgramSender(this)

        keepAwakeAndUnlocked()
        hideSystemBars()

        airplaneOnEntry = airplane.isAirplaneModeOn()
        airplaneOn = airplaneOnEntry
        if (!airplaneOnEntry) {
            if (airplane.setAirplaneMode(true)) {
                airplaneOn = true
            } else {
                message = getString(R.string.airplane_fail)
            }
        }

        midi.onStatus = { connected, name ->
            runOnUiThread {
                midiConnected = connected
                midiName = name
            }
        }
        midi.start()

        setContent {
            LiveperfTheme {
                HomeScreen(
                    airplaneOn = airplaneOn,
                    midiConnected = midiConnected,
                    midiName = midiName,
                    message = message,
                    buildLabel = buildLabel(),
                    onOrgan = { sendPc(69) },
                    onPiano = { sendPc(0) },
                    onToggleAirplane = ::toggleAirplane,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        airplaneOn = airplane.isAirplaneModeOn()
        keepAwakeAndUnlocked()
    }

    override fun onDestroy() {
        if (isFinishing) {
            airplane.setAirplaneMode(airplaneOnEntry)
            midi.stop()
        }
        super.onDestroy()
    }

    private fun sendPc(program: Int) {
        val ok = midi.sendProgramChange(program, channel = 0)
        message = if (ok) {
            getString(R.string.midi_sent, program)
        } else {
            getString(R.string.midi_fail)
        }
    }

    private fun toggleAirplane() {
        val target = !airplane.isAirplaneModeOn()
        if (airplane.setAirplaneMode(target)) {
            airplaneOn = target
            message = ""
        } else {
            message = getString(R.string.airplane_fail)
            airplaneOn = airplane.isAirplaneModeOn()
        }
    }

    private fun keepAwakeAndUnlocked() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun buildLabel(): String {
        val name = BuildConfig.VERSION_NAME
        return if (name.isBlank()) {
            getString(R.string.build_label, BuildConfig.BUILD_TIME)
        } else {
            name
        }
    }
}
