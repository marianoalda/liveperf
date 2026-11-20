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
fun EditHubScreen(
    onBack: () -> Unit,
    onGroups: () -> Unit,
    onSongs: () -> Unit,
    onImportJson: () -> Unit,
) {
    ScreenScaffold(title = "Modo edición", onBack = onBack, content = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ListRow(
                "Grupos",
                "Definir: grupo → setlist → canciones → instrumentos",
                onClick = onGroups,
                compact = true,
            )
            ListRow(
                "Canciones",
                "Atajo para retocar BPM, tonalidad o instrumentos",
                onClick = onSongs,
                compact = true,
            )
            ListRow(
                "Importar JSON",
                "Reemplaza la biblioteca desde un fichero del móvil",
                onClick = onImportJson,
                compact = true,
            )
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
                        subtitle = "${g.setlistIds.size} setlists · tocar para editar",
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
    onOpenSetlist: (String) -> Unit,
    onSave: (LiveperfLibrary) -> Unit,
) {
    val group = library.group(groupId) ?: return
    var name by remember(group.id) { mutableStateOf(group.name) }
    var newSetlistName by remember { mutableStateOf("") }

    fun updateGroup(transform: (Group) -> Group) {
        onSave(
            library.copy(
                groups = library.groups.map {
                    if (it.id == groupId) transform(it.copy(name = name.trim().ifEmpty { it.name })) else it
                },
            ),
        )
    }

    ScreenScaffold(title = group.name, onBack = onBack, content = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Grupo", color = Soft.copy(alpha = 0.7f), fontSize = 13.sp)
            DarkField(name, { name = it }, "Nombre del grupo")
            PrimaryButton("Guardar nombre") { updateGroup { it } }

            Text("Setlists de este grupo", color = Accent, fontWeight = FontWeight.SemiBold)
            Text(
                "Se crean ya asignadas a este grupo. Toca una para definir canciones.",
                color = Soft.copy(alpha = 0.7f),
                fontSize = 13.sp,
            )
            library.setlistsInGroup(groupId).forEach { sl ->
                ListRow(
                    title = sl.name,
                    subtitle = "${sl.songIds.size} canciones",
                    onClick = { onOpenSetlist(sl.id) },
                    onDelete = {
                        updateGroup { g -> g.copy(setlistIds = g.setlistIds.filterNot { it == sl.id }) }
                    },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DarkField(
                    newSetlistName,
                    { newSetlistName = it },
                    "Nueva setlist en ${name.ifBlank { group.name }}",
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton("Crear") {
                    val n = newSetlistName.trim()
                    if (n.isEmpty()) return@PrimaryButton
                    val sl = Setlist(name = n)
                    onSave(
                        library.copy(
                            setlists = library.setlists + sl,
                            groups = library.groups.map {
                                if (it.id == groupId) {
                                    it.copy(
                                        name = name.trim().ifEmpty { it.name },
                                        setlistIds = it.setlistIds + sl.id,
                                    )
                                } else it
                            },
                        ),
                    )
                    newSetlistName = ""
                    onOpenSetlist(sl.id)
                }
            }
        }
    })
}

@Composable
fun EditSetlistDetailScreen(
    library: LiveperfLibrary,
    setlistId: String,
    parentGroupId: String,
    onBack: () -> Unit,
    onOpenSong: (String) -> Unit,
    onSave: (LiveperfLibrary) -> Unit,
) {
    val setlist = library.setlist(setlistId) ?: return
    val group = library.group(parentGroupId)
    var name by remember(setlist.id) { mutableStateOf(setlist.name) }
    var newSongTitle by remember { mutableStateOf("") }

    fun updateSetlist(songIds: List<String> = setlist.songIds) {
        onSave(
            library.copy(
                setlists = library.setlists.map {
                    if (it.id == setlistId) {
                        it.copy(name = name.trim().ifEmpty { it.name }, songIds = songIds)
                    } else it
                },
            ),
        )
    }

    val crumb = library.breadcrumb(groupId = parentGroupId, setlistId = setlistId)

    ScreenScaffold(title = setlist.name, onBack = onBack, content = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(crumb, color = Soft.copy(alpha = 0.75f), fontSize = 13.sp)
            if (group != null) {
                Text(
                    "Padre: grupo «${group.name}» (asignado al crear / abrir desde el grupo)",
                    color = Soft.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                )
            }
            DarkField(name, { name = it }, "Nombre de la setlist")
            PrimaryButton("Guardar nombre") { updateSetlist() }

            Text("Canciones (orden = directo)", color = Accent, fontWeight = FontWeight.SemiBold)
            Text(
                "Al crear una canción se abre para completar BPM, tonalidad e instrumentos.",
                color = Soft.copy(alpha = 0.7f),
                fontSize = 13.sp,
            )
            setlist.songIds.mapNotNull { id -> library.song(id)?.let { id to it } }
                .forEachIndexed { index, (id, song) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Panel, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenSong(id) },
                        ) {
                            Text("${index + 1}. ${song.title}", color = Soft, fontSize = 16.sp)
                            Text(
                                buildString {
                                    song.bpm?.let { append("${it} BPM · ") }
                                    if (song.key.isNotBlank()) append(song.key)
                                    if (song.instruments.isNotEmpty()) {
                                        append(" · ${song.instruments.size} instr.")
                                    }
                                }.ifBlank { "tocar para completar" },
                                color = Soft.copy(alpha = 0.65f),
                                fontSize = 12.sp,
                            )
                        }
                        PrimaryButton("↑") {
                            if (index == 0) return@PrimaryButton
                            val ids = setlist.songIds.toMutableList()
                            val tmp = ids[index - 1]
                            ids[index - 1] = ids[index]
                            ids[index] = tmp
                            updateSetlist(ids)
                        }
                        PrimaryButton("↓") {
                            if (index >= setlist.songIds.lastIndex) return@PrimaryButton
                            val ids = setlist.songIds.toMutableList()
                            val tmp = ids[index + 1]
                            ids[index + 1] = ids[index]
                            ids[index] = tmp
                            updateSetlist(ids)
                        }
                        PrimaryButton("Quitar") {
                            updateSetlist(setlist.songIds.filterNot { it == id })
                        }
                    }
                }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DarkField(
                    newSongTitle,
                    { newSongTitle = it },
                    "Nueva canción en ${name.ifBlank { setlist.name }}",
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton("Crear") {
                    val title = newSongTitle.trim()
                    if (title.isEmpty()) return@PrimaryButton
                    val song = Song(id = UUID.randomUUID().toString(), title = title)
                    onSave(
                        library.copy(
                            songs = library.songs + song,
                            setlists = library.setlists.map {
                                if (it.id == setlistId) {
                                    it.copy(
                                        name = name.trim().ifEmpty { it.name },
                                        songIds = it.songIds + song.id,
                                    )
                                } else it
                            },
                        ),
                    )
                    newSongTitle = ""
                    onOpenSong(song.id)
                }
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
    ScreenScaffold(title = "Canciones", onBack = onBack, content = {
        Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
            Text(
                "Atajo para retocar. Las canciones nuevas se crean dentro de una setlist (desde el grupo).",
                color = Soft.copy(alpha = 0.75f),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn {
                items(library.songs, key = { it.id }) { s ->
                    val parents = library.setlistsContainingSong(s.id)
                    val path = when {
                        parents.isEmpty() -> "Sin setlist"
                        parents.size == 1 -> library.breadcrumb(setlistId = parents.first().id, songId = s.id)
                        else -> parents.joinToString(", ") { it.name }
                    }
                    ListRow(
                        title = s.title,
                        subtitle = buildString {
                            append(path)
                            s.bpm?.let { append(" · ${it} BPM") }
                            if (s.key.isNotBlank()) append(" · ${s.key}")
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
    parentSetlistId: String?,
    parentGroupId: String?,
    onBack: () -> Unit,
    onSave: (LiveperfLibrary) -> Unit,
) {
    val song = library.song(songId) ?: return
    var title by remember(song.id) { mutableStateOf(song.title) }
    var bpmText by remember(song.id) { mutableStateOf(song.bpm?.toString().orEmpty()) }
    var key by remember(song.id) { mutableStateOf(song.key) }
    var notes by remember(song.id) { mutableStateOf(song.instrumentNotes) }
    var filter by remember { mutableStateOf("") }

    fun persist(instruments: List<Int> = library.song(songId)?.instruments ?: song.instruments) {
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
    val crumb = library.breadcrumb(
        groupId = parentGroupId,
        setlistId = parentSetlistId,
        songId = songId,
    ).ifBlank { song.title }

    ScreenScaffold(title = song.title, onBack = onBack, content = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(crumb, color = Soft.copy(alpha = 0.75f), fontSize = 13.sp)
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

            DarkField(filter, { filter = it }, "Buscar sonido V3 (nombre)")
            val q = filter.trim().lowercase()
            val matches = V3SoundCatalog.sounds.filter {
                q.isEmpty() || it.name.lowercase().contains(q) || it.program.toString() == q
            }.filter { it.program !in current.instruments }.take(40)
            Column(modifier = Modifier.heightIn(max = 220.dp)) {
                matches.forEach { sound ->
                    Text(
                        text = sound.name,
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
