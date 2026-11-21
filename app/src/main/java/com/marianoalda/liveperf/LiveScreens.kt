package com.marianoalda.liveperf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LivePickGroupScreen(
    library: LiveperfLibrary,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
) {
    ScreenScaffold(title = "Directo · Grupo", onBack = onBack, content = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            library.groups.forEach { g ->
                ListRow(g.name, "${library.setlistsInGroup(g.id).size} setlists", onClick = { onPick(g.id) })
            }
        }
    })
}

@Composable
fun LivePickSetlistScreen(
    library: LiveperfLibrary,
    groupId: String,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
) {
    val group = library.group(groupId)
    ScreenScaffold(title = "Directo · Setlist", onBack = onBack, content = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(group?.name.orEmpty(), color = Soft, modifier = Modifier.padding(bottom = 8.dp))
            library.setlistsInGroup(groupId).forEach { sl ->
                ListRow(sl.name, "${library.songsInSetlist(sl.id).size} canciones", onClick = { onPick(sl.id) })
            }
        }
    })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiveSongScreen(
    library: LiveperfLibrary,
    setlistId: String,
    songIndex: Int,
    onBack: () -> Unit,
    onSongIndex: (Int) -> Unit,
    onProgramChange: (Int) -> Unit,
    onMidiReset: () -> Unit,
    onSave: (LiveperfLibrary) -> Unit,
) {
    val songs = library.songsInSetlist(setlistId)
    if (songs.isEmpty()) {
        ScreenScaffold(title = "Directo", onBack = onBack, content = {
            Text("Setlist vacía", color = Soft)
        })
        return
    }
    val index = songIndex.coerceIn(0, songs.lastIndex)
    val song = songs[index]

    var activeProgram by remember(song.id) {
        mutableIntStateOf(song.instruments.firstOrNull() ?: 0)
    }
    var pendingNav by remember { mutableStateOf<NavPending?>(null) }
    var pendingAt by remember { mutableLongStateOf(0L) }
    var hint by remember { mutableStateOf("") }
    var editingProgram by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(song.id) {
        val first = song.instruments.firstOrNull()
        if (first != null) {
            activeProgram = first
            onProgramChange(first)
        }
        pendingNav = null
        hint = ""
    }

    fun clearPendingNav() {
        pendingNav = null
        hint = ""
    }

    fun armOrFire(nav: NavPending) {
        val now = System.currentTimeMillis()
        if (pendingNav == nav && now - pendingAt < 1200) {
            clearPendingNav()
            when (nav) {
                NavPending.Next -> if (index < songs.lastIndex) onSongIndex(index + 1)
                NavPending.Prev -> if (index > 0) onSongIndex(index - 1)
                NavPending.MidiReset -> onMidiReset()
            }
        } else {
            pendingNav = nav
            pendingAt = now
            hint = when (nav) {
                NavPending.Next -> "Pulsa otra vez SIGUIENTE"
                NavPending.Prev -> "Pulsa otra vez ATRÁS"
                NavPending.MidiReset -> "Pulsa otra vez MIDI RESET"
            }
        }
    }

    val clearOutsideInteraction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy)
            .clickable(
                interactionSource = clearOutsideInteraction,
                indication = null,
                enabled = pendingNav != null,
            ) { clearPendingNav() }
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${index + 1}/${songs.size} · ${song.title}",
                    color = Accent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (hint.isNotBlank()) {
                    Text(hint, color = Danger, fontSize = 14.sp)
                }
            }
            PrimaryButton("Salir") { onBack() }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .background(PanelDim, RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("Notas", color = Accent, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("BPM: ${song.bpm?.toString() ?: "—"}", color = Soft, fontSize = 18.sp)
                Text("Tonalidad: ${song.key.ifBlank { "—" }}", color = Soft, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text("Cambios de instrumento", color = Accent, fontWeight = FontWeight.SemiBold)
                Text(
                    song.instrumentNotes.ifBlank { "—" },
                    color = Soft,
                    fontSize = 16.sp,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1.4f)
                    .fillMaxHeight(),
            ) {
                Text("Instrumentos", color = Accent, fontWeight = FontWeight.SemiBold)
                Text(
                    "Toque = cambio · mantener = etiqueta",
                    color = Soft.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(6.dp))
                if (song.instruments.isEmpty()) {
                    Text("Sin instrumentos en la canción", color = Soft)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(song.instruments, key = { it }) { pc ->
                            val active = pc == activeProgram
                            Box(
                                modifier = Modifier
                                    .width(160.dp)
                                    .height(96.dp)
                                    .background(
                                        if (active) AccentHot else Panel,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            activeProgram = pc
                                            onProgramChange(pc)
                                            clearPendingNav()
                                        },
                                        onLongClick = { editingProgram = pc },
                                    )
                                    .padding(10.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Column {
                                    Text(
                                        library.buttonTitle(pc),
                                        color = if (active) Navy else Soft,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 3,
                                    )
                                    Text(
                                        "PC $pc",
                                        color = if (active) Navy.copy(alpha = 0.8f) else Accent,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }

                editingProgram?.let { pc ->
                    LabelEditDialog(
                        program = pc,
                        currentLabel = library.customLabel(pc).orEmpty(),
                        v3Name = library.v3Name(pc),
                        onDismiss = { editingProgram = null },
                        onConfirm = { label ->
                            val name = V3SoundCatalog.find(pc)?.name
                            if (name != null) onSave(library.withInstrumentLabel(name, label))
                            editingProgram = null
                        },
                    )
                }

                Spacer(Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(72.dp)
                            .background(
                                if (pendingNav == NavPending.Prev) Danger else Panel,
                                RoundedCornerShape(12.dp),
                            )
                            .clickable(enabled = index > 0) { armOrFire(NavPending.Prev) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Atrás\n(2×)", color = Soft, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(72.dp)
                            .background(
                                if (pendingNav == NavPending.MidiReset) Danger else Panel,
                                RoundedCornerShape(12.dp),
                            )
                            .clickable { armOrFire(NavPending.MidiReset) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("MIDI\nRESET\n(2×)", color = Soft, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(96.dp)
                            .background(
                                if (pendingNav == NavPending.Next) AccentHot else Accent,
                                RoundedCornerShape(16.dp),
                            )
                            .clickable(enabled = index < songs.lastIndex) { armOrFire(NavPending.Next) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "SIGUIENTE (2×)",
                            color = Navy,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

private enum class NavPending { Next, Prev, MidiReset }
