package com.marianoalda.liveperf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

@Composable
fun EditHubScreen(onBack: () -> Unit, onGroups: () -> Unit, onSetlists: () -> Unit, onSongs: () -> Unit) {
    ScreenScaffold(title = "Modo edición", onBack = onBack, content = {
        Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ListRow("Grupos", "Nivel superior", onClick = onGroups)
            ListRow("Setlists", "Espectáculos", onClick = onSetlists)
            ListRow("Canciones", "BPM, tonalidad, instrumentos", onClick = onSongs)
        }
    })
}

@Composable
fun EditGroupsScreen(
    library: LiveperfLibrary,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onSave: (LiveperfLibrary) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    ScreenScaffold(title = "Grupos", onBack = onBack, content = {
        Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DarkField(newName, { newName = it }, "Nuevo grupo", modifier = Modifier.weight(1f))
                PrimaryButton("Añadir") {
                    val name = newName.trim()
                    if (name.isEmpty()) return@PrimaryButton
                    onSave(library.copy(groups = library.groups + Group(name = name)))
                    newName = ""
                }
            }
            LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
                items(library.groups, key = { it.id }) { g ->
                    ListRow(
                        title = g.name,
                        subtitle = "${g.setlistIds.size} setlists",
                        onClick = { onOpen(g.id) },
                        onDelete = {
                            onSave(library.copy(groups = library.groups.filterNot { it.id == g.id }))
                        },
                    )
                }
            }
        }
    })
}

@Composable
fun EditGroupDetailScreen(
    library: LiveperfLibrary,
    groupId: String,
    onBack: () -> Unit,
    onSave: (LiveperfLibrary) -> Unit,
) {
    val group = library.group(groupId) ?: return
    var name by remember(group.id) { mutableStateOf(group.name) }
    ScreenScaffold(title = "Grupo", onBack = onBack, content = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DarkField(name, { name = it }, "Nombre")
            PrimaryButton("Guardar nombre") {
                onSave(
                    library.copy(
                        groups = library.groups.map {
                            if (it.id == groupId) it.copy(name = name.trim().ifEmpty { it.name }) else it
                        },
                    ),
                )
            }
            Text("Setlists en el grupo", color = Accent, fontWeight = FontWeight.SemiBold)
            library.setlists.forEach { sl ->
                val checked = group.setlistIds.contains(sl.id)
                Text(
                    text = (if (checked) "✓ " else "○ ") + sl.name,
                    color = Soft,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (checked) Panel else PanelDim, RoundedCornerShape(8.dp))
                        .clickable {
                            val ids = if (checked) {
                                group.setlistIds - sl.id
                            } else {
                                group.setlistIds + sl.id
                            }
                            onSave(
                                library.copy(
                                    groups = library.groups.map {
                                        if (it.id == groupId) it.copy(name = name.trim().ifEmpty { it.name }, setlistIds = ids) else it
                                    },
                                ),
                            )
                        }
                        .padding(12.dp),
                )
            }
        }
    })
}

@Composable
fun EditSetlistsScreen(
    library: LiveperfLibrary,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onSave: (LiveperfLibrary) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    ScreenScaffold(title = "Setlists", onBack = onBack, content = {
        Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DarkField(newName, { newName = it }, "Nueva setlist", modifier = Modifier.weight(1f))
                PrimaryButton("Añadir") {
                    val name = newName.trim()
                    if (name.isEmpty()) return@PrimaryButton
                    onSave(library.copy(setlists = library.setlists + Setlist(name = name)))
                    newName = ""
                }
            }
            LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
                items(library.setlists, key = { it.id }) { s ->
                    ListRow(
                        title = s.name,
                        subtitle = "${s.songIds.size} canciones",
                        onClick = { onOpen(s.id) },
                        onDelete = {
                            val groups = library.groups.map { g ->
                                g.copy(setlistIds = g.setlistIds.filterNot { it == s.id })
                            }
                            onSave(
                                library.copy(
                                    setlists = library.setlists.filterNot { it.id == s.id },
                                    groups = groups,
                                ),
                            )
                        },
                    )
                }
            }
        }
    })
}

