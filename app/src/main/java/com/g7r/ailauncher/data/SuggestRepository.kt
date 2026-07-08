package com.g7r.ailauncher.data

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Google search suggestions (keyless; the `firefox` client returns a JSON array). */
object SuggestRepository {
    fun suggest(query: String, limit: Int = 5): List<String> {
        if (query.isBlank()) return emptyList()
        return runCatching {
            val q = URLEncoder.encode(query, "UTF-8")
            val url = "https://suggestqueries.google.com/complete/search?client=firefox&hl=ja&q=$q"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            conn.inputStream.use { stream ->
                val text = stream.readBytes().decodeToString()
                val suggestions = JSONArray(text).getJSONArray(1)
                (0 until minOf(limit, suggestions.length())).map { suggestions.getString(it) }
            }
        }.getOrDefault(emptyList())
    }
}
