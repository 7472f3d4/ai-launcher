package com.g7r.ailauncher.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Lightweight persistence (SharedPreferences + JSON) for stats, favorites and category overrides. */
class Storage(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("ai_launcher", Context.MODE_PRIVATE)

    // ---- launch stats (keyed by package name) ----
    fun loadStats(): MutableMap<String, AppStat> {
        val raw = prefs.getString(KEY_STATS, null) ?: return mutableMapOf()
        return runCatching {
            val obj = JSONObject(raw)
            val result = mutableMapOf<String, AppStat>()
            for (key in obj.keys()) result[key] = AppStat.fromJson(obj.getJSONObject(key))
            result
        }.getOrDefault(mutableMapOf())
    }

    fun saveStats(stats: Map<String, AppStat>) {
        val obj = JSONObject()
        for ((k, v) in stats) obj.put(k, v.toJson())
        prefs.edit().putString(KEY_STATS, obj.toString()).apply()
    }

    // ---- favorites (ordered, unique package names; order = pin order for the dock) ----
    fun loadFavorites(): MutableList<String> {
        val raw = prefs.getString(KEY_FAVORITES, null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) out.add(arr.getString(i))
            out
        }.getOrDefault(mutableListOf())
    }

    fun saveFavorites(favorites: List<String>) {
        prefs.edit().putString(KEY_FAVORITES, JSONArray(favorites).toString()).apply()
    }

    // ---- Todoist API token ----
    fun loadTodoistToken(): String? = prefs.getString(KEY_TODOIST_TOKEN, null)

    fun saveTodoistToken(token: String) {
        prefs.edit().putString(KEY_TODOIST_TOKEN, token.trim()).apply()
    }

    // ---- settings ----
    fun loadNewsEnabled(): Boolean = prefs.getBoolean(KEY_NEWS, true)

    fun saveNewsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NEWS, enabled).apply()
    }

    // ---- hosted App Widgets on the home screen (grid cells) ----
    fun loadWidgets(): MutableList<WidgetEntry> {
        val raw = prefs.getString(KEY_WIDGETS, null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            val out = mutableListOf<WidgetEntry>()
            var legacyRow = 0
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.has("c")) {
                    out.add(
                        WidgetEntry(
                            id = o.getInt("id"),
                            col = o.getInt("c"),
                            row = o.getInt("r"),
                            spanW = o.getInt("w"),
                            spanH = o.getInt("h")
                        )
                    )
                } else {
                    // Pre-grid format: full-width stack entries with a pixel height in "h".
                    val spanH = Math.round(o.getInt("h") / WidgetGrid.TARGET_CELL_HEIGHT_DP.toFloat())
                        .coerceIn(1, 4)
                    out.add(WidgetEntry(o.getInt("id"), 0, legacyRow, WidgetGrid.COLUMNS, spanH))
                    legacyRow += spanH
                }
            }
            out
        }.getOrDefault(mutableListOf())
    }

    fun saveWidgets(widgets: List<WidgetEntry>) {
        val arr = JSONArray()
        for (w in widgets) {
            arr.put(
                JSONObject()
                    .put("id", w.id)
                    .put("c", w.col)
                    .put("r", w.row)
                    .put("w", w.spanW)
                    .put("h", w.spanH)
            )
        }
        prefs.edit().putString(KEY_WIDGETS, arr.toString()).apply()
    }

    // ---- category overrides (package name -> AppCategory.name) ----
    fun loadOverrides(): MutableMap<String, String> {
        val raw = prefs.getString(KEY_OVERRIDES, null) ?: return mutableMapOf()
        return runCatching {
            val obj = JSONObject(raw)
            val result = mutableMapOf<String, String>()
            for (key in obj.keys()) result[key] = obj.getString(key)
            result
        }.getOrDefault(mutableMapOf())
    }

    fun saveOverrides(overrides: Map<String, String>) {
        val obj = JSONObject()
        for ((k, v) in overrides) obj.put(k, v)
        prefs.edit().putString(KEY_OVERRIDES, obj.toString()).apply()
    }

    companion object {
        private const val KEY_STATS = "stats"
        private const val KEY_FAVORITES = "favorites_ordered"
        private const val KEY_OVERRIDES = "overrides"
        private const val KEY_NEWS = "news_enabled"
        private const val KEY_WIDGETS = "widgets"
        private const val KEY_TODOIST_TOKEN = "todoist_token"
    }
}
