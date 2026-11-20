package com.marianoalda.liveperf

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class LibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): LiveperfLibrary {
        val raw = prefs.getString(KEY, null) ?: return seedLibrary().also { save(it) }
        return try {
            fromJson(JSONObject(raw))
        } catch (_: Exception) {
            seedLibrary().also { save(it) }
        }
    }

    fun save(library: LiveperfLibrary) {
        prefs.edit().putString(KEY, toJson(library).toString()).apply()
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

    private fun toJson(lib: LiveperfLibrary): JSONObject {
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

    private fun fromJson(obj: JSONObject): LiveperfLibrary {
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
                    for (j in 0 until ia.length()) add(ia.getInt(j))
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
