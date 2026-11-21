package com.marianoalda.liveperf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import kotlin.concurrent.thread

@Composable
fun EditHubScreen(
    onBack: () -> Unit,
    onGroups: () -> Unit,
    onSongs: () -> Unit,
    onLabels: () -> Unit,
    onImportJson: () -> Unit,
    onExportJson: () -> Unit,
    onDownloadGithub: () -> Unit,
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
                "Etiquetas",
                "Renombrar y remapear sonidos V3 de las etiquetas de botón",
                onClick = onLabels,
                compact = true,
            )
            ListRow(
                "Descargar desde GitHub",
                "libraries/ del repo → library.json local del teléfono",
                onClick = onDownloadGithub,
                compact = true,
            )
            ListRow(
                "Importar JSON",
                "Reemplaza la biblioteca desde un fichero del móvil",
                onClick = onImportJson,
                compact = true,
            )
            ListRow(
                "Exportar JSON",
                "Guarda el estado actual (instrumentos por nombre)",
                onClick = onExportJson,
                compact = true,
            )
        }
    })
}

@Composable
fun OverwriteLocalLibraryDialog(
    title: String,
    detail: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                "Se sobrescribirá el fichero local del teléfono ($detail). " +
                    "La biblioteca actual de la app se reemplazará.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Sobrescribir") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
fun EditGitHubDownloadScreen(
    defaultFileName: String,
    onBack: () -> Unit,
    onDownloaded: (GitHubLibraryFile, String) -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var files by remember { mutableStateOf<List<GitHubLibraryFile>>(emptyList()) }
    var pending by remember { mutableStateOf<GitHubLibraryFile?>(null) }
    var downloading by remember { mutableStateOf(false) }

    fun refresh() {
        loading = true
        error = null
        thread {
            try {
                val list = GitHubLibraries.listJsonFiles()
                // post to main via Compose state — Activity will recompose if we set on main
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    files = list
                    loading = false
                    if (list.isEmpty()) error = "No hay JSON en libraries/"
                }
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    loading = false
                    error = e.message ?: e.javaClass.simpleName
                }
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    pending?.let { file ->
        OverwriteLocalLibraryDialog(
            title = "Descargar ${file.name}",
            detail = defaultFileName,
            onDismiss = { if (!downloading) pending = null },
            onConfirm = {
                downloading = true
                thread {
                    try {
                        val text = GitHubLibraries.download(file)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            downloading = false
                            pending = null
                            onDownloaded(file, text)
                        }
                    } catch (e: Exception) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            downloading = false
                            pending = null
                            error = "Descarga fallida: ${e.message ?: e.javaClass.simpleName}"
                        }
                    }
                }
            },
        )
    }

    ScreenScaffold(title = "GitHub · libraries/", onBack = onBack, content = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Elige un JSON del repositorio. Se guardará como $defaultFileName en el almacenamiento interno de la app y reemplazará la biblioteca local.",
                color = Soft.copy(alpha = 0.75f),
                fontSize = 13.sp,
            )
            when {
                loading -> Text("Cargando lista…", color = Soft)
                downloading -> Text("Descargando…", color = Accent)
                error != null -> {
                    Text(error!!, color = Danger)
                    PrimaryButton("Reintentar") { refresh() }
                }
                else -> {
                    files.forEach { f ->
                        ListRow(
                            title = f.name,
                            subtitle = if (f.size > 0) "${f.size} B" else null,
                            onClick = { pending = f },
                            compact = true,
                        )
                    }
                }
            }
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

@OptIn(ExperimentalFoundationApi::class)
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
    var editingProgram by remember { mutableStateOf<Int?>(null) }

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

    editingProgram?.let { pc ->
        LabelEditDialog(
            program = pc,
            currentLabel = library.customLabel(pc).orEmpty(),
            v3Name = library.v3Name(pc),
            onDismiss = { editingProgram = null },
            onConfirm = { label ->
                val name = V3SoundCatalog.find(pc)?.name
                if (name != null) {
                    onSave(library.withInstrumentLabel(name, label))
                }
                editingProgram = null
            },
        )
    }

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
            Text(
                "Mantén pulsado para editar la etiqueta propia del botón.",
                color = Soft.copy(alpha = 0.7f),
                fontSize = 13.sp,
            )
            current.instruments.forEach { pc ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { editingProgram = pc },
                        )
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(library.buttonTitle(pc), color = Soft, fontWeight = FontWeight.SemiBold)
                        Text(
                            "PC $pc · ${library.v3Name(pc)}",
                            color = Soft.copy(alpha = 0.65f),
                            fontSize = 12.sp,
                        )
                    }
                    PrimaryButton("Quitar") {
                        persist(current.instruments.filterNot { it == pc })
                    }
                }
            }

            V3InstrumentPicker(
                excludePrograms = current.instruments.toSet(),
                onPick = { sound -> persist(current.instruments + sound.program) },
            )
        }
    })
}
