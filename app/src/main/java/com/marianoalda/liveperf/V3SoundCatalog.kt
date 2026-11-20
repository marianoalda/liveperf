package com.marianoalda.liveperf

import android.content.Context
import org.json.JSONObject

/** V3 Grand Piano Pro sound list, Bank MSB 0 (Rev. 01.Feb.2026). Loaded from assets. */
data class V3Sound(val program: Int, val name: String)

object V3SoundCatalog {
    private const val ASSET = "v3sound-grandpianopro.json"

    @Volatile
    private var soundsInternal: List<V3Sound> = emptyList()

    val sounds: List<V3Sound>
        get() = soundsInternal

    private val byProgram: Map<Int, V3Sound>
        get() = soundsInternal.associateBy { it.program }

    private val byName: Map<String, V3Sound>
        get() = soundsInternal.associateBy { it.name.lowercase() }

    fun init(context: Context) {
        if (soundsInternal.isNotEmpty()) return
        synchronized(this) {
            if (soundsInternal.isNotEmpty()) return
            soundsInternal = context.assets.open(ASSET).bufferedReader().use { reader ->
                val root = JSONObject(reader.readText())
                val arr = root.getJSONArray("sounds")
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        add(V3Sound(program = o.getInt("program"), name = o.getString("name")))
                    }
                }
            }
        }
    }

    fun find(program: Int): V3Sound? = byProgram[program]

    fun findByName(name: String): V3Sound? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        byProgram[trimmed.toIntOrNull()]?.let { return it }
        return byName[trimmed.lowercase()]
    }

    fun label(program: Int): String =
        find(program)?.let { "${it.name} (PC ${it.program})" } ?: "PC $program"
}
