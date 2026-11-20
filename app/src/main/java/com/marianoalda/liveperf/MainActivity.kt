package com.marianoalda.liveperf

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private lateinit var airplane: AirplaneController
    private lateinit var midi: MidiProgramSender
    private lateinit var store: LibraryStore

    private var airplaneOnEntry: Boolean = false
    private var airplaneOn by mutableStateOf(false)
    private var midiConnected by mutableStateOf(false)
    private var midiName by mutableStateOf<String?>(null)
    private var message by mutableStateOf("")
    private var library by mutableStateOf(LiveperfLibrary())
    private var screen by mutableStateOf<AppScreen>(AppScreen.Home)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        airplane = AirplaneController(this)
        midi = MidiProgramSender(this)
        store = LibraryStore(this)
        library = store.load()

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
                Surface(modifier = Modifier.fillMaxSize(), color = Navy) {
                    BackHandler(enabled = screen !is AppScreen.Home) {
                        navigateBack()
                    }
                    when (val s = screen) {
                        AppScreen.Home -> HomeScreen(
                            airplaneOn = airplaneOn,
                            midiConnected = midiConnected,
                            midiName = midiName,
                            message = message,
                            buildLabel = buildLabel(),
                            onEdit = { screen = AppScreen.EditHub },
                            onLive = { screen = AppScreen.LivePickGroup },
                            onToggleAirplane = ::toggleAirplane,
                        )

                        AppScreen.EditHub -> EditHubScreen(
                            onBack = { screen = AppScreen.Home },
                            onGroups = { screen = AppScreen.EditGroups },
                            onSetlists = { screen = AppScreen.EditSetlists },
                            onSongs = { screen = AppScreen.EditSongs },
                        )

                        AppScreen.EditGroups -> EditGroupsScreen(
                            library = library,
                            onBack = { screen = AppScreen.EditHub },
                            onOpen = { screen = AppScreen.EditGroupDetail(it) },
                            onSave = ::persist,
                        )

                        is AppScreen.EditGroupDetail -> EditGroupDetailScreen(
                            library = library,
                            groupId = s.groupId,
                            onBack = { screen = AppScreen.EditGroups },
                            onSave = ::persist,
                        )

                        AppScreen.EditSetlists -> EditSetlistsScreen(
                            library = library,
                            onBack = { screen = AppScreen.EditHub },
                            onOpen = { screen = AppScreen.EditSetlistDetail(it) },
                            onSave = ::persist,
                        )

                        is AppScreen.EditSetlistDetail -> EditSetlistDetailScreen(
                            library = library,
                            setlistId = s.setlistId,
                            onBack = { screen = AppScreen.EditSetlists },
                            onSave = ::persist,
                        )

                        AppScreen.EditSongs -> EditSongsScreen(
                            library = library,
                            onBack = { screen = AppScreen.EditHub },
                            onOpen = { screen = AppScreen.EditSongDetail(it) },
                            onSave = ::persist,
                        )

                        is AppScreen.EditSongDetail -> EditSongDetailScreen(
                            library = library,
                            songId = s.songId,
                            onBack = { screen = AppScreen.EditSongs },
                            onSave = ::persist,
                        )

                        AppScreen.LivePickGroup -> LivePickGroupScreen(
                            library = library,
                            onBack = { screen = AppScreen.Home },
                            onPick = { screen = AppScreen.LivePickSetlist(it) },
                        )

                        is AppScreen.LivePickSetlist -> LivePickSetlistScreen(
                            library = library,
                            groupId = s.groupId,
                            onBack = { screen = AppScreen.LivePickGroup },
                            onPick = { setlistId ->
                                screen = AppScreen.LiveSong(s.groupId, setlistId, 0)
                            },
                        )

                        is AppScreen.LiveSong -> LiveSongScreen(
                            library = library,
                            setlistId = s.setlistId,
                            songIndex = s.songIndex,
                            onBack = { screen = AppScreen.LivePickSetlist(s.groupId) },
                            onSongIndex = { idx ->
                                screen = s.copy(songIndex = idx)
                            },
                            onProgramChange = { pc ->
                                sendPc(pc)
                            },
                        )
                    }
                }
            }
        }
    }

    private fun persist(lib: LiveperfLibrary) {
        library = lib
        store.save(lib)
    }

    private fun navigateBack() {
        screen = when (val s = screen) {
            AppScreen.Home -> s
            AppScreen.EditHub, AppScreen.LivePickGroup -> AppScreen.Home
            AppScreen.EditGroups, AppScreen.EditSetlists, AppScreen.EditSongs -> AppScreen.EditHub
            is AppScreen.EditGroupDetail -> AppScreen.EditGroups
            is AppScreen.EditSetlistDetail -> AppScreen.EditSetlists
            is AppScreen.EditSongDetail -> AppScreen.EditSongs
            is AppScreen.LivePickSetlist -> AppScreen.LivePickGroup
            is AppScreen.LiveSong -> AppScreen.LivePickSetlist(s.groupId)
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
