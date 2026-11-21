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
    // Default on-device JSON (mirrors repo libraries/ JSON files).
    val defaultFile = java.io.File(context.filesDir, DEFAULT_FILE_NAME)

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
        runCatching {
            defaultFile.writeText(exportHierarchy(library), Charsets.UTF_8)
        }
    }

    fun hasLocalData(): Boolean =
        prefs.contains(KEY) || defaultFile.exists()

    /** Import JSON text into prefs + default local file. */
    fun replaceFromJsonText(jsonText: String): LibraryImportResult {
        val result = importHierarchy(jsonText)
        save(result.library)
        return result
    }

    /** Hierarchical import: groups → setlists → songs; instruments by V3 name (or PC number). */
    fun importHierarchy(jsonText: String): LibraryImportResult {
        val root = JSONObject(jsonText)
        val warnings = mutableListOf<String>()
        val songs = linkedMapOf<String, Song>()
        val setlists = linkedMapOf<String, Setlist>()
        val groups = mutableListOf<Group>()

        val labels = linkedMapOf<String, String>()
        fun readLabels(obj: JSONObject) {
            val map = obj.optJSONObject("instrumentLabels") ?: return
            map.keys().forEach { key ->
                val value = map.optString(key, "").trim()
                if (value.isNotEmpty()) labels[key] = value
            }
        }
        readLabels(root)

        fun resolveInstruments(title: String, arr: JSONArray?): List<Int> {
            if (arr == null) return emptyList()
            val out = mutableListOf<Int>()
            for (i in 0 until arr.length()) {
                val raw = arr.get(i)
                val token: String
                var inlineLabel: String? = null
                when (raw) {
                    is Number -> token = raw.toInt().toString()
                    is JSONObject -> {
                        token = raw.optString("name").ifBlank {
                            raw.optString("instrument")
                        }.ifBlank {
                            raw.opt("program")?.toString().orEmpty()
                        }
                        inlineLabel = raw.optString("label").trim().takeIf { it.isNotEmpty() }
                    }
                    else -> token = raw.toString()
                }
                val sound = V3SoundCatalog.findByName(token)
                if (sound == null) {
                    warnings.add("Canción \"$title\": instrumento desconocido \"$token\"")
                } else if (sound.program !in out) {
                    out.add(sound.program)
                    if (inlineLabel != null) labels[sound.name] = inlineLabel
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
            instrumentLabels = labels,
        )
        return LibraryImportResult(library = library, warnings = warnings)
    }

    /**
     * Hierarchical export for filesystem: instruments as V3 names (same shape as import).
     * Pretty-printed JSON.
     */
    fun exportHierarchy(lib: LiveperfLibrary): String {
        fun songObject(s: Song): JSONObject {
            val names = JSONArray()
            s.instruments.forEach { pc ->
                val v3 = V3SoundCatalog.find(pc)?.name ?: pc.toString()
                val label = lib.instrumentLabels[v3]?.trim().orEmpty()
                if (label.isNotEmpty()) {
                    names.put(JSONObject().put("name", v3).put("label", label))
                } else {
                    names.put(v3)
                }
            }
            return JSONObject()
                .put("id", s.id)
                .put("title", s.title)
                .put("bpm", s.bpm ?: JSONObject.NULL)
                .put("key", s.key)
                .put("instrumentNotes", s.instrumentNotes)
                .put("instruments", names)
        }

        val nestedSetlistIds = mutableSetOf<String>()
        val nestedSongIds = mutableSetOf<String>()
        val groupsArr = JSONArray()
        lib.groups.forEach { g ->
            val setlistsArr = JSONArray()
            g.setlistIds.mapNotNull { lib.setlist(it) }.forEach { sl ->
                nestedSetlistIds.add(sl.id)
                val songsArr = JSONArray()
                sl.songIds.mapNotNull { lib.song(it) }.forEach { song ->
                    nestedSongIds.add(song.id)
                    songsArr.put(songObject(song))
                }
                setlistsArr.put(
                    JSONObject()
                        .put("id", sl.id)
                        .put("name", sl.name)
                        .put("songs", songsArr),
                )
            }
            groupsArr.put(
                JSONObject()
                    .put("id", g.id)
                    .put("name", g.name)
                    .put("setlists", setlistsArr),
            )
        }

        val root = JSONObject().put("groups", groupsArr)
        if (lib.instrumentLabels.isNotEmpty()) {
            val labelsObj = JSONObject()
            lib.instrumentLabels.entries.sortedBy { it.key }.forEach { (k, v) ->
                if (v.isNotBlank()) labelsObj.put(k, v)
            }
            root.put("instrumentLabels", labelsObj)
        }

        val orphanSetlists = lib.setlists.filter { it.id !in nestedSetlistIds }
        if (orphanSetlists.isNotEmpty()) {
            val arr = JSONArray()
            orphanSetlists.forEach { sl ->
                val songsArr = JSONArray()
                sl.songIds.mapNotNull { lib.song(it) }.forEach { song ->
                    nestedSongIds.add(song.id)
                    songsArr.put(songObject(song))
                }
                arr.put(
                    JSONObject()
                        .put("id", sl.id)
                        .put("name", sl.name)
                        .put("songs", songsArr),
                )
            }
            root.put("setlists", arr)
        }

        val orphanSongs = lib.songs.filter { it.id !in nestedSongIds }
        if (orphanSongs.isNotEmpty()) {
            val arr = JSONArray()
            orphanSongs.forEach { arr.put(songObject(it)) }
            root.put("songs", arr)
        }

        return root.toString(2)
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
            instrumentLabels = mapOf(
                "Grand Piano BÖS 270 Classic" to "Piano",
                "Organ 776555678 slow" to "Órgano",
            ),
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
        val labelsObj = JSONObject()
        lib.instrumentLabels.forEach { (k, v) ->
            if (v.isNotBlank()) labelsObj.put(k, v)
        }
        return JSONObject()
            .put("groups", groupsArr())
            .put("setlists", setlistsArr())
            .put("songs", songsArr())
            .put("instrumentLabels", labelsObj)
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
                            is JSONObject -> {
                                val token = raw.optString("name").ifBlank {
                                    raw.opt("program")?.toString().orEmpty()
                                }
                                V3SoundCatalog.findByName(token)?.program?.let { add(it) }
                            }
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
        val labels = linkedMapOf<String, String>()
        obj.optJSONObject("instrumentLabels")?.let { map ->
            map.keys().forEach { key ->
                val value = map.optString(key, "").trim()
                if (value.isNotEmpty()) labels[key] = value
            }
        }
        // Also accept labels embedded in song instrument objects (storage edge case)
        obj.optJSONArray("songs")?.let { arr ->
            for (i in 0 until arr.length()) {
                val ia = arr.getJSONObject(i).optJSONArray("instruments") ?: continue
                for (j in 0 until ia.length()) {
                    val raw = ia.opt(j)
                    if (raw is JSONObject) {
                        val name = raw.optString("name")
                        val label = raw.optString("label").trim()
                        if (name.isNotBlank() && label.isNotEmpty()) labels[name] = label
                    }
                }
            }
        }
        return LiveperfLibrary(
            groups = groups,
            setlists = setlists,
            songs = songs,
            instrumentLabels = labels,
        )
    }

    companion object {
        private const val PREFS = "liveperf_library"
        private const val KEY = "library_json"
        const val DEFAULT_FILE_NAME = "library.json"
    }
}