@Composable
fun EditSetlistDetailScreen(
    library: LiveperfLibrary,
    setlistId: String,
    onBack: () -> Unit,
    onSave: (LiveperfLibrary) -> Unit,
) {
    val setlist = library.setlist(setlistId) ?: return
    var name by remember(setlist.id) { mutableStateOf(setlist.name) }
    ScreenScaffold(title = "Setlist", onBack = onBack, content = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DarkField(name, { name = it }, "Nombre")
            PrimaryButton("Guardar nombre") {
                onSave(
                    library.copy(
                        setlists = library.setlists.map {
                            if (it.id == setlistId) it.copy(name = name.trim().ifEmpty { it.name }) else it
                        },
                    ),
                )
            }
            Text("Canciones (orden = toques)", color = Accent, fontWeight = FontWeight.SemiBold)
            Text("Pulsa para añadir/quitar. Mantén el orden con ↑ ↓.", color = Soft.copy(alpha = 0.7f), fontSize = 13.sp)
            setlist.songIds.mapNotNull { id -> library.song(id)?.let { id to it } }.forEachIndexed { index, (id, song) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${index + 1}. ${song.title}",
                        color = Soft,
                        modifier = Modifier.weight(1f),
                        fontSize = 16.sp,
                    )
                    PrimaryButton("↑") {
                        if (index == 0) return@PrimaryButton
                        val ids = setlist.songIds.toMutableList()
                        val tmp = ids[index - 1]
                        ids[index - 1] = ids[index]
                        ids[index] = tmp
                        onSave(library.copy(setlists = library.setlists.map {
                            if (it.id == setlistId) it.copy(name = name.trim().ifEmpty { it.name }, songIds = ids) else it
                        }))
                    }
                    PrimaryButton("↓") {
                        if (index >= setlist.songIds.lastIndex) return@PrimaryButton
                        val ids = setlist.songIds.toMutableList()
                        val tmp = ids[index + 1]
                        ids[index + 1] = ids[index]
                        ids[index] = tmp
                        onSave(library.copy(setlists = library.setlists.map {
                            if (it.id == setlistId) it.copy(name = name.trim().ifEmpty { it.name }, songIds = ids) else it
                        }))
                    }
                    PrimaryButton("Quitar") {
                        onSave(library.copy(setlists = library.setlists.map {
                            if (it.id == setlistId) {
                                it.copy(
                                    name = name.trim().ifEmpty { it.name },
                                    songIds = it.songIds.filterNot { sid -> sid == id },
                                )
                            } else it
                        }))
                    }
                }
            }
            Text("Añadir canción", color = Accent, fontWeight = FontWeight.SemiBold)
            library.songs.filter { it.id !in setlist.songIds }.forEach { song ->
                ListRow(song.title, onClick = {
                    onSave(library.copy(setlists = library.setlists.map {
                        if (it.id == setlistId) {
                            it.copy(
                                name = name.trim().ifEmpty { it.name },
                                songIds = it.songIds + song.id,
                            )
                        } else it
                    }))
                })
            }
        }
    })
}

@Composable
fun EditSongsScreen(
    library: LiveperfLibrary,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onSave: (LiveperfLibrary) -> Unit,
) {
    var newTitle by remember { mutableStateOf("") }
    ScreenScaffold(title = "Canciones", onBack = onBack, content = {
        Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DarkField(newTitle, { newTitle = it }, "Nueva canción", modifier = Modifier.weight(1f))
                PrimaryButton("Añadir") {
                    val title = newTitle.trim()
                    if (title.isEmpty()) return@PrimaryButton
                    onSave(
                        library.copy(
                            songs = library.songs + Song(id = UUID.randomUUID().toString(), title = title),
                        ),
                    )
                    newTitle = ""
                }
            }
            LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
                items(library.songs, key = { it.id }) { s ->
                    ListRow(
                        title = s.title,
                        subtitle = buildString {
                            s.bpm?.let { append("${it} BPM · ") }
                            if (s.key.isNotBlank()) append(s.key)
                            if (s.instruments.isNotEmpty()) append(" · ${s.instruments.size} instr.")
                        },
                        onClick = { onOpen(s.id) },
                        onDelete = {
                            val setlists = library.setlists.map { sl ->
                                sl.copy(songIds = sl.songIds.filterNot { it == s.id })
                            }
                            onSave(
                                library.copy(
                                    songs = library.songs.filterNot { it.id == s.id },
                                    setlists = setlists,
                                ),
                            )
                        },
                    )
                }
            }
        }
    })
}

@Composable
fun EditSongDetailScreen(
    library: LiveperfLibrary,
    songId: String,
    onBack: () -> Unit,
    onSave: (LiveperfLibrary) -> Unit,
) {
    val song = library.song(songId) ?: return
    var title by remember(song.id) { mutableStateOf(song.title) }
    var bpmText by remember(song.id) { mutableStateOf(song.bpm?.toString().orEmpty()) }
    var key by remember(song.id) { mutableStateOf(song.key) }
    var notes by remember(song.id) { mutableStateOf(song.instrumentNotes) }
    var filter by remember { mutableStateOf("") }

    fun persist(instruments: List<Int> = song.instruments) {
        onSave(
            library.copy(
                songs = library.songs.map {
                    if (it.id != songId) it else it.copy(
                        title = title.trim().ifEmpty { it.title },
                        bpm = bpmText.toIntOrNull(),
                        key = key,
                        instrumentNotes = notes,
                        instruments = instruments,
                    )
                },
            ),
        )
    }

    val current = library.song(songId) ?: song

    ScreenScaffold(title = "Canción", onBack = onBack, content = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DarkField(title, { title = it }, "Título")
            DarkField(bpmText, { bpmText = it.filter { ch -> ch.isDigit() } }, "BPM")
            DarkField(key, { key = it }, "Tonalidad")
            DarkField(notes, { notes = it }, "Cambios de instrumento (notas)", singleLine = false)
            PrimaryButton("Guardar datos") { persist() }

            Text("Instrumentos V3 (canal 1)", color = Accent, fontWeight = FontWeight.SemiBold)
            current.instruments.forEach { pc ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(V3SoundCatalog.label(pc), color = Soft, modifier = Modifier.weight(1f))
                    PrimaryButton("Quitar") {
                        persist(current.instruments.filterNot { it == pc })
                    }
                }
            }

            DarkField(filter, { filter = it }, "Buscar sonido V3")
            val q = filter.trim().lowercase()
            val matches = V3SoundCatalog.sounds.filter {
                q.isEmpty() || it.name.lowercase().contains(q) || it.program.toString() == q
            }.filter { it.program !in current.instruments }.take(40)
            Column(modifier = Modifier.heightIn(max = 220.dp)) {
                matches.forEach { sound ->
                    Text(
                        text = "${sound.program} · ${sound.name}",
                        color = Soft,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(PanelDim, RoundedCornerShape(6.dp))
                            .clickable { persist(current.instruments + sound.program) }
                            .padding(10.dp),
                    )
                }
            }
        }
    })
}
