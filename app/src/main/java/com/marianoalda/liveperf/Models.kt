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
) {
    fun song(id: String): Song? = songs.firstOrNull { it.id == id }
    fun setlist(id: String): Setlist? = setlists.firstOrNull { it.id == id }
    fun group(id: String): Group? = groups.firstOrNull { it.id == id }

    fun songsInSetlist(setlistId: String): List<Song> {
        val sl = setlist(setlistId) ?: return emptyList()
        return sl.songIds.mapNotNull { song(it) }
    }

    fun setlistsInGroup(groupId: String): List<Setlist> {
        val g = group(groupId) ?: return emptyList()
        return g.setlistIds.mapNotNull { setlist(it) }
    }
}

sealed class AppScreen {
    data object Home : AppScreen()
    data object EditHub : AppScreen()
    data object EditGroups : AppScreen()
    data class EditGroupDetail(val groupId: String) : AppScreen()
    data object EditSetlists : AppScreen()
    data class EditSetlistDetail(val setlistId: String) : AppScreen()
    data object EditSongs : AppScreen()
    data class EditSongDetail(val songId: String) : AppScreen()
    data object LivePickGroup : AppScreen()
    data class LivePickSetlist(val groupId: String) : AppScreen()
    data class LiveSong(
        val groupId: String,
        val setlistId: String,
        val songIndex: Int,
    ) : AppScreen()
}
