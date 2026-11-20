package com.marianoalda.liveperf

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class LibraryImportResult(
    val library: LiveperfLibrary,
    val warnings: List<String>,
)

class LibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): LiveperfLibrary {
        val raw = prefs.getString(KEY, null) ?: return seedLibrary().also { save(it) }
        return try {
            fromStorageJson(JSONObject(raw))
        } catch (_: Exception) {
            seedLibrary().also { save(it) }
        }
    }

    fun save(library: LiveperfLibrary) {
        prefs.edit().putString(KEY, toStorageJson(library).toString()).apply()
    }

    /** Hierarchical import: groups → setlists → songs; instruments by V3 name (or PC number). */
    fun importHierarchy(jsonText: String): LibraryImportResult {
        val root = JSONObject(jsonText)
        val warnings = mutableListOf<String>()
        val songs = linkedMapOf<String, Song>()
        val setlists = linkedMapOf<String, Setlist>()
        val groups = mutableListOf<Group>()

        fun resolveInstruments(title: String, arr: JSONArray?): List<Int> {
            if (arr == null) return emptyList()
            val out = mutableListOf<Int>()
            for (i in 0 until arr.length()) {
                val raw = arr.get(i)
                val token = when (raw) {
                    is Number -> raw.toInt().toString()
                    else -> raw.toString()
                }
                val sound = V3SoundCatalog.findByName(token)
                if (sound == null) {
                    warnings.add("Canción \"$title\": instrumento desconocido \"$token\"")
                } else if (sound.program !in out) {
                    out.add(sound.program)
                }
            }
            return out
        }

        fun parseSong(o: JSONObject): Song {
            val title = o.getString("title").trim()
            val bpmVal = o.opt("bpm")
            val bpm = when (bpmVal) {
                null, JSONObject.NULL -> null
                is Number -> bpmVal.toInt()
                else -> o.optString("bpm").toIntOrNull()
            }
            val song = Song(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                title = title,
                bpm = bpm,
                key = o.optString("key"),
                instrumentNotes = o.optString("instrumentNotes").ifBlank {
                    o.optString("notes")
                },
                instruments = resolveInstruments(title, o.optJSONArray("instruments")),
            )
            songs[song.id] = song
            return song
        }

        fun parseSetlist(o: JSONObject): Setlist {
            val name = o.getString("name").trim()
            val songIds = mutableListOf<String>()
            val nested = o.optJSONArray("songs")
            if (nested != null) {
                for (i in 0 until nested.length()) {
                    songIds.add(parseSong(nested.getJSONObject(i)).id)
                }
            }
            val refs = o.optJSONArray("songIds")
            if (refs != null) {
                for (i in 0 until refs.length()) {
                    songIds.add(refs.getString(i))
                }
            }
            val setlist = Setlist(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = name,
                songIds = songIds.distinct(),
            )
            setlists[setlist.id] = setlist
            return setlist
        }

        val groupsArr = root.optJSONArray("groups") ?: JSONArray()
        for (i in 0 until groupsArr.length()) {
            val o = groupsArr.getJSONObject(i)
            val name = o.getString("name").trim()
            val setlistIds = mutableListOf<String>()
            val nested = o.optJSONArray("setlists")
            if (nested != null) {
                for (j in 0 until nested.length()) {
                    setlistIds.add(parseSetlist(nested.getJSONObject(j)).id)
                }
            }
            val refs = o.optJSONArray("setlistIds")
            if (refs != null) {
                for (j in 0 until refs.length()) {
                    setlistIds.add(refs.getString(j))
                }
            }
            groups.add(
                Group(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = name,
                    setlistIds = setlistIds.distinct(),
                ),
            )
        }

        // Flat sections optional (merge / orphans)
        root.optJSONArray("songs")?.let { arr ->
            for (i in 0 until arr.length()) parseSong(arr.getJSONObject(i))
        }
        root.optJSONArray("setlists")?.let { arr ->
            for (i in 0 until arr.length()) parseSetlist(arr.getJSONObject(i))
        }

        val library = LiveperfLibrary(
            groups = groups,
            setlists = setlists.values.toList(),
            songs = songs.values.toList(),
        )
        return LibraryImportResult(library = library, warnings = warnings)
    }

    private fun seedLibrary(): LiveperfLibrary {
        val song = Song(
            id = UUID.randomUUID().toString(),
            title = "Ejemplo",
            bpm = 120,
            key = "C",
            instrumentNotes = "Intro piano, estribillo órgano",
            instruments = listOf(0, 78),
        )
        val setlist = Setlist(
            id = UUID.randomUUID().toString(),
            name = "Setlist demo",
            songIds = listOf(song.id),
        )
        val group = Group(
            id = UUID.randomUUID().toString(),
            name = "Grupo demo",
            setlistIds = listOf(setlist.id),
        )
        return LiveperfLibrary(
            groups = listOf(group),
            setlists = listOf(setlist),
            songs = listOf(song),
        )
    }

    /** Internal storage keeps program numbers for MIDI. */
    private fun toStorageJson(lib: LiveperfLibrary): JSONObject {
        fun songsArr() = JSONArray().also { arr ->
            lib.songs.forEach { s ->
                arr.put(
                    JSONObject()
                        .put("id", s.id)
                        .put("title", s.title)
                        .put("bpm", s.bpm ?: JSONObject.NULL)
                        .put("key", s.key)
                        .put("instrumentNotes", s.instrumentNotes)
                        .put("instruments", JSONArray(s.instruments)),
                )
            }
        }
        fun setlistsArr() = JSONArray().also { arr ->
            lib.setlists.forEach { s ->
                arr.put(
                    JSONObject()
                        .put("id", s.id)
                        .put("name", s.name)
                        .put("songIds", JSONArray(s.songIds)),
                )
            }
        }
        fun groupsArr() = JSONArray().also { arr ->
            lib.groups.forEach { g ->
                arr.put(
                    JSONObject()
                        .put("id", g.id)
                        .put("name", g.name)
                        .put("setlistIds", JSONArray(g.setlistIds)),
                )
            }
        }
        return JSONObject()
            .put("groups", groupsArr())
            .put("setlists", setlistsArr())
            .put("songs", songsArr())
    }

    private fun fromStorageJson(obj: JSONObject): LiveperfLibrary {
        val songs = buildList {
            val arr = obj.optJSONArray("songs") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val bpmVal = o.opt("bpm")
                val bpm = when (bpmVal) {
                    null, JSONObject.NULL -> null
                    is Number -> bpmVal.toInt()
                    else -> o.optString("bpm").toIntOrNull()
                }
                val instruments = buildList {
                    val ia = o.optJSONArray("instruments") ?: JSONArray()
                    for (j in 0 until ia.length()) {
                        val raw = ia.get(j)
                        when (raw) {
                            is Number -> add(raw.toInt())
                            else -> V3SoundCatalog.findByName(raw.toString())?.program?.let { add(it) }
                        }
                    }
                }
                add(
                    Song(
                        id = o.getString("id"),
                        title = o.getString("title"),
                        bpm = bpm,
                        key = o.optString("key"),
                        instrumentNotes = o.optString("instrumentNotes"),
                        instruments = instruments,
                    ),
                )
            }
        }
        val setlists = buildList {
            val arr = obj.optJSONArray("setlists") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ids = buildList {
                    val a = o.optJSONArray("songIds") ?: JSONArray()
                    for (j in 0 until a.length()) add(a.getString(j))
                }
                add(Setlist(id = o.getString("id"), name = o.getString("name"), songIds = ids))
            }
        }
        val groups = buildList {
            val arr = obj.optJSONArray("groups") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ids = buildList {
                    val a = o.optJSONArray("setlistIds") ?: JSONArray()
                    for (j in 0 until a.length()) add(a.getString(j))
                }
                add(Group(id = o.getString("id"), name = o.getString("name"), setlistIds = ids))
            }
        }
        return LiveperfLibrary(groups = groups, setlists = setlists, songs = songs)
    }

    companion object {
        private const val PREFS = "liveperf_library"
        private const val KEY = "library_json"
    }
}
