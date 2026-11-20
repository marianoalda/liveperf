package com.marianoalda.liveperf

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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

    private val importJsonLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) importLibraryFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        V3SoundCatalog.init(this)
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
                            onSongs = { screen = AppScreen.EditSongs },
                            onImportJson = {
                                importJsonLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                            },
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
                            onOpenSetlist = { setlistId ->
                                screen = AppScreen.EditSetlistDetail(
                                    setlistId = setlistId,
                                    parentGroupId = s.groupId,
                                )
                            },
                            onSave = ::persist,
                        )

                        is AppScreen.EditSetlistDetail -> EditSetlistDetailScreen(
                            library = library,
                            setlistId = s.setlistId,
                            parentGroupId = s.parentGroupId,
                            onBack = { screen = AppScreen.EditGroupDetail(s.parentGroupId) },
                            onOpenSong = { songId ->
                                screen = AppScreen.EditSongDetail(
                                    songId = songId,
                                    parentSetlistId = s.setlistId,
                                    parentGroupId = s.parentGroupId,
                                )
                            },
                            onSave = ::persist,
                        )

                        AppScreen.EditSongs -> EditSongsScreen(
                            library = library,
                            onBack = { screen = AppScreen.EditHub },
                            onOpen = { songId ->
                                val (groupId, setlistId) = library.songParentContext(songId)
                                screen = AppScreen.EditSongDetail(
                                    songId = songId,
                                    parentSetlistId = setlistId,
                                    parentGroupId = groupId,
                                )
                            },
                            onSave = ::persist,
                        )

                        is AppScreen.EditSongDetail -> EditSongDetailScreen(
                            library = library,
                            songId = s.songId,
                            parentSetlistId = s.parentSetlistId,
                            parentGroupId = s.parentGroupId,
                            onBack = {
                                val setlistId = s.parentSetlistId
                                val groupId = s.parentGroupId
                                screen = when {
                                    setlistId != null && groupId != null ->
                                        AppScreen.EditSetlistDetail(setlistId, groupId)
                                    setlistId != null -> {
                                        val inferred = library.groupsContainingSetlist(setlistId)
                                            .singleOrNull()?.id
                                        if (inferred != null) {
                                            AppScreen.EditSetlistDetail(setlistId, inferred)
                                        } else {
                                            AppScreen.EditSongs
                                        }
                                    }
                                    else -> AppScreen.EditSongs
                                }
                            },
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

    private fun importLibraryFromUri(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: run {
                    message = "No se pudo leer el fichero"
                    return
                }
            val result = store.importHierarchy(text)
            persist(result.library)
            message = if (result.warnings.isEmpty()) {
                "Biblioteca importada (${result.library.groups.size} grupos, " +
                    "${result.library.setlists.size} setlists, ${result.library.songs.size} canciones)"
            } else {
                "Importada con avisos: ${result.warnings.take(3).joinToString("; ")}" +
                    if (result.warnings.size > 3) "…" else ""
            }
            screen = AppScreen.EditHub
        } catch (e: Exception) {
            message = "Error al importar: ${e.message ?: e.javaClass.simpleName}"
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
            AppScreen.EditGroups, AppScreen.EditSongs -> AppScreen.EditHub
            is AppScreen.EditGroupDetail -> AppScreen.EditGroups
            is AppScreen.EditSetlistDetail -> AppScreen.EditGroupDetail(s.parentGroupId)
            is AppScreen.EditSongDetail -> {
                val setlistId = s.parentSetlistId
                val groupId = s.parentGroupId
                    ?: setlistId?.let { library.groupsContainingSetlist(it).singleOrNull()?.id }
                when {
                    setlistId != null && groupId != null ->
                        AppScreen.EditSetlistDetail(setlistId, groupId)
                    else -> AppScreen.EditSongs
                }
            }
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
