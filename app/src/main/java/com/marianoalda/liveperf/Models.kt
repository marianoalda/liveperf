package com.marianoalda.liveperf

import java.util.UUID

data class Group(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val setlistIds: List<String> = emptyList(),
)

data class Setlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val songIds: List<String> = emptyList(),
)

/**
 * @param instruments distinct V3 program numbers (channel 1), in display order.
 * @param instrumentNotes free text about when/how to change instruments.
 */
data class Song(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val bpm: Int? = null,
    val key: String = "",
    val instrumentNotes: String = "",
    val instruments: List<Int> = emptyList(),
)

data class LiveperfLibrary(
    val groups: List<Group> = emptyList(),
    val setlists: List<Setlist> = emptyList(),
    val songs: List<Song> = emptyList(),
    /** Custom button labels keyed by V3 documentation name. */
    val instrumentLabels: Map<String, String> = emptyMap(),
) {
    fun song(id: String): Song? = songs.firstOrNull { it.id == id }
    fun setlist(id: String): Setlist? = setlists.firstOrNull { it.id == id }
    fun group(id: String): Group? = groups.firstOrNull { it.id == id }

    fun v3Name(program: Int): String =
        V3SoundCatalog.find(program)?.name ?: "PC $program"

    fun customLabel(program: Int): String? {
        val name = V3SoundCatalog.find(program)?.name ?: return null
        return instrumentLabels[name]?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Button title: custom label, or "(?) " + V3 name when missing. */
    fun buttonTitle(program: Int): String {
        val custom = customLabel(program)
        return if (custom != null) custom else "(?) ${v3Name(program)}"
    }

    fun withInstrumentLabel(v3Name: String, label: String): LiveperfLibrary {
        val trimmed = label.trim()
        val next = instrumentLabels.toMutableMap()
        if (trimmed.isEmpty()) next.remove(v3Name) else next[v3Name] = trimmed
        return copy(instrumentLabels = next)
    }

    fun songsInSetlist(setlistId: String): List<Song> {
        val sl = setlist(setlistId) ?: return emptyList()
        return sl.songIds.mapNotNull { song(it) }
    }

    fun setlistsInGroup(groupId: String): List<Setlist> {
        val g = group(groupId) ?: return emptyList()
        return g.setlistIds.mapNotNull { setlist(it) }
    }

    fun groupsContainingSetlist(setlistId: String): List<Group> =
        groups.filter { setlistId in it.setlistIds }

    fun setlistsContainingSong(songId: String): List<Setlist> =
        setlists.filter { songId in it.songIds }

    /** Prefer explicit ids; otherwise infer a unique parent path. */
    fun breadcrumb(
        groupId: String? = null,
        setlistId: String? = null,
        songId: String? = null,
    ): String {
        val groupName = groupId?.let { group(it)?.name }
            ?: setlistId?.let { groupsContainingSetlist(it).singleOrNull()?.name }
            ?: songId?.let { sid ->
                setlistsContainingSong(sid).singleOrNull()?.let { sl ->
                    groupsContainingSetlist(sl.id).singleOrNull()?.name
                }
            }
        val setlistName = setlistId?.let { setlist(it)?.name }
            ?: songId?.let { setlistsContainingSong(it).singleOrNull()?.name }
        val songName = songId?.let { song(it)?.title }
        return listOfNotNull(groupName, setlistName, songName).joinToString(" › ")
    }

    fun songParentContext(songId: String): Pair<String?, String?> {
        val sl = setlistsContainingSong(songId).singleOrNull()
        val g = sl?.let { groupsContainingSetlist(it.id).singleOrNull() }
        return g?.id to sl?.id
    }
}

sealed class AppScreen {
    data object Home : AppScreen()
    data object EditHub : AppScreen()
    data object EditGitHubDownload : AppScreen()
    data object EditGroups : AppScreen()
    data class EditGroupDetail(val groupId: String) : AppScreen()
    data class EditSetlistDetail(
        val setlistId: String,
        val parentGroupId: String,
    ) : AppScreen()
    data object EditSongs : AppScreen()
    data class EditSongDetail(
        val songId: String,
        val parentSetlistId: String? = null,
        val parentGroupId: String? = null,
    ) : AppScreen()
    data object LivePickGroup : AppScreen()
    data class LivePickSetlist(val groupId: String) : AppScreen()
    data class LiveSong(
        val groupId: String,
        val setlistId: String,
        val songIndex: Int,
    ) : AppScreen()
}
