package com.marianoalda.liveperf

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class GitHubLibraryFile(
    val name: String,
    val size: Int,
    val downloadUrl: String,
)

object GitHubLibraries {
    private const val OWNER = "marianoalda"
    private const val REPO = "liveperf"
    private const val BRANCH = "main"
    private const val DIR = "libraries"

    private val listUrl =
        "https://api.github.com/repos/$OWNER/$REPO/contents/$DIR?ref=$BRANCH"

    fun listJsonFiles(): List<GitHubLibraryFile> {
        val body = httpGet(listUrl)
        val arr = JSONArray(body)
        val out = mutableListOf<GitHubLibraryFile>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("type") != "file") continue
            val name = o.getString("name")
            if (!name.endsWith(".json", ignoreCase = true)) continue
            if (name == ".gitkeep") continue
            out.add(
                GitHubLibraryFile(
                    name = name,
                    size = o.optInt("size"),
                    downloadUrl = o.optString("download_url").ifBlank {
                        "https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/$DIR/$name"
                    },
                ),
            )
        }
        return out.sortedBy { it.name.lowercase() }
    }

    fun download(file: GitHubLibraryFile): String = httpGet(file.downloadUrl)

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json, application/json, text/plain")
            setRequestProperty("User-Agent", "liveperf-android")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code: ${text.take(200)}")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }
}
